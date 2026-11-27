package com.eoinknd16.nexus.ui.apps

import com.eoinknd16.nexus.ui.home.NotificationDot

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.filled.Info

import androidx.compose.material.icons.filled.PushPin

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.fillMaxHeight

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
    NexusBackdrop(intensity = 0.6f) {
        AppDrawerContent(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), autoFocus = LookState.look.drawer.autoFocusSearch)
    }
}

/**
 * The app list itself, shared by the full-screen route and the swipe-up
 * overlay on home. [gridState] lets the overlay know when the list is at the
 * top (so a downward drag closes the drawer instead of scrolling).
 */
@Composable
fun AppDrawerContent(
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    autoFocus: Boolean = false,
    onLaunched: () -> Unit = {},
    headerExtra: @Composable () -> Unit = {},
) {
    val repo = appContainer().apps
    val apps by repo.apps.collectAsStateWithLifecycle()
    val prefs by repo.prefs.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = com.eoinknd16.nexus.ui.fx.rememberHaptics()

    val c = appContainer()
    val drawer = LookState.look.drawer
    val dockKeys = LookState.look.home.dock.items.filter { it.action.kind == TileAction.Kind.LaunchApp }.map { it.action.arg }.toSet()
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }

    val visible = apps
        .filter { showHidden || it.key !in prefs.hidden }
        .filter { query.isBlank() || fuzzyMatch(it.label, query) }
        .let { list -> if (query.isBlank()) list else list.sortedByDescending { fuzzyScore(it.label, query) } }

    Column(modifier) {
        ScreenHeader("Apps", "${apps.size} installed") {
            headerExtra()
            Box(Modifier.size(40.dp).nexusPress({ showHidden = !showHidden }), contentAlignment = Alignment.Center) {
                Icon(
                    if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    if (showHidden) "Hide hidden apps" else "Show hidden apps",
                    tint = if (showHidden) Nx.Cyan else Nx.Muted,
                )
            }
        }
        if (drawer.showSearch) SearchField(query, { query = it }, focus, onGo = { visible.firstOrNull()?.let { repo.launch(it); onLaunched() } })
        if (drawer.showSuggestions && query.isBlank()) {
            val suggested = remember(apps, prefs.usage) { repo.suggestions(5) }
            if (suggested.isNotEmpty()) {
                Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, color = Nx.Muted, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    suggested.forEach { app ->
                        Column(Modifier.weight(1f).nexusPress({ repo.launch(app); onLaunched() }), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(app.icon, null, Modifier.size(drawer.iconSizeDp.dp))
                            if (drawer.showLabels) Text(
                                app.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = Nx.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Nx.CyanDim))
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = if (drawer.columns > 0) GridCells.Fixed(drawer.columns) else GridCells.Adaptive((drawer.iconSizeDp + 26).dp),
                modifier = Modifier.weight(1f).fillMaxSize().padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(visible, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        pinned = app.key in dockKeys,
                        hidden = app.key in prefs.hidden,
                        iconSize = drawer.iconSizeDp.dp,
                        showLabel = drawer.showLabels,
                        onLaunch = { repo.launch(app); onLaunched() },
                        onPin = {
                            c.editLook(if (app.key in dockKeys) "Removed from tray" else "Added to tray") { l ->
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
            // A–Z scrubber: slide your thumb down it to jump, with a tick per letter.
            if (query.isBlank() && visible.size > 30) {
                val letters = remember(visible) { visible.map { firstLetter(it.label) }.distinct() }
                AlphabetScrubber(letters, Modifier.fillMaxHeight().width(28.dp)) { letter ->
                    val idx = visible.indexOfFirst { firstLetter(it.label) == letter }
                    if (idx >= 0) {
                        haptics.tick()
                        scope.launch { gridState.scrollToItem(idx) }
                    }
                }
            }
        }
    }
}

private fun firstLetter(label: String): Char = label.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

@Composable
private fun AlphabetScrubber(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    var active by remember { mutableStateOf<Char?>(null) }
    Column(
        modifier
            .pointerInput(letters) {
                fun at(y: Float): Char? = if (letters.isEmpty()) null else letters[((y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)]
                awaitEachGesture {
                    val down = awaitFirstDown()
                    at(down.position.y)?.let { active = it; onLetter(it) }
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        val l = at(ch.position.y)
                        if (l != null && l != active) { active = l; onLetter(l) }
                    }
                    active = null
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            Text(
                l.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (l == active) 14.sp else 10.sp),
                color = if (l == active) Nx.Cyan else Nx.Muted,
                fontWeight = if (l == active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** "spt" finds Spotify: letters in order, word starts count most. */
fun fuzzyMatch(label: String, query: String): Boolean = fuzzyScore(label, query) > 0

fun fuzzyScore(label: String, query: String): Int {
    val l = label.lowercase()
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 1
    if (l.startsWith(q)) return 1000 - l.length
    if (l.split(' ', '-', '_').any { it.startsWith(q) }) return 800 - l.length
    if (l.contains(q)) return 600 - l.length
    // Initials: "gm" -> Google Maps.
    val initials = l.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
    if (initials.startsWith(q)) return 500
    var i = 0
    var score = 0
    for (ch in l) {
        if (i < q.length && ch == q[i]) { i++; score += 10 }
    }
    return if (i == q.length) 100 + score - l.length else 0
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
                NotificationDot(com.eoinknd16.nexus.ui.home.notificationCount(listOf(app.packageName)))
            }
            if (showLabel) Spacer(Modifier.height(6.dp))
            if (showLabel) Text(
                app.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = if (hidden) Nx.Muted else Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        if (menu) {
            com.eoinknd16.nexus.ui.home.AppQuickMenu(
                app,
                listOf(
                    com.eoinknd16.nexus.ui.home.QuickAction(if (pinned) "Unpin" else "To tray", Icons.Filled.PushPin, onClick = onPin),
                    com.eoinknd16.nexus.ui.home.QuickAction(if (hidden) "Unhide" else "Hide", if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, onClick = onHide),
                    com.eoinknd16.nexus.ui.home.QuickAction("Info", Icons.Filled.Info, onClick = onInfo),
                    com.eoinknd16.nexus.ui.home.QuickAction("Uninstall", Icons.Filled.Delete, danger = true, onClick = onUninstall),
                ),
            ) { menu = false }
        }
    }
}
