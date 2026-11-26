package com.eoinknd16.nexus.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A folder's apps laid out to fill the whole tile: picks the column count that
 * gives the biggest icons for however many apps there are, so it fills up
 * nicely as you add more.
 */
@Composable
fun FolderFill(icons: List<ImageBitmap>, scale: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val n = icons.size.coerceAtLeast(1)
        val w = maxWidth.value
        val h = maxHeight.value
        val gapRatio = 0.18f
        var best = 1
        var bestSize = 0f
        for (cols in 1..n) {
            val rows = ceil(n / cols.toFloat()).toInt()
            val size = min(w / (cols + (cols - 1) * gapRatio), h / (rows + (rows - 1) * gapRatio))
            if (size > bestSize) { bestSize = size; best = cols }
        }
        val cols = best
        val rows = ceil(n / cols.toFloat()).toInt()
        val cell = bestSize
        val gap = cell * gapRatio
        val icon = (cell * scale.coerceIn(0.5f, 1.5f)).coerceAtMost(min(w, h))
        // Centre the whole grid; a short last row is centred too.
        val gridH = rows * cell + (rows - 1) * gap
        val top = (h - gridH) / 2
        icons.forEachIndexed { i, img ->
            val r = i / cols
            val inRow = if (r == rows - 1) n - r * cols else cols
            val rowW = inRow * cell + (inRow - 1) * gap
            val left = (w - rowW) / 2
            val c = i % cols
            val x = left + c * (cell + gap) + (cell - icon) / 2
            val y = top + r * (cell + gap) + (cell - icon) / 2
            Image(img, null, Modifier.offset(x.dp, y.dp).size(icon.dp))
        }
    }
}

/**
 * The folder's apps as loose objects in a box: they fall with the phone's
 * real gravity (tilt it and they slide), bump into each other and the walls,
 * and get thrown about when the tile moves (scrolling, page swipes).
 * Drawn on one Canvas from plain arrays, so it never recomposes per frame.
 */
@Composable
fun FolderPhysics(icons: List<ImageBitmap>, tile: TileConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gravity = remember { FloatArray(2).also { it[1] = 9.81f } }
    if (tile.folderPhysicsTilt) GravitySensor(context, gravity)

    var size by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableLongStateOf(0L) }
    val n = icons.size
    val sim = remember(n) { PhysicsSim(n) }
    val lastPos = remember { floatArrayOf(Float.NaN, Float.NaN) }
    val moved = remember { FloatArray(2) }

    Canvas(
        modifier
            .clipToBounds()
            .onGloballyPositioned { coords ->
                size = coords.size
                // How far the tile itself moved since last frame -> inertia kick.
                val p = coords.positionInRoot()
                if (!lastPos[0].isNaN()) { moved[0] += p.x - lastPos[0]; moved[1] += p.y - lastPos[1] }
                lastPos[0] = p.x; lastPos[1] = p.y
            },
    ) {
        tick // read so each frame redraws
        val r = sim.radius
        for (i in 0 until n) {
            val img = icons[i]
            val d = (r * 2).roundToInt()
            drawImage(
                img,
                srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((sim.x[i] - r).roundToInt(), (sim.y[i] - r).roundToInt()),
                dstSize = IntSize(d, d),
            )
        }
    }

    LaunchedEffect(sim, size, tile.folderTileIconScale) {
        if (size.width == 0 || n == 0) return@LaunchedEffect
        sim.layout(size.width.toFloat(), size.height.toFloat(), tile.folderTileIconScale)
        var last = 0L
        val g = with(density) { 2400.dp.toPx() } // px/s^2 at 1 g
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 1f / 30f)
                last = now
                val ax = if (tile.folderPhysicsTilt) -gravity[0] / 9.81f * g else 0f
                val ay = (if (tile.folderPhysicsTilt) gravity[1] / 9.81f else 1f) * g
                // The tile moved by `moved` px: in its frame the icons are thrown the other way.
                val inertia = tile.folderPhysicsInertia.coerceIn(0f, 3f)
                val kx = -moved[0] * inertia
                val ky = -moved[1] * inertia
                moved[0] = 0f; moved[1] = 0f
                sim.step(dt, ax, ay, kx, ky, tile.folderPhysicsBounce.coerceIn(0f, 1f))
                tick = now
            }
        }
    }
}

private class PhysicsSim(val n: Int) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    var radius = 10f
    private var w = 0f
    private var h = 0f

    fun layout(width: Float, height: Float, scale: Float) {
        val first = w == 0f
        w = width; h = height
        // Size so they pack into roughly half the box: room to slosh about.
        val perSide = ceil(sqrt(n * 2f)).coerceAtLeast(2f)
        radius = (min(w, h) / perSide / 2f * 1.15f * scale.coerceIn(0.5f, 1.5f)).coerceAtLeast(6f)
        if (first) {
            val cols = max(1, (w / (radius * 2)).toInt())
            for (i in 0 until n) {
                x[i] = radius + (i % cols) * radius * 2 + (i / cols % 2) * radius * 0.5f
                y[i] = radius + (i / cols) * radius * 2
            }
        }
        for (i in 0 until n) { x[i] = x[i].coerceIn(radius, w - radius); y[i] = y[i].coerceIn(radius, h - radius) }
    }

    fun step(dt: Float, ax: Float, ay: Float, kickX: Float, kickY: Float, bounce: Float) {
        if (w == 0f) return
        val sub = 3
        val sdt = dt / sub
        // A sudden tile move becomes a velocity change, capped so a page swipe doesn't fire them into orbit.
        val cap = 4000f
        val kvx = (if (dt > 0f) kickX / dt else 0f).coerceIn(-cap, cap) * 0.25f
        val kvy = (if (dt > 0f) kickY / dt else 0f).coerceIn(-cap, cap) * 0.25f
        for (i in 0 until n) { vx[i] += kvx; vy[i] += kvy }
        val r = radius
        val wallBounce = 0.15f + bounce * 0.8f
        repeat(sub) {
            for (i in 0 until n) {
                vx[i] += ax * sdt
                vy[i] += ay * sdt
                vx[i] *= 0.998f
                vy[i] *= 0.998f
                x[i] += vx[i] * sdt
                y[i] += vy[i] * sdt
                if (x[i] < r) { x[i] = r; vx[i] = -vx[i] * wallBounce; vy[i] *= 0.96f }
                if (x[i] > w - r) { x[i] = w - r; vx[i] = -vx[i] * wallBounce; vy[i] *= 0.96f }
                if (y[i] < r) { y[i] = r; vy[i] = -vy[i] * wallBounce; vx[i] *= 0.96f }
                if (y[i] > h - r) { y[i] = h - r; vy[i] = -vy[i] * wallBounce; vx[i] *= 0.96f }
            }
            // Icon-on-icon: push apart, swap the speed along the line between them.
            val minD = r * 2
            for (i in 0 until n) for (j in i + 1 until n) {
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD || d2 == 0f) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val overlap = (minD - d) / 2
                x[i] -= nx * overlap; y[i] -= ny * overlap
                x[j] += nx * overlap; y[j] += ny * overlap
                val rel = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
                if (rel < 0) {
                    val imp = -(1 + bounce * 0.7f) * rel / 2
                    vx[i] -= imp * nx; vy[i] -= imp * ny
                    vx[j] += imp * nx; vy[j] += imp * ny
                }
            }
        }
    }
}

/** Live gravity direction in screen terms (x right, y down), only while home is showing. */
@Composable
private fun GravitySensor(context: Context, out: FloatArray) {
    LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Device x points right, y up: screen-down gravity is +y here.
                out[0] = e.values[0]
                out[1] = e.values[1]
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
}

/** What goes inside a folder tile for the Fill / Physics styles. */
@Composable
fun FolderTileContent(tile: TileConfig, icons: List<ImageBitmap>, showLabel: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        val area = Modifier.weight(1f).fillMaxSize()
        if (icons.isEmpty()) {
            Box(area, contentAlignment = Alignment.Center) {
                Text(hud("Empty folder"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
            }
        } else when (tile.folderTileStyle) {
            com.eoinknd16.nexus.look.FolderTileStyle.Physics -> FolderPhysics(icons, tile, area)
            else -> FolderFill(icons, tile.folderTileIconScale, area)
        }
        if (showLabel && tile.folderTileLabel && tile.label.isNotBlank()) {
            Text(
                tile.label, style = MaterialTheme.typography.titleSmall, color = Nx.Text, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

