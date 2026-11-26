package com.eoinknd16.nexus.media

import kotlinx.serialization.Serializable

/** Same shapes as the PC build's src/shared/stremioTypes.ts, so both apps
 * (and the phone -> PC link) speak the same vocabulary. */

@Serializable
enum class CatalogType { movie, series }

@Serializable
data class CatalogItem(
    val id: String,
    val type: CatalogType,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val imdbRating: String? = null,
)

@Serializable
data class AddonCatalogRef(val type: CatalogType, val id: String, val name: String)

@Serializable
data class Addon(
    val name: String,
    val url: String,
    val resources: List<String>,
    val catalogs: List<AddonCatalogRef> = emptyList(),
    val logo: String? = null,
)

data class CatalogRow(val key: String, val label: String, val items: List<CatalogItem>)

data class Episode(
    /** Full Stremio video id, e.g. "tt1520211:1:4". */
    val id: String,
    val season: Int,
    val episode: Int,
    val name: String,
    val overview: String?,
    val thumbnail: String?,
    val released: String?,
)

data class MetaDetail(
    val item: CatalogItem,
    val runtime: String?,
    val cast: List<String>,
    val director: List<String>,
    val released: String?,
    val episodes: List<Episode>,
)

/** Where a stream actually comes from -- decides whether the phone can play
 * it on its own, or needs the living-room PC's torrent server. */
sealed interface StreamSource {
    data class Direct(val url: String) : StreamSource
    data class Torrent(val infoHash: String, val fileIdx: Int) : StreamSource
}

data class StreamOption(
    val title: String,
    val addonName: String,
    val source: StreamSource,
    /** Null when it's a torrent stream and the PC link isn't set up/reachable. */
    val playableUrl: String?,
    val resolution: String?,
    val languages: List<String>,
)

data class StreamResult(
    val streams: List<StreamOption>,
    val hasStreamAddons: Boolean,
    val addonErrors: List<String>,
    /** Why torrent streams can't play right now, or null if they can. */
    val torrentUnavailableReason: String?,
)

data class SubtitleTrack(val id: String, val lang: String, val url: String)

@Serializable
data class CommunityAddon(
    val id: String,
    val name: String,
    val description: String,
    val logo: String?,
    val types: List<String>,
    val resources: List<String>,
    val transportUrl: String,
)

/** Mirrors PC src/shared/progressTypes.ts: one entry per title; for a series
 * `id` is the series id and season/episode/episodeId point at the last one. */
@Serializable
data class WatchProgress(
    val type: CatalogType,
    val id: String,
    val name: String,
    val poster: String? = null,
    val positionSeconds: Double,
    val durationSeconds: Double? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeId: String? = null,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = durationSeconds?.takeIf { it > 0 }?.let { (positionSeconds / it).toFloat().coerceIn(0f, 1f) } ?: 0f

    /** Same rule of thumb the PC uses: worth resuming unless barely started or basically done. */
    val isResumable: Boolean
        get() = positionSeconds > 30 && (durationSeconds == null || positionSeconds < durationSeconds * 0.95)
}

fun seasonSortKey(season: Int): Int = if (season == 0) Int.MAX_VALUE else season
