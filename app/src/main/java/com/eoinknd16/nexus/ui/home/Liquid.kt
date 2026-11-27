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
import androidx.compose.runtime.rememberUpdatedState
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
import com.eoinknd16.nexus.look.LiquidFluid
import com.eoinknd16.nexus.ui.fx.MotionSensors
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Your battery as real liquid in a glass, simulated at the glass's actual
 * size: tilt the phone and it flows to the low side (the surface ends up
 * level with the ground), move it and waves run across and bounce off the
 * sides, shake it hard and droplets fly and fall back in. The liquid you pick
 * sets how thick it is: water sloshes, honey slowly slumps, mercury is heavy
 * with a rounded edge. Bubbles rise while charging.
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

    val metal = block.fluid == LiquidFluid.Mercury
    val colour = when {
        metal -> Color(0xFFD1D5DB)
        !block.colourByLevel -> Nx.Cyan
        level <= 15 -> Color(0xFFFF4D6D)
        level <= 35 -> Nx.Amber
        else -> Nx.Lime
    }
    val pxPerMeter = remember {
        val dm = context.resources.displayMetrics
        (dm.ydpi.takeIf { it in 100f..900f } ?: dm.densityDpi.toFloat()) / 0.0254f
    }
    val sim = remember { ShallowWater(56) }
    var tick by remember { mutableLongStateOf(0L) }
    val blockNow by rememberUpdatedState(block)
    val levelNow by rememberUpdatedState(level)
    val chargingNow by rememberUpdatedState(charging)

    Box(
        Modifier.fillMaxWidth().height(block.heightDp.dp)
            .then(if (block.framed) Modifier.holoPanel(colour, glow = 0.12f) else Modifier)
            .clip(nexusShape()),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(block.heightDp.dp)) {
            tick
            sim.setSize(size.width / pxPerMeter, size.height / pxPerMeter)
            val w = size.width
            val h = size.height
            val n = sim.n
            val toPx = pxPerMeter
            // Meniscus: curls up the glass (or rolls down, for mercury) right at the edges.
            val men = blockNow.fluid.meniscus * 2.5.dp.toPx()
            val reach = 3.dp.toPx()
            fun surfaceY(i: Int): Float {
                val x = w * i / (n - 1)
                val edge = exp(-x / reach) + exp(-(w - x) / reach)
                return (h - sim.h[i] * toPx - men * edge).coerceIn(-h, h)
            }
            fun line(offset: Float) = Path().apply {
                for (i in 0 until n) { val x = w * i / (n - 1); val y = surfaceY(i) + offset; if (i == 0) moveTo(x, y) else lineTo(x, y) }
            }
            val body = Path().apply {
                moveTo(0f, h)
                for (i in 0 until n) lineTo(w * i / (n - 1), surfaceY(i))
                lineTo(w, h); close()
            }
            val top = (0 until n).minOf { surfaceY(it) }.coerceAtLeast(0f)
            drawPath(
                body,
                if (metal) Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFF9CA3AF), Color(0xFF4B5563), Color(0xFF9CA3AF)), top, h)
                else Brush.verticalGradient(listOf(colour.copy(alpha = 0.78f), colour.copy(alpha = 0.5f), colour.copy(alpha = 0.32f)), top, h),
            )
            // Light caught just under the surface, then the bright surface line itself.
            drawPath(line(5.dp.toPx()), Color.White.copy(alpha = if (metal) 0.35f else 0.12f), style = Stroke(6.dp.toPx()))
            drawPath(line(0f), Color.White.copy(alpha = if (metal) 0.8f else 0.45f), style = Stroke(1.5.dp.toPx()))
            for (d in sim.drops) drawCircle(colour.copy(alpha = 0.85f), d.r * toPx, Offset(d.x * toPx, h - d.y * toPx))
            if (chargingNow && !metal) for (b in sim.bubbles) {
                val col = (b.x * (n - 1)).toInt().coerceIn(0, n - 1)
                drawCircle(Color.White.copy(alpha = 0.4f), b.r * 3.dp.toPx(), Offset(b.x * w, h - b.y * sim.h[col] * toPx), style = Stroke(1.dp.toPx()))
            }
        }
        if (block.showPercent) {
            Text(
                "$level%" + if (charging) " ⚡" else "",
                color = if (metal) Color(0xFF111827) else Nx.Text, fontWeight = FontWeight.Bold,
                fontSize = (block.heightDp / 3.2f).coerceIn(18f, 54f).sp, style = MaterialTheme.typography.headlineMedium,
            )
        }
    }

    LaunchedEffect(sim) {
        var last = 0L
        var still = 0
        while (true) {
            withFrameNanos { now ->
                val b = blockNow
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
                last = now
                val g = MotionSensors.gravity
                val lin = MotionSensors.linear
                fun dz(v: Float) = if (abs(v) < 0.2f) 0f else v
                // What the liquid feels (m/s²): gravity minus the phone's own movement.
                val realism = b.realism.coerceIn(0.15f, 1f)
                val ax = (g[0] - dz(lin[0])) * realism
                val ay = max(1.5f, g[1] - dz(lin[1])) * realism
                sim.fluid = b.fluid
                sim.thickness = b.thickness.coerceIn(0.1f, 5f)
                sim.splashes = b.splashes
                val active = sim.step(dt, ax, ay, levelNow / 100f, chargingNow)
                if (active || chargingNow) { still = 0; tick = now } else if (still++ < 2) tick = now
            }
        }
    }
}

/**
 * 1-D shallow-water simulation across the glass (depth per column, flow
 * between columns): the physics of real sloshing in a wide, shallow
 * container. Waves travel at √(g·depth), bounce off the walls, and the surface
 * settles level with whatever "down" is. Everything is in metres.
 */
private class ShallowWater(val n: Int) {
    val h = FloatArray(n)
    private val u = FloatArray(n + 1) // flow across each boundary; the walls stay 0
    private val prev = FloatArray(n)
    private var w = 0.06f
    private var height = 0.02f
    private var started = false
    var fluid = LiquidFluid.Water
    var thickness = 1f
    var splashes = true

    class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)
    val drops = ArrayList<Drop>()
    class Bubble(var x: Float, var y: Float, var r: Float, var speed: Float)
    val bubbles = List(10) { Bubble(Math.random().toFloat(), Math.random().toFloat(), 0.6f + Math.random().toFloat(), 0.25f + Math.random().toFloat() * 0.3f) }
    private var t = 0f

    fun setSize(widthM: Float, heightM: Float) {
        if (widthM > 0f && heightM > 0f) { w = widthM; height = heightM }
    }

    /** Returns whether anything is still moving. */
    fun step(dt: Float, ax: Float, ay: Float, fill: Float, charging: Boolean): Boolean {
        val target = (fill.coerceIn(0f, 1f) * height).coerceAtLeast(height * 0.02f)
        if (!started) { h.fill(target); started = true }
        t += dt
        val dx = w / n
        var hMax = 0f; var uMax = 0f
        for (i in 0 until n) hMax = max(hMax, h[i])
        for (i in 0..n) uMax = max(uMax, abs(u[i]))
        // Enough sub-steps that a wave never skips a column.
        val c = sqrt(ay * max(hMax, 0.001f))
        val sub = ((dt / (0.4f * dx / (c + uMax + 0.01f))).toInt() + 1).coerceIn(1, 60)
        val sdt = dt / sub
        val damping = fluid.damping * thickness
        val visc = (fluid.viscosity * thickness * sdt / (dx * dx)).coerceIn(0f, 0.5f)
        val tension = fluid.tension.coerceIn(0f, 0.25f)
        h.copyInto(prev)

        repeat(sub) {
            // Flow: pushed by the slope of the surface and the sideways pull, slowed by drag.
            for (i in 1 until n) {
                u[i] += (-ay * (h[i] - h[i - 1]) / dx + ax) * sdt
                u[i] /= 1f + damping * sdt
                if (u[i] > 0 && h[i - 1] <= 0f) u[i] = 0f
                if (u[i] < 0 && h[i] <= 0f) u[i] = 0f
            }
            u[0] = 0f; u[n] = 0f
            // Viscosity: neighbouring flows drag each other along.
            if (visc > 0f) for (i in 1 until n) u[i] += visc * (u[i - 1] + u[i + 1] - 2 * u[i])
            // Move the liquid between columns (never more than a column holds).
            for (i in 1 until n) {
                val up = if (u[i] > 0) h[i - 1] else h[i]
                val q = u[i] * up * sdt / dx
                val moved = if (q > 0) min(q, h[i - 1]) else max(q, -h[i])
                h[i - 1] -= moved
                h[i] += moved
            }
            // Surface tension smooths tiny ripples (keeps the volume the same).
            if (tension > 0f) {
                val a = h.copyOf()
                for (i in 0 until n) {
                    val l = a[(i - 1).coerceAtLeast(0)]; val r = a[(i + 1).coerceAtMost(n - 1)]
                    h[i] = a[i] + tension * 0.5f * (l + r - 2 * a[i])
                }
            }
        }
        // Battery level changes: ease the amount of liquid towards it.
        var vol = 0f
        for (i in 0 until n) vol += h[i]
        val want = target * n
        if (vol > 0f) {
            val k = 1f + ((want - vol) / vol) * min(1f, dt * 2f)
            for (i in 0 until n) h[i] *= k
        }
        // Splashes: a crest rising fast enough throws droplets (runny liquids only).
        if (splashes && fluid.damping * thickness < 5f) {
            for (i in 1 until n - 1) {
                val rise = (h[i] - prev[i]) / dt
                if (rise > 0.18f && h[i] >= h[i - 1] && h[i] >= h[i + 1] && drops.size < 30 && Math.random() < 0.35) {
                    drops += Drop((i + 0.5f) * dx, h[i], (u[i] + u[i + 1]) / 2 + ((Math.random() - 0.5) * 0.15).toFloat(), rise * 1.3f, 0.0006f + Math.random().toFloat() * 0.0008f)
                }
            }
        }
        val iter = drops.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            d.vx += ax * dt; d.vy -= ay * dt
            d.x += d.vx * dt; d.y += d.vy * dt
            if (d.x < 0f) { d.x = 0f; d.vx = -d.vx * 0.3f }
            if (d.x > w) { d.x = w; d.vx = -d.vx * 0.3f }
            if (d.y > height) { d.y = height; d.vy = -d.vy * 0.2f }
            val col = ((d.x / w) * n).toInt().coerceIn(0, n - 1)
            if (d.vy < 0 && d.y <= h[col]) iter.remove() // fell back in
        }
        if (charging) for (b in bubbles) {
            b.y += b.speed * dt
            b.x += sin(t * 3f + b.r * 7f) * 0.002f
            if (b.y > 1f) { b.y = 0f; b.x = Math.random().toFloat(); b.r = 0.6f + Math.random().toFloat() }
        }
        var motion = 0f
        for (i in 0..n) motion = max(motion, abs(u[i]))
        return motion > 0.0004f || drops.isNotEmpty()
    }
}

private fun batteryNow(context: Context): Pair<Int, Boolean> = runCatching {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 50 to false
    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 50)
    val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    (l * 100 / s.coerceAtLeast(1)) to (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}.getOrDefault(50 to false)
