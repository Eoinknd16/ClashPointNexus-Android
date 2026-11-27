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
        if (strength < 0.06f) return
        val now = System.nanoTime()
        if (now - last < 45_000_000L) return
        last = now
        runCatching {
            if (amplitude) v.vibrate(VibrationEffect.createOneShot(if (strength > 0.6f) 14 else 9, (30 + strength * 225).toInt().coerceIn(1, 255)))
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
 * Positions are in px; [pxPerMeter] turns the sensors' m/s² into px/s².
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
    var glassGrip = 1f // multiplies the glass friction
    var spinOn = true

    /** Strongest impact this step, 0..1, for haptics. */
    var hardestHit = 0f
        private set

    fun layout(width: Float, height: Float, r: Float) {
        val first = w == 0f
        w = width; h = height; radius = r
        if (first) {
            val cols = maxOf(1, (w / (r * 2)).toInt())
            for (i in 0 until n) {
                x[i] = r + (i % cols) * r * 2 + (i / cols % 2) * r * 0.5f
                y[i] = r + (i / cols) * r * 2
                angle[i] = ((i * 37) % 30 - 15).toFloat()
            }
        }
        for (i in 0 until n) { x[i] = x[i].coerceIn(r, w - r); y[i] = y[i].coerceIn(r, h - r) }
    }

    /**
     * ax, ay: in-plane acceleration of everything (gravity + shake + the tile
     * moving), px/s². normal: how hard they're pressed onto the glass/back, px/s².
     * Returns whether anything moved (so the caller can skip a redraw).
     */
    fun step(dt: Float, ax: Float, ay: Float, normal: Float, pxPerMeter: Float): Boolean {
        if (w == 0f || n == 0) return false
        hardestHit = 0f
        val r = radius
        // Enough sub-steps that nothing moves more than a third of a radius per step (no tunnelling).
        var vmax = 0f
        for (i in 0 until n) vmax = maxOf(vmax, abs(vx[i]) + abs(vy[i]))
        val sub = ((vmax + hypot(ax, ay) * dt) * dt / (r * 0.33f)).toInt().coerceIn(2, 16)
        val sdt = dt / sub
        val muK = 0.22f * glassGrip // sliding friction on the glass
        val muS = 0.32f * glassGrip // what it takes to get them moving from rest
        val restSpeed = 0.012f * pxPerMeter // below this (1.2 cm/s) they count as resting
        val bounceCut = 0.06f * pxPerMeter // slower impacts than 6 cm/s don't bounce (so piles settle)
        val inPlane = hypot(ax, ay)
        val stuckToGlass = inPlane < muS * normal
        val x0 = x.copyOf(); val y0 = y.copyOf(); val a0 = angle.copyOf()

        repeat(sub) {
            for (i in 0 until n) {
                val speed = hypot(vx[i], vy[i])
                if (speed < restSpeed && stuckToGlass) {
                    // Static friction holds it where it is.
                    vx[i] = 0f; vy[i] = 0f
                } else {
                    vx[i] += ax * sdt
                    vy[i] += ay * sdt
                    // Kinetic friction against the glass slows it, never reversing it.
                    val s = hypot(vx[i], vy[i])
                    if (s > 0f && normal > 0f) {
                        val drop = min(s, muK * normal * sdt)
                        vx[i] -= vx[i] / s * drop
                        vy[i] -= vy[i] / s * drop
                    }
                }
                // A spinning disc on a surface winds down (uniform-pressure disc: α = 4μg/3r).
                if (spinOn && spin[i] != 0f) {
                    val decel = (4f * muK * normal / (3f * r)) * 57.3f * sdt + 20f * sdt
                    spin[i] = if (abs(spin[i]) <= decel) 0f else spin[i] - Math.signum(spin[i]) * decel
                }
                x[i] += vx[i] * sdt
                y[i] += vy[i] * sdt
                if (spinOn) angle[i] += spin[i] * sdt
                // Walls.
                if (x[i] < r) wall(i, 1f, 0f, r - x[i], r, bounceCut, pxPerMeter)
                if (x[i] > w - r) wall(i, -1f, 0f, x[i] - (w - r), r, bounceCut, pxPerMeter)
                if (y[i] < r) wall(i, 0f, 1f, r - y[i], r, bounceCut, pxPerMeter)
                if (y[i] > h - r) wall(i, 0f, -1f, y[i] - (h - r), r, bounceCut, pxPerMeter)
            }
            // Disc on disc, a few passes so stacks hold their shape.
            repeat(3) { pass ->
                for (i in 0 until n) for (j in i + 1 until n) {
                    val dx = x[j] - x[i]
                    val dy = y[j] - y[i]
                    val d2 = dx * dx + dy * dy
                    val minD = r * 2
                    if (d2 >= minD * minD) continue
                    val d = sqrt(d2).coerceAtLeast(0.001f)
                    val nx = if (d2 == 0f) 1f else dx / d
                    val ny = if (d2 == 0f) 0f else dy / d
                    val push = (minD - d) / 2
                    x[i] -= nx * push; y[i] -= ny * push
                    x[j] += nx * push; y[j] += ny * push
                    if (pass == 0) collide(i, j, nx, ny, r, bounceCut, pxPerMeter)
                }
            }
            for (i in 0 until n) { x[i] = x[i].coerceIn(r, w - r); y[i] = y[i].coerceIn(r, h - r) }
        }
        var moved = 0f
        for (i in 0 until n) moved = maxOf(moved, abs(x[i] - x0[i]) + abs(y[i] - y0[i]) + abs(angle[i] - a0[i]) * 0.05f)
        return moved > 0.05f
    }

    /** Disc i against a wall with inward normal (nx, ny). */
    private fun wall(i: Int, nx: Float, ny: Float, depth: Float, r: Float, bounceCut: Float, ppm: Float) {
        x[i] += nx * depth; y[i] += ny * depth
        val vn = vx[i] * nx + vy[i] * ny
        if (vn >= 0f) return
        val e = if (-vn < bounceCut) 0f else restitution
        val jn = -(1 + e) * vn // per unit mass
        vx[i] += jn * nx; vy[i] += jn * ny
        hardestHit = maxOf(hardestHit, (-vn / (0.5f * ppm)).coerceAtMost(1f))
        if (!spinOn) return
        // Friction at the contact point turns sliding along the wall into spin (solid disc: I = ½mr²).
        val tx = -ny; val ty = nx
        val rcx = -nx * r; val rcy = -ny * r
        val om = spin[i] / 57.3f
        val vt = (vx[i] - om * rcy) * tx + (vy[i] + om * rcx) * ty
        val jt = (-vt / 3f).coerceIn(-0.4f * jn, 0.4f * jn)
        vx[i] += jt * tx; vy[i] += jt * ty
        spin[i] += (rcx * (jt * ty) - rcy * (jt * tx)) / (0.5f * r * r) * 57.3f
    }

    /** Discs i and j touching along (nx, ny) from i to j; equal masses. */
    private fun collide(i: Int, j: Int, nx: Float, ny: Float, r: Float, bounceCut: Float, ppm: Float) {
        val rvn = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
        if (rvn >= 0f) return
        val e = if (-rvn < bounceCut) 0f else restitution
        val jn = -(1 + e) * rvn / 2f
        vx[i] -= jn * nx; vy[i] -= jn * ny
        vx[j] += jn * nx; vy[j] += jn * ny
        hardestHit = maxOf(hardestHit, (-rvn / (0.7f * ppm)).coerceAtMost(1f))
        if (!spinOn) return
        val tx = -ny; val ty = nx
        val wi = spin[i] / 57.3f; val wj = spin[j] / 57.3f
        // Contact point velocities (i's contact at +n·r, j's at −n·r).
        val vti = (vx[i] - wi * (ny * r)) * tx + (vy[i] + wi * (nx * r)) * ty
        val vtj = (vx[j] - wj * (-ny * r)) * tx + (vy[j] + wj * (-nx * r)) * ty
        val jt = ((vtj - vti) / 6f).coerceIn(-0.4f * jn, 0.4f * jn)
        vx[i] += jt * tx; vy[i] += jt * ty
        vx[j] -= jt * tx; vy[j] -= jt * ty
        val inertia = 0.5f * r * r
        spin[i] += ((nx * r) * (jt * ty) - (ny * r) * (jt * tx)) / inertia * 57.3f
        spin[j] += ((-nx * r) * (-jt * ty) - (-ny * r) * (-jt * tx)) / inertia * 57.3f
    }
}
