package com.eoinknd16.nexus.ui.fx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What the phone is physically doing, in screen terms (x right, y down, z out
 * of the screen), in m/s². Shared by everything with physics; the sensors only
 * run while something on screen asks for them ([MotionSensors.Use]).
 */
object MotionSensors {
    /** Gravity as felt by objects inside the phone (where they'd fall). */
    val gravity = floatArrayOf(0f, 9.81f, 0f)
    /** The phone's own acceleration (shakes, bumps), without gravity. */
    val linear = floatArrayOf(0f, 0f, 0f)
    private var users = 0
    private var sm: SensorManager? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Android reports the reaction to gravity (pointing up); objects fall the other way.
            // Device y points up the screen, so screen-down is +device-y here.
            when (e.sensor.type) {
                Sensor.TYPE_GRAVITY -> { gravity[0] = -e.values[0]; gravity[1] = e.values[1]; gravity[2] = -e.values[2] }
                Sensor.TYPE_LINEAR_ACCELERATION -> { linear[0] = e.values[0]; linear[1] = -e.values[1]; linear[2] = e.values[2] }
                Sensor.TYPE_ACCELEROMETER -> {
                    // No fused sensors: split gravity off with a low-pass filter.
                    val a = 0.9f
                    for (k in 0..2) {
                        val v = if (k == 1) e.values[k] else -e.values[k]
                        gravity[k] = gravity[k] * a + v * (1 - a)
                        // What objects feel = gravity − the phone's own acceleration.
                        linear[k] = gravity[k] - v
                    }
                }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun start(context: Context) {
        if (users++ > 0) return
        val m = context.getSystemService(SensorManager::class.java) ?: return
        sm = m
        val grav = m.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val lin = m.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (grav != null && lin != null) {
            m.registerListener(listener, grav, SensorManager.SENSOR_DELAY_GAME)
            m.registerListener(listener, lin, SensorManager.SENSOR_DELAY_GAME)
        } else m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { m.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stop() {
        if (--users > 0) return
        users = 0
        sm?.unregisterListener(listener)
        linear.fill(0f)
    }

    /** Keeps the sensors running while the calling screen is showing. */
    @Composable
    fun Use() {
        val context = LocalContext.current
        LifecycleResumeEffect(Unit) {
            start(context.applicationContext)
            onPauseOrDispose { stop() }
        }
    }
}

/** Little "tock" buzzes for things hitting each other; strength follows the hit, rate-limited. */
class ImpactHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    private val amplitude = vibrator?.hasAmplitudeControl() == true
    private var last = 0L

    /** [strength] 0..1. */
    fun hit(strength: Float) {
        val v = vibrator ?: return
        if (strength < 0.12f) return
        val now = System.nanoTime()
        if (now - last < 70_000_000L) return
        last = now
        runCatching {
            if (amplitude) v.vibrate(VibrationEffect.createOneShot(if (strength > 0.6f) 10 else 6, (15 + strength * 140).toInt().coerceIn(1, 255)))
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && strength > 0.25f) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }
}

/**
 * Loose discs in a box, at real-world scale: they fall with the phone's actual
 * gravity, get thrown about when it's shaken, grip the glass when it's lying
 * flat (so a small tilt doesn't move them, a bigger one sends them sliding),
 * and spin when they glance off the walls or each other.
 *
 * Solved position-first (move, then push apart, then work out velocity from
 * how far things really moved), which keeps piles steady instead of jittering.
 * Everything goes to sleep once settled and only wakes when the forces on it
 * change. Positions are in px; [pxPerMeter] turns m/s² into px/s².
 */
class DiscSim(val n: Int) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    /** Spin, degrees and degrees/s (clockwise on screen). */
    val angle = FloatArray(n)
    val spin = FloatArray(n)
    var radius = 10f
    var w = 0f
        private set
    var h = 0f
        private set

    // Tunables (set from the tile's settings).
    var restitution = 0.45f
    var glassGrip = 1f
    var spinOn = true

    /** Strongest *new* impact this step, 0..1, for haptics (resting contact never counts). */
    var hardestHit = 0f
        private set
    var asleep = false
        private set

    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val pvx = FloatArray(n)
    private val pvy = FloatArray(n)
    // Who was touching what last step (index n..n+3 = the four walls), so a knock only counts once.
    private val touching = Array(n) { BooleanArray(n + 4) }
    private val touchingNow = Array(n) { BooleanArray(n + 4) }
    private var stillTime = 0f
    private var sleepAx = 0f
    private var sleepAy = 0f
    private var sleepNormal = 0f

    fun layout(width: Float, height: Float, r: Float) {
        val first = w == 0f
        w = width; h = height; radius = r
        if (first) {
            val cols = maxOf(1, (w / (r * 2)).toInt())
            for (i in 0 until n) {
                x[i] = r + (i % cols) * r * 2.05f + (i / cols % 2) * r * 0.5f
                y[i] = r + (i / cols) * r * 2.05f
                angle[i] = ((i * 37) % 30 - 15).toFloat()
            }
        }
        for (i in 0 until n) { x[i] = x[i].coerceIn(r, w - r); y[i] = y[i].coerceIn(r, h - r) }
        asleep = false
    }

    /** Wake everything (e.g. a sudden knock from the screen moving). */
    fun wake() { asleep = false; stillTime = 0f }

    /**
     * ax, ay: in-plane acceleration of everything (gravity + shake + the tile
     * moving), px/s². normal: how hard they're pressed onto the glass/back, px/s².
     * Returns whether anything moved (so the caller can skip a redraw).
     */
    fun step(dt: Float, ax: Float, ay: Float, normal: Float, pxPerMeter: Float): Boolean {
        if (w == 0f || n == 0) return false
        hardestHit = 0f
        val ppm = pxPerMeter
        // Asleep: stay put until the forces change noticeably (a tilt, a shake, a knock).
        if (asleep) {
            val change = hypot(ax - sleepAx, ay - sleepAy) + abs(normal - sleepNormal) * 0.5f
            if (change < 0.6f * ppm) return false
            asleep = false
            stillTime = 0f
        }
        val r = radius
        var vmax = 0f
        for (i in 0 until n) vmax = maxOf(vmax, hypot(vx[i], vy[i]))
        val sub = (((vmax + hypot(ax, ay) * dt) * dt) / (r * 0.25f)).toInt().coerceIn(3, 20)
        val sdt = dt / sub
        val muK = 0.22f * glassGrip
        val muS = 0.32f * glassGrip
        val restSpeed = 0.015f * ppm
        val bounceCut = 0.08f * ppm // gentler impacts than 8 cm/s don't bounce, so piles settle
        val hitCut = 0.12f * ppm // and only knocks harder than 12 cm/s buzz
        val stuckToGlass = hypot(ax, ay) < muS * normal
        val x0 = x.copyOf(); val y0 = y.copyOf(); val a0 = angle.copyOf()
        for (row in touchingNow) row.fill(false)

        repeat(sub) {
            // 1. Forces, friction against the glass, then move.
            for (i in 0 until n) {
                val speed = hypot(vx[i], vy[i])
                if (speed < restSpeed && stuckToGlass) { vx[i] = 0f; vy[i] = 0f }
                else {
                    vx[i] += ax * sdt; vy[i] += ay * sdt
                    val sp = hypot(vx[i], vy[i])
                    if (sp > 0f && normal > 0f) {
                        val drop = min(sp, muK * normal * sdt)
                        vx[i] -= vx[i] / sp * drop; vy[i] -= vy[i] / sp * drop
                    }
                }
                if (spinOn && spin[i] != 0f) {
                    val decel = (4f * muK * normal / (3f * r)) * 57.3f * sdt + 25f * sdt
                    spin[i] = if (abs(spin[i]) <= decel) 0f else spin[i] - Math.signum(spin[i]) * decel
                }
                pvx[i] = vx[i]; pvy[i] = vy[i]
                px[i] = x[i]; py[i] = y[i]
                x[i] += vx[i] * sdt; y[i] += vy[i] * sdt
                if (spinOn) angle[i] += spin[i] * sdt
            }
            // 2. Push everything out of the walls and each other, several passes.
            repeat(6) {
                for (i in 0 until n) {
                    if (x[i] < r) x[i] = r
                    if (x[i] > w - r) x[i] = w - r
                    if (y[i] < r) y[i] = r
                    if (y[i] > h - r) y[i] = h - r
                }
                for (i in 0 until n) for (j in i + 1 until n) {
                    val dx = x[j] - x[i]; val dy = y[j] - y[i]
                    val d2 = dx * dx + dy * dy
                    val minD = r * 2
                    if (d2 >= minD * minD) continue
                    val d = sqrt(d2)
                    val nx = if (d < 0.0001f) 1f else dx / d
                    val ny = if (d < 0.0001f) 0f else dy / d
                    val push = (minD - d) / 2
                    x[i] -= nx * push; y[i] -= ny * push
                    x[j] += nx * push; y[j] += ny * push
                }
            }
            // 3. Velocity = how far they actually got (so nothing keeps pushing into a wall or a pile).
            for (i in 0 until n) { vx[i] = (x[i] - px[i]) / sdt; vy[i] = (y[i] - py[i]) / sdt }
            // 4. Bounces, friction-spin and knocks at the contacts.
            val eps = r * 0.02f
            for (i in 0 until n) {
                if (x[i] <= r + eps) wallContact(i, 1f, 0f, n, r, bounceCut, hitCut, ppm)
                if (x[i] >= w - r - eps) wallContact(i, -1f, 0f, n + 1, r, bounceCut, hitCut, ppm)
                if (y[i] <= r + eps) wallContact(i, 0f, 1f, n + 2, r, bounceCut, hitCut, ppm)
                if (y[i] >= h - r - eps) wallContact(i, 0f, -1f, n + 3, r, bounceCut, hitCut, ppm)
            }
            for (i in 0 until n) for (j in i + 1 until n) {
                val dx = x[j] - x[i]; val dy = y[j] - y[i]
                val d = hypot(dx, dy)
                if (d > r * 2 + eps || d < 0.0001f) continue
                pairContact(i, j, dx / d, dy / d, r, bounceCut, hitCut, ppm)
            }
        }
        for (i in 0 until n) touchingNow[i].copyInto(touching[i])

        var moved = 0f
        var fastest = 0f
        for (i in 0 until n) {
            moved = maxOf(moved, abs(x[i] - x0[i]) + abs(y[i] - y0[i]) + abs(angle[i] - a0[i]) * 0.05f)
            fastest = maxOf(fastest, hypot(vx[i], vy[i]) + abs(spin[i]) * r / 57.3f)
        }
        // Settled for a moment: go to sleep until something changes.
        if (fastest < restSpeed * 1.5f) stillTime += dt else stillTime = 0f
        if (stillTime > 0.35f) {
            asleep = true
            for (i in 0 until n) { vx[i] = 0f; vy[i] = 0f; spin[i] = 0f }
            sleepAx = ax; sleepAy = ay; sleepNormal = normal
        }
        return moved > 0.05f
    }

    /** Disc i resting on / hitting a wall with inward normal (nx, ny). */
    private fun wallContact(i: Int, nx: Float, ny: Float, key: Int, r: Float, bounceCut: Float, hitCut: Float, ppm: Float) {
        val vnPre = pvx[i] * nx + pvy[i] * ny // approach speed before the push-out (negative = into the wall)
        val isNew = !touching[i][key]
        touchingNow[i][key] = true
        if (vnPre >= 0f) return
        val e = if (-vnPre < bounceCut) 0f else restitution
        val vn = vx[i] * nx + vy[i] * ny
        val target = -vnPre * e
        if (vn < target) { vx[i] += (target - vn) * nx; vy[i] += (target - vn) * ny }
        if (isNew && -vnPre > hitCut) hardestHit = maxOf(hardestHit, (-vnPre / (0.6f * ppm)).coerceAtMost(1f))
        if (!spinOn || -vnPre < bounceCut * 0.5f) return
        // Friction at the contact turns sliding along the wall into spin (solid disc: I = ½mr²).
        val jn = -vnPre * (1 + e)
        val tx = -ny; val ty = nx
        val rcx = -nx * r; val rcy = -ny * r
        val om = spin[i] / 57.3f
        val vt = (vx[i] - om * rcy) * tx + (vy[i] + om * rcx) * ty
        val jt = (-vt / 3f).coerceIn(-0.35f * jn, 0.35f * jn)
        vx[i] += jt * tx; vy[i] += jt * ty
        spin[i] += (rcx * (jt * ty) - rcy * (jt * tx)) / (0.5f * r * r) * 57.3f
    }

    /** Discs i and j touching along (nx, ny) from i to j; equal masses. */
    private fun pairContact(i: Int, j: Int, nx: Float, ny: Float, r: Float, bounceCut: Float, hitCut: Float, ppm: Float) {
        val isNew = !touching[i][j]
        touchingNow[i][j] = true
        val rvnPre = (pvx[j] - pvx[i]) * nx + (pvy[j] - pvy[i]) * ny
        if (rvnPre >= 0f) return
        val e = if (-rvnPre < bounceCut) 0f else restitution
        val rvn = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
        val target = -rvnPre * e
        if (rvn < target) {
            val dv = (target - rvn) / 2
            vx[i] -= dv * nx; vy[i] -= dv * ny
            vx[j] += dv * nx; vy[j] += dv * ny
        }
        if (isNew && -rvnPre > hitCut) hardestHit = maxOf(hardestHit, (-rvnPre / (0.8f * ppm)).coerceAtMost(1f))
        if (!spinOn || -rvnPre < bounceCut * 0.5f) return
        val jn = -rvnPre * (1 + e) / 2
        val tx = -ny; val ty = nx
        val wi = spin[i] / 57.3f; val wj = spin[j] / 57.3f
        val vti = (vx[i] - wi * (ny * r)) * tx + (vy[i] + wi * (nx * r)) * ty
        val vtj = (vx[j] - wj * (-ny * r)) * tx + (vy[j] + wj * (-nx * r)) * ty
        val jt = ((vtj - vti) / 6f).coerceIn(-0.35f * jn, 0.35f * jn)
        vx[i] += jt * tx; vy[i] += jt * ty
        vx[j] -= jt * tx; vy[j] -= jt * ty
        val inertia = 0.5f * r * r
        spin[i] += ((nx * r) * (jt * ty) - (ny * r) * (jt * tx)) / inertia * 57.3f
        spin[j] += ((-nx * r) * (-jt * ty) - (-ny * r) * (-jt * tx)) / inertia * 57.3f
    }
}
