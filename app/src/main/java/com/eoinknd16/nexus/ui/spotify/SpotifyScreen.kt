package com.eoinknd16.nexus.ui.spotify

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.spotify.SpCollection
import com.eoinknd16.nexus.spotify.SpDevice
import com.eoinknd16.nexus.spotify.SpPlayer
import com.eoinknd16.nexus.spotify.SpSearch
import com.eoinknd16.nexus.spotify.SpTrack
import com.eoinknd16.nexus.spotify.Spotify
import com.eoinknd16.nexus.spotify.livePosition
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.plainTap
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.settings.Field
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SpTab(val label: String) { Home("For you"), Liked("Liked"), Playlists("Playlists"), Albums("Albums"), Search("Search") }

/** What's open on top of the tabs: a playlist / album / artist. */
private data class Opened(val col: SpCollection)

/**
 * The Nexus Spotify control centre: now playing, your library and search, in
 * Nexus's own look. The real Spotify app does the playing in the background.
 */
@Composable
fun SpotifyScreen() {
    val c = appContainer()
    val sp = c.spotify
    val prefs by sp.prefs.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    NexusBackdrop(intensity = 0.45f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Spotify", if (prefs.connected) prefs.userName.ifBlank { "Connected" } else "Not connected") {
                if (prefs.connected) {
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.size(40.dp).nexusPress({ menu = !menu }), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreVert, "More", tint = Nx.Cyan) }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = Nx.PanelSolid) {
                        androidx.compose.material3.DropdownMenuItem({ Text("Open the Spotify app", color = Nx.Text) }, {
                            menu = false
                            ctx.packageManager.getLaunchIntentForPackage(Spotify.SPOTIFY_PKG)?.let { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        })
                        androidx.compose.material3.DropdownMenuItem({ Text("Disconnect", color = Nx.Amber) }, { menu = false; sp.signOut() })
                    }
                }
            }
            if (!prefs.connected) ConnectPanel(sp, prefs.clientId) else ControlCentre(sp)
        }
    }
}

// ---------------------------------------------------------------------------
// One-time setup: your own (free) Spotify developer app.
// ---------------------------------------------------------------------------

@Composable
private fun ConnectPanel(sp: Spotify, savedId: String) {
    var clientId by remember(savedId) { mutableStateOf(savedId) }
    val signingIn by sp.signingIn.collectAsStateWithLifecycle()
    val error by sp.error.collectAsStateWithLifecycle()
    val clip = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScrollCompat().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Text("Connect your Spotify", style = MaterialTheme.typography.titleLarge, color = Nx.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Spotify only lets apps like this in through your own free developer app. It takes about two minutes, once. " +
                    "It needs Spotify Premium (Spotify's rule for remote control).",
                style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
            )
        }
        Step(1, "Open the Spotify developer dashboard and sign in.") {
            NexusButton("Open dashboard", icon = Icons.Filled.OpenInNew) { uri.openUri("https://developer.spotify.com/dashboard") }
        }
        Step(2, "Create app. Any name (e.g. Nexus). Tick Web API. Add this exact Redirect URI:") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Spotify.REDIRECT, color = Nx.Cyan, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).nexusPress({ clip.setText(AnnotatedString(Spotify.REDIRECT)) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ContentCopy, "Copy", tint = Nx.Cyan)
                }
            }
        }
        Step(3, "Open the app's Settings, copy its Client ID and paste it here.") {
            Field("Client ID", clientId, { clientId = it.trim() })
        }
        Step(4, "Connect, then tap Agree on Spotify's page.") {
            NexusButton(if (signingIn) "Waiting for Spotify…" else "Connect", icon = Icons.Filled.Link, filled = true, enabled = !signingIn && clientId.length >= 20) {
                sp.signIn(clientId)
            }
        }
        error?.let { Text(it, color = Nx.Amber, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Step(n: Int, text: String, content: @Composable ColumnScope.() -> Unit) {
    HoloPanel(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Nx.Cyan), contentAlignment = Alignment.Center) {
                Text("$n", color = Nx.Void, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Text(text, color = Nx.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

// ---------------------------------------------------------------------------
// The control centre.
// ---------------------------------------------------------------------------

@Composable
private fun ControlCentre(sp: Spotify) {
    val scope = rememberCoroutineScope()
    val player by sp.player.collectAsStateWithLifecycle()
    val error by sp.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SpTab.Home) }
    var opened by remember { mutableStateOf<Opened?>(null) }
    var showDevices by remember { mutableStateOf(false) }

    // Poll the player while this screen is showing.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            while (true) {
                sp.refreshPlayer()
                delay(if (player?.playing == true) 2500 else 5000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    BackHandler(opened != null || showDevices) { if (showDevices) showDevices = false else opened = null }

    // Library data, loaded once per visit.
    var recent by remember { mutableStateOf<List<SpTrack>?>(null) }
    var top by remember { mutableStateOf<List<SpTrack>>(emptyList()) }
    var queue by remember { mutableStateOf<List<SpTrack>>(emptyList()) }
    var liked by remember { mutableStateOf<List<SpTrack>?>(null) }
    var likedEnd by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<SpCollection>?>(null) }
    var albums by remember { mutableStateOf<List<SpCollection>?>(null) }
    LaunchedEffect(Unit) {
        launch { recent = runCatching { sp.recent() }.getOrDefault(emptyList()) }
        launch { top = runCatching { sp.topTracks() }.getOrDefault(emptyList()) }
        launch { playlists = runCatching { sp.playlists() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(player?.track?.uri) { queue = runCatching { sp.queue() }.getOrDefault(emptyList()) }
    LaunchedEffect(tab) {
        if (tab == SpTab.Liked && liked == null) liked = runCatching { sp.liked() }.getOrDefault(emptyList())
        if (tab == SpTab.Albums && albums == null) albums = runCatching { sp.albums() }.getOrDefault(emptyList())
    }

    val list = rememberLazyListState()
    // Endless Liked Songs.
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 6 } == true } }
    LaunchedEffect(nearEnd, tab, liked?.size) {
        val l = liked
        if (nearEnd && tab == SpTab.Liked && l != null && !likedEnd && l.size >= 50) {
            val more = runCatching { sp.liked(l.size) }.getOrDefault(emptyList())
            if (more.isEmpty()) likedEnd = true else liked = l + more
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(opened, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "spOpen") { o ->
            if (o != null) {
                CollectionView(sp, o.col, player)
            } else {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "now") { NowPlaying(sp, player, onDevices = { showDevices = true }) }
                    error?.let { e -> item(key = "err") { ErrorBar(e) { sp.clearError() } } }
                    if (queue.isNotEmpty()) item(key = "queue") {
                        Column(Modifier.padding(top = 18.dp)) {
                            HudLabel("Up next", Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                queue.take(12).forEach { t -> MiniCard(t.name, t.artists, t.art, 108.dp) { sp.next() } }
                            }
                        }
                    }
                    item(key = "tabs") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { SpTab.entries.forEach { t -> Pill(t.label, t == tab) { tab = t } } }
                    }
                    when (tab) {
                        SpTab.Home -> homeTab(sp, recent, top, playlists) { opened = Opened(it) }
                        SpTab.Liked -> {
                            val l = liked
                            if (l == null) item { LoadingBlock("Loading your likes…") }
                            else {
                                item(key = "likedHead") {
                                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        NexusButton("Play", icon = Icons.Filled.PlayArrow, filled = true) { sp.playLiked(l.firstOrNull(), l) }
                                        NexusButton("Shuffle", icon = Icons.Filled.Shuffle) { sp.setShuffle(true); sp.playLiked(l.randomOrNull(), l) }
                                    }
                                }
                                items(l, key = { "liked-" + it.uri }) { t -> TrackRow(t, player) { sp.playLiked(t, l) } }
                            }
                        }
                        SpTab.Playlists -> collectionsGrid(playlists, "No playlists yet") { opened = Opened(it) }
                        SpTab.Albums -> collectionsGrid(albums, "No saved albums") { opened = Opened(it) }
                        SpTab.Search -> item(key = "search") { SearchPanel(sp, player) { opened = Opened(it) } }
                    }
                }
            }
        }
        if (showDevices) DevicesSheet(sp, player?.device) { showDevices = false }
    }
}

private fun LazyListScope.homeTab(
    sp: Spotify, recent: List<SpTrack>?, top: List<SpTrack>, playlists: List<SpCollection>?, open: (SpCollection) -> Unit,
) {
    if (recent == null) { item { LoadingBlock("Loading…") }; return }
    if (!playlists.isNullOrEmpty()) item(key = "homePl") {
        ShelfRow("Your playlists") { playlists.take(12).forEach { p -> MiniCard(p.name, p.subtitle, p.art, 128.dp) { open(p) } } }
    }
    if (top.isNotEmpty()) item(key = "homeTop") {
        ShelfRow("On repeat lately") { top.forEachIndexed { i, t -> MiniCard(t.name, t.artists, t.art, 128.dp) { sp.playTracks(top.map { it.uri }, i) } } }
    }
    if (recent.isNotEmpty()) {
        item(key = "homeRecentH") { HudLabel("Recently played", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        items(recent.take(25), key = { "recent-" + it.uri }) { t -> TrackRow(t, null) { sp.playTracks(recent.map { it.uri }, recent.indexOf(t)) } }
    }
}

private fun LazyListScope.collectionsGrid(cols: List<SpCollection>?, empty: String, open: (SpCollection) -> Unit) {
    when {
        cols == null -> item { LoadingBlock("Loading…") }
        cols.isEmpty() -> item { Text(empty, color = Nx.Muted, modifier = Modifier.padding(16.dp)) }
        else -> items(cols.chunked(2), key = { row -> "grid-" + row.first().uri }) { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> CollectionCard(p, Modifier.weight(1f)) { open(p) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Now playing hero.
// ---------------------------------------------------------------------------

@Composable
private fun NowPlaying(sp: Spotify, player: SpPlayer?, onDevices: () -> Unit) {
    val t = player?.track
    // Ticking position between polls.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(player?.playing) { while (player?.playing == true) { now = System.currentTimeMillis(); delay(500) } }
    val scope = rememberCoroutineScope()
    var liked by remember(t?.uri) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(t?.uri) { liked = t?.let { runCatching { sp.isLiked(it) }.getOrNull() } }

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Ambient glow from the album art.
        if (t?.art != null) AsyncImage(
            t.art, null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(nexusShape()).blur(60.dp).alpha(0.45f),
        )
        Column(
            Modifier.fillMaxWidth().clip(nexusShape()).background(Nx.Panel.copy(alpha = 0.55f)).border(1.dp, Nx.Line, nexusShape()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (t == null) {
                Icon(Icons.Filled.MusicNote, null, tint = Nx.Cyan, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nothing playing", color = Nx.Text, style = MaterialTheme.typography.titleMedium)
                Text("Pick something below and it plays through Spotify.", color = Nx.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                return@Column
            }
            AnimatedContent(t.art, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art") { art ->
                AsyncImage(
                    art, t.album, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f).clip(nexusShape(0.8f)).background(Nx.Deep).border(1.dp, Nx.CyanDim, nexusShape(0.8f)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = Nx.Text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artists, color = Nx.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val l = liked
                if (l != null) Box(Modifier.size(44.dp).nexusPress({ liked = !l; scope.launch { sp.setLiked(t, !l) } }), contentAlignment = Alignment.Center) {
                    Icon(if (l) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (l) "Unlike" else "Like", tint = if (l) Nx.Magenta else Nx.Muted)
                }
            }
            // Seek bar.
            val dur = t.durationMs.coerceAtLeast(1)
            var dragging by remember { mutableStateOf<Float?>(null) }
            val pos = player.livePosition(now)
            Slider(
                value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { sp.seek((it * dur).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = Nx.Cyan, activeTrackColor = Nx.Cyan, inactiveTrackColor = Nx.CyanDim),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(dragging?.let { (it * dur).toLong() } ?: pos), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(fmt(t.durationMs), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Ctl(Icons.Filled.Shuffle, "Shuffle", if (player.shuffle) Nx.Cyan else Nx.Muted) { sp.setShuffle(!player.shuffle) }
                Ctl(Icons.Filled.SkipPrevious, "Previous", Nx.Text, 36) { sp.previous() }
                Box(
                    Modifier.size(68.dp).nexusPress({ sp.togglePlay() }).clip(CircleShape).background(Nx.Cyan),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.playing) "Pause" else "Play", tint = Nx.Void, modifier = Modifier.size(38.dp)) }
                Ctl(Icons.Filled.SkipNext, "Next", Nx.Text, 36) { sp.next() }
                Ctl(if (player.repeat == "track") Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", if (player.repeat != "off") Nx.Cyan else Nx.Muted) { sp.cycleRepeat() }
            }
            Spacer(Modifier.height(10.dp))
            // Device + volume.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.nexusPress(onDevices).clip(nexusShape(0.5f)).background(Nx.Cyan.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(deviceIcon(player.device?.type), null, tint = Nx.Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(player.device?.name ?: "Choose device", color = Nx.Cyan, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                player.device?.volume?.let { v ->
                    var vol by remember(v) { mutableFloatStateOf(v / 100f) }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.VolumeUp, null, tint = Nx.Muted, modifier = Modifier.size(18.dp))
                    Slider(
                        vol, { vol = it }, Modifier.weight(1f), onValueChangeFinished = { sp.setVolume((vol * 100).toInt()) },
                        colors = SliderDefaults.colors(thumbColor = Nx.Cyan, activeTrackColor = Nx.Cyan, inactiveTrackColor = Nx.CyanDim),
                    )
                }
            }
        }
    }
}

@Composable
private fun Ctl(icon: ImageVector, label: String, tint: Color, size: Int = 26, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint, modifier = Modifier.size(size.dp)) }
}

private fun deviceIcon(type: String?): ImageVector = when (type?.lowercase()) {
    "smartphone" -> Icons.Filled.Smartphone
    "computer" -> Icons.Filled.Computer
    "speaker" -> Icons.Filled.Speaker
    "tv", "castvideo" -> Icons.Filled.Tv
    else -> Icons.Filled.Devices
}

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

// ---------------------------------------------------------------------------
// Rows, cards, panels.
// ---------------------------------------------------------------------------

@Composable
private fun TrackRow(t: SpTrack, player: SpPlayer?, onPlay: () -> Unit) {
    val sp = appContainer().spotify
    val current = player?.track?.uri == t.uri
    Row(
        Modifier.fillMaxWidth().nexusPress(onPlay, onLongClick = { sp.addToQueue(t) }).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(nexusShape(0.35f)).background(Nx.Deep))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = if (current) Nx.Cyan else Nx.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artists, color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current && player?.playing == true) Icon(Icons.Filled.GraphicEq, "Playing", tint = Nx.Cyan, modifier = Modifier.size(18.dp))
        else Text(fmt(t.durationMs), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MiniCard(title: String, sub: String, art: String?, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).nexusPress(onClick)) {
        AsyncImage(art, title, contentScale = ContentScale.Crop, modifier = Modifier.size(width).clip(nexusShape(0.5f)).background(Nx.Deep).border(1.dp, Nx.Line, nexusShape(0.5f)))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Nx.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = Nx.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShelfRow(title: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        HudLabel(title, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun CollectionCard(p: SpCollection, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.nexusPress(onClick)) {
        AsyncImage(p.art, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(nexusShape(0.6f)).background(Nx.Deep).border(1.dp, Nx.Line, nexusShape(0.6f)))
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = Nx.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.subtitle, color = Nx.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = nexusShape(0.57f)
    Text(
        hud(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Cyan,
        modifier = Modifier.nexusPress(onClick).clip(shape).background(if (selected) Nx.Cyan else Nx.Cyan.copy(alpha = 0.08f))
            .border(1.dp, Nx.CyanDim, shape).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ErrorBar(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth().clip(nexusShape(0.5f)).background(Nx.Amber.copy(alpha = 0.12f)).nexusPress(onDismiss).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, null, tint = Nx.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Nx.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// A playlist / album / artist.
// ---------------------------------------------------------------------------

@Composable
private fun CollectionView(sp: Spotify, col: SpCollection, player: SpPlayer?) {
    var tracks by remember(col.uri) { mutableStateOf<List<SpTrack>?>(null) }
    LaunchedEffect(col.uri) {
        tracks = runCatching {
            when (col.kind) {
                "album" -> sp.albumTracks(col.id)
                "playlist" -> sp.playlistTracks(col.id)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(col.art, col.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f).clip(nexusShape(0.8f)).background(Nx.Deep).border(1.dp, Nx.CyanDim, nexusShape(0.8f)))
                Spacer(Modifier.height(14.dp))
                Text(col.name, color = Nx.Text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(hud(col.subtitle.ifBlank { col.kind }), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Play", icon = Icons.Filled.PlayArrow, filled = true) { sp.playContext(col.uri) }
                    NexusButton("Shuffle", icon = Icons.Filled.Shuffle) { sp.playContext(col.uri, shuffle = true) }
                }
            }
        }
        val t = tracks
        when {
            t == null -> item { LoadingBlock("Loading tracks…") }
            t.isEmpty() && col.kind == "playlist" -> item {
                Text(
                    "Spotify only shares the track list for playlists you made or collaborate on. Play still works.",
                    color = Nx.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            else -> items(t, key = { col.uri + it.uri }) { tr -> TrackRow(tr, player) { sp.playContext(col.uri, startUri = tr.uri) } }
        }
    }
}

// ---------------------------------------------------------------------------
// Search.
// ---------------------------------------------------------------------------

@Composable
private fun SearchPanel(sp: Spotify, player: SpPlayer?, open: (SpCollection) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<SpSearch?>(null) }
    LaunchedEffect(q) {
        if (q.isBlank()) { res = null; return@LaunchedEffect }
        delay(350)
        res = runCatching { sp.search(q) }.getOrNull()
    }
    Column {
        val shape = nexusShape(0.57f)
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(Nx.Deep).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Nx.Cyan)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Songs, artists, albums, playlists", color = Nx.Muted)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp), cursorBrush = SolidColor(Nx.Cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val r = res ?: return@Column
        if (r.collections.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ShelfRow("Artists, albums and playlists") { r.collections.forEach { p -> MiniCard(p.name, p.subtitle, p.art, 118.dp) { if (p.kind == "artist") sp.playContext(p.uri) else open(p) } } }
        }
        if (r.tracks.isNotEmpty()) {
            HudLabel("Songs", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            r.tracks.forEachIndexed { i, t -> TrackRow(t, player) { sp.playTracks(r.tracks.map { it.uri }, i) } }
        }
    }
}

// ---------------------------------------------------------------------------
// Devices.
// ---------------------------------------------------------------------------

@Composable
private fun DevicesSheet(sp: Spotify, current: SpDevice?, onClose: () -> Unit) {
    var devices by remember { mutableStateOf<List<SpDevice>?>(null) }
    LaunchedEffect(Unit) { devices = runCatching { sp.devices() }.getOrDefault(emptyList()) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).plainTap(onClose), contentAlignment = Alignment.BottomCenter) {
        HoloPanel(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()) {
            Text("Play on", color = Nx.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val d = devices
            when {
                d == null -> LoadingBlock("Finding devices…")
                d.isEmpty() -> Text("No Spotify devices online. Open Spotify on a phone, PC or speaker.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
                else -> d.forEach { dev ->
                    Row(
                        Modifier.fillMaxWidth().nexusPress({ sp.transfer(dev); onClose() }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(deviceIcon(dev.type), null, tint = if (dev.id == current?.id) Nx.Cyan else Nx.Muted)
                        Spacer(Modifier.width(12.dp))
                        Text(dev.name, color = if (dev.id == current?.id) Nx.Cyan else Nx.Text, modifier = Modifier.weight(1f))
                        if (dev.id == current?.id) Icon(Icons.Filled.Check, "Current", tint = Nx.Cyan)
                    }
                }
            }
        }
    }
}

