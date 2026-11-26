package com.eoinknd16.nexus.media

import com.eoinknd16.nexus.core.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/** Saved after logging in. Only the auth key is kept -- never the password. */
@Serializable
data class StremioAccountState(
    val authKey: String = "",
    val email: String = "",
    val lastSyncAt: Long = 0,
) {
    val isLinked: Boolean get() = authKey.isNotBlank()
}

/** What an import brought in, for the summary line in Settings. */
data class StremioImportResult(
    val addons: Int,
    val skippedAddons: List<String>,
    val libraryItems: Int,
    val inProgress: Int,
)

/**
 * Stremio's account API (api.strem.io) -- the same calls the official apps
 * make: log in for an auth key, read the account's installed addon
 * collection (in the user's own order, each with its full manifest), and
 * read the library, whose items carry watch state (position, duration,
 * last-watched episode) -- that's where Continue Watching comes from.
 */
object StremioAccount {
    private const val API = "https://api.strem.io/api"

    class StremioApiException(message: String) : Exception(message)

    private suspend fun call(method: String, body: JsonObject): JsonObject {
        val raw = Http.postJson("$API/$method", body.toString(), timeoutMs = 20_000)
        val root = Http.json.parseToJsonElement(raw).jsonObject
        (root["error"] as? JsonObject)?.let { err ->
            throw StremioApiException((err["message"] as? JsonPrimitive)?.contentOrNull ?: "Stremio API error")
        }
        return root
    }

    suspend fun login(email: String, password: String): String {
        val root = call(
            "login",
            buildJsonObject {
                put("type", "Login")
                put("email", email.trim())
                put("password", password)
                put("facebook", false)
            },
        )
        val result = root["result"] as? JsonObject ?: throw StremioApiException("Login failed")
        return (result["authKey"] as? JsonPrimitive)?.contentOrNull ?: throw StremioApiException("No auth key returned")
    }

    /** Installed addons in the account's order. Skips ones that can't work on
     * a phone: Stremio's "local files" addon (lives on the desktop app's own
     * localhost server) and anything else pointing at localhost. */
    suspend fun addons(authKey: String): Pair<List<Addon>, List<String>> {
        val root = call(
            "addonCollectionGet",
            buildJsonObject {
                put("type", "AddonCollectionGet")
                put("authKey", authKey)
                put("update", true)
            },
        )
        val list = ((root["result"] as? JsonObject)?.get("addons") as? JsonArray) ?: return emptyList<Addon>() to emptyList()
        val skipped = mutableListOf<String>()
        val addons = list.mapNotNull { e ->
            val entry = e as? JsonObject ?: return@mapNotNull null
            val transport = (entry["transportUrl"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val manifest = entry["manifest"] as? JsonObject ?: return@mapNotNull null
            val name = (manifest["name"] as? JsonPrimitive)?.contentOrNull ?: transport
            if (Regex("^https?://(127\\.0\\.0\\.1|localhost)", RegexOption.IGNORE_CASE).containsMatchIn(transport)) {
                skipped += name
                return@mapNotNull null
            }
            StremioProtocol.parseManifest(manifest, transport, fromStremio = true)
        }.distinctBy { it.url }
        return addons to skipped
    }

    /** [inLibrary] false = only watched ("temp") or removed: feeds Continue Watching, not the Library grid. */
    data class LibraryEntry(val item: CatalogItem, val inLibrary: Boolean, val progress: WatchProgress?)

    suspend fun library(authKey: String): List<LibraryEntry> {
        val root = call(
            "datastoreGet",
            buildJsonObject {
                put("authKey", authKey)
                put("collection", "libraryItem")
                put("all", true)
                putJsonArray("ids") {}
            },
        )
        val items = root["result"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            fun bool(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull == "true"
            val id = str("_id") ?: return@mapNotNull null
            val type = str("type")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = str("name") ?: return@mapNotNull null
            // "temp" = only watched, never added; "removed" = taken out of the library.
            val removed = bool("removed")
            val temp = bool("temp")
            val item = CatalogItem(id = id, type = CatalogType(type), name = name, poster = str("poster"))

            val state = o["state"] as? JsonObject
            fun stateNum(k: String) = (state?.get(k) as? JsonPrimitive)?.doubleOrNull
            fun stateStr(k: String) = (state?.get(k) as? JsonPrimitive)?.contentOrNull
            val offsetMs = stateNum("timeOffset") ?: 0.0
            val durationMs = stateNum("duration")
            val videoId = stateStr("video_id")?.takeIf { it.isNotBlank() && it != id }
            val lastWatched = stateStr("lastWatched")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: str("_mtime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L
            // Episode ids look like tt123:1:4 -> season 1, episode 4.
            val parts = videoId?.split(":")
            val season = parts?.getOrNull(parts.size - 2)?.toIntOrNull()
            val episode = parts?.lastOrNull()?.toIntOrNull()
            val progress = if (offsetMs > 0 || videoId != null) {
                WatchProgress(
                    type = CatalogType(type),
                    id = id,
                    name = name,
                    poster = item.poster,
                    positionSeconds = offsetMs / 1000.0,
                    durationSeconds = durationMs?.takeIf { it > 0 }?.div(1000.0),
                    season = if (videoId != null) season else null,
                    episode = if (videoId != null) episode else null,
                    episodeId = videoId,
                    updatedAt = lastWatched,
                )
            } else null

            if (removed && progress == null) return@mapNotNull null
            LibraryEntry(item = item, inLibrary = !removed && !temp, progress = progress)
        }
    }
}
