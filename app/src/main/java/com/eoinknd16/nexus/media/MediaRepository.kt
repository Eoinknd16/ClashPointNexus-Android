package com.eoinknd16.nexus.media

import com.eoinknd16.nexus.core.JsonStore
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.PcLinkClient
import com.eoinknd16.nexus.link.PcLinkConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Phone-side equivalent of the PC's src/main/stremio/service.ts +
 * subtitles/progress/library. Same rules as the PC: your own addons decide
 * what can be streamed (Cinemeta is only the built-in catalog/metadata
 * source), addon order is kept as-is, and a failing addon only drops its
 * own results instead of failing the whole request.
 */
class MediaRepository(
    private val addonStore: JsonStore<List<Addon>>,
    private val progressStore: JsonStore<Map<String, WatchProgress>>,
    private val libraryStore: JsonStore<List<CatalogItem>>,
    private val pcStore: JsonStore<PcLinkConfig>,
    private val pc: PcLinkClient,
) {
    val addons = addonStore.state
    val progress = progressStore.state
    val library = libraryStore.state

    // ---- Catalog rows -------------------------------------------------------

    private val cinemetaCache = HashMap<String, List<CatalogItem>>()
    private val cacheLock = Mutex()

    suspend fun cinemetaRow(type: CatalogType, catalogId: String, genre: String? = null): List<CatalogItem> {
        val key = "$type:$catalogId:$genre"
        cacheLock.withLock { cinemetaCache[key] }?.let { return it }
        val items = runCatching { StremioProtocol.catalog(StremioProtocol.CINEMETA, type, catalogId, genre) }
            .getOrDefault(emptyList())
        if (items.isNotEmpty()) cacheLock.withLock { cinemetaCache[key] = items }
        return items
    }

    /** One row per movie/series catalog your own catalog addons declare. */
    suspend fun addonRows(type: CatalogType): List<CatalogRow> = coroutineScope {
        addonStore.get()
            .filter { "catalog" in it.resources }
            .flatMap { addon -> addon.catalogs.filter { it.type == type }.map { addon to it } }
            .map { (addon, cat) ->
                async {
                    runCatching { StremioProtocol.catalog(addon.url, type, cat.id) }.getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { CatalogRow("addon:${addon.url}:${cat.id}", "${addon.name} · ${cat.name}", it) }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    suspend fun search(query: String): List<CatalogItem> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        listOf(CatalogType.movie, CatalogType.series)
            .map { type -> async { runCatching { StremioProtocol.catalog(StremioProtocol.CINEMETA, type, "top", search = q) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .distinctBy { "${it.type}:${it.id}" }
    }

    // ---- Meta ---------------------------------------------------------------

    private val metaCache = HashMap<String, MetaDetail>()

    suspend fun meta(type: CatalogType, id: String): MetaDetail? {
        val key = "$type:$id"
        cacheLock.withLock { metaCache[key] }?.let { return it }
        // Cinemeta first (authoritative, same as the PC); fall back to any of
        // your meta addons for ids Cinemeta doesn't know (addon-only catalogs).
        val fromCinemeta = runCatching { StremioProtocol.meta(type, id) }.getOrNull()
        val detail = fromCinemeta ?: addonStore.get()
            .filter { "meta" in it.resources }
            .firstNotNullOfOrNull { runCatching { StremioProtocol.meta(type, id, it.url) }.getOrNull() }
        if (detail != null) cacheLock.withLock { metaCache[key] = detail }
        return detail
    }

    // ---- Streams ------------------------------------------------------------

    /**
     * Direct-URL streams play on the phone as-is. Torrent (infoHash) streams
     * go through the living-room PC: Nexus there starts its Stremio torrent
     * engine and proxies it to the phone -- same engine the PC itself uses.
     */
    suspend fun streams(type: CatalogType, id: String): StreamResult = coroutineScope {
        val streamAddons = addonStore.get().filter { "stream" in it.resources }
        if (streamAddons.isEmpty()) return@coroutineScope StreamResult(emptyList(), false, emptyList(), null)

        val pcConfig = pcStore.get()
        val torrentCheck = async {
            if (!pcConfig.isPaired) {
                "Pair your PC in Settings to play torrent streams"
            } else {
                runCatching { pc.ensureTorrentServer() }.fold(
                    onSuccess = { if (it.available) null else (it.reason ?: "PC torrent server unavailable") },
                    onFailure = { "Couldn't reach ${pcConfig.name.ifBlank { "your PC" }} (${it.describe()})" },
                )
            }
        }

        val perAddon = streamAddons.map { addon ->
            async { addon to runCatching { StremioProtocol.streams(addon.url, type, id) } }
        }.awaitAll()

        val torrentBlocked = torrentCheck.await()
        val errors = mutableListOf<String>()
        val streams = mutableListOf<StreamOption>()
        for ((addon, result) in perAddon) {
            result.onFailure { errors += "${addon.name}: ${it.describe()}" }
            for (raw in result.getOrDefault(emptyList())) {
                val source = when {
                    raw.url != null -> StreamSource.Direct(raw.url)
                    raw.infoHash != null -> StreamSource.Torrent(raw.infoHash, raw.fileIdx ?: 0)
                    else -> continue // YouTube/external-only streams: nothing playable here
                }
                val playable = when (source) {
                    is StreamSource.Direct -> source.url
                    is StreamSource.Torrent ->
                        if (torrentBlocked == null) pc.torrentStreamUrl(pcConfig, source.infoHash, source.fileIdx) else null
                }
                streams += StreamOption(
                    title = raw.title,
                    addonName = addon.name,
                    source = source,
                    playableUrl = playable,
                    resolution = StreamMeta.resolution(raw.title),
                    languages = StreamMeta.languages(raw.title),
                )
            }
        }
        val anyTorrent = streams.any { it.source is StreamSource.Torrent }
        StreamResult(streams, true, errors, if (anyTorrent) torrentBlocked else null)
    }

    /** OpenSubtitles always, plus every installed subtitles addon -- same as the PC. */
    suspend fun subtitles(type: CatalogType, id: String): List<SubtitleTrack> = coroutineScope {
        val sources = listOf("OpenSubtitles" to StremioProtocol.OPENSUBTITLES) +
            addonStore.get().filter { "subtitles" in it.resources }.map { it.name to it.url }
        sources.map { (label, url) ->
            async { runCatching { StremioProtocol.subtitles(url, label, type, id) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
    }

    // ---- Addons -------------------------------------------------------------

    private var communityCache: List<CommunityAddon>? = null

    suspend fun communityAddons(): List<CommunityAddon> =
        communityCache ?: StremioProtocol.communityAddons().also { communityCache = it }

    suspend fun installAddon(url: String): Addon {
        val addon = StremioProtocol.manifest(url)
        addonStore.update { list -> list.filterNot { it.url == addon.url } + addon }
        return addon
    }

    suspend fun removeAddon(url: String) = addonStore.update { list -> list.filterNot { it.url == url } }

    suspend fun moveAddon(url: String, up: Boolean) = addonStore.update { list ->
        val i = list.indexOfFirst { it.url == url }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Copies the PC's addon list (Settings > Plugins > TV Addons there). Keeps
     * any phone-only addons, PC ones first in the PC's order. */
    suspend fun importAddonsFromPc(): Int {
        val fromPc = pc.addons().map { it.copy(url = StremioProtocol.normalizeUrl(it.url)) }
        addonStore.update { mine -> fromPc + mine.filterNot { m -> fromPc.any { it.url == m.url } } }
        return fromPc.size
    }

    // ---- Progress + library -------------------------------------------------

    private fun key(type: CatalogType, id: String) = "${type.name}:$id"

    fun progressFor(type: CatalogType, id: String): WatchProgress? = progressStore.value[key(type, id)]

    suspend fun saveProgress(entry: WatchProgress) = progressStore.update { it + (key(entry.type, entry.id) to entry) }

    suspend fun clearProgress(type: CatalogType, id: String) = progressStore.update { it - key(type, id) }

    fun continueWatching(all: Map<String, WatchProgress>): List<WatchProgress> =
        all.values.filter { it.isResumable || it.type == CatalogType.series }.sortedByDescending { it.updatedAt }.take(25)

    fun isInLibrary(item: CatalogItem): Boolean = libraryStore.value.any { it.id == item.id && it.type == item.type }

    suspend fun toggleLibrary(item: CatalogItem) = libraryStore.update { list ->
        if (list.any { it.id == item.id && it.type == item.type }) list.filterNot { it.id == item.id && it.type == item.type }
        else listOf(item) + list
    }

    companion object {
        val addonListSerializer = ListSerializer(Addon.serializer())
        val progressSerializer = MapSerializer(String.serializer(), WatchProgress.serializer())
        val librarySerializer = ListSerializer(CatalogItem.serializer())

        val MOVIE_GENRES = listOf("Action", "Comedy", "Drama", "Thriller", "Sci-Fi", "Horror", "Animation", "Documentary")
        val SERIES_GENRES = listOf("Drama", "Comedy", "Crime", "Sci-Fi", "Animation", "Documentary", "Reality-TV")
    }
}
