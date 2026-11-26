package com.eoinknd16.nexus.ui.pc

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
import androidx.compose.foundation.shape.CutCornerShape
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
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PcScreenState(val status: PcStatus? = null, val error: String? = null, val toast: String? = null)

class PcViewModel(private val c: AppContainer) : ViewModel() {
    val config = c.pcStore.state
    private val _state = MutableStateFlow(PcScreenState())
    val state: StateFlow<PcScreenState> = _state
    private var poll: Job? = null

    fun start() {
        poll?.cancel()
        poll = viewModelScope.launch {
            while (isActive) {
                if (c.pcStore.get().isPaired) {
                    runCatching { c.pc.status() }.fold(
                        onSuccess = { s -> _state.update { it.copy(status = s, error = null) } },
                        onFailure = { e -> _state.update { it.copy(status = null, error = e.describe()) } },
                    )
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

    fun wake() = viewModelScope.launch {
        val cfg = c.pcStore.get()
        if (cfg.macs.isEmpty()) return@launch toast("No MAC address on file. Re-pair while the PC is on.")
        val subnet = Regex("^(\\d+\\.\\d+\\.\\d+)\\.\\d+$").find(cfg.host)?.groupValues?.get(1)?.let { "$it.255" }
        val sent = runCatching { WakeOnLan.wake(cfg.macs, listOfNotNull(subnet)) }.getOrDefault(0)
        toast(if (sent > 0) "Wake packet sent" else "Couldn't send wake packet")
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
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }

    NexusBackdrop(intensity = 0.6f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader(
                config.name.ifBlank { "Living room PC" },
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

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                state.status?.nowPlaying?.let { np ->
                    HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Magenta) {
                        Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Nx.Magenta)
                        Text(np.title, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Wake", Modifier.weight(1f), icon = Icons.Filled.PowerSettingsNew, accent = Nx.Amber) { vm.wake() }
                    NexusButton("Quick menu", Modifier.weight(1f), icon = Icons.Filled.Widgets) { vm.nav("quickMenu") }
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
                it.uppercase(), style = MaterialTheme.typography.labelMedium, color = Nx.Text,
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
        Modifier.size(56.dp).nexusPress(onClick).clip(CutCornerShape(12.dp)).background(Nx.Panel).border(1.dp, Nx.CyanDim, CutCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Nx.Text) }
}
