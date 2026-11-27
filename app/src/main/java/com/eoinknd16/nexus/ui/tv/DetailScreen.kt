package com.eoinknd16.nexus.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.media.Episode
import com.eoinknd16.nexus.media.StreamOption
import com.eoinknd16.nexus.media.StreamSource
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.EmptyBlock
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud

@Composable
fun DetailScreen(type: CatalogType, id: String) {
    val vm = nexusViewModel(key = "detail:$type:$id") { DetailViewModel(it, type, id) }
    val playback = appContainer().playback
    val nav = LocalNav.current
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val inLibrary by vm.inLibrary.collectAsStateWithLifecycle()
    val pcPaired by vm.pcPaired.collectAsStateWithLifecycle()

    val startPlayer: (com.eoinknd16.nexus.media.PlaybackRequest) -> Unit = { req ->
        playback.start(req)
        nav.navigate(Routes.PLAYER)
    }
    // "Play the next episode of X" from search / Ask Nexus: start as soon as the title loads.
    androidx.compose.runtime.LaunchedEffect(state.meta) {
        val meta = state.meta ?: return@LaunchedEffect
        val req = AutoPlay.consume(type, id) ?: return@LaunchedEffect
        val ep = if (meta.episodes.isNotEmpty()) vm.nextEpisode() else null
        if (req.onTv) vm.sendToTv(ep) else vm.play(ep, startPlayer)
    }

    NexusBackdrop(intensity = 0.4f) {
        val meta = state.meta
        if (meta != null) {
            // Blurred-feeling backdrop: the title's own art, heavily darkened.
            AsyncImage(
                model = meta.item.background ?: meta.item.poster, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(320.dp),
            )
            Box(
                Modifier.fillMaxWidth().height(320.dp).background(
                    Brush.verticalGradient(listOf(Color(0x8805070D), Color(0xEE05070D), Nx.Void)),
                ),
            )
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader(meta?.item?.name ?: "Loading…", meta?.item?.year)
            when {
                state.loading -> LoadingBlock("Pulling metadata…")
                meta == null -> EmptyBlock(state.error ?: "Not found", null) { NexusButton("Retry") { vm.load() } }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    // Anything with an episode list behaves like a series (anime, channels...), whatever its type.
                    val episodic = meta.episodes.isNotEmpty()
                    val autoPlay = com.eoinknd16.nexus.look.LookState.look.tv.autoPlayTopSource
                    item { Header(meta, progress) }
                    item {
                        Actions(
                            isSeries = episodic,
                            resumeLabel = resumeLabel(episodic, progress, vm.nextEpisode()),
                            inLibrary = inLibrary,
                            pcPaired = pcPaired && (type == CatalogType.movie || type == CatalogType.series),
                            busy = state.busy,
                            onPlay = {
                                val ep = if (episodic) vm.nextEpisode() else null
                                if (autoPlay) vm.play(ep, startPlayer) else vm.openSources(ep)
                            },
                            onSources = { vm.openSources(if (episodic) vm.nextEpisode() else null) },
                            onLibrary = vm::toggleLibrary,
                            onCast = { vm.sendToTv(if (episodic) vm.nextEpisode() else null) },
                        )
                    }
                    if (episodic) {
                        val seasons = meta.episodes.map { it.season }.distinct().sortedBy { com.eoinknd16.nexus.media.seasonSortKey(it) }
                        item {
                            Column {
                                HudLabel("Seasons", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(seasons) { s ->
                                        SeasonChip(if (s == 0) "Specials" else "S$s", s == state.season) { vm.selectSeason(s) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        items(meta.episodes.filter { it.season == state.season }, key = { it.id }) { ep ->
                            EpisodeRow(
                                ep = ep,
                                progress = progress?.takeIf { it.episodeId == ep.id },
                                onPlay = { if (autoPlay) vm.play(ep, startPlayer) else vm.openSources(ep) },
                                onSources = { vm.openSources(ep) },
                            )
                        }
                    }
                }
            }
        }

        // Status line ("Finding streams…", errors, "Playing on TV").
        AnimatedVisibility(
            visible = state.message != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) {
            Text(
                hud(state.message ?: ""),
                style = MaterialTheme.typography.labelMedium, color = Nx.Text,
                modifier = Modifier.holoPanel().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    state.sheet?.let { sheet ->
        SourcesBottomSheet(
            sheet = sheet,
            pcPaired = pcPaired,
            onDismiss = vm::closeSources,
            onPlay = { s -> vm.playFromSheet(s)?.let(startPlayer) },
            onCast = { s -> vm.closeSources(); vm.sendToTv(sheet.episode, s) },
        )
    }
}

private fun resumeLabel(episodic: Boolean, p: WatchProgress?, next: Episode?): String = when {
    !episodic -> if (p?.isResumable == true) "Resume" else "Play"
    next == null -> "Play"
    p?.episodeId == next.id && p.isResumable -> "Resume S${next.season}E${next.episode}"
    else -> "Play S${next.season}E${next.episode}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(meta: com.eoinknd16.nexus.media.MetaDetail, progress: WatchProgress?) = Column {
    Row(Modifier.padding(16.dp)) {
        AsyncImage(
            model = meta.item.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(PosterShape).background(Nx.Deep)
                .border(1.dp, Nx.CyanDim, PosterShape),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(meta.item.name, style = MaterialTheme.typography.headlineSmall, color = Nx.Text)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(
                    meta.item.year,
                    meta.runtime,
                    meta.item.imdbRating?.let { "IMDb $it" },
                ).forEach { Text(hud(it), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan) }
            }
            Spacer(Modifier.height(6.dp))
            if (meta.item.genres.isNotEmpty()) {
                Text(meta.item.genres.take(4).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
            }
            if (meta.director.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("DIR · ${meta.director.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress?.isResumable == true && meta.episodes.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                GlowProgress(progress.fraction, Modifier.fillMaxWidth())
            }
        }
    }
    meta.item.description?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Nx.Text.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (meta.cast.isNotEmpty()) {
        Text(
            "CAST · ${meta.cast.take(5).joinToString(", ")}",
            style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    isSeries: Boolean,
    resumeLabel: String,
    inLibrary: Boolean,
    pcPaired: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onSources: () -> Unit,
    onLibrary: () -> Unit,
    onCast: () -> Unit,
) = Column {
    FlowRow(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NexusButton(resumeLabel, icon = Icons.Filled.PlayArrow, filled = true, enabled = !busy, onClick = onPlay)
        NexusButton("Sources", icon = Icons.AutoMirrored.Filled.FormatListBulleted, onClick = onSources)
        if (pcPaired) NexusButton("TV", icon = Icons.Filled.Cast, accent = Nx.Magenta, enabled = !busy, onClick = onCast)
        NexusButton(if (inLibrary) "Saved" else "Library", icon = if (inLibrary) Icons.Filled.Check else Icons.Filled.Add, accent = Nx.Amber, onClick = onLibrary)
    }
    if (isSeries) Spacer(Modifier.height(4.dp))
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = nexusShape(0.43f)
    Text(
        hud(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Cyan,
        modifier = Modifier.nexusPress(onClick).clip(shape)
            .background(if (selected) Nx.Cyan else Nx.Cyan.copy(alpha = 0.08f))
            .border(1.dp, Nx.CyanDim, shape).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun EpisodeRow(ep: Episode, progress: WatchProgress?, onPlay: () -> Unit, onSources: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .nexusPress(onPlay, onLongClick = onSources).holoPanel(glow = 0.05f).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(nexusShape(0.43f)).background(Nx.Deep)) {
            AsyncImage(ep.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (progress != null) GlowProgress(progress.fraction, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("E${ep.episode}", style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
            Text(ep.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesBottomSheet(
    sheet: SourcesSheet,
    pcPaired: Boolean,
    onDismiss: () -> Unit,
    onPlay: (StreamOption) -> Unit,
    onCast: (StreamOption) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Nx.PanelSolid,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HudLabel(
                sheet.episode?.let { "Sources · S${it.season}E${it.episode}" } ?: "Sources",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val result = sheet.result
            when {
                sheet.loading -> LoadingBlock("Querying addons…")
                result == null || result.streams.isEmpty() ->
                    EmptyBlock(DetailViewModel.describeFailure(result ?: com.eoinknd16.nexus.media.StreamResult(emptyList(), true, emptyList(), null)))
                else -> {
                    result.torrentUnavailableReason?.let {
                        Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = Nx.Amber, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (result.addonErrors.isNotEmpty()) {
                        Text(
                            "Failed: ${result.addonErrors.joinToString("; ")}",
                            style = MaterialTheme.typography.labelSmall, color = Nx.Danger,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                        items(result.streams) { s -> StreamRow(s, pcPaired, { onPlay(s) }, { onCast(s) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(s: StreamOption, pcPaired: Boolean, onPlay: () -> Unit, onCast: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val external = s.source as? StreamSource.External
    val playable = s.playableUrl != null || external != null
    Row(
        Modifier.fillMaxWidth()
            .then(
                when {
                    external != null -> Modifier.nexusPress({
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(external.url))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    })
                    playable -> Modifier.nexusPress(onPlay)
                    else -> Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                s.resolution?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nx.Void, modifier = Modifier.background(Nx.Cyan).padding(horizontal = 6.dp, vertical = 2.dp)) }
                Text(
                    when (s.source) {
                        is StreamSource.Torrent -> "P2P via PC · "
                        is StreamSource.External -> "Opens outside Nexus · "
                        is StreamSource.Direct -> "Direct · "
                    } + s.addonName,
                    style = MaterialTheme.typography.labelSmall, color = if (playable) Nx.Cyan else Nx.Muted,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(s.title, style = MaterialTheme.typography.bodyMedium, color = if (playable) Nx.Text else Nx.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (s.languages.isNotEmpty()) Text(s.languages.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
        if (pcPaired && external == null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).nexusPress(onCast), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Cast, "Play on TV", tint = Nx.Magenta)
            }
        }
    }
}


/** A pending "play this as soon as it opens" request (from search / Ask Nexus). */
object AutoPlay {
    data class Request(val key: String, val onTv: Boolean)
    private var pending: Request? = null
    fun request(type: CatalogType, id: String, onTv: Boolean) { pending = Request("${type.name}:$id", onTv) }
    fun consume(type: CatalogType, id: String): Request? = pending?.takeIf { it.key == "${type.name}:$id" }?.also { pending = null }
}
