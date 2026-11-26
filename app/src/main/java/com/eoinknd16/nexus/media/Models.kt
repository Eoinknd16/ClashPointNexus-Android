package com.eoinknd16.nexus.media

import kotlinx.serialization.Serializable

/** Same shapes as the PC build's src/shared/stremioTypes.ts, so both apps
 * (and the phone -> PC link) speak the same vocabulary. */

/**
 * A Stremio content type. Usually "movie" or "series", but addons can declare
 * any type ("anime", "channel", "tv", ...) and Stremio shows those as their
 * own sections -- so this is an open string, not an enum. Serialized as the
 * bare string, so data saved by earlier builds (when it was an enum) still loads.
 */
@Serializable
@JvmInline
value class CatalogType(val name: String) {
    override fun toString(): String = name

    /** Human label for tabs/headings: "movie" -> "Movies", "anime" -> "Anime". */
    val label: String
        get() = when (name) {
            "movie" -> "Movies"
            "series" -> "Series"
            "tv" -> "TV channels"
            "channel" -> "Channels"
            else -> name.replaceFirstChar { it.uppercase() }
        }

    companion object {
        val movie = CatalogType("movie")
        val series = CatalogType("series")
    }
}

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
data class AddonCatalogRef(
    val type: CatalogType,
    val id: String,
    val name: String,
    /** Needs an extra (e.g. search-only, or a required genre) so it can't be a plain board row. */
    val requiresExtra: Boolean = false,
    /** Declares the "search" extra -- queried by global search, like Stremio does. */
    val searchable: Boolean = false,
)

/** A manifest resource can narrow which types / id prefixes it answers for. */
@Serializable
data class ResourceRule(val name: String, val types: List<String>? = null, val idPrefixes: List<String>? = null)

@Serializable
data class Addon(
    val name: String,
    val url: String,
    val resources: List<String>,
    val catalogs: List<AddonCatalogRef> = emptyList(),
    val logo: String? = null,
    /** Manifest-level types / idPrefixes (null = not declared = anything). */
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
    val rules: List<ResourceRule> = emptyList(),
    /** Came from a Stremio account import (vs added by hand on the phone). */
    val fromStremio: Boolean = false,
) {
    /**
     * Same routing the Stremio app does: only ask an addon for a resource if
     * it declares that resource for this type and id prefix. Stops e.g. an
     * IMDb-only stream addon being queried for a kitsu: anime id (wasted
     * request + a confusing "failed" line in the sources list).
     */
    fun supports(resource: String, type: CatalogType, id: String): Boolean {
        if (resource !in resources) return false
        val rule = rules.firstOrNull { it.name == resource }
        val t = rule?.types ?: types
        val p = rule?.idPrefixes ?: idPrefixes
        if (t != null && t.isNotEmpty() && type.name !in t) return false
        if (p != null && p.isNotEmpty() && p.none { id.startsWith(it) }) return false
        return true
    }
}

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
    /** Plain HTTP(S) URL. [headers] = behaviorHints.proxyHeaders.request, which
     * some debrid/CDN addons need sent with every request or the host rejects it. */
    data class Direct(val url: String, val headers: Map<String, String> = emptyMap()) : StreamSource

    /** [fileIdx] null = the addon didn't say which file; the torrent server picks the largest video. */
    data class Torrent(val infoHash: String, val fileIdx: Int?) : StreamSource

    /** Opens outside Nexus (externalUrl, YouTube ytId). */
    data class External(val url: String) : StreamSource
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
