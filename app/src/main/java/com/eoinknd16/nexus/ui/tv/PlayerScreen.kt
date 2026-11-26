package com.eoinknd16.nexus.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.compose.ui.graphics.toArgb
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.ResizeMode
import com.eoinknd16.nexus.look.SubtitleEdge
import com.eoinknd16.nexus.look.parseColor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.CastRequest
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.media.PlaybackRequest
import com.eoinknd16.nexus.media.StreamSource
import com.eoinknd16.nexus.media.SubtitleTrack
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Panel { None, Sources, Subtitles }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen() {
    val container = appContainer()
    val nav = LocalNav.current
    val context = LocalContext.current
    val request by container.playback.request.collectAsStateWithLifecycle()
    val req = request
    if (req == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.None) }
    var message by remember { mutableStateOf<String?>(null) }
    var subtitles by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var activeSubtitle by remember { mutableStateOf<SubtitleTrack?>(null) }
    val failedIndexes = remember { mutableSetOf<Int>() }

    val prefs = LookState.look.player
    // Headers the current stream's addon asked for (behaviorHints.proxyHeaders).
    // Read per request by the resolving data source below, so switching to a
    // source with different headers needs no new player.
    val streamHeaders = remember { java.util.concurrent.atomic.AtomicReference<Map<String, String>>(emptyMap()) }
    val player = remember {
        val okFactory = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.ADDON_HEADERS.getValue("User-Agent"))
        val dataSource = ResolvingDataSource.Factory(okFactory) { spec ->
            val h = streamHeaders.get()
            if (h.isEmpty()) spec else spec.withAdditionalHeaders(h)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(prefs.seekBackSeconds * 1000L)
            .setSeekForwardIncrementMs(prefs.seekForwardSeconds * 1000L)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(prefs.audioLanguage.ifBlank { Locale.getDefault().language })
                    .setPreferredTextLanguage(prefs.subtitleLanguage.ifBlank { null })
                    .build()
                playWhenReady = true
            }
    }

    fun mediaItemFor(r: PlaybackRequest, sub: SubtitleTrack?): MediaItem {
        val builder = MediaItem.Builder().setUri(r.stream.playableUrl)
        if (sub != null) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(guessSubtitleMime(sub.url))
                        .setLanguage(sub.lang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                        .build(),
                ),
            )
        }
        return builder.build()
    }

    fun saveProgress(ended: Boolean = false) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000.0)
        val position = if (ended && duration != null) duration else player.currentPosition / 1000.0
        if (position < 5 && !ended) return
        val ep = req.episode
        val entry = WatchProgress(
            type = req.item.type,
            id = req.item.id,
            name = req.item.name,
            poster = req.item.poster,
            positionSeconds = position,
            durationSeconds = duration,
            season = ep?.season,
            episode = ep?.episode,
            episodeId = ep?.id,
            updatedAt = System.currentTimeMillis(),
        )
        container.scope.launch { container.media.saveProgress(entry) }
    }

    // (Re)load whenever the selected stream changes. Position comes from the request.
    LaunchedEffect(req.streamIndex, req.stream.playableUrl) {
        streamHeaders.set((req.stream.source as? StreamSource.Direct)?.headers ?: emptyMap())
        player.setMediaItem(mediaItemFor(req, activeSubtitle), (req.startPositionSeconds * 1000).toLong())
        player.prepare()
        message = "${req.stream.addonName}${req.stream.resolution?.let { " · $it" } ?: ""}"
        delay(3000)
        message = null
    }

    LaunchedEffect(req.videoId) {
        val want = prefs.subtitleLanguage.lowercase()
        fun matches(lang: String) = want.isNotBlank() && (lang.lowercase().startsWith(want) || languageCode3(want) == lang.lowercase())
        subtitles = container.media.subtitles(req.item.type, req.videoId)
            .sortedWith(compareBy({ !matches(it.lang) }, { it.lang }))
        // Customise > Player > "Turn subtitles on automatically".
        if (prefs.autoSubtitles && activeSubtitle == null) {
            subtitles.firstOrNull { matches(it.lang) }?.let { sub ->
                activeSubtitle = sub
                val pos = player.currentPosition
                player.setMediaItem(mediaItemFor(req, sub), pos)
                player.prepare()
            }
        }
    }

    // Periodic progress save + a stream that dies mid-way falls over to the next source.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val current = container.playback.request.value ?: return
                failedIndexes += current.streamIndex
                val next = current.streams.indices.firstOrNull { it > current.streamIndex && it !in failedIndexes }
                if (next != null && prefs.autoFallback) {
                    message = "Source failed (${error.errorCodeName}) · trying next"
                    container.playback.switchStream(next, player.currentPosition / 1000.0)
                } else {
                    message = "Playback failed: ${error.errorCodeName}"
                    panel = Panel.Sources
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress(ended = true)
                    nav.popBackStack()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            delay(10_000)
            if (player.isPlaying) saveProgress()
        }
    }
    LifecycleStartEffect(player) {
        onStopOrDispose {
            player.pause()
            saveProgress()
        }
    }

    ImmersiveLandscape(prefs.forceLandscape)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    resizeMode = when (prefs.resizeMode) {
                        ResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        ResizeMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                parseColor(prefs.subtitleColor, Color.White).toArgb(),
                                parseColor(prefs.subtitleBackground, Color.Transparent).toArgb(),
                                android.graphics.Color.TRANSPARENT,
                                when (prefs.subtitleEdge) {
                                    SubtitleEdge.None -> CaptionStyleCompat.EDGE_TYPE_NONE
                                    SubtitleEdge.Outline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                    SubtitleEdge.Shadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                    SubtitleEdge.Raised -> CaptionStyleCompat.EDGE_TYPE_RAISED
                                    SubtitleEdge.Depressed -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                                },
                                android.graphics.Color.BLACK,
                                null,
                            ),
                        )
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * prefs.subtitleScale)
                        setBottomPaddingFraction(prefs.subtitleBottomPadding)
                    }
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == android.view.View.VISIBLE })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(controlsVisible || panel != Panel.None, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { nav.popBackStack() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(req.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (prefs.showSourceBadge) {
                        Text(
                            hud((if (req.stream.source is StreamSource.Torrent) "P2P via PC · " else "Direct · ") + req.stream.addonName),
                            style = MaterialTheme.typography.labelSmall, color = Nx.Cyan, maxLines = 1,
                        )
                    }
                }
                PlayerIcon(Icons.Filled.ClosedCaption, "Subtitles", if (activeSubtitle != null) Nx.Cyan else Color.White) {
                    panel = if (panel == Panel.Subtitles) Panel.None else Panel.Subtitles
                }
                PlayerIcon(Icons.Filled.SwapHoriz, "Sources") { panel = if (panel == Panel.Sources) Panel.None else Panel.Sources }
                if (container.pcStore.value.isPaired) {
                    PlayerIcon(Icons.Filled.Cast, "Play on TV", Nx.Magenta) {
                        player.pause()
                        saveProgress()
                        val s = req.stream.source
                        scope.launch {
                            message = "Sending to TV…"
                            val res = runCatching {
                                container.pc.cast(
                                    CastRequest(
                                        type = req.item.type,
                                        item = req.item.slim(),
                                        episodeId = req.episode?.id,
                                        season = req.episode?.season,
                                        episode = req.episode?.episode,
                                        positionSeconds = player.currentPosition / 1000.0,
                                        directUrl = (s as? StreamSource.Direct)?.url,
                                        infoHash = (s as? StreamSource.Torrent)?.infoHash,
                                        fileIdx = (s as? StreamSource.Torrent)?.fileIdx,
                                    ),
                                )
                            }
                            val ok = res.getOrNull()
                            if (ok?.ok == true) {
                                nav.popBackStack()
                            } else if (ok != null) {
                                message = ok.error ?: "PC refused"
                            } else {
                                message = "Couldn't reach PC (${res.exceptionOrNull()?.describe()})"
                            }
                        }
                    }
                }
            }
        }

        when (panel) {
            Panel.Sources -> SidePanel("Sources", Modifier.align(Alignment.CenterEnd)) {
                itemsIndexed(req.streams) { i, s ->
                    PanelRow(
                        title = listOfNotNull(s.resolution, s.addonName).joinToString(" · "),
                        body = s.title,
                        selected = i == req.streamIndex,
                        dim = i in failedIndexes,
                    ) {
                        panel = Panel.None
                        failedIndexes -= i
                        container.playback.switchStream(i, player.currentPosition / 1000.0)
                    }
                }
            }
            Panel.Subtitles -> SidePanel("Subtitles", Modifier.align(Alignment.CenterEnd)) {
                itemsIndexed(listOf<SubtitleTrack?>(null) + subtitles) { _, sub ->
                    PanelRow(
                        title = sub?.let { languageName(it.lang) } ?: "Off",
                        body = sub?.id?.substringBefore(":"),
                        selected = sub?.id == activeSubtitle?.id,
                    ) {
                        panel = Panel.None
                        activeSubtitle = sub
                        // Side-loaded subtitles are part of the MediaItem, so swap it in place and keep position.
                        val pos = player.currentPosition
                        player.setMediaItem(mediaItemFor(req, sub), pos)
                        player.prepare()
                    }
                }
            }
            Panel.None -> {}
        }

        message?.let {
            Text(
                hud(it), style = MaterialTheme.typography.labelMedium, color = Nx.Text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).holoPanel().padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PlayerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
}

@Composable
private fun SidePanel(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier.width(340.dp).heightIn(max = 520.dp).padding(16.dp).holoPanel().padding(12.dp)) {
        HudLabel(title)
        Spacer(Modifier.size(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun PanelRow(title: String, body: String?, selected: Boolean, dim: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().nexusPress(onClick)
            .background(if (selected) Nx.Cyan.copy(alpha = 0.15f) else Color.Transparent)
            .padding(10.dp),
    ) {
        Text(hud(title), style = MaterialTheme.typography.labelMedium, color = if (dim) Nx.Danger else if (selected) Nx.Cyan else Nx.Text)
        if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Landscape + hidden system bars while the player is up; restored on exit. */
@Composable
private fun ImmersiveLandscape(forceLandscape: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, forceLandscape) {
        val previous = activity.requestedOrientation
        if (forceLandscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun guessSubtitleMime(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        // OpenSubtitles v3 and most addons serve SRT, often without an extension.
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

/** ISO 639-1 -> 639-2 ("en" -> "eng"), since subtitle addons use either. */
private fun languageCode3(code: String): String? =
    runCatching { Locale(code).isO3Language.lowercase() }.getOrNull()

private fun languageName(code: String): String {
    val name = runCatching { Locale(code).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()
    return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) code else name
}

@Suppress("unused")
private fun CatalogType.label() = if (this == CatalogType.movie) "Movie" else "Series"
