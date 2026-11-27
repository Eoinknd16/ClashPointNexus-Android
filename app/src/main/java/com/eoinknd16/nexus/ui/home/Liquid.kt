package com.eoinknd16.nexus.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.fx.MotionSensors
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import kotlin.math.abs
import kotlin.math.sin

/**
 * Your battery as liquid in a glass: the surface stays level with the real
 * ground as you tilt the phone, sloshes and settles when you move or shake it,
 * and bubbles while charging. Uses the same motion sensors as folder physics;
 * stops animating once it's still.
 */
@Composable
fun LiquidBattery(block: HomeBlock.Liquid) {
    val context = LocalContext.current
    MotionSensors.Use()
    var level by remember { mutableIntStateOf(batteryNow(context).first) }
    var charging by remember { mutableStateOf(batteryNow(context).second) }
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) level = l * 100 / s.coerceAtLeast(1)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(rx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(rx) } }
    }

    val colour = when {
        !block.colourByLevel -> Nx.Cyan
        level <= 15 -> Color(0xFFFF4D6D)
        level <= 35 -> Nx.Amber
        else -> Nx.Lime
    }
    val sim = remember { SloshSim(56) }
    var tick by remember { mutableLongStateOf(0L) }

    Box(
        Modifier.fillMaxWidth().height(block.heightDp.dp)
            .then(if (block.framed) Modifier.holoPanel(colour, glow = 0.12f) else Modifier)
            .clip(nexusShape()),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(block.heightDp.dp)) {
            tick
            val w = size.width
            val h = size.height
            sim.aspect = w / h
            val fill = level / 100f
            val n = sim.n
            val path = Path().apply {
                moveTo(0f, h)
                for (i in 0 until n) {
                    val x = w * i / (n - 1)
                    val y = h - (fill * h + sim.h[i] * h)
                    lineTo(x, y.coerceIn(0f, h))
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(colour.copy(alpha = 0.75f), colour.copy(alpha = 0.28f))))
            // Bright meniscus line along the surface.
            val surface = Path().apply {
                for (i in 0 until n) {
                    val x = w * i / (n - 1)
                    val y = (h - (fill * h + sim.h[i] * h)).coerceIn(0f, h)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(surface, colour, style = Stroke(2.dp.toPx()))
            // Bubbles while charging, rising through the liquid and popping at the surface.
            if (charging) for (b in sim.bubbles) {
                val bx = b.x * w
                val col = ((b.x * (n - 1)).toInt()).coerceIn(0, n - 1)
                val top = h - (fill * h + sim.h[col] * h)
                val by = h - b.y * h
                if (by > top + 4f) drawCircle(Color.White.copy(alpha = 0.35f), b.r * 3.dp.toPx(), Offset(bx, by), style = Stroke(1.dp.toPx()))
            }
        }
        if (block.showPercent) {
            Text(
                "$level%" + if (charging) " ⚡" else "",
                color = Nx.Text, fontWeight = FontWeight.Bold, fontSize = (block.heightDp / 3.2f).coerceIn(18f, 54f).sp,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }

    LaunchedEffect(sim, block.wobble) {
        var last = 0L
        var still = 0
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
                last = now
                val g = MotionSensors.gravity
                val lin = MotionSensors.linear
                // What the liquid feels: gravity minus the phone's own movement.
                val fx = g[0] - (if (abs(lin[0]) < 0.25f) 0f else lin[0])
                val fy = (g[1] - (if (abs(lin[1]) < 0.25f) 0f else lin[1])).coerceAtLeast(1.5f)
                val moving = sim.step(dt, fx / fy, block.wobble.coerceIn(0f, 1f), charging)
                if (moving || charging) { still = 0; tick = now } else if (still++ < 2) tick = now
            }
        }
    }
}

/**
 * The liquid surface as columns on springs: each column is pulled toward the
 * level surface for the current tilt and passes waves to its neighbours, so
 * a sudden tilt or shake sends a wave sloshing side to side that dies away.
 * Heights are fractions of the glass height, relative to the battery level.
 */
private class SloshSim(val n: Int) {
    val h = FloatArray(n)
    private val v = FloatArray(n)
    private var slope = 0f
    /** Glass width / height: a level surface across a wide glass climbs further up the side. */
    var aspect = 3f
    class Bubble(var x: Float, var y: Float, var r: Float, var speed: Float)
    val bubbles = List(10) { Bubble(Math.random().toFloat(), Math.random().toFloat(), 0.6f + Math.random().toFloat(), 0.15f + Math.random().toFloat() * 0.2f) }
    private var t = 0f

    fun step(dt: Float, targetSlope: Float, wobble: Float, charging: Boolean): Boolean {
        t += dt
        // The surface wants to stay level with the real ground: tilt the phone right-side down and
        // the liquid runs right, climbing (width/height × gx/gy) of the glass's height.
        slope += ((targetSlope.coerceIn(-2.5f, 2.5f) * aspect) - slope) * (dt * 6f).coerceAtMost(1f)
        val k = 70f
        val damp = 1.2f + (1f - wobble) * 9f
        var energy = 0f
        for (i in 0 until n) {
            val pos = i / (n - 1f) - 0.5f
            val target = slope * pos
            v[i] += (k * (target - h[i]) - damp * v[i]) * dt
        }
        // Waves spread to neighbours (a couple of passes for smoothness).
        repeat(2) {
            for (i in 0 until n) {
                val l = h[(i - 1).coerceAtLeast(0)]
                val r = h[(i + 1).coerceAtMost(n - 1)]
                v[i] += (l + r - 2 * h[i]) * 260f * wobble.coerceAtLeast(0.15f) * dt
            }
        }
        var mean = 0f
        for (i in 0 until n) { h[i] += v[i] * dt; mean += h[i]; energy += abs(v[i]) }
        // Keep the amount of liquid the same (tilting moves it, never creates it).
        mean /= n
        for (i in 0 until n) h[i] -= mean
        if (charging) for (b in bubbles) {
            b.y += b.speed * dt
            b.x += sin(t * 3f + b.r * 7f) * 0.002f
            if (b.y > 1f) { b.y = 0f; b.x = Math.random().toFloat(); b.r = 0.6f + Math.random().toFloat() }
        }
        return energy > 0.002f
    }
}

private fun batteryNow(context: Context): Pair<Int, Boolean> = runCatching {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 50 to false
    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 50)
    val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    (l * 100 / s.coerceAtLeast(1)) to (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}.getOrDefault(50 to false)
