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
 * subtitles/progress/library, extended to behave like the Stremio app itself
 * once an account is imported: your addons (in your order) decide the board,
 * metadata, streams and subtitles, each addon is only asked for the types/ids
 * it declares, and a failing addon only drops its own results.
 */
class MediaRepository(
    private val addonStore: JsonStore<List<Addon>>,
    private val progressStore: JsonStore<Map<String, WatchProgress>>,
    private val libraryStore: JsonStore<List<CatalogItem>>,
    val accountStore: JsonStore<StremioAccountState>,
    private val pcStore: JsonStore<PcLinkConfig>,
    private val pc: PcLinkClient,
) {
    val addons = addonStore.state
    val progress = progressStore.state
    val library = libraryStore.state
    val account = accountStore.state

    private val cacheLock = Mutex()

    // ---- Board ----------------------------------------------------------------

    private val cinemetaCache = HashMap<String, List<CatalogItem>>()

    /** Built-in Cinemeta rows (used when your addons don't already include Cinemeta). */
    suspend fun cinemetaRow(type: CatalogType, catalogId: String, genre: String? = null): List<CatalogItem> {
        val key = "$type:$catalogId:$genre"
        cacheLock.withLock { cinemetaCache[key] }?.let { return it }
        val items = runCatching { StremioProtocol.catalog(StremioProtocol.CINEMETA, type, catalogId, genre) }
            .getOrDefault(emptyList())
        if (items.isNotEmpty()) cacheLock.withLock { cinemetaCache[key] = items }
        return items
    }

    /** True when one of your addons is Cinemeta itself (always the case after a Stremio import). */
    suspend fun hasCinemetaAddon(): Boolean = addonStore.get().any { it.url.startsWith(StremioProtocol.CINEMETA) }

    /** Content types your addons have board catalogs for, movie/series first -- Stremio's Discover sections. */
    suspend fun boardTypes(): List<CatalogType> {
        val declared = addonStore.get().flatMap { a ->
            if ("catalog" in a.resources) a.catalogs.filter { !it.requiresExtra }.map { it.type } else emptyList()
        }
        return (listOf(CatalogType.movie, CatalogType.series) + declared).distinct()
    }

    /** One row per board catalog your addons declare for this type, in addon order (= Stremio's board order). */
    suspend fun addonRows(type: CatalogType): List<CatalogRow> = coroutineScope {
        addonStore.get()
            .filter { "catalog" in it.resources }
            .flatMap { addon -> addon.catalogs.filter { it.type == type && !it.requiresExtra }.map { addon to it } }
            .map { (addon, cat) ->
                async {
                    runCatching { StremioProtocol.catalog(addon.url, type, cat.id) }.getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { CatalogRow("addon:${addon.url}:${cat.type}:${cat.id}", rowLabel(addon, cat), it) }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /** Stremio shows "Popular - Movie" style names; Cinemeta's own rows get no addon prefix. */
    private fun rowLabel(addon: Addon, cat: AddonCatalogRef): String =
        if (addon.url.startsWith(StremioProtocol.CINEMETA)) cat.name else "${cat.name} · ${addon.name}"

    /** Searches every catalog that supports search (your addons, like Stremio), else Cinemeta. */
    suspend fun search(query: String): List<CatalogItem> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val searchable = addonStore.get().flatMap { a ->
            if ("catalog" in a.resources) a.catalogs.filter { it.searchable }.map { a to it } else emptyList()
        }
        val tasks = if (searchable.isNotEmpty()) {
            searchable.map { (a, c) -> async { runCatching { StremioProtocol.catalog(a.url, c.type, c.id, search = q) }.getOrDefault(emptyList()) } }
        } else {
            listOf(CatalogType.movie, CatalogType.series).map { type ->
                async { runCatching { StremioProtocol.catalog(StremioProtocol.CINEMETA, type, "top", search = q) }.getOrDefault(emptyList()) }
            }
        }
        tasks.awaitAll().flatten().distinctBy { "${it.type}:${it.id}" }
    }

    // ---- Meta -----------------------------------------------------------------

    private val metaCache = HashMap<String, MetaDetail>()

    suspend fun meta(type: CatalogType, id: String): MetaDetail? {
        val key = "$type:$id"
        cacheLock.withLock { metaCache[key] }?.let { return it }
        // Your meta addons that claim this type/id, in your order (Stremio's
        // rule); Cinemeta as the fallback if none of them answer.
        val sources = addonStore.get().filter { it.supports("meta", type, id) }.map { it.url } +
            StremioProtocol.CINEMETA
        val detail = sources.distinct().firstNotNullOfOrNull { url ->
            runCatching { StremioProtocol.meta(type, id, url) }.getOrNull()
        }
        if (detail != null) cacheLock.withLock { metaCache[key] = detail }
        return detail
    }

    // ---- Streams --------------------------------------------------------------

    /**
     * Direct-URL streams play on the phone as-is (with any headers the addon
     * asks for). Torrent (infoHash) streams go through the living-room PC:
     * Nexus there runs its Stremio torrent engine and proxies it to the phone.
     */
    suspend fun streams(type: CatalogType, id: String): StreamResult = coroutineScope {
        val all = addonStore.get()
        val streamAddons = all.filter { it.supports("stream", type, id) }
        if (streamAddons.isEmpty()) {
            return@coroutineScope StreamResult(emptyList(), all.any { "stream" in it.resources }, emptyList(), null)
        }

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
                    raw.url != null -> StreamSource.Direct(raw.url, raw.headers)
                    raw.infoHash != null -> StreamSource.Torrent(raw.infoHash.lowercase(), raw.fileIdx)
                    raw.externalUrl != null -> StreamSource.External(raw.externalUrl)
                    else -> continue
                }
                val playable = when (source) {
                    is StreamSource.Direct -> source.url
                    is StreamSource.Torrent ->
                        if (torrentBlocked == null) pc.torrentStreamUrl(pcConfig, source.infoHash, source.fileIdx) else null
                    is StreamSource.External -> null
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

    /** Your subtitle addons (that claim this id) + OpenSubtitles unless you already have it installed. */
    suspend fun subtitles(type: CatalogType, id: String): List<SubtitleTrack> = coroutineScope {
        val mine = addonStore.get().filter { it.supports("subtitles", type, id) }.map { it.name to it.url }
        val sources = if (mine.any { it.second.startsWith(StremioProtocol.OPENSUBTITLES) }) mine
        else listOf("OpenSubtitles" to StremioProtocol.OPENSUBTITLES) + mine
        sources.map { (label, url) ->
            async { runCatching { StremioProtocol.subtitles(url, label, type, id) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    // ---- Addons ---------------------------------------------------------------

    private var communityCache: List<CommunityAddon>? = null

    suspend fun communityAddons(): List<CommunityAddon> =
        communityCache ?: StremioProtocol.communityAddons().also { communityCache = it }

    suspend fun installAddon(url: String): Addon {
        val addon = StremioProtocol.manifest(url)
        addonStore.update { list -> list.filterNot { it.url == addon.url } + addon }
        clearCaches()
        return addon
    }

    suspend fun removeAddon(url: String) {
        addonStore.update { list -> list.filterNot { it.url == url } }
        clearCaches()
    }

    suspend fun moveAddon(url: String, up: Boolean) = addonStore.update { list ->
        val i = list.indexOfFirst { it.url == url }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Copies the PC's addon list. Keeps phone-only addons, PC ones first in the PC's order. */
    suspend fun importAddonsFromPc(): Int {
        val fromPc = pc.addons().map { it.copy(url = StremioProtocol.normalizeUrl(it.url)) }
        addonStore.update { mine -> fromPc + mine.filterNot { m -> fromPc.any { it.url == m.url } } }
        clearCaches()
        return fromPc.size
    }

    private suspend fun clearCaches() = cacheLock.withLock { metaCache.clear() }

    // ---- Stremio account --------------------------------------------------------

    suspend fun stremioLogin(email: String, password: String): StremioImportResult {
        val authKey = StremioAccount.login(email, password)
        accountStore.set(StremioAccountState(authKey = authKey, email = email.trim()))
        return stremioSync()
    }

    /** For people who'd rather not type their password: the auth key from a
     * logged-in Stremio Web session (localStorage "profile" -> auth.key). */
    suspend fun stremioLinkWithKey(authKey: String): StremioImportResult {
        accountStore.set(StremioAccountState(authKey = authKey.trim().trim('"')))
        return stremioSync()
    }

    /**
     * Mirrors the account: addons replaced with the account's collection (in
     * its order -- addons you added only on the phone are kept, after them),
     * library merged in, and watch progress taken from Stremio wherever it's
     * newer than what the phone has.
     */
    suspend fun stremioSync(): StremioImportResult {
        val state = accountStore.get()
        require(state.isLinked) { "Not linked to a Stremio account" }
        val (addons, skipped) = StremioAccount.addons(state.authKey)
        addonStore.update { mine ->
            addons + mine.filter { m -> !m.fromStremio && addons.none { it.url == m.url } }
        }

        val library = StremioAccount.library(state.authKey)
        val saved = library.filter { it.inLibrary }.map { it.item }
        libraryStore.update { mine -> (saved + mine).distinctBy { "${it.type}:${it.id}" } }

        val incoming = library.mapNotNull { it.progress }
        progressStore.update { mine ->
            val merged = mine.toMutableMap()
            for (p in incoming) {
                val k = key(p.type, p.id)
                val existing = merged[k]
                if (existing == null || p.updatedAt > existing.updatedAt) merged[k] = p
            }
            merged
        }
        accountStore.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
        clearCaches()
        return StremioImportResult(addons.size, skipped, saved.size, incoming.count { it.isResumable || it.episodeId != null })
    }

    suspend fun stremioUnlink() = accountStore.set(StremioAccountState())

    // ---- Progress + library -----------------------------------------------------

    private fun key(type: CatalogType, id: String) = "${type.name}:$id"

    fun progressFor(type: CatalogType, id: String): WatchProgress? = progressStore.value[key(type, id)]

    suspend fun saveProgress(entry: WatchProgress) = progressStore.update { it + (key(entry.type, entry.id) to entry) }

    suspend fun clearProgress(type: CatalogType, id: String) = progressStore.update { it - key(type, id) }

    fun continueWatching(all: Map<String, WatchProgress>): List<WatchProgress> =
        all.values.filter { it.isResumable || it.episodeId != null }.sortedByDescending { it.updatedAt }.take(25)

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
