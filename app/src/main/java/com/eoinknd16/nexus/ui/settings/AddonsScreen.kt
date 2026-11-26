package com.eoinknd16.nexus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.media.Addon
import com.eoinknd16.nexus.media.CommunityAddon
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.launch

@Composable
fun AddonsScreen() {
    val media = appContainer().media
    val scope = rememberCoroutineScope()
    val installed by media.addons.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var community by remember { mutableStateOf<List<CommunityAddon>?>(null) }
    var communityError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { media.communityAddons() }
            .onSuccess { community = it }
            .onFailure { communityError = it.describe() }
    }

    fun install(target: String) {
        status = "Installing…"
        scope.launch {
            status = runCatching { media.installAddon(target) }.fold(
                { "Installed ${it.name}" },
                { "Install failed (${it.describe()})" },
            )
        }
    }

    NexusBackdrop(intensity = 0.4f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Addons", "Stremio-compatible")
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { HudLabel("Installed · order = stream priority") }
                if (installed.isEmpty()) {
                    item {
                        Text(
                            "Nothing yet. Import from the PC in Settings, paste an addon URL below, or pick one from the store.",
                            style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                        )
                    }
                }
                items(installed, key = { "i:${it.url}" }) { addon ->
                    InstalledRow(
                        addon,
                        onUp = { scope.launch { media.moveAddon(addon.url, up = true) } },
                        onDown = { scope.launch { media.moveAddon(addon.url, up = false) } },
                        onRemove = { scope.launch { media.removeAddon(addon.url) } },
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    HudLabel("Add by URL")
                }
                item {
                    Field("https://…/manifest.json", url, { url = it }, KeyboardType.Uri)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexusButton("Install", icon = Icons.Filled.Add, filled = true, enabled = url.isNotBlank()) { install(url); url = "" }
                        Spacer(Modifier.width(12.dp))
                        status?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Amber) }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    HudLabel("Community store")
                }
                item { Field("Filter", filter, { filter = it }) }
                val list = community
                when {
                    list == null && communityError == null -> item { LoadingBlock("Fetching collection…") }
                    list == null -> item { Text("Store unavailable ($communityError)", color = Nx.Danger, style = MaterialTheme.typography.labelSmall) }
                    else -> {
                        val shown = list.filter {
                            filter.isBlank() || it.name.contains(filter, true) || it.description.contains(filter, true)
                        }.take(150)
                        items(shown, key = { "c:${it.transportUrl}" }) { ca ->
                            CommunityRow(ca, installed = installed.any { it.url == ca.transportUrl }) { install(ca.transportUrl) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledRow(addon: Addon, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().holoPanel(glow = 0.05f).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(addon.logo)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(addon.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(addon.resources.joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
        }
        SmallIcon(Icons.Filled.KeyboardArrowUp, "Move up", onUp)
        SmallIcon(Icons.Filled.KeyboardArrowDown, "Move down", onDown)
        SmallIcon(Icons.Filled.Delete, "Remove", onRemove, Nx.Danger)
    }
}

@Composable
private fun CommunityRow(addon: CommunityAddon, installed: Boolean, onInstall: () -> Unit) {
    Row(Modifier.fillMaxWidth().holoPanel(glow = 0.0f).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(addon.logo)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(addon.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(addon.description, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(addon.resources.joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
        }
        if (installed) SmallIcon(Icons.Filled.Check, "Installed", {}, Nx.Lime)
        else SmallIcon(Icons.Filled.Add, "Install", onInstall)
    }
}

@Composable
private fun Logo(url: String?) {
    Box(Modifier.size(40.dp).clip(CutCornerShape(6.dp)).background(Nx.Deep), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = Nx.Cyan) {
    Box(Modifier.size(38.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
}
