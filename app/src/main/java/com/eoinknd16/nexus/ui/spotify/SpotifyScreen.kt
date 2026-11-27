package com.eoinknd16.nexus.ui.spotify

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.SpArtStyle
import com.eoinknd16.nexus.look.SpotifyLook
import com.eoinknd16.nexus.spotify.SpCollection
import com.eoinknd16.nexus.spotify.SpDevice
import com.eoinknd16.nexus.spotify.SpPlayer
import com.eoinknd16.nexus.spotify.SpSearch
import com.eoinknd16.nexus.spotify.SpTrack
import com.eoinknd16.nexus.spotify.Spotify
import com.eoinknd16.nexus.spotify.livePosition
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.fx.plainTap
import com.eoinknd16.nexus.ui.home.EditorSheet
import com.eoinknd16.nexus.ui.settings.ChoiceRow
import com.eoinknd16.nexus.ui.settings.Field
import com.eoinknd16.nexus.ui.settings.SliderRow
import com.eoinknd16.nexus.ui.settings.ToggleRow
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SpTab(val label: String) { Home("For you"), Liked("Liked songs"), Playlists("Playlists"), Albums("Albums") }

/** Screens inside the control centre (a small back stack of its own). */
private sealed interface SpView {
    data object Library : SpView
    data object Player : SpView
    data object Search : SpView
    data class Collection(val col: SpCollection) : SpView
}

/** Album-art colour (or the theme accent), used for buttons, bars and highlights. */
private val LocalSpTint = compositionLocalOf { Color.Cyan }

/**
 * The Nexus Spotify control centre: your library with a mini player along the
 * bottom, a full now-playing view, and search always in the top right. The real
 * Spotify app does the playing in the background.
 */
@Composable
fun SpotifyScreen() {
    val c = appContainer()
    val sp = c.spotify
    val prefs by sp.prefs.state.collectAsStateWithLifecycle()
    val look = LookState.look.spotify
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val player by sp.player.collectAsStateWithLifecycle()

    var stack by remember { mutableStateOf(listOf(if (look.openOn == "player") SpView.Player else SpView.Library)) }
    val view = stack.last()
    fun push(v: SpView) { if (stack.last() != v) stack = stack + v }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) else nav.popBackStack() }
    var showLook by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    BackHandler(stack.size > 1 || showDevices) { if (showDevices) showDevices = false else back() }

    // Poll the player while this screen is showing.
    val scope = rememberCoroutineScope()
    if (prefs.connected) LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            while (true) {
                sp.refreshPlayer()
                delay(if (sp.player.value?.playing == true) 2500 else 5000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    val artTint = rememberArtColour(player?.track?.art, look.artColours)
    val tint by animateColorAsState(artTint ?: Nx.Cyan, label = "spTint")

    CompositionLocalProvider(LocalSpTint provides tint) {
        NexusBackdrop(intensity = 0.4f) {
            Box(Modifier.fillMaxSize()) {
                // Ambient album-art glow behind everything.
                val art = player?.track?.art
                if (prefs.connected && look.artGlow && art != null) AsyncImage(
                    art, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f).blur(80.dp).alpha(look.glowStrength.coerceIn(0f, 1f) * 0.8f),
                )
                Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                    TopBar(
                        title = when (view) {
                            SpView.Library -> "Spotify"
                            SpView.Player -> "Now playing"
                            SpView.Search -> "Search"
                            is SpView.Collection -> view.col.name
                        },
                        subtitle = when (view) {
                            SpView.Library -> if (prefs.connected) prefs.userName.ifBlank { "Connected" } else "Not connected"
                            SpView.Player -> player?.device?.name?.let { "on $it" } ?: ""
                            SpView.Search -> "Songs, artists, albums, playlists"
                            is SpView.Collection -> view.col.kind
                        },
                        onBack = ::back,
                        showActions = prefs.connected,
                        searchActive = view == SpView.Search,
                        onSearch = { push(SpView.Search) },
                        onLook = { showLook = true },
                        onOpenApp = {
                            ctx.packageManager.getLaunchIntentForPackage(Spotify.SPOTIFY_PKG)?.let { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        onDisconnect = { sp.signOut(); stack = listOf(SpView.Library) },
                    )
                    Box(Modifier.weight(1f)) {
                        if (!prefs.connected) ConnectPanel(sp, prefs.clientId)
                        else AnimatedContent(view, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "spView") { v ->
                            when (v) {
                                SpView.Library -> LibraryView(sp, player, look, open = { push(SpView.Collection(it)) }, openPlayer = { push(SpView.Player) })
                                SpView.Player -> PlayerView(sp, player, look) { showDevices = true }
                                SpView.Search -> SearchView(sp, player, look) { push(SpView.Collection(it)) }
                                is SpView.Collection -> CollectionView(sp, v.col, player, look)
                            }
                        }
                    }
                    ErrorLine(sp)
                    AnimatedVisibility(
                        prefs.connected && look.miniPlayer && view != SpView.Player && player?.track != null,
                        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
                    ) { player?.let { MiniPlayer(sp, it) { push(SpView.Player) } } }
                }
                if (showDevices) DevicesSheet(sp, player?.device) { showDevices = false }
                if (showLook) LookSheet(look) { showLook = false }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar: back, title, and the persistent search / look / more actions.
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    title: String, subtitle: String, onBack: () -> Unit, showActions: Boolean, searchActive: Boolean,
    onSearch: () -> Unit, onLook: () -> Unit, onOpenApp: () -> Unit, onDisconnect: () -> Unit,
) {
    val tint = LocalSpTint.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Nx.Cyan, onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(hud(subtitle), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showActions) {
            RoundIcon(Icons.Filled.Search, "Search", if (searchActive) Nx.Void else tint, onSearch, filled = searchActive, fill = tint)
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Filled.Tune, "Customise", Nx.Cyan, onLook)
            var menu by remember { mutableStateOf(false) }
            Box {
                Box(Modifier.size(40.dp).nexusPress({ menu = true }), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreVert, "More", tint = Nx.Muted) }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = Nx.PanelSolid) {
                    androidx.compose.material3.DropdownMenuItem({ Text("Open the Spotify app", color = Nx.Text) }, { menu = false; onOpenApp() })
                    androidx.compose.material3.DropdownMenuItem({ Text("Disconnect Spotify", color = Nx.Amber) }, { menu = false; onDisconnect() })
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, filled: Boolean = false, fill: Color = Nx.Cyan) {
    Box(
        Modifier.size(40.dp).nexusPress(onClick).clip(nexusShape(0.57f)).background(if (filled) fill else Nx.Cyan.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint) }
}

// ---------------------------------------------------------------------------
// Library: fixed tabs, then the tab's content.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryView(sp: Spotify, player: SpPlayer?, look: SpotifyLook, open: (SpCollection) -> Unit, openPlayer: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SpTab.Home) }
    val lib = rememberLibrary(sp)
    LaunchedEffect(tab) { lib.ensure(tab) }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 6 } == true } }
    LaunchedEffect(nearEnd, tab) { if (nearEnd && tab == SpTab.Liked) lib.moreLiked() }

    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
        stickyHeader(key = "tabs") {
            Row(
                Modifier.fillMaxWidth().background(Nx.Void.copy(alpha = 0.88f)).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { SpTab.entries.forEach { t -> Pill(t.label, t == tab) { tab = t } } }
        }
        when (tab) {
            SpTab.Home -> {
                // No mini player? Then a compact now-playing card leads the page.
                if (!look.miniPlayer) player?.takeIf { it.track != null }?.let { p -> item(key = "nowCard") { NowCard(sp, p, openPlayer) } }
                val recent = lib.recent
                if (recent == null) item { LoadingBlock("Loading…") }
                else {
                    val pl = lib.playlists.orEmpty()
                    if (look.showPlaylistsShelf && pl.isNotEmpty()) item(key = "homePl") {
                        Shelf("Your playlists", "See all" to { tab = SpTab.Playlists }) { pl.take(10).forEach { p -> MiniCard(p.name, p.subtitle, p.art, 132.dp) { open(p) } } }
                    }
                    if (look.showOnRepeat && lib.top.isNotEmpty()) item(key = "homeTop") {
                        Shelf("On repeat lately") { lib.top.forEachIndexed { i, t -> MiniCard(t.name, t.artists, t.art, 132.dp) { sp.playTracks(lib.top.map { it.uri }, i) } } }
                    }
                    if (look.showRecent && recent.isNotEmpty()) {
                        item(key = "recentH") { SectionHead("Recently played") }
                        items(recent.take(25), key = { "recent-" + it.uri }) { t -> TrackRow(t, player, look) { sp.playTracks(recent.map { it.uri }, recent.indexOf(t)) } }
                    }
                    if (!look.showPlaylistsShelf && !look.showOnRepeat && !look.showRecent) item {
                        Text("Everything on this tab is switched off in Customise.", color = Nx.Muted, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            SpTab.Liked -> {
                val l = lib.liked
                if (l == null) item { LoadingBlock("Loading your liked songs…") }
                else {
                    item(key = "likedHead") {
                        PlayHeader("${l.size}${if (lib.likedEnd) "" else "+"} songs", onPlay = { sp.playLiked(l.firstOrNull(), l) }, onShuffle = { sp.setShuffle(true); sp.playLiked(l.randomOrNull(), l) })
                    }
                    items(l, key = { "liked-" + it.uri }) { t -> TrackRow(t, player, look) { sp.playLiked(t, l) } }
                }
            }
            SpTab.Playlists -> collections(lib.playlists, look, "No playlists yet", open)
            SpTab.Albums -> collections(lib.albums, look, "No saved albums", open)
        }
    }
}

/** The library, loaded lazily per tab and kept while the screen is open. */
private class Library(private val sp: Spotify, private val scope: kotlinx.coroutines.CoroutineScope) {
    var recent by mutableStateOf<List<SpTrack>?>(null)
    var top by mutableStateOf<List<SpTrack>>(emptyList())
    var liked by mutableStateOf<List<SpTrack>?>(null)
    var likedEnd by mutableStateOf(false)
    var playlists by mutableStateOf<List<SpCollection>?>(null)
    var albums by mutableStateOf<List<SpCollection>?>(null)
    private var loadingMore = false

    fun ensure(tab: SpTab) {
        when (tab) {
            SpTab.Home -> {
                if (recent == null) scope.launch { recent = runCatching { sp.recent() }.getOrDefault(emptyList()) }
                if (top.isEmpty()) scope.launch { top = runCatching { sp.topTracks() }.getOrDefault(emptyList()) }
                if (playlists == null) scope.launch { playlists = runCatching { sp.playlists() }.getOrDefault(emptyList()) }
            }
            SpTab.Liked -> if (liked == null) scope.launch { liked = runCatching { sp.liked() }.getOrDefault(emptyList()).also { likedEnd = it.size < 50 } }
            SpTab.Playlists -> if (playlists == null) scope.launch { playlists = runCatching { sp.playlists() }.getOrDefault(emptyList()) }
            SpTab.Albums -> if (albums == null) scope.launch { albums = runCatching { sp.albums() }.getOrDefault(emptyList()) }
        }
    }

    fun moreLiked() {
        val l = liked ?: return
        if (likedEnd || loadingMore) return
        loadingMore = true
        scope.launch {
            val more = runCatching { sp.liked(l.size) }.getOrDefault(emptyList())
            if (more.size < 50) likedEnd = true
            liked = (l + more).distinctBy { it.uri }
            loadingMore = false
        }
    }
}

@Composable
private fun rememberLibrary(sp: Spotify): Library {
    val scope = rememberCoroutineScope()
    return remember { Library(sp, scope) }
}

private fun LazyListScope.collections(cols: List<SpCollection>?, look: SpotifyLook, empty: String, open: (SpCollection) -> Unit) {
    when {
        cols == null -> item { LoadingBlock("Loading…") }
        cols.isEmpty() -> item { Text(empty, color = Nx.Muted, modifier = Modifier.padding(16.dp)) }
        look.gridLibrary -> {
            val n = look.gridColumns.coerceIn(2, 4)
            items(cols.chunked(n), key = { row -> "grid-" + row.first().uri }) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { p -> CollectionCard(p, Modifier.weight(1f)) { open(p) } }
                    repeat(n - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        else -> items(cols, key = { "row-" + it.uri }) { p ->
            Row(Modifier.fillMaxWidth().nexusPress({ open(p) }).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.art, p.name, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(nexusShape(0.4f)).background(Nx.Deep))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Nx.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.subtitle, color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Nx.Muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mini player (bottom) and the compact card used when it's switched off.
// ---------------------------------------------------------------------------

@Composable
private fun MiniPlayer(sp: Spotify, p: SpPlayer, onOpen: () -> Unit) {
    val t = p.track ?: return
    val tint = LocalSpTint.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.playing, p.at) { while (p.playing) { now = System.currentTimeMillis(); delay(1000) } }
    val frac = if (t.durationMs > 0) p.livePosition(now).toFloat() / t.durationMs else 0f
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().clip(nexusShape(0.6f)).background(Nx.PanelSolid).border(1.dp, tint.copy(alpha = 0.35f), nexusShape(0.6f))) {
        Row(Modifier.fillMaxWidth().nexusPress(onOpen).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(nexusShape(0.35f)).background(Nx.Deep))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = Nx.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(t.artists, p.device?.name).joinToString(" · "), color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ctl(if (p.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.playing) "Pause" else "Play", tint, 30) { sp.togglePlay() }
            Ctl(Icons.Filled.SkipNext, "Next", Nx.Text, 28) { sp.next() }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(tint.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(tint))
        }
    }
}

@Composable
private fun NowCard(sp: Spotify, p: SpPlayer, onOpen: () -> Unit) {
    val t = p.track ?: return
    val tint = LocalSpTint.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(nexusShape()).background(Nx.Panel.copy(alpha = 0.7f)).border(1.dp, tint.copy(alpha = 0.35f), nexusShape())
            .nexusPress(onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(nexusShape(0.5f)).background(Nx.Deep))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hud("Now playing"), color = tint, style = MaterialTheme.typography.labelSmall)
            Text(t.name, color = Nx.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artists, color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ctl(if (p.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint, 32) { sp.togglePlay() }
    }
}

// ---------------------------------------------------------------------------
// Full now-playing view.
// ---------------------------------------------------------------------------

@Composable
private fun PlayerView(sp: Spotify, player: SpPlayer?, look: SpotifyLook, onDevices: () -> Unit) {
    val t = player?.track
    val tint = LocalSpTint.current
    if (t == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = tint, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(10.dp))
            Text("Nothing playing", color = Nx.Text, style = MaterialTheme.typography.titleLarge)
            Text("Pick something from your library or search, top right.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        return
    }
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(player.playing, player.at) { while (player.playing) { now = System.currentTimeMillis(); delay(500) } }
    var liked by remember(t.uri) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(t.uri) { liked = runCatching { sp.isLiked(t) }.getOrNull() }
    var queue by remember { mutableStateOf<List<SpTrack>>(emptyList()) }
    LaunchedEffect(t.uri, look.showUpNext) { if (look.showUpNext) queue = runCatching { sp.queue() }.getOrDefault(emptyList()) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                AlbumArt(t, player.playing, look.artStyle, Modifier.fillMaxWidth(0.86f))
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = Nx.Text, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(t.artists, color = Nx.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val l = liked
                    if (l != null) Ctl(if (l) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (l) "Remove from Liked" else "Add to Liked", if (l) tint else Nx.Muted, 28) {
                        liked = !l; scope.launch { sp.setLiked(t, !l) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val dur = t.durationMs.coerceAtLeast(1)
                var dragging by remember { mutableStateOf<Float?>(null) }
                val pos = player.livePosition(now)
                Slider(
                    value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { sp.seek((it * dur).toLong()) }; dragging = null },
                    colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint, inactiveTrackColor = tint.copy(alpha = 0.2f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(dragging?.let { (it * dur).toLong() } ?: pos), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(t.durationMs), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Ctl(Icons.Filled.Shuffle, "Shuffle", if (player.shuffle) tint else Nx.Muted) { sp.setShuffle(!player.shuffle) }
                    Ctl(Icons.Filled.SkipPrevious, "Previous", Nx.Text, 38) { sp.previous() }
                    Box(Modifier.size(74.dp).nexusPress({ sp.togglePlay() }).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                        Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.playing) "Pause" else "Play", tint = Nx.Void, modifier = Modifier.size(40.dp))
                    }
                    Ctl(Icons.Filled.SkipNext, "Next", Nx.Text, 38) { sp.next() }
                    Ctl(if (player.repeat == "track") Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", if (player.repeat != "off") tint else Nx.Muted) { sp.cycleRepeat() }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.nexusPress(onDevices).clip(nexusShape(0.5f)).background(tint.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(deviceIcon(player.device?.type), null, tint = tint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(player.device?.name ?: "Choose device", color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    player.device?.volume?.let { v ->
                        var vol by remember(v) { mutableFloatStateOf(v / 100f) }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.VolumeUp, null, tint = Nx.Muted, modifier = Modifier.size(18.dp))
                        Slider(
                            vol, { vol = it }, Modifier.weight(1f), onValueChangeFinished = { sp.setVolume((vol * 100).toInt()) },
                            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint, inactiveTrackColor = tint.copy(alpha = 0.2f)),
                        )
                    }
                }
            }
        }
        if (look.showUpNext && queue.isNotEmpty()) {
            item(key = "upnextH") { Spacer(Modifier.height(18.dp)); SectionHead("Up next") }
            itemsIndexed(queue.take(20), key = { i, q -> "q-$i-" + q.uri }) { _, q -> TrackRow(q, null, look) { sp.next() } }
        }
    }
}

@Composable
private fun AlbumArt(t: SpTrack, playing: Boolean, style: SpArtStyle, modifier: Modifier) {
    val shape: Shape = when (style) {
        SpArtStyle.Rounded -> RoundedCornerShape(18.dp)
        SpArtStyle.Square -> RectangleShape
        SpArtStyle.Vinyl -> CircleShape
    }
    var angle by remember(t.uri) { mutableFloatStateOf(0f) }
    if (style == SpArtStyle.Vinyl) LaunchedEffect(playing) {
        var last = 0L
        while (playing) withFrameNanos { n -> if (last != 0L) angle = (angle + (n - last) / 1e9f * 24f) % 360f; last = n }
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        AnimatedContent(t.art, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art") { art ->
            AsyncImage(
                art, t.album, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().rotate(if (style == SpArtStyle.Vinyl) angle else 0f).clip(shape).background(Nx.Deep).border(1.dp, Nx.Line, shape),
            )
        }
        if (style == SpArtStyle.Vinyl) Box(Modifier.fillMaxSize(0.14f).clip(CircleShape).background(Nx.Void).border(2.dp, Nx.Line, CircleShape))
    }
}

// ---------------------------------------------------------------------------
// A playlist / album.
// ---------------------------------------------------------------------------

@Composable
private fun CollectionView(sp: Spotify, col: SpCollection, player: SpPlayer?, look: SpotifyLook) {
    var tracks by remember(col.uri) { mutableStateOf<List<SpTrack>?>(null) }
    LaunchedEffect(col.uri) {
        tracks = runCatching { if (col.kind == "album") sp.albumTracks(col.id) else if (col.kind == "playlist") sp.playlistTracks(col.id) else emptyList() }.getOrDefault(emptyList())
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(col.art, col.name, contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(nexusShape(0.6f)).background(Nx.Deep).border(1.dp, Nx.Line, nexusShape(0.6f)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(col.name, color = Nx.Text, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(col.subtitle, color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
        item { PlayHeader(tracks?.let { if (it.isEmpty()) "" else "${it.size} songs" } ?: "", onPlay = { sp.playContext(col.uri) }, onShuffle = { sp.playContext(col.uri, shuffle = true) }) }
        val t = tracks
        when {
            t == null -> item { LoadingBlock("Loading songs…") }
            t.isEmpty() && col.kind == "playlist" -> item {
                Text(
                    "Spotify only shares the song list for playlists you made or collaborate on. Play and Shuffle still work.",
                    color = Nx.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            else -> items(t, key = { col.uri + it.uri }) { tr -> TrackRow(tr, player, look) { sp.playContext(col.uri, startUri = tr.uri) } }
        }
    }
}

// ---------------------------------------------------------------------------
// Search (top-right icon).
// ---------------------------------------------------------------------------

@Composable
private fun SearchView(sp: Spotify, player: SpPlayer?, look: SpotifyLook, open: (SpCollection) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<SpSearch?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        if (q.isBlank()) { res = null; return@LaunchedEffect }
        delay(350)
        busy = true
        res = runCatching { sp.search(q) }.getOrNull()
        busy = false
    }
    val tint = LocalSpTint.current
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            val shape = nexusShape(0.57f)
            Row(
                Modifier.padding(16.dp, 4.dp, 16.dp, 12.dp).fillMaxWidth().clip(shape).background(Nx.Deep).border(1.dp, tint.copy(alpha = 0.5f), shape).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = tint)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("What do you want to hear?", color = Nx.Muted)
                    BasicTextField(
                        q, { q = it }, singleLine = true, textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp), cursorBrush = SolidColor(tint),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (q.isNotEmpty()) Box(Modifier.size(28.dp).nexusPress({ q = "" }), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Clear", tint = Nx.Muted) }
            }
        }
        val r = res
        when {
            q.isBlank() -> item { Text("Search songs, artists, albums and playlists.", color = Nx.Muted, modifier = Modifier.padding(horizontal = 16.dp)) }
            busy && r == null -> item { LoadingBlock("Searching…") }
            r != null && r.tracks.isEmpty() && r.collections.isEmpty() -> item { Text("Nothing found for “$q”.", color = Nx.Muted, modifier = Modifier.padding(16.dp)) }
            r != null -> {
                if (r.tracks.isNotEmpty()) {
                    item { SectionHead("Songs") }
                    items(r.tracks, key = { "s-" + it.uri }) { t -> TrackRow(t, player, look) { sp.playTracks(r.tracks.map { it.uri }, r.tracks.indexOf(t)) } }
                }
                val artists = r.collections.filter { it.kind == "artist" }
                val others = r.collections.filter { it.kind != "artist" }
                if (artists.isNotEmpty()) item { Shelf("Artists · tap to play") { artists.forEach { a -> MiniCard(a.name, "Artist", a.art, 110.dp, round = true) { sp.playContext(a.uri) } } } }
                if (others.isNotEmpty()) item { Shelf("Albums and playlists") { others.forEach { p -> MiniCard(p.name, p.subtitle, p.art, 120.dp) { open(p) } } } }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Look panel (tune icon).
// ---------------------------------------------------------------------------

@Composable
private fun LookSheet(look: SpotifyLook, onClose: () -> Unit) {
    val c = appContainer()
    fun set(t: (SpotifyLook) -> SpotifyLook) = c.editLook("Spotify look") { it.copy(spotify = t(it.spotify)) }
    EditorSheet("Spotify look", onDismiss = onClose, showUndo = true) {
        Column {
            HudLabel("Layout")
            ChoiceRow("Opens on", listOf("library", "player"), look.openOn, { if (it == "player") "Now playing" else "Library" }) { v -> set { it.copy(openOn = v) } }
            ToggleRow("Mini player", look.miniPlayer, "Bar along the bottom with play, skip and progress. Tap it for the full player.") { v -> set { it.copy(miniPlayer = v) } }
            ToggleRow("Playlists and albums as a grid", look.gridLibrary, "Off shows them as a list") { v -> set { it.copy(gridLibrary = v) } }
            if (look.gridLibrary) ChoiceRow("Grid columns", listOf(2, 3, 4), look.gridColumns, { "$it" }) { v -> set { it.copy(gridColumns = v) } }
            ToggleRow("Compact song rows", look.compactRows) { v -> set { it.copy(compactRows = v) } }
            Spacer(Modifier.height(12.dp))
            HudLabel("Player")
            ChoiceRow("Album art", SpArtStyle.entries, look.artStyle, { it.label }) { v -> set { it.copy(artStyle = v) } }
            ToggleRow("Colours from the album art", look.artColours, "Buttons and bars take the cover's colour") { v -> set { it.copy(artColours = v) } }
            ToggleRow("Album art glow", look.artGlow, "Soft blurred cover behind the screen") { v -> set { it.copy(artGlow = v) } }
            if (look.artGlow) SliderRow("Glow strength", look.glowStrength, 0.1f..1f, { "${(it * 100).toInt()}%" }) { v -> set { it.copy(glowStrength = v) } }
            ToggleRow("Show Up next", look.showUpNext, "Your queue under the full player") { v -> set { it.copy(showUpNext = v) } }
            Spacer(Modifier.height(12.dp))
            HudLabel("For you tab")
            ToggleRow("Your playlists", look.showPlaylistsShelf) { v -> set { it.copy(showPlaylistsShelf = v) } }
            ToggleRow("On repeat lately", look.showOnRepeat) { v -> set { it.copy(showOnRepeat = v) } }
            ToggleRow("Recently played", look.showRecent) { v -> set { it.copy(showRecent = v) } }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces.
// ---------------------------------------------------------------------------

@Composable
private fun SectionHead(title: String) {
    Text(title, color = Nx.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun Shelf(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHead(title) }
            action?.let { (label, go) -> Text(label, color = LocalSpTint.current, style = MaterialTheme.typography.labelLarge, modifier = Modifier.nexusPress(go).padding(8.dp)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun PlayHeader(info: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val tint = LocalSpTint.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hud(info), color = Nx.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Ctl(Icons.Filled.Shuffle, "Shuffle play", tint, 26, onShuffle)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(52.dp).nexusPress(onPlay).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = Nx.Void, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TrackRow(t: SpTrack, player: SpPlayer?, look: SpotifyLook, onPlay: () -> Unit) {
    val sp = appContainer().spotify
    val tint = LocalSpTint.current
    val current = player?.track?.uri == t.uri
    val art = if (look.compactRows) 38.dp else 50.dp
    Row(
        Modifier.fillMaxWidth().nexusPress(onPlay, onLongClick = { sp.addToQueue(t) })
            .background(if (current) tint.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = if (look.compactRows) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(art).clip(nexusShape(0.35f)).background(Nx.Deep))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = if (current) tint else Nx.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artists, color = Nx.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current && player?.playing == true) Icon(Icons.Filled.GraphicEq, "Playing", tint = tint, modifier = Modifier.size(18.dp))
        else Text(fmt(t.durationMs), color = Nx.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MiniCard(title: String, sub: String, art: String?, width: Dp, round: Boolean = false, onClick: () -> Unit) {
    val shape = if (round) CircleShape else nexusShape(0.5f)
    Column(Modifier.width(width).nexusPress(onClick), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        AsyncImage(art, title, contentScale = ContentScale.Crop, modifier = Modifier.size(width).clip(shape).background(Nx.Deep).border(1.dp, Nx.Line, shape))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Nx.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = Nx.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val tint = LocalSpTint.current
    Text(
        hud(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Text,
        modifier = Modifier.nexusPress(onClick).clip(shape).background(if (selected) tint else Nx.Cyan.copy(alpha = 0.06f))
            .border(1.dp, if (selected) tint else Nx.Line, shape).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Ctl(icon: ImageVector, label: String, tint: Color, size: Int = 26, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint, modifier = Modifier.size(size.dp)) }
}

@Composable
private fun ErrorLine(sp: Spotify) {
    val error by sp.error.collectAsStateWithLifecycle()
    val e = error ?: return
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(nexusShape(0.5f)).background(Nx.Amber.copy(alpha = 0.12f)).nexusPress({ sp.clearError() }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, null, tint = Nx.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(e, color = Nx.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Close, "Dismiss", tint = Nx.Amber, modifier = Modifier.size(16.dp))
    }
}

private fun deviceIcon(type: String?): ImageVector = when (type?.lowercase()) {
    "smartphone" -> Icons.Filled.Smartphone
    "computer" -> Icons.Filled.Computer
    "speaker" -> Icons.Filled.Speaker
    "tv", "castvideo" -> Icons.Filled.Tv
    else -> Icons.Filled.Devices
}

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** The album art's main colour, brightened enough to read on the dark theme. */
@Composable
private fun rememberArtColour(url: String?, enabled: Boolean): Color? {
    val ctx = LocalContext.current
    val result by produceState<Color?>(null, url, enabled) {
        if (!enabled || url == null) { value = null; return@produceState }
        value = runCatching {
            val req = coil.request.ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build()
            val bmp = (coil.Coil.imageLoader(ctx).execute(req) as? coil.request.SuccessResult)?.drawable
                ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap } ?: return@runCatching null
            val pal = androidx.palette.graphics.Palette.from(bmp).generate()
            val sw = pal.vibrantSwatch ?: pal.lightVibrantSwatch ?: pal.dominantSwatch ?: return@runCatching null
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(sw.rgb, hsv)
            hsv[1] = hsv[1].coerceAtLeast(0.45f)
            hsv[2] = hsv[2].coerceAtLeast(0.85f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }.getOrNull()
    }
    return result
}

// ---------------------------------------------------------------------------
// Devices.
// ---------------------------------------------------------------------------

@Composable
private fun DevicesSheet(sp: Spotify, current: SpDevice?, onClose: () -> Unit) {
    var devices by remember { mutableStateOf<List<SpDevice>?>(null) }
    LaunchedEffect(Unit) { devices = runCatching { sp.devices() }.getOrDefault(emptyList()) }
    val tint = LocalSpTint.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).plainTap(onClose), contentAlignment = Alignment.BottomCenter) {
        HoloPanel(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().plainTap {}) {
            Text("Play on", color = Nx.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val d = devices
            when {
                d == null -> LoadingBlock("Finding devices…")
                d.isEmpty() -> Text("No Spotify devices online. Open Spotify on a phone, PC or speaker.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
                else -> d.forEach { dev ->
                    val on = dev.id == current?.id
                    Row(Modifier.fillMaxWidth().nexusPress({ sp.transfer(dev); onClose() }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(deviceIcon(dev.type), null, tint = if (on) tint else Nx.Muted)
                        Spacer(Modifier.width(12.dp))
                        Text(dev.name, color = if (on) tint else Nx.Text, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Filled.Check, "Current", tint = tint)
                    }
                }
            }
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

