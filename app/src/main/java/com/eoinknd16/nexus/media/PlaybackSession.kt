package com.eoinknd16.nexus.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the player screen should play. Handed over through [PlaybackSession]
 * rather than nav arguments since it carries the whole stream list (so the
 * player can switch source without going back). */
data class PlaybackRequest(
    val item: CatalogItem,
    val episode: Episode? = null,
    val streams: List<StreamOption>,
    val streamIndex: Int,
    val startPositionSeconds: Double = 0.0,
) {
    val stream: StreamOption get() = streams[streamIndex]
    val title: String get() = episode?.let { "${item.name} · S${it.season}E${it.episode} ${it.name}" } ?: item.name

    /** Stremio id that streams/subtitles are keyed on (episode id for a series). */
    val videoId: String get() = episode?.id ?: item.id
}

class PlaybackSession {
    private val _request = MutableStateFlow<PlaybackRequest?>(null)
    val request: StateFlow<PlaybackRequest?> = _request

    fun start(request: PlaybackRequest) {
        _request.value = request
    }

    fun switchStream(index: Int, positionSeconds: Double) {
        _request.value = _request.value?.copy(streamIndex = index, startPositionSeconds = positionSeconds)
    }

    fun clear() {
        _request.value = null
    }
}
