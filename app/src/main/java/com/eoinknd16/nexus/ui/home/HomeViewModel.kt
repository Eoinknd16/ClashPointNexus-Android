package com.eoinknd16.nexus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.link.WakeOnLan
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PcState(val kind: Kind, val label: String, val short: String) {
    enum class Kind { Unpaired, Checking, Online, Offline }

    companion object {
        val Unpaired = PcState(Kind.Unpaired, "PC not linked", "Not linked")
    }
}

data class HomeState(val pc: PcState = PcState.Unpaired, val wakeMessage: String? = null)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    val continueWatching: StateFlow<List<WatchProgress>> = c.media.progress
        .map { c.media.continueWatching(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dock: StateFlow<List<LaunchableApp>> = combine(c.apps.apps, c.apps.prefs.state) { apps, prefs ->
        val byKey = apps.associateBy { it.key }
        prefs.pinned.mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var pollJob: Job? = null

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshPc()
                delay(15_000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    private suspend fun refreshPc() {
        val config = c.pcStore.get()
        if (!config.isPaired) {
            _state.update { it.copy(pc = PcState.Unpaired) }
            return
        }
        val name = config.name.ifBlank { "PC" }
        if (_state.value.pc.kind == PcState.Kind.Unpaired) {
            _state.update { it.copy(pc = PcState(PcState.Kind.Checking, "Linking $name…", "Linking…")) }
        }
        val pc = runCatching { c.pc.status() }.fold(
            onSuccess = { s ->
                val playing = s.nowPlaying?.title
                PcState(PcState.Kind.Online, if (playing != null) "$name · playing $playing" else "$name online", "Online")
            },
            onFailure = { PcState(PcState.Kind.Offline, "$name offline", "Offline") },
        )
        _state.update { it.copy(pc = pc) }
    }

    suspend fun wakePc() {
        val config = c.pcStore.get()
        if (config.macs.isEmpty()) {
            flashWake(if (config.isPaired) "No MAC on file" else "Link PC first")
            return
        }
        // Also aim at the PC's subnet broadcast (x.y.z.255) -- some routers drop 255.255.255.255.
        val subnet = Regex("^(\\d+\\.\\d+\\.\\d+)\\.\\d+$").find(config.host)?.groupValues?.get(1)?.let { "$it.255" }
        val sent = runCatching { WakeOnLan.wake(config.macs, listOfNotNull(subnet)) }.getOrDefault(0)
        flashWake(if (sent > 0) "Packet sent" else "Send failed")
        // Give it time to boot, then re-check.
        delay(20_000)
        refreshPc()
    }

    private fun flashWake(message: String) {
        _state.update { it.copy(wakeMessage = message) }
        viewModelScope.launch {
            delay(4_000)
            _state.update { it.copy(wakeMessage = null) }
        }
    }

    fun launch(app: LaunchableApp) = c.apps.launch(app)

    fun openContinue(p: WatchProgress, nav: NavController) {
        nav.navigate(Routes.detail(p.type.name, p.id))
    }
}
