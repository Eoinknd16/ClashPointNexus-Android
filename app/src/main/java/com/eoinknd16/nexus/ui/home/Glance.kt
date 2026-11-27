package com.eoinknd16.nexus.ui.home

import android.Manifest
import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class GlanceLine(val key: String, val icon: ImageVector?, val text: String, val tint: Color, val onClick: (() -> Unit)? = null)
private data class NextEvent(val id: Long, val title: String, val begin: Long, val allDay: Boolean)

/**
 * "At a glance": a greeting, then only what matters right now. Next calendar
 * event, tomorrow's alarm after dark, rain in the next few hours, battery when
 * it's low or charging, what's playing, and a nudge if the PC is still on late
 * at night. Each line taps through to the relevant thing.
 */
@Composable
fun GlanceView(b: HomeBlock.Glance, vm: HomeViewModel) {
    val c = appContainer()
    val context = LocalContext.current
    // Re-evaluate every minute (time-of-day lines) without recomposing more often.
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            minute = System.currentTimeMillis() / 60_000
        }
    }
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val weather by vm.weather.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val playing by c.externalMedia.items.collectAsStateWithLifecycle()

    var calendarAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAllowed = it }
    val event by produceState<NextEvent?>(null, calendarAllowed, minute / 5) {
        value = if (b.calendar && calendarAllowed) withContext(Dispatchers.IO) { nextEvent(context) } else null
    }
    val battery = remember(minute) { batteryInfo(context) }
    val alarm = remember(minute) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
    }
    val timeFmt = remember { SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault()) }

    val lines = buildList {
        if (b.calendar) {
            val e = event
            if (e != null) {
                val mins = (e.begin - System.currentTimeMillis()) / 60_000
                val whenText = when {
                    e.allDay -> "today"
                    mins <= 0 -> "now"
                    mins < 60 -> "in $mins min"
                    else -> "at ${timeFmt.format(Date(e.begin))}"
                }
                add(GlanceLine("cal", Icons.Filled.Event, "${e.title} · $whenText", Nx.Cyan) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
            } else if (!calendarAllowed) {
                add(GlanceLine("cal-ask", Icons.Filled.Event, "Tap to show your next event here", Nx.Muted) { askCalendar.launch(Manifest.permission.READ_CALENDAR) })
            }
        }
        if (b.rain) weather?.rainAt?.let { at -> add(GlanceLine("rain", Icons.Filled.Umbrella, "Rain likely around $at (${weather?.rainChance}%)", Nx.Cyan)) }
        if (b.nowPlaying) playing.firstOrNull { it.playing }?.let { m ->
            add(GlanceLine("music", Icons.Filled.MusicNote, listOf(m.title, m.subtitle).filter { it.isNotBlank() }.joinToString(" · "), Nx.Magenta) {
                c.externalMedia.open(m, context)
            })
        }
        if (b.battery && battery != null) {
            val (pct, charging) = battery
            if (charging) add(GlanceLine("bat", Icons.Filled.BatteryChargingFull, "Charging · $pct%", Nx.Lime))
            else if (pct <= 20) add(GlanceLine("bat", Icons.Filled.BatteryAlert, "Battery low · $pct%", Nx.Danger))
        }
        if (b.alarm && alarm != null && (hour >= 20 || hour < 4)) {
            val inHours = (alarm - System.currentTimeMillis()) / 3_600_000.0
            if (inHours in 0.0..16.0) add(GlanceLine("alarm", Icons.Filled.Alarm, "Alarm ${timeFmt.format(Date(alarm))} · ${"%.0f".format(inHours)}h of sleep", Nx.Amber))
        }
        if (b.pcLateNight && (hour >= 23 || hour < 5) && state.pc.kind == PcState.Kind.Online) {
            add(GlanceLine("pc", Icons.Filled.Computer, "${c.pcStore.value.displayName} is still on", Nx.Amber) { PcHub.open = true })
        }
    }.take(b.maxLines.coerceIn(1, 6))

    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Good night"
    }
    Column(
        Modifier.fillMaxWidth().then(if (b.framed) Modifier.holoPanel().padding(14.dp) else Modifier),
    ) {
        if (b.greeting) {
            Text(greeting, style = MaterialTheme.typography.titleLarge, color = Nx.Text)
            if (lines.isEmpty()) Text(hud("Nothing needs you right now"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
        lines.forEach { l ->
            AnimatedContent(l.text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "glance-${l.key}") { text ->
                Row(
                    Modifier.fillMaxWidth().then(if (l.onClick != null) Modifier.nexusPress(l.onClick) else Modifier).padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (l.icon != null) Icon(l.icon, null, tint = l.tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun batteryInfo(context: Context): Pair<Int, Boolean>? = runCatching {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    if (level < 0) return null
    (level * 100 / scale.coerceAtLeast(1)) to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}.getOrNull()

/** The next event in the coming 24 hours (or one happening now). */
private fun nextEvent(context: Context): NextEvent? = runCatching {
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .let { ContentUris.appendId(it, now - 60 * 60_000); ContentUris.appendId(it, now + 24 * 3_600_000); it }
        .build()
    context.contentResolver.query(
        uri,
        arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
        null, null, CalendarContract.Instances.BEGIN + " ASC",
    )?.use { cur ->
        while (cur.moveToNext()) {
            val end = cur.getLong(3)
            if (end < now) continue
            return@use NextEvent(cur.getLong(0), cur.getString(1) ?: "Event", cur.getLong(2), cur.getInt(4) == 1)
        }
        null
    }
}.getOrNull()
