package com.eoinknd16.nexus.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.SetupInfo
import com.eoinknd16.nexus.look.parseColor
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved setups: your whole home screen saved under a name, so you can try
 * anything and come back. Loading one always saves what you had first, and
 * Nexus keeps automatic backups as you go.
 */
@Composable
fun SetupsScreen() {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by c.setups.list.collectAsStateWithLifecycle()
    val saved = list.filter { it.kind == "manual" }
    val backups = list.filter { it.kind != "manual" }
    var naming by remember { mutableStateOf<Pair<String, String?>?>(null) } // (initial name, id when renaming)
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showBackups by rememberSaveable { mutableStateOf(false) }
    var exportId by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch { message = if (c.setups.exportTo(id, uri)) "Exported" else "Couldn't export that" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = c.setups.importFrom(uri)
            message = if (name != null) "Imported “$name”" else "That file isn't a Nexus setup"
        }
    }

    fun load(s: SetupInfo) {
        confirm = "Load “${s.name}”? What you have now is saved first as a backup, and Undo works too." to {
            scope.launch { message = if (c.loadSetup(s.id)) "“${s.name}” loaded" else "Couldn't load it" }
        }
    }

    NexusBackdrop(intensity = 0.45f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Saved setups", "${saved.size} saved · ${backups.size} backups")
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Lime) {
                        Text("Your setup right now", style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                        Text(
                            "Saves everything: every page, block, tile, folder and tray slot, colours, fonts, tile styles, gestures, motion, your icon pack and hidden apps.",
                            style = MaterialTheme.typography.bodySmall, color = Nx.Muted,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NexusButton("Save as new", icon = Icons.Filled.Add, accent = Nx.Lime, filled = true) {
                                naming = (LookState.look.presetName.takeIf { it != "Nexus" } ?: "My setup") to null
                            }
                            NexusButton("Import", icon = Icons.Filled.FileDownload) { importer.launch(arrayOf("application/json", "*/*")) }
                        }
                    }
                }
                message?.let { m -> item { Text(m, color = Nx.Lime, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(4.dp)) } }
                item { Spacer(Modifier.height(4.dp)); HudLabel("Ideas to try") }
                item {
                    Text(
                        "These restyle your own setup: your tray, tiles, folders and apps stay put. What you have now is saved first, and Undo works too.",
                        color = Nx.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(com.eoinknd16.nexus.look.lookIdeas, key = { "idea-" + it.name }) { idea ->
                    Row(
                        Modifier.fillMaxWidth().clip(nexusShape(0.6f)).background(Nx.Panel).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(idea.name, color = Nx.Text, style = MaterialTheme.typography.titleSmall)
                            Text(idea.description, color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(10.dp))
                        NexusButton("Try it", accent = Nx.Lime) {
                            scope.launch { c.tryIdea(idea); message = "“${idea.name}” applied. Your previous setup is in the backups below." }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)); HudLabel("Saved") }
                if (saved.isEmpty()) item {
                    Text("Nothing saved yet. Save what you have, then experiment as much as you like.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                items(saved, key = { it.id }) { s ->
                    SetupRow(
                        s,
                        onLoad = { load(s) },
                        menu = listOf(
                            "Update with what I have now" to {
                                confirm = "Replace “${s.name}” with your current setup?" to { scope.launch { c.updateSetup(s.id); message = "“${s.name}” updated" } }
                            },
                            "Rename" to { naming = s.name to s.id },
                            "Duplicate" to { scope.launch { c.setups.duplicate(s.id) } },
                            "Export to a file" to { exportId = s.id; exporter.launch("nexus-${s.name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.json") },
                            "Delete" to { confirm = "Delete “${s.name}”? This can't be undone." to { scope.launch { c.setups.delete(s.id) } } },
                        ),
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().nexusPress({ showBackups = !showBackups }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HudLabel("Automatic backups (${backups.size})", Modifier.weight(1f))
                        Text(hud(if (showBackups) "Hide" else "Show"), color = Nx.Cyan, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "Made for you: whenever you load a setup (what you had before), and every 15 minutes or so while you change things. The newest 12 of each are kept.",
                        color = Nx.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (showBackups) items(backups, key = { it.id }) { s ->
                    SetupRow(
                        s,
                        onLoad = { load(s) },
                        menu = listOf(
                            "Keep as a saved setup" to { scope.launch { c.setups.keep(s.id); message = "Kept" } },
                            "Export to a file" to { exportId = s.id; exporter.launch("nexus-backup.json") },
                            "Delete" to { scope.launch { c.setups.delete(s.id) } },
                        ),
                    )
                }
            }
        }
    }

    naming?.let { (initial, renameId) ->
        var name by remember(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { naming = null },
            containerColor = Nx.PanelSolid,
            title = { Text(if (renameId == null) "Save setup" else "Rename", color = Nx.Text) },
            text = {
                BasicTextField(
                    name, { name = it }, singleLine = true, textStyle = TextStyle(color = Nx.Text, fontSize = 18.sp), cursorBrush = SolidColor(Nx.Cyan),
                    modifier = Modifier.fillMaxWidth().clip(nexusShape(0.5f)).background(Nx.Deep).padding(12.dp),
                )
            },
            confirmButton = {
                TextButton({
                    val n = name.trim().ifBlank { "My setup" }
                    naming = null
                    scope.launch {
                        if (renameId == null) { c.saveSetup(n); message = "Saved “$n”" } else c.setups.rename(renameId, n)
                    }
                }) { Text("Save", color = Nx.Cyan) }
            },
            dismissButton = { TextButton({ naming = null }) { Text("Cancel", color = Nx.Muted) } },
        )
    }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Nx.PanelSolid,
            text = { Text(text, color = Nx.Text) },
            confirmButton = { TextButton({ confirm = null; action() }) { Text("Yes", color = Nx.Cyan) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel", color = Nx.Muted) } },
        )
    }
}

@Composable
private fun SetupRow(s: SetupInfo, onLoad: () -> Unit, menu: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    val date = remember(s.updatedAt) { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(s.updatedAt)) }
    Row(
        Modifier.fillMaxWidth().nexusPress(onLoad).clip(nexusShape(0.6f)).background(Nx.Panel).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Its colours at a glance, background first.
        Box(Modifier.size(46.dp).clip(nexusShape(0.5f)).background(parseColor(s.colours[0])), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                s.colours.drop(1).take(3).forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(parseColor(it))) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Nx.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$date · ${s.pages} page${if (s.pages == 1) "" else "s"} · ${s.tiles} tiles", color = Nx.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Icon(Icons.Filled.Restore, "Load", tint = Nx.Cyan, modifier = Modifier.size(20.dp))
        Box {
            Box(Modifier.size(40.dp).nexusPress({ open = true }), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreVert, "More", tint = Nx.Muted) }
            DropdownMenu(open, { open = false }, containerColor = Nx.PanelSolid) {
                menu.forEach { (label, action) ->
                    DropdownMenuItem({ Text(label, color = if (label == "Delete") Nx.Amber else Nx.Text) }, { open = false; action() })
                }
            }
        }
    }
}
