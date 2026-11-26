package com.eoinknd16.nexus.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.appTile
import com.eoinknd16.nexus.look.updateDock
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.launch

@Composable
fun AppDrawerScreen() {
    val repo = appContainer().apps
    val apps by repo.apps.collectAsStateWithLifecycle()
    val prefs by repo.prefs.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val c = appContainer()
    val drawer = LookState.look.drawer
    val dockKeys = LookState.look.home.dock.items.filter { it.action.kind == TileAction.Kind.LaunchApp }.map { it.action.arg }.toSet()
    val focus = remember { FocusRequester() }
    LaunchedEffect(drawer.autoFocusSearch) { if (drawer.autoFocusSearch) runCatching { focus.requestFocus() } }

    val visible = apps
        .filter { showHidden || it.key !in prefs.hidden }
        .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    NexusBackdrop(intensity = 0.6f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Apps", "${apps.size} installed") {
                Box(Modifier.size(40.dp).nexusPress({ showHidden = !showHidden }), contentAlignment = Alignment.Center) {
                    Icon(
                        if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        if (showHidden) "Hide hidden apps" else "Show hidden apps",
                        tint = if (showHidden) Nx.Cyan else Nx.Muted,
                    )
                }
            }
            if (drawer.showSearch) SearchField(query, { query = it }, focus, onGo = { visible.firstOrNull()?.let(repo::launch) })
            LazyVerticalGrid(
                columns = if (drawer.columns > 0) GridCells.Fixed(drawer.columns) else GridCells.Adaptive((drawer.iconSizeDp + 26).dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(visible, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        pinned = app.key in dockKeys,
                        hidden = app.key in prefs.hidden,
                        iconSize = drawer.iconSizeDp.dp,
                        showLabel = drawer.showLabels,
                        onLaunch = { repo.launch(app) },
                        onPin = {
                            c.editLook { l ->
                                if (app.key in dockKeys) l.updateDock { d -> d.copy(items = d.items.filterNot { it.action.kind == TileAction.Kind.LaunchApp && it.action.arg == app.key }) }
                                else l.updateDock { d -> d.copy(items = d.items + appTile(app.key, app.label)) }
                            }
                        },
                        onHide = { scope.launch { repo.toggleHidden(app) } },
                        onInfo = { repo.openAppInfo(app) },
                        onUninstall = { repo.uninstall(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, focus: FocusRequester, onGo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(nexusShape(0.71f))
            .background(Nx.Panel)
            .border(1.dp, Nx.CyanDim, nexusShape(0.71f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Nx.Cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("SEARCH APPS", style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Nx.Cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

@Composable
private fun AppCell(
    app: LaunchableApp,
    pinned: Boolean,
    hidden: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    showLabel: Boolean,
    onLaunch: () -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().nexusPress(onLaunch, onLongClick = { menu = true }).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(app.icon, null, Modifier.size(iconSize))
                if (pinned) Box(Modifier.size(8.dp).align(Alignment.TopEnd).background(Nx.Cyan, nexusShape(0.14f)))
            }
            if (showLabel) Spacer(Modifier.height(6.dp))
            if (showLabel) Text(
                app.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = if (hidden) Nx.Muted else Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Nx.PanelSolid) {
            DropdownMenuItem(text = { Text(if (pinned) "Remove from tray" else "Add to tray") }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text(if (hidden) "Unhide" else "Hide") }, onClick = { menu = false; onHide() })
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; onInfo() })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
        }
    }
}
