package com.eoinknd16.nexus.ui.home

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.verticalScroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.allPcs
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.plainTap
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether the one-tile PC panel is open (set by the "PC controls" tile action). */
object PcHub {
    var open by mutableStateOf(false)
}

/**
 * Every PC control behind one tile: status, which PC, remote, wake, and the
 * power actions -- which need a press-and-hold so they can't fire by accident.
 */
@Composable
fun PcHubPanel(onOpenRemote: () -> Unit) {
    if (!PcHub.open) return
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val active by c.pcStore.state.collectAsStateWithLifecycle()
    val saved by c.pcSaved.state.collectAsStateWithLifecycle()
    val pcs = allPcs(active, saved)
    var online by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    fun close() { PcHub.open = false }

    LaunchedEffect(active.key) {
        online = null
        while (true) {
            online = if (active.isPaired) runCatching { c.pc.status() }.isSuccess else false
            delay(4_000)
        }
    }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        for (s in 20 downTo 1) { message = "$p in $s s"; delay(1_000) }
        pending = null
        message = null
    }

    fun power(action: String) = scope.launch {
        runCatching { c.pc.power(action) }.fold(
            onSuccess = { r ->
                when {
                    !r.ok -> message = r.error ?: "PC refused"
                    action == "shutdown" -> pending = "Shutting down"
                    action == "restart" -> pending = "Restarting"
                    action == "cancel" -> { pending = null; message = "Cancelled" }
                    else -> message = "Going to sleep"
                }
            },
            onFailure = { message = "PC didn't respond (${it.describe()})" },
        )
    }

    val bars = rememberSystemBarPadding()
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().plainTap(::close).padding(top = bars.top, bottom = bars.bottom), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp)
                    .scrollIfTall()
                    .plainTap(null) // taps inside don't close it
                    .holoPanel(Nx.Cyan, 0.25f).background(Nx.PanelSolid.copy(alpha = 0.9f), nexusShape(0.6f)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dot = when (online) { true -> Nx.Lime; false -> Nx.Danger; null -> Nx.Amber }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (active.isPaired) active.displayName else "No PC linked", style = MaterialTheme.typography.titleLarge, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hud(when (online) { true -> "Online"; false -> "Offline / asleep"; null -> "Checking…" }), style = MaterialTheme.typography.labelSmall, color = dot)
                    }
                    Box(Modifier.size(40.dp).nexusPress(::close), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Close", tint = Nx.Muted) }
                }
                if (pcs.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pcs.forEach { p ->
                            val on = p.key == active.key
                            Text(
                                p.displayName, style = MaterialTheme.typography.labelLarge, color = if (on) Nx.Text else Nx.Muted,
                                modifier = Modifier.nexusPress({ if (!on) scope.launch { c.pc.switchTo(p.key) } })
                                    .holoPanel(if (on) Nx.Lime else Nx.CyanDim, if (on) 0.2f else 0f).padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubButton("Remote", Icons.Filled.SettingsRemote, Nx.Cyan, Modifier.weight(1f)) { close(); onOpenRemote() }
                    HubButton("Wake", Icons.Filled.PowerSettingsNew, Nx.Amber, Modifier.weight(1f)) {
                        scope.launch {
                            message = when (c.pc.wake()) { -1 -> "No MAC address on file"; 0 -> "Couldn't send wake packet"; else -> "Wake packet sent" }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubButton("Quick menu", Icons.Filled.Widgets, Nx.Cyan, Modifier.weight(1f)) {
                        scope.launch { runCatching { c.pc.nav("quickMenu") }.onFailure { message = "PC didn't respond" } }
                    }
                    HubButton("TV on PC", Icons.Filled.Movie, Nx.Magenta, Modifier.weight(1f)) {
                        scope.launch { runCatching { c.pc.openScreen("tv") }.onFailure { message = "PC didn't respond" } }
                    }
                }
                Spacer(Modifier.height(18.dp))
                HudLabel("Power · hold to confirm")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldButton("Sleep", Icons.Filled.Bedtime, Nx.Cyan, Modifier.weight(1f)) { power("sleep") }
                    HoldButton("Restart", Icons.Filled.RestartAlt, Nx.Amber, Modifier.weight(1f)) { power("restart") }
                    HoldButton("Shut down", Icons.Filled.PowerSettingsNew, Nx.Danger, Modifier.weight(1f)) { power("shutdown") }
                }
                message?.let { m ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hud(m), style = MaterialTheme.typography.labelMedium, color = Nx.Amber, modifier = Modifier.weight(1f))
                        if (pending != null) {
                            Text(
                                hud("Cancel"), style = MaterialTheme.typography.labelLarge, color = Nx.Text,
                                modifier = Modifier.nexusPress({ power("cancel") }).holoPanel(Nx.Danger, 0.3f).padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubButton(label: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).nexusPress(onClick).holoPanel(accent, 0.12f).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Nx.Text, maxLines = 1)
    }
}

/** Fires only after being held for ~0.8 s; the fill shows how close you are. */
@Composable
private fun HoldButton(label: String, icon: ImageVector, accent: Color, modifier: Modifier, onConfirmed: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = com.eoinknd16.nexus.ui.fx.rememberHaptics()
    Box(
        modifier.height(64.dp).clip(nexusShape(0.6f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        haptics.tick()
                        progress.animateTo(1f, tween(800, easing = LinearEasing))
                        haptics.confirm()
                        onConfirmed()
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    scope.launch { progress.animateTo(0f, tween(200)) }
                }
            }
            .holoPanel(accent, 0.1f),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(accent.copy(alpha = 0.35f)))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = accent)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Nx.Text, maxLines = 1)
        }
    }
}

/** The PC-related tile actions that "PC controls" replaces. */
fun isPcTile(t: com.eoinknd16.nexus.look.TileConfig): Boolean {
    val k = t.action.kind
    return k == com.eoinknd16.nexus.look.TileAction.Kind.WakePc || k == com.eoinknd16.nexus.look.TileAction.Kind.SwitchPc ||
        k == com.eoinknd16.nexus.look.TileAction.Kind.PcPower || k == com.eoinknd16.nexus.look.TileAction.Kind.PcNav ||
        k == com.eoinknd16.nexus.look.TileAction.Kind.PcScreen || k == com.eoinknd16.nexus.look.TileAction.Kind.PcHub ||
        (k == com.eoinknd16.nexus.look.TileAction.Kind.Screen && t.action.arg == "pc")
}



/** Lets the panel scroll on short screens instead of running under the bars. */
@Composable
private fun Modifier.scrollIfTall(): Modifier = this.then(Modifier.heightIn(max = 4000.dp).verticalScroll(rememberScrollState()))
