package com.eoinknd16.nexus.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.CastRequest
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.media.Episode
import com.eoinknd16.nexus.media.MetaDetail
import com.eoinknd16.nexus.media.PlaybackRequest
import com.eoinknd16.nexus.media.StreamOption
import com.eoinknd16.nexus.media.StreamResult
import com.eoinknd16.nexus.media.StreamSource
import com.eoinknd16.nexus.media.WatchProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bottom sheet listing every stream for one title/episode. */
data class SourcesSheet(
    val episode: Episode?,
    val loading: Boolean = true,
    val result: StreamResult? = null,
)

data class DetailState(
    val loading: Boolean = true,
    val meta: MetaDetail? = null,
    val error: String? = null,
    val season: Int? = null,
    val sheet: SourcesSheet? = null,
    /** One-line status toasts ("Finding streams…", "Sent to TV"). */
    val message: String? = null,
    val busy: Boolean = false,
)

class DetailViewModel(private val c: AppContainer, val type: CatalogType, val id: String) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private val progressKey = "${type.name}:$id"
    val progress: StateFlow<WatchProgress?> = c.media.progress
        .map { it[progressKey] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.media.progressFor(type, id))

    val inLibrary: StateFlow<Boolean> = c.media.library
        .map { list -> list.any { it.id == id && it.type == type } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pcPaired: StateFlow<Boolean> = c.pcStore.state.map { it.isPaired }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val meta = c.media.meta(type, id)
            if (meta == null) {
                _state.update { it.copy(loading = false, error = "Couldn't load this title") }
                return@launch
            }
            // Land on the season you were last watching, else the first real (non-specials) one.
            val seasons = meta.episodes.map { it.season }.distinct()
            val lastSeason = c.media.progressFor(type, id)?.season
            val season = lastSeason?.takeIf { it in seasons } ?: seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull()
            _state.update { it.copy(loading = false, meta = meta, season = season) }
        }
    }

    fun selectSeason(season: Int) = _state.update { it.copy(season = season) }

    fun toggleLibrary() {
        val item = _state.value.meta?.item ?: return
        viewModelScope.launch { c.media.toggleLibrary(item.slim()) }
    }

    /** The episode a series' main Play button should start. */
    fun nextEpisode(): Episode? {
        val meta = _state.value.meta ?: return null
        val p = progress.value
        val eps = meta.episodes
        if (p?.episodeId != null) {
            val i = eps.indexOfFirst { it.id == p.episodeId }
            if (i >= 0) return if (p.isResumable || i == eps.lastIndex) eps[i] else eps[i + 1]
        }
        return eps.firstOrNull { it.season > 0 } ?: eps.firstOrNull()
    }

    fun startPositionFor(episode: Episode?): Double {
        val p = progress.value ?: return 0.0
        if (!p.isResumable) return 0.0
        return if (episode == null || p.episodeId == episode.id) p.positionSeconds else 0.0
    }

    /** Play = top-ranked playable stream straight away, same as the PC's Play. */
    fun play(episode: Episode?, onReady: (PlaybackRequest) -> Unit) {
        val meta = _state.value.meta ?: return
        flash(if (episode != null) "Finding streams for S${episode.season}E${episode.episode}…" else "Finding streams…", busy = true)
        viewModelScope.launch {
            val result = c.media.streams(type, episode?.id ?: id)
            val playable = result.streams.filter { it.playableUrl != null }
            if (playable.isEmpty()) {
                flash(describeFailure(result))
                return@launch
            }
            _state.update { it.copy(busy = false, message = null) }
            onReady(PlaybackRequest(meta.item, episode, playable, 0, startPositionFor(episode)))
        }
    }

    fun openSources(episode: Episode?) {
        _state.update { it.copy(sheet = SourcesSheet(episode)) }
        viewModelScope.launch {
            val result = c.media.streams(type, episode?.id ?: id)
            _state.update { s -> if (s.sheet?.episode == episode) s.copy(sheet = SourcesSheet(episode, false, result)) else s }
        }
    }

    fun closeSources() = _state.update { it.copy(sheet = null) }

    fun playFromSheet(stream: StreamOption): PlaybackRequest? {
        val meta = _state.value.meta ?: return null
        val sheet = _state.value.sheet ?: return null
        val playable = sheet.result?.streams?.filter { it.playableUrl != null } ?: return null
        val index = playable.indexOf(stream).takeIf { it >= 0 } ?: return null
        closeSources()
        return PlaybackRequest(meta.item, sheet.episode, playable, index, startPositionFor(sheet.episode))
    }

    /** Hands the title to the living-room PC. With [stream] it plays exactly
     * that source; without, the PC picks its own top stream (its own addon list). */
    fun sendToTv(episode: Episode?, stream: StreamOption? = null) {
        val meta = _state.value.meta ?: return
        flash("Sending to ${c.pcStore.value.name.ifBlank { "PC" }}…", busy = true)
        viewModelScope.launch {
            val request = CastRequest(
                type = type,
                item = meta.item.slim(),
                episodeId = episode?.id,
                season = episode?.season,
                episode = episode?.episode,
                positionSeconds = startPositionFor(episode),
                directUrl = (stream?.source as? StreamSource.Direct)?.url,
                infoHash = (stream?.source as? StreamSource.Torrent)?.infoHash,
                fileIdx = (stream?.source as? StreamSource.Torrent)?.fileIdx,
            )
            runCatching { c.pc.cast(request) }.fold(
                onSuccess = { flash(if (it.ok) "Playing on TV" else (it.error ?: "PC refused")) },
                onFailure = { flash("Couldn't reach PC (${it.describe()})") },
            )
        }
    }

    private fun flash(message: String, busy: Boolean = false) {
        _state.update { it.copy(message = message, busy = busy) }
        if (!busy) viewModelScope.launch {
            delay(3_500)
            _state.update { if (it.message == message) it.copy(message = null) else it }
        }
    }

    companion object {
        fun describeFailure(result: StreamResult): String = when {
            !result.hasStreamAddons -> "Add a stream addon first (TV › Addons)"
            result.streams.isEmpty() && result.addonErrors.isNotEmpty() -> "Addons failed: ${result.addonErrors.joinToString("; ")}"
            result.streams.isEmpty() -> "No streams found"
            result.torrentUnavailableReason != null -> "Only torrent streams found. ${result.torrentUnavailableReason}"
            else -> "No playable streams"
        }
    }
}

/** What gets stored in library/cast payloads -- no long descriptions. */
fun CatalogItem.slim(): CatalogItem = copy(description = null, background = null)
