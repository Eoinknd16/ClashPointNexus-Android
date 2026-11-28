package com.eoinknd16.nexus.ui.home

import android.Manifest
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.eoinknd16.nexus.look.AgendaStyle
import com.eoinknd16.nexus.look.Align
import com.eoinknd16.nexus.look.CountdownStyle
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.ToggleKind
import com.eoinknd16.nexus.look.ToggleStyle
import com.eoinknd16.nexus.look.updateBlock
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun Framed(framed: Boolean, content: @Composable () -> Unit) {
    if (framed) Box(Modifier.fillMaxWidth().holoPanel().padding(14.dp)) { content() } else Box(Modifier.fillMaxWidth()) { content() }
}

private fun Context.go(intent: Intent) = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

// ---- Quick toggles -------------------------------------------------------------

private fun ToggleKind.icon(): ImageVector = when (this) {
    ToggleKind.Torch -> Icons.Filled.FlashlightOn
    ToggleKind.Wifi -> Icons.Filled.Wifi
    ToggleKind.Bluetooth -> Icons.Filled.Bluetooth
    ToggleKind.Dnd -> Icons.Filled.DoNotDisturbOn
    ToggleKind.Volume -> Icons.Filled.VolumeUp
    ToggleKind.Silent -> Icons.Filled.NotificationsActive
    ToggleKind.Brightness -> Icons.Filled.BrightnessMedium
    ToggleKind.Hotspot -> Icons.Filled.WifiTethering
    ToggleKind.Location -> Icons.Filled.LocationOn
    ToggleKind.Airplane -> Icons.Filled.AirplanemodeActive
    ToggleKind.Camera -> Icons.Filled.CameraAlt
    ToggleKind.Nfc -> Icons.Filled.Nfc
    ToggleKind.Data -> Icons.Filled.SignalCellularAlt
    ToggleKind.Battery -> Icons.Filled.BatterySaver
}

/** null = this switch has no on/off state we can read (it just opens something). */
private fun toggleState(context: Context, k: ToggleKind, torch: Boolean): Boolean? = runCatching {
    when (k) {
        ToggleKind.Torch -> torch
        ToggleKind.Wifi -> context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
        ToggleKind.Bluetooth -> context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled
        ToggleKind.Dnd -> context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        ToggleKind.Silent -> context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL
        ToggleKind.Location -> if (Build.VERSION.SDK_INT >= 28) context.getSystemService(android.location.LocationManager::class.java).isLocationEnabled else null
        ToggleKind.Airplane -> Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        ToggleKind.Nfc -> android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled
        ToggleKind.Battery -> context.getSystemService(PowerManager::class.java).isPowerSaveMode
        else -> null
    }
}.getOrNull()

private fun panelOr(context: Context, panel: String, fallback: String) =
    (Build.VERSION.SDK_INT >= 29 && context.go(Intent(panel))) || context.go(Intent(fallback))

private fun runToggle(context: Context, k: ToggleKind, torch: Boolean, setTorch: (Boolean) -> Unit) {
    val nm = context.getSystemService(NotificationManager::class.java)
    when (k) {
        ToggleKind.Torch -> runCatching {
            val cm = context.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cm.setTorchMode(id, !torch); setTorch(!torch)
        }
        ToggleKind.Wifi -> panelOr(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        ToggleKind.Bluetooth -> context.go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        ToggleKind.Dnd -> if (nm.isNotificationPolicyAccessGranted) {
            val on = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            android.widget.Toast.makeText(context, "Allow Nexus to change Do Not Disturb, then tap again", android.widget.Toast.LENGTH_LONG).show()
            context.go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        ToggleKind.Volume -> context.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        ToggleKind.Silent -> runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            am.ringerMode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                else -> AudioManager.RINGER_MODE_NORMAL
            }
        }
        ToggleKind.Brightness -> context.go(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        ToggleKind.Hotspot -> context.go(Intent("android.settings.TETHER_SETTINGS")) || context.go(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        ToggleKind.Location -> context.go(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        ToggleKind.Airplane -> context.go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        ToggleKind.Camera -> context.go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        ToggleKind.Nfc -> panelOr(context, Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS)
        ToggleKind.Data -> panelOr(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS)
        ToggleKind.Battery -> context.go(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }
}

@Composable
fun TogglesView(block: HomeBlock.Toggles) {
    val context = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(CameraManager::class.java)
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torch = enabled }
        }
        runCatching { cm.registerTorchCallback(cb, null) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    Framed(block.framed) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = if (block.items.size <= 5) Arrangement.SpaceEvenly else Arrangement.spacedBy(12.dp),
        ) {
            block.items.forEach { k ->
                @Suppress("UNUSED_EXPRESSION") tick
                val on = toggleState(context, k, torch)
                ToggleButton(block, k, on) { runToggle(context, k, torch) { torch = it }; tick++ }
            }
        }
    }
}

@Composable
private fun ToggleButton(block: HomeBlock.Toggles, k: ToggleKind, on: Boolean?, onClick: () -> Unit) {
    val active = on == true
    val col = if (active) Nx.Cyan else Nx.Text
    val size = block.sizeDp.coerceIn(32f, 90f).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (block.style) {
            ToggleStyle.Pills -> Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (active) Nx.Cyan.copy(alpha = 0.22f) else Nx.Text.copy(alpha = 0.07f))
                    .nexusPress(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(k.icon(), null, tint = col, modifier = Modifier.size(size * 0.38f))
                if (block.showLabels) Text("  " + k.label, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            else -> {
                val shape = when (block.style) { ToggleStyle.Squares -> RoundedCornerShape(size * 0.28f); else -> CircleShape }
                Box(
                    Modifier.size(size).clip(shape)
                        .then(
                            if (block.style == ToggleStyle.Minimal) Modifier
                            else Modifier.background(if (active) Nx.Cyan.copy(alpha = 0.85f) else Nx.Text.copy(alpha = 0.08f))
                                .border(1.dp, if (active) Nx.Cyan else Nx.Text.copy(alpha = 0.12f), shape),
                        )
                        .nexusPress(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(k.icon(), k.label, tint = if (active && block.style != ToggleStyle.Minimal) Nx.Void else col, modifier = Modifier.size(size * 0.45f))
                }
                if (block.showLabels) Text(k.label, color = Nx.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).widthIn(max = size + 16.dp))
            }
        }
    }
}

// ---- Calendar ------------------------------------------------------------------

private data class AgendaItem(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val colour: Int)

private fun agenda(context: Context, days: Int, max: Int): List<AgendaItem> = runCatching {
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .let { ContentUris.appendId(it, now - 3_600_000); ContentUris.appendId(it, now + days * 86_400_000L); it }.build()
    val out = mutableListOf<AgendaItem>()
    context.contentResolver.query(
        uri,
        arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR),
        null, null, CalendarContract.Instances.BEGIN + " ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < max) {
            if (c.getLong(3) < now) continue
            out += AgendaItem(c.getLong(0), c.getString(1) ?: "Event", c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getInt(5))
        }
    }
    out
}.getOrDefault(emptyList())

private fun whenText(e: AgendaItem): String {
    val cal = Calendar.getInstance(); val today = cal.get(Calendar.DAY_OF_YEAR); val year = cal.get(Calendar.YEAR)
    cal.timeInMillis = e.begin
    val day = when {
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.DAY_OF_YEAR) == today -> "Today"
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.DAY_OF_YEAR) == today + 1 -> "Tomorrow"
        else -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(e.begin))
    }
    return if (e.allDay) day else "$day · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.begin))
}

@Composable
fun AgendaView(block: HomeBlock.Agenda) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var items by remember { mutableStateOf<List<AgendaItem>>(emptyList()) }
    LaunchedEffect(granted, block.days, block.maxItems) {
        while (granted) { items = agenda(context, block.days, block.maxItems); delay(60_000) }
    }
    fun open(e: AgendaItem) = context.go(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)))
    Framed(block.framed) {
        Column(verticalArrangement = Arrangement.spacedBy(if (block.style == AgendaStyle.Compact) 4.dp else 8.dp)) {
            if (block.title.isNotBlank()) HudLabel(block.title)
            when {
                !granted -> Text("Tap to show your calendar here", color = Nx.Muted, modifier = Modifier.clickable { ask.launch(Manifest.permission.READ_CALENDAR) })
                items.isEmpty() -> Text("Nothing in the next ${block.days} day" + if (block.days == 1) "" else "s", color = Nx.Muted)
                else -> items.forEachIndexed { i, e ->
                    val dot = Color(e.colour).copy(alpha = 1f).takeIf { e.colour != 0 } ?: Nx.Cyan
                    when (block.style) {
                        AgendaStyle.Compact -> Row(Modifier.fillMaxWidth().clickable { open(e) }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(dot, CircleShape))
                            Text("  ${whenText(e)}  ", color = Nx.Muted, fontSize = 12.sp)
                            Text(e.title, color = Nx.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AgendaStyle.Cards -> Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(dot.copy(alpha = 0.16f)).clickable { open(e) }.padding(12.dp),
                        ) {
                            Text(e.title, color = Nx.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(whenText(e), color = dot, fontSize = 12.sp)
                        }
                        AgendaStyle.Timeline -> Row(Modifier.fillMaxWidth().clickable { open(e) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
                                Box(Modifier.size(10.dp).background(dot, CircleShape))
                                if (i < items.size - 1) Box(Modifier.width(2.dp).height(34.dp).background(Nx.Line))
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(whenText(e), color = Nx.Muted, fontSize = 11.sp)
                                Text(e.title, color = Nx.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        AgendaStyle.List -> Row(Modifier.fillMaxWidth().clickable { open(e) }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(32.dp).background(dot, RoundedCornerShape(2.dp)))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(e.title, color = Nx.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(whenText(e), color = Nx.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Note ----------------------------------------------------------------------

@Composable
fun NoteView(block: HomeBlock.Note) {
    val c = appContainer()
    var editing by remember { mutableStateOf(false) }
    Framed(block.framed) {
        Text(
            block.text.ifBlank { "Tap to write a note" },
            color = if (block.text.isBlank()) Nx.Muted else Nx.Text,
            fontSize = block.sizeSp.sp, lineHeight = (block.sizeSp * 1.35f).sp,
            fontFamily = if (block.handwritten) FontFamily.Cursive else null,
            textAlign = when (block.align) { Align.Start -> TextAlign.Start; Align.Center -> TextAlign.Center; Align.End -> TextAlign.End },
            modifier = Modifier.fillMaxWidth().clickable { editing = true },
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(block.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Note") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp)) },
            confirmButton = {
                TextButton({ c.editLook("Note edited") { l -> l.updateBlock(block.id) { b -> if (b is HomeBlock.Note) b.copy(text = text) else b } }; editing = false }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = false }) { Text("Cancel") } },
        )
    }
}

// ---- Countdown -----------------------------------------------------------------

fun parseCountdown(s: String): Long? = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s.trim())?.time }.getOrNull()
    ?: runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.trim())?.time }.getOrNull()

@Composable
fun CountdownView(block: HomeBlock.Countdown) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.style) { while (true) { now = System.currentTimeMillis(); delay(if (block.style == CountdownStyle.Split) 1000 else 30_000) } }
    val target = parseCountdown(block.target)
    val left = ((target ?: now) - now).coerceAtLeast(0)
    val d = left / 86_400_000; val h = (left / 3_600_000) % 24; val m = (left / 60_000) % 60; val sec = (left / 1000) % 60
    val start = block.startedAt.takeIf { it in 1 until (target ?: 0) } ?: ((target ?: now) - 30L * 86_400_000)
    val progress = if (target == null) 0f else ((now - start).toFloat() / (target - start)).coerceIn(0f, 1f)
    val done = target != null && left == 0L
    Framed(block.framed) {
        when (block.style) {
            CountdownStyle.Compact -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.title, color = Nx.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (done) "Today!" else if (d > 0) "$d days" else "${h}h ${m}m", color = Nx.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            CountdownStyle.Ring -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(76.dp)) {
                        val t = 7.dp.toPx(); val tl = Offset(t / 2, t / 2); val sz = Size(size.width - t, size.height - t)
                        drawArc(Nx.Cyan.copy(alpha = 0.15f), -90f, 360f, false, tl, sz, style = Stroke(t))
                        drawArc(Nx.Cyan, -90f, 360f * progress, false, tl, sz, style = Stroke(t, cap = StrokeCap.Round))
                    }
                    Text(if (done) "🎉" else "$d", color = Nx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(block.title, color = Nx.Text, style = MaterialTheme.typography.titleMedium)
                    Text(if (done) "It's here" else "days to go · ${h}h ${m}m", color = Nx.Muted, fontSize = 12.sp)
                }
            }
            CountdownStyle.Split -> Column {
                Text(hud(block.title), style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(d to "DAYS", h to "HRS", m to "MIN", sec to "SEC").forEach { (v, l) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v.toString().padStart(2, '0'), color = Nx.Text, fontSize = 32.sp, fontWeight = FontWeight.Light, fontFamily = MaterialTheme.typography.displaySmall.fontFamily)
                            Text(l, color = Nx.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                        }
                    }
                }
            }
            CountdownStyle.Big -> Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (done) "Today" else "$d", color = Nx.Text, fontSize = 54.sp, fontWeight = FontWeight.Light, fontFamily = MaterialTheme.typography.displaySmall.fontFamily)
                    if (!done) Text("  days · ${h}h", color = Nx.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(block.title, color = Nx.Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (block.showProgress && block.style != CountdownStyle.Ring) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Nx.Cyan.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Brush.horizontalGradient(listOf(Nx.Cyan, Nx.Magenta))))
        }
    }
}

// ---- Photo ---------------------------------------------------------------------

@Composable
fun PhotoView(block: HomeBlock.Photo) {
    val c = appContainer()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            c.editLook("Photo set") { l -> l.updateBlock(block.id) { b -> if (b is HomeBlock.Photo) b.copy(uri = uri.toString()) else b } }
        }
    }
    val shape = com.eoinknd16.nexus.ui.fx.nexusShape()
    Box(Modifier.fillMaxWidth().height(block.heightDp.coerceIn(60f, 500f).dp).clip(shape)) {
        if (block.uri.isBlank()) {
            Box(Modifier.fillMaxSize().holoPanel().clickable { pick.launch(arrayOf("image/*")) }, contentAlignment = Alignment.Center) {
                Text("Tap to pick a photo", color = Nx.Muted)
            }
        } else {
            val drift = rememberInfiniteTransition(label = "drift")
            val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
            AsyncImage(
                model = Uri.parse(block.uri), contentDescription = block.caption, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (block.drift) { val sc = 1.04f + 0.1f * t; scaleX = sc; scaleY = sc; translationX = (t - 0.5f) * size.width * 0.05f }
                },
            )
            if (block.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = block.dim.coerceIn(0f, 0.9f))))
            if (block.fade || block.caption.isNotBlank()) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            if (block.caption.isNotBlank()) Text(
                block.caption, color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
    }
}
