package com.eoinknd16.nexus.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import com.eoinknd16.nexus.update.UpdateState
import kotlinx.coroutines.launch

/** After granting "Install unknown apps", carry on with the waiting update automatically. */
@Composable
private fun ResumeAfterPermission() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val st = c.updater.state.value
        val pending = c.updater.pending
        if (st is UpdateState.NeedsPermission && pending != null && c.updater.canInstall()) {
            scope.launch { c.updater.downloadAndInstall(pending) }
        }
        onPauseOrDispose { }
    }
}

/** Slim card at the top of home whenever there's something to do about an update. */
@Composable
fun UpdateBanner() {
    val c = appContainer()
    val state by c.updater.state.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf<String?>(null) }
    ResumeAfterPermission()
    val version = when (val s = state) {
        is UpdateState.Available -> s.version
        is UpdateState.Downloading -> s.version
        is UpdateState.Installing -> s.version
        is UpdateState.NeedsPermission -> s.version
        else -> null
    } ?: return
    if (dismissed == version && state is UpdateState.Available) return
    HoloPanel(Modifier.fillMaxWidth().padding(bottom = 4.dp), accent = Nx.Lime) {
        UpdateBody(state, compact = true, onLater = { dismissed = version })
    }
}

/** Settings > Updates. */
@Composable
fun UpdateSection() {
    val c = appContainer()
    val state by c.updater.state.collectAsStateWithLifecycle()
    ResumeAfterPermission()
    HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Lime) {
        Text("Nexus ${c.updater.currentVersion}", style = MaterialTheme.typography.titleMedium, color = Nx.Text)
        Text(
            "Updates come from GitHub and install over this version, so your setup stays put. Checks every few hours by itself.",
            style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
        )
        Spacer(Modifier.height(10.dp))
        UpdateBody(state, compact = false, onLater = null)
    }
}

@Composable
private fun UpdateBody(state: UpdateState, compact: Boolean, onLater: (() -> Unit)?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    when (state) {
        UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed -> {
            when (state) {
                is UpdateState.UpToDate -> Text(hud("Up to date"), style = MaterialTheme.typography.labelMedium, color = Nx.Lime)
                is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Nx.Amber)
                else -> {}
            }
            Spacer(Modifier.height(8.dp))
            NexusButton("Check for updates", icon = Icons.Filled.Refresh) { scope.launch { c.updater.check() } }
        }
        UpdateState.Checking -> Text(hud("Checking…"), style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
        is UpdateState.Available -> {
            Text("Update available: ${state.version}", style = MaterialTheme.typography.titleMedium, color = Nx.Lime)
            if (!compact && state.notes.isNotBlank()) {
                Text(state.notes.take(600), style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NexusButton("Update now", icon = Icons.Filled.SystemUpdate, accent = Nx.Lime, filled = true) {
                    scope.launch { c.updater.downloadAndInstall(state) }
                }
                if (onLater != null) NexusButton("Later", accent = Nx.Muted, onClick = onLater)
            }
        }
        is UpdateState.Downloading -> Column {
            Text(hud("Downloading ${state.version}… ${(state.progress * 100).toInt()}%"), style = MaterialTheme.typography.labelMedium, color = Nx.Lime)
            Spacer(Modifier.height(8.dp))
            GlowProgress(state.progress, Modifier.fillMaxWidth(), Nx.Lime)
        }
        is UpdateState.Installing -> Text(hud("Installing ${state.version}… confirm on the Android prompt"), style = MaterialTheme.typography.labelMedium, color = Nx.Lime)
        is UpdateState.NeedsPermission -> {
            Text(
                "One-time step: allow Nexus to install updates. Turn on \"Allow from this source\", then come back and it carries on.",
                style = MaterialTheme.typography.bodyMedium, color = Nx.Text,
            )
            Spacer(Modifier.height(8.dp))
            NexusButton("Allow updates", accent = Nx.Lime, filled = true) { c.updater.openInstallPermissionSettings() }
        }
    }
}
