package com.eoinknd16.nexus.ui.pc

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.foundation.layout.width

import com.eoinknd16.nexus.ui.fx.holoPanel

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.PcStatus
import com.eoinknd16.nexus.link.WakeOnLan
import com.eoinknd16.nexus.ui.EmptyBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PcScreenState(val status: PcStatus? = null, val error: String? = null, val toast: String? = null, val powerPending: String? = null)

class PcViewModel(private val c: AppContainer) : ViewModel() {
    val config = c.pcStore.state
    val saved = c.pcSaved.state
    /** Online/offline for every saved PC, for the switcher. */
    private val _online = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val online: StateFlow<Map<String, Boolean>> = _online

    fun switchTo(key: String) = viewModelScope.launch {
        c.pc.switchTo(key)?.let { toast("Now controlling ${it.displayName}") }
        _state.update { PcScreenState(toast = it.toast) }
        start()
    }
    private val _state = MutableStateFlow(PcScreenState())
    val state: StateFlow<PcScreenState> = _state
    private var poll: Job? = null

    fun start() {
        poll?.cancel()
        poll = viewModelScope.launch {
            while (isActive) {
                val active = c.pcStore.get()
                if (active.isPaired) {
                    runCatching { c.pc.status() }.fold(
                        onSuccess = { s -> _state.update { it.copy(status = s, error = null) } },
                        onFailure = { e -> _state.update { it.copy(status = null, error = e.describe()) } },
                    )
                    _online.update { it + (active.key to (_state.value.status != null)) }
                    // Check the other PCs too, so the switcher shows which are on.
                    c.pc.all().filter { it.key != active.key }.forEach { p ->
                        launch { val on = c.pc.isOnline(p); _online.update { it + (p.key to on) } }
                    }
                }
                delay(4_000)
            }
        }
    }

    fun stop() {
        poll?.cancel()
    }

    /** Fire-and-forget remote presses; only failures surface. */
    fun nav(action: String) = viewModelScope.launch {
        runCatching { c.pc.nav(action) }.onFailure { e -> toast("Not delivered (${e.describe()})") }
    }

    fun screen(screen: String) = viewModelScope.launch {
        runCatching { c.pc.openScreen(screen) }.onFailure { e -> toast("Not delivered (${e.describe()})") }
    }

    private var powerJob: Job? = null

    fun power(action: String) = viewModelScope.launch {
        runCatching { c.pc.power(action) }.fold(
            onSuccess = { r ->
                if (!r.ok) return@fold toast(r.error ?: "PC refused")
                when (action) {
                    "shutdown", "restart" -> {
                        powerJob?.cancel()
                        powerJob = viewModelScope.launch {
                            for (sLeft in 20 downTo 1) {
                                _state.update { it.copy(powerPending = "${if (action == "shutdown") "Shutting down" else "Restarting"} in $sLeft s") }
                                delay(1_000)
                            }
                            _state.update { it.copy(powerPending = null) }
                        }
                    }
                    "cancel" -> { powerJob?.cancel(); _state.update { it.copy(powerPending = null) }; toast("Cancelled") }
                    "sleep" -> toast("PC going to sleep")
                }
            },
            onFailure = { e -> toast("Not delivered (${e.describe()})") },
        )
    }

    fun wake() = viewModelScope.launch {
        when (c.pc.wake()) {
            -1 -> toast("No MAC address on file. Re-pair while the PC is on.")
            0 -> toast("Couldn't send wake packet")
            else -> toast("Wake packet sent")
        }
    }

    private fun toast(text: String) {
        _state.update { it.copy(toast = text) }
        viewModelScope.launch {
            delay(3_000)
            _state.update { if (it.toast == text) it.copy(toast = null) else it }
        }
    }
}

@Composable
fun PcScreen() {
    val vm = nexusViewModel { PcViewModel(it) }
    val nav = LocalNav.current
    val config by vm.config.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val pcs = com.eoinknd16.nexus.link.allPcs(config, saved)

    LifecycleResumeEffect(Unit) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }

    NexusBackdrop(intensity = 0.6f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader(
                if (config.isPaired) config.displayName else "Living room PC",
                when {
                    !config.isPaired -> "not linked"
                    state.status != null -> "online · nexus ${state.status?.version}"
                    state.error != null -> "offline · ${state.error}"
                    else -> "linking…"
                },
            )
            if (!config.isPaired) {
                EmptyBlock("Link your PC first", "Open Nexus on the PC → Settings › Phone Link for the pairing code.") {
                    NexusButton("Pair now", filled = true) { nav.navigate(Routes.SETTINGS) }
                }
                return@Column
            }

            if (pcs.size > 1) {
                // One tap to switch which PC the remote controls.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pcs.forEach { p ->
                        val active = p.key == config.key
                        Row(
                            Modifier.nexusPress({ if (!active) vm.switchTo(p.key) })
                                .holoPanel(if (active) Nx.Lime else Nx.CyanDim, if (active) 0.2f else 0f)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(when (online[p.key]) { true -> Nx.Lime; false -> Nx.Danger; null -> Nx.Muted }),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(p.displayName, style = MaterialTheme.typography.labelLarge, color = if (active) Nx.Text else Nx.Muted, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                state.status?.nowPlaying?.let { np ->
                    HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Magenta) {
                        Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Nx.Magenta)
                        Text(np.title, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        var powerMenu by remember { mutableStateOf(false) }
                        NexusButton("Power", Modifier.fillMaxWidth(), icon = Icons.Filled.PowerSettingsNew, accent = Nx.Amber) { powerMenu = true }
                        androidx.compose.material3.DropdownMenu(expanded = powerMenu, onDismissRequest = { powerMenu = false }, containerColor = Nx.PanelSolid) {
                            listOf("wake" to "Wake", "sleep" to "Sleep", "restart" to "Restart", "shutdown" to "Shut down").forEach { (k, l) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(l, color = if (k == "shutdown") Nx.Danger else Nx.Text) },
                                    onClick = { powerMenu = false; if (k == "wake") vm.wake() else vm.power(k) },
                                )
                            }
                        }
                    }
                    NexusButton("Quick menu", Modifier.weight(1f), icon = Icons.Filled.Widgets) { vm.nav("quickMenu") }
                }
                state.powerPending?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().holoPanel(Nx.Danger, 0.2f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(hud(msg), style = MaterialTheme.typography.labelLarge, color = Nx.Text, modifier = Modifier.weight(1f))
                        NexusButton("Cancel", filled = true, accent = Nx.Danger) { vm.power("cancel") }
                    }
                }
                Spacer(Modifier.height(24.dp))

                HudLabel("Remote")
                Spacer(Modifier.height(16.dp))
                DPad(onAction = vm::nav)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { vm.nav("back") }
                    RoundKey(Icons.Filled.Menu, "Menu") { vm.nav("menu") }
                    RoundKey(Icons.Filled.Search, "Search") { vm.nav("search") }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundKey(Icons.Filled.VolumeDown, "Volume down") { vm.nav("volumeDown") }
                    RoundKey(Icons.Filled.ClosedCaption, "Subtitles") { vm.nav("toggleSubtitles") }
                    RoundKey(Icons.Filled.SkipNext, "Next episode") { vm.nav("skipNext") }
                    RoundKey(Icons.Filled.VolumeUp, "Volume up") { vm.nav("volumeUp") }
                }

                Spacer(Modifier.height(28.dp))
                HudLabel("Jump to")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Home", Modifier.weight(1f)) { vm.screen("home") }
                    NexusButton("TV", Modifier.weight(1f)) { vm.screen("tv") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Games", Modifier.weight(1f)) { vm.screen("games") }
                    NexusButton("Apps", Modifier.weight(1f)) { vm.screen("apps") }
                }
            }
        }
        state.toast?.let {
            Text(
                hud(it), style = MaterialTheme.typography.labelMedium, color = Nx.Text,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                    .background(Nx.PanelSolid).border(1.dp, Nx.CyanDim).padding(12.dp),
            )
        }
    }
}

/** Circular D-pad: four wedge keys around a central confirm button. */
@Composable
private fun DPad(onAction: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(236.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x223CF2FF), Color(0x0A3CF2FF))))
                .border(1.dp, Nx.CyanDim, CircleShape),
        ) {
            PadKey(Icons.Filled.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter)) { onAction("up") }
            PadKey(Icons.Filled.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter)) { onAction("down") }
            PadKey(Icons.Filled.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart)) { onAction("left") }
            PadKey(Icons.Filled.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd)) { onAction("right") }
            Box(
                Modifier.align(Alignment.Center).size(88.dp).nexusPress({ onAction("confirm") }).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Nx.Cyan.copy(alpha = 0.55f), Nx.Cyan.copy(alpha = 0.15f))))
                    .border(2.dp, Nx.Cyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("OK", style = MaterialTheme.typography.labelMedium, color = Nx.Text) }
        }
    }
}

@Composable
private fun PadKey(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(72.dp).nexusPress(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Nx.Cyan, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).nexusPress(onClick).clip(nexusShape(0.86f)).background(Nx.Panel).border(1.dp, Nx.CyanDim, nexusShape(0.86f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Nx.Text) }
}
