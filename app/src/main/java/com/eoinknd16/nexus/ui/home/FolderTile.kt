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
import com.eoinknd16.nexus.ui.fx.DiscSim
import com.eoinknd16.nexus.ui.fx.ImpactHaptics
import com.eoinknd16.nexus.ui.fx.MotionSensors
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
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
 * The folder's apps as loose objects trapped inside the phone: real-world
 * gravity and scale, thrown about when you shake it or the screen moves,
 * gripping the glass when it's lying flat, spinning when they glance off
 * things, with a little buzz on each knock. Drawn on one Canvas from plain
 * arrays, so it never recomposes per frame, and it stops redrawing once
 * everything has settled.
 */
@Composable
fun FolderPhysics(icons: List<ImageBitmap>, tile: TileConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    MotionSensors.Use()
    val haptics = remember { ImpactHaptics(context) }
    val hapticsOn = tile.folderPhysicsHaptics && com.eoinknd16.nexus.look.LookState.look.motion.haptics

    var size by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableLongStateOf(0L) }
    val n = icons.size
    val sim = remember(n) { DiscSim(n) }
    val lastPos = remember { floatArrayOf(Float.NaN, Float.NaN) }
    val moved = remember { FloatArray(2) }

    // Real size: how many px make a metre on this screen.
    val pxPerMeter = remember {
        val dm = context.resources.displayMetrics
        val dpi = dm.ydpi.takeIf { it in 100f..900f } ?: dm.densityDpi.toFloat()
        dpi / 0.0254f
    }

    Canvas(
        modifier
            .clipToBounds()
            .onGloballyPositioned { coords ->
                size = coords.size
                val p = coords.positionInRoot()
                if (!lastPos[0].isNaN()) { moved[0] += p.x - lastPos[0]; moved[1] += p.y - lastPos[1] }
                lastPos[0] = p.x; lastPos[1] = p.y
            },
    ) {
        tick // read so each frame that moved redraws
        val r = sim.radius
        val d = (r * 2).roundToInt()
        for (i in 0 until n) {
            val img = icons[i]
            rotate(if (tile.folderPhysicsSpin) sim.angle[i] else 0f, pivot = androidx.compose.ui.geometry.Offset(sim.x[i], sim.y[i])) {
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset((sim.x[i] - r).roundToInt(), (sim.y[i] - r).roundToInt()),
                    dstSize = IntSize(d, d),
                )
            }
        }
    }

    LaunchedEffect(sim, size, tile.folderTileIconScale) {
        if (size.width == 0 || n == 0) return@LaunchedEffect
        // Size them so they fill roughly half the box: room to slosh about.
        val perSide = ceil(sqrt(n * 2f)).coerceAtLeast(2f)
        val r = (min(size.width, size.height) / perSide / 2f * 1.15f * tile.folderTileIconScale.coerceIn(0.5f, 1.5f)).coerceAtLeast(6f)
        sim.layout(size.width.toFloat(), size.height.toFloat(), r)
        var last = 0L
        val prevVel = FloatArray(2)
        var settledFrames = 0
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
                last = now
                val ppm = pxPerMeter * tile.folderPhysicsRealism.coerceIn(0.1f, 1f)
                sim.restitution = tile.folderPhysicsBounce.coerceIn(0f, 1f) * 0.85f
                sim.glassGrip = tile.folderPhysicsGrip.coerceIn(0f, 2f)
                sim.spinOn = tile.folderPhysicsSpin

                // What the icons feel, in m/s²: gravity, minus the phone's own movement.
                val g = MotionSensors.gravity
                val lin = MotionSensors.linear
                val shake = tile.folderPhysicsShake.coerceIn(0f, 3f)
                fun dz(v: Float) = if (abs(v) < 0.25f) 0f else v // ignore sensor noise
                var fx = if (tile.folderPhysicsTilt) g[0] else 0f
                var fy = if (tile.folderPhysicsTilt) g[1] else 9.81f
                fx -= dz(lin[0]) * shake
                fy -= dz(lin[1]) * shake
                val normal = if (tile.folderPhysicsTilt) abs(g[2] - dz(lin[2]) * shake) else 0f

                // The tile moving on screen (scrolling, page swipes): its acceleration throws them the other way.
                val inertia = tile.folderPhysicsInertia.coerceIn(0f, 3f)
                val vx = moved[0] / dt; val vy = moved[1] / dt
                moved[0] = 0f; moved[1] = 0f
                val cap = 40f * pxPerMeter // ~4 g
                val tax = ((vx - prevVel[0]) / dt).coerceIn(-cap, cap) * inertia * 0.5f
                val tay = ((vy - prevVel[1]) / dt).coerceIn(-cap, cap) * inertia * 0.5f
                prevVel[0] = vx; prevVel[1] = vy

                val movedAny = sim.step(dt, fx * ppm - tax, fy * ppm - tay, normal * ppm, ppm)
                if (hapticsOn && sim.hardestHit > 0f) haptics.hit(sim.hardestHit)
                // Only redraw while something's moving.
                if (movedAny) { settledFrames = 0; tick = now } else if (settledFrames++ < 2) tick = now
            }
        }
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

