package com.eoinknd16.nexus.media

import com.eoinknd16.nexus.core.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLEncoder

/**
 * The open Stremio addon protocol, ported from the PC build's
 * src/main/stremio/{cinemeta,addonManifest,addonCatalog,addonMeta,
 * streamAddons,streamId,streamMeta}.ts. Plain HTTP + JSON, so it runs the
 * same on the phone; parsing is deliberately tolerant (manual JsonObject
 * walking rather than strict DTOs) because third-party addons vary wildly.
 */
object StremioProtocol {
    const val CINEMETA = "https://v3-cinemeta.strem.io"
    const val OPENSUBTITLES = "https://opensubtitles-v3.strem.io"
    private const val COLLECTION_URL = "https://api.strem.io/addonscollection.json"

    /** Accepts either an addon's base URL or its full manifest.json URL. */
    fun normalizeUrl(url: String): String =
        url.trim().removeSuffix("/manifest.json").removeSuffix("/")
            .replace(Regex("^stremio://"), "https://")

    /** Episode ids look like "tt123:1:4" -- encode each part, keep the colons. */
    fun encodeId(id: String): String = id.split(":").joinToString(":") { enc(it) }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- JSON helpers -------------------------------------------------------

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    private fun JsonObject.arr(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private suspend fun getJson(url: String, addonHeaders: Boolean, timeoutMs: Long = 10_000): JsonElement =
        Http.json.parseToJsonElement(Http.getText(url, if (addonHeaders) Http.ADDON_HEADERS else emptyMap(), timeoutMs))

    private fun parseItem(meta: JsonObject, fallbackType: CatalogType): CatalogItem? {
        val id = meta.str("id") ?: return null
        val name = meta.str("name") ?: return null
        val type = when (meta.str("type")) {
            "movie" -> CatalogType.movie
            "series" -> CatalogType.series
            else -> fallbackType
        }
        return CatalogItem(
            id = id,
            type = type,
            name = name,
            poster = meta.str("poster"),
            background = meta.str("background"),
            description = meta.str("description"),
            year = meta.str("year") ?: meta.str("releaseInfo"),
            genres = meta.strList("genres").ifEmpty { meta.strList("genre") },
            imdbRating = meta.str("imdbRating"),
        )
    }

    // ---- Catalogs -----------------------------------------------------------

    /** /catalog/{type}/{id}[/extra].json against Cinemeta or any catalog addon. */
    suspend fun catalog(
        baseUrl: String,
        type: CatalogType,
        catalogId: String,
        genre: String? = null,
        search: String? = null,
        skip: Int = 0,
        addonHeaders: Boolean = baseUrl != CINEMETA,
    ): List<CatalogItem> {
        val extras = buildList {
            if (search != null) add("search=${enc(search)}")
            if (genre != null) add("genre=${enc(genre)}")
            if (skip > 0) add("skip=$skip")
        }
        val suffix = if (extras.isEmpty()) "" else "/" + extras.joinToString("&")
        val url = "${normalizeUrl(baseUrl)}/catalog/${type.name}/${enc(catalogId)}$suffix.json"
        val root = getJson(url, addonHeaders).jsonObject
        return root.arr("metas").mapNotNull { parseItem(it, type) }
    }

    // ---- Meta ---------------------------------------------------------------

    suspend fun meta(type: CatalogType, id: String, baseUrl: String = CINEMETA): MetaDetail? {
        val url = "${normalizeUrl(baseUrl)}/meta/${type.name}/${enc(id)}.json"
        val meta = (getJson(url, baseUrl != CINEMETA, 8_000).jsonObject["meta"] as? JsonObject) ?: return null
        val item = parseItem(meta, type) ?: return null
        val episodes = meta.arr("videos").mapNotNull { v ->
            val vid = v.str("id") ?: return@mapNotNull null
            val season = v.int("season") ?: return@mapNotNull null
            val episode = v.int("episode") ?: v.int("number") ?: return@mapNotNull null
            Episode(
                id = vid,
                season = season,
                episode = episode,
                name = v.str("name") ?: v.str("title") ?: "Episode $episode",
                overview = v.str("overview") ?: v.str("description"),
                thumbnail = v.str("thumbnail"),
                released = v.str("released"),
            )
        }.distinctBy { it.id }.sortedWith(compareBy<Episode>({ seasonSortKey(it.season) }, { it.episode }))
        return MetaDetail(
            item = item,
            runtime = meta.str("runtime"),
            cast = meta.strList("cast"),
            director = meta.strList("director"),
            released = meta.str("released"),
            episodes = episodes,
        )
    }

    // ---- Manifests / addon store -------------------------------------------

    suspend fun manifest(addonUrl: String): Addon {
        val base = normalizeUrl(addonUrl)
        val m = getJson("$base/manifest.json", true, 8_000).jsonObject
        return Addon(
            name = m.str("name") ?: runCatching { URI(base).host }.getOrNull() ?: base,
            url = base,
            resources = resourceNames(m),
            catalogs = m.arr("catalogs").mapNotNull { c ->
                val type = when (c.str("type")) {
                    "movie" -> CatalogType.movie
                    "series" -> CatalogType.series
                    else -> return@mapNotNull null // channel/tv/etc. not supported, same as PC
                }
                // Catalogs that *require* an extra (e.g. search-only) can't render as a plain row.
                val required = c.arr("extra").any { e ->
                    (e["isRequired"] as? JsonPrimitive)?.contentOrNull == "true"
                } || c.strList("extraRequired").isNotEmpty()
                if (required) return@mapNotNull null
                val id = c.str("id") ?: return@mapNotNull null
                AddonCatalogRef(type, id, c.str("name") ?: id)
            },
            logo = m.str("logo"),
        )
    }

    private fun resourceNames(manifest: JsonObject): List<String> =
        (manifest["resources"] as? JsonArray)?.mapNotNull { r ->
            when (r) {
                is JsonPrimitive -> r.contentOrNull
                is JsonObject -> r.str("name")
                else -> null
            }
        } ?: emptyList()

    /** Stremio's own public addon collection -- same source as the PC's Addon Store. */
    suspend fun communityAddons(): List<CommunityAddon> {
        val root = getJson(COLLECTION_URL, false, 15_000).jsonArray
        return root.mapNotNull { e ->
            val entry = e as? JsonObject ?: return@mapNotNull null
            val transport = entry.str("transportUrl") ?: return@mapNotNull null
            val m = entry["manifest"] as? JsonObject ?: return@mapNotNull null
            CommunityAddon(
                id = m.str("id") ?: return@mapNotNull null,
                name = m.str("name") ?: return@mapNotNull null,
                description = m.str("description") ?: "",
                logo = m.str("logo"),
                types = m.strList("types"),
                resources = resourceNames(m),
                transportUrl = normalizeUrl(transport),
            )
        }.distinctBy { it.transportUrl }
    }

    // ---- Streams ------------------------------------------------------------

    data class RawStream(val title: String, val url: String?, val infoHash: String?, val fileIdx: Int?)

    suspend fun streams(addonUrl: String, type: CatalogType, id: String): List<RawStream> {
        val url = "${normalizeUrl(addonUrl)}/stream/${type.name}/${encodeId(id)}.json"
        val root = getJson(url, true, 12_000).jsonObject
        return root.arr("streams").map { s ->
            // Addons put the useful release text in either field; join both like Stremio does.
            val name = s.str("name")
            val title = s.str("title") ?: s.str("description")
            RawStream(
                title = listOfNotNull(name, title).joinToString("\n").ifBlank { "Stream" },
                url = s.str("url"),
                infoHash = s.str("infoHash"),
                fileIdx = s.int("fileIdx"),
            )
        }
    }

    // ---- Subtitles ----------------------------------------------------------

    suspend fun subtitles(baseUrl: String, label: String, type: CatalogType, id: String): List<SubtitleTrack> {
        val url = "${normalizeUrl(baseUrl)}/subtitles/${type.name}/${encodeId(id)}.json"
        val root = getJson(url, baseUrl != OPENSUBTITLES, 8_000).jsonObject
        return root.arr("subtitles").mapNotNull { s ->
            SubtitleTrack(
                id = "$label:${s.str("id") ?: return@mapNotNull null}",
                lang = s.str("lang") ?: "und",
                url = s.str("url") ?: return@mapNotNull null,
            )
        }
    }
}

/** Best-effort release-title parsing, ported from PC src/main/stremio/streamMeta.ts. */
object StreamMeta {
    private val RESOLUTIONS = listOf(
        Regex("2160p|\\b4k\\b", RegexOption.IGNORE_CASE) to "4K",
        Regex("1440p", RegexOption.IGNORE_CASE) to "1440p",
        Regex("1080p", RegexOption.IGNORE_CASE) to "1080p",
        Regex("720p", RegexOption.IGNORE_CASE) to "720p",
        Regex("480p", RegexOption.IGNORE_CASE) to "480p",
    )

    fun resolution(text: String): String? = RESOLUTIONS.firstOrNull { it.first.containsMatchIn(text) }?.second

    private val LANGUAGE_CODES = mapOf(
        "eng" to "English", "fre" to "French", "fra" to "French", "ger" to "German", "deu" to "German",
        "ita" to "Italian", "spa" to "Spanish", "cze" to "Czech", "hun" to "Hungarian", "pol" to "Polish",
        "hin" to "Hindi", "jpn" to "Japanese", "rus" to "Russian", "ukr" to "Ukrainian", "kor" to "Korean",
        "chi" to "Chinese", "ara" to "Arabic", "por" to "Portuguese", "dut" to "Dutch", "nld" to "Dutch",
        "gre" to "Greek", "tur" to "Turkish", "heb" to "Hebrew", "swe" to "Swedish",
    )

    fun languages(text: String): List<String> {
        val found = linkedSetOf<String>()
        Regex("\\b[A-Za-z]{3}\\b").findAll(text).forEach { m ->
            LANGUAGE_CODES[m.value.lowercase()]?.let(found::add)
        }
        if (Regex("[\\u0400-\\u04FF]").containsMatchIn(text)) found.add("Russian")
        return found.toList()
    }
}
