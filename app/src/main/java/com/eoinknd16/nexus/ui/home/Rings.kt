package com.eoinknd16.nexus.ui.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.RingKind
import com.eoinknd16.nexus.look.RingStyle
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

/** One reading: 0..1 plus the text shown under it. */
private data class Reading(val value: Float, val text: String)

private fun read(context: Context, kind: RingKind): Reading {
    val now = Calendar.getInstance()
    fun frac(a: Float) = Reading(a.coerceIn(0f, 1f), "${(a * 100).roundToInt()}%")
    return when (kind) {
        RingKind.Battery -> {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (l < 0) 0f else l / s.coerceAtLeast(1).toFloat()
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING
            Reading(pct, "${(pct * 100).roundToInt()}%" + if (charging) " ⚡" else "")
        }
        RingKind.Storage -> runCatching {
            val fs = StatFs(Environment.getDataDirectory().path)
            val total = fs.totalBytes.toFloat(); val free = fs.availableBytes.toFloat()
            Reading(1f - free / total, "${((total - free) / 1e9f).roundToInt()}/${(total / 1e9f).roundToInt()}GB")
        }.getOrDefault(Reading(0f, "--"))
        RingKind.Memory -> runCatching {
            val am = context.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            frac(1f - mi.availMem.toFloat() / mi.totalMem)
        }.getOrDefault(Reading(0f, "--"))
        RingKind.Day -> {
            val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            frac(m / 1440f)
        }
        RingKind.Week -> {
            val d = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
            frac((d * 24 + now.get(Calendar.HOUR_OF_DAY)) / 168f)
        }
        RingKind.Month -> frac((now.get(Calendar.DAY_OF_MONTH) - 1 + now.get(Calendar.HOUR_OF_DAY) / 24f) / now.getActualMaximum(Calendar.DAY_OF_MONTH))
        RingKind.Year -> frac((now.get(Calendar.DAY_OF_YEAR) - 1) / now.getActualMaximum(Calendar.DAY_OF_YEAR).toFloat())
    }
}

private fun ringColour(kind: RingKind, v: Float): Color = when (kind) {
    RingKind.Battery -> when { v < 0.15f -> Nx.Danger; v < 0.3f -> Nx.Amber; else -> Nx.Lime }
    RingKind.Storage, RingKind.Memory -> if (v > 0.9f) Nx.Danger else if (kind == RingKind.Storage) Nx.Cyan else Nx.Magenta
    else -> Nx.Amber
}

@Composable
fun RingsView(block: HomeBlock.Rings) {
    val context = LocalContext.current
    var readings by remember { mutableStateOf<Map<RingKind, Reading>>(emptyMap()) }
    LaunchedEffect(block.rings) {
        while (true) {
            readings = withContext(Dispatchers.Default) { block.rings.associateWith { read(context, it) } }
            delay(20_000)
        }
    }
    val inner = @Composable {
        when (block.style) {
            RingStyle.Nested -> NestedRings(block, readings)
            RingStyle.Bars -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.rings.forEach { k -> BarRow(block, k, readings[k]) }
            }
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                block.rings.forEach { k -> SingleRing(block, k, readings[k]) }
            }
        }
    }
    if (block.framed) Box(Modifier.fillMaxWidth().holoPanel().padding(14.dp)) { inner() } else Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { inner() }
}

@Composable
private fun SingleRing(block: HomeBlock.Rings, kind: RingKind, r: Reading?) {
    val v by animateFloatAsState(r?.value ?: 0f, tween(900), label = "ring")
    val col = ringColour(kind, v)
    val arcs = block.style == RingStyle.Arcs
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(block.sizeDp.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(block.sizeDp.dp)) {
                val t = block.thickness.dp.toPx()
                val inset = t / 2 + (if (block.glow) t * 0.6f else 0f)
                val tl = Offset(inset, inset); val sz = Size(size.width - inset * 2, size.height - inset * 2)
                val start = if (arcs) 135f else -90f; val sweep = if (arcs) 270f else 360f
                drawArc(col.copy(alpha = 0.14f), start, sweep, false, tl, sz, style = Stroke(t, cap = StrokeCap.Round))
                if (block.glow) drawArc(col.copy(alpha = 0.22f), start, sweep * v, false, tl, sz, style = Stroke(t * 2.2f, cap = StrokeCap.Round))
                drawArc(col, start, sweep * v, false, tl, sz, style = Stroke(t, cap = StrokeCap.Round))
            }
            Text(r?.text ?: "", color = Nx.Text, fontSize = (block.sizeDp / 6.2f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (block.showLabels) Text(hud(kind.short), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NestedRings(block: HomeBlock.Rings, readings: Map<RingKind, Reading>) {
    val vals = block.rings.map { k -> k to (animateFloatAsState(readings[k]?.value ?: 0f, tween(900), label = "n").value) }
    val big = (block.sizeDp * 1.6f).dp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(big)) {
            val t = block.thickness.dp.toPx() * 1.2f
            vals.forEachIndexed { i, (k, v) ->
                val col = ringColour(k, v)
                val inset = t / 2 + i * (t + 3.dp.toPx())
                if (size.width / 2 - inset < t) return@forEachIndexed
                val tl = Offset(inset, inset); val sz = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(col.copy(alpha = 0.15f), -90f, 360f, false, tl, sz, style = Stroke(t, cap = StrokeCap.Round))
                drawArc(col, -90f, 360f * v, false, tl, sz, style = Stroke(t, cap = StrokeCap.Round))
            }
        }
        if (block.showLabels) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            vals.forEach { (k, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(ringColour(k, v), androidx.compose.foundation.shape.CircleShape))
                    Text("  ${hud(k.short)}  ", style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                    Text(readings[k]?.text ?: "", color = Nx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BarRow(block: HomeBlock.Rings, kind: RingKind, r: Reading?) {
    val v by animateFloatAsState(r?.value ?: 0f, tween(900), label = "bar")
    val col = ringColour(kind, v)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (block.showLabels) Text(hud(kind.short), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, modifier = Modifier.width(52.dp))
        Canvas(Modifier.weight(1f).height(block.thickness.dp.coerceAtLeast(4.dp))) {
            val h = size.height
            drawRoundRect(col.copy(alpha = 0.15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            if (block.glow) drawRoundRect(col.copy(alpha = 0.25f), size = Size(size.width * v, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            drawRoundRect(col, size = Size((size.width * v).coerceAtLeast(h), h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        }
        Text("  " + (r?.text ?: ""), color = Nx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(78.dp))
    }
}
