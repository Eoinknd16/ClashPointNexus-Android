package com.eoinknd16.nexus.ai

import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.HttpException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Your own Claude API key and model choice. Stored only on this phone. */
@Serializable
data class AiPrefs(
    val apiKey: String = "",
    val model: String = "claude-haiku-5-5",
    /** "device" = the phone's built-in Gemini Nano, "claude" = your Claude API key. */
    val engine: String = "device",
) {
    val claudeReady: Boolean get() = apiKey.isNotBlank()
    /** Whether Ask Nexus should appear (the on-device check happens when asking). */
    val ready: Boolean get() = if (engine == "claude") claudeReady else true
}

/** Something the assistant decided to do; Nexus carries it out (power actions ask first). */
sealed interface AiAction {
    data class OpenApp(val name: String) : AiAction
    data class Pc(val action: String) : AiAction
    data class Page(val page: String) : AiAction
    data class Phone(val action: String) : AiAction
    data class Web(val query: String) : AiAction
    data class Nexus(val screen: String) : AiAction
    /** Play / continue a movie or show from the Nexus TV section ([where] = "phone" or "tv"). */
    data class PlayMedia(val title: String, val where: String) : AiAction
    /** Play music on Spotify. [type]: song, artist, album, playlist or any. */
    data class PlayMusic(val query: String, val type: String) : AiAction
    /** "Play X" when it's unclear whether X is a show/film or music: Nexus checks both. */
    data class PlayAnything(val title: String, val where: String) : AiAction
}

data class AiReply(val text: String, val actions: List<AiAction>)

/**
 * "Ask Nexus": sends one request to Claude with a small set of tools that map
 * onto things Nexus can already do (open an app, control the PC, change page,
 * phone toggles, web search, open a Nexus screen). The reply's tool calls come
 * back as [AiAction]s; nothing runs on the PC or phone without going through
 * Nexus's own code.
 */
object NexusAssistant {
    private const val URL = "https://api.anthropic.com/v1/messages"

    suspend fun ask(prefs: AiPrefs, question: String, apps: List<String>, pages: List<String>, pcs: List<String>): AiReply {
        if (prefs.engine != "claude" || !prefs.claudeReady) return OnDeviceAi.ask(question, apps, pages, pcs)
        val system = buildString {
            appendLine("You are Nexus, the assistant built into an Android launcher. Be brief: one or two short sentences.")
            appendLine("When the user wants something done, call the tools (several if needed) and say in a few words what you did.")
            appendLine("If they just ask a question, answer it directly without tools.")
            appendLine("Installed apps: ${apps.take(150).joinToString(", ")}")
            if (pages.isNotEmpty()) appendLine("Home pages: main, ${pages.joinToString(", ")}")
            if (pcs.isNotEmpty()) appendLine("Linked PCs: ${pcs.joinToString(", ")} (actions apply to the active one)")
        }
        val body = buildJsonObject {
            put("model", prefs.model)
            put("max_tokens", 400)
            put("system", system)
            put("tools", tools())
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", question) })
            }
        }
        val raw = try {
            Http.postJson(
                URL, body.toString(),
                mapOf("x-api-key" to prefs.apiKey.trim(), "anthropic-version" to "2023-06-01"),
                timeoutMs = 30_000,
            )
        } catch (e: HttpException) {
            throw IllegalStateException(
                when (e.code) {
                    401, 403 -> "The API key was rejected. Check it in Nexus settings."
                    404 -> "That model isn't available on your key. Try another in Nexus settings."
                    429 -> "Rate limited. Try again in a moment."
                    else -> "Claude returned an error (${e.code})."
                },
            )
        }
        val root = Http.json.parseToJsonElement(raw).jsonObject
        val content = root["content"] as? JsonArray ?: JsonArray(emptyList())
        val text = StringBuilder()
        val actions = mutableListOf<AiAction>()
        content.forEach { el ->
            val block = el as? JsonObject ?: return@forEach
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> text.append(block["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "tool_use" -> {
                    val input = block["input"] as? JsonObject ?: JsonObject(emptyMap())
                    fun arg(k: String) = input[k]?.jsonPrimitive?.contentOrNull.orEmpty()
                    when (block["name"]?.jsonPrimitive?.contentOrNull) {
                        "open_app" -> actions += AiAction.OpenApp(arg("name"))
                        "pc" -> actions += AiAction.Pc(arg("action"))
                        "go_to_page" -> actions += AiAction.Page(arg("page"))
                        "phone" -> actions += AiAction.Phone(arg("action"))
                        "web_search" -> actions += AiAction.Web(arg("query"))
                        "open_nexus" -> actions += AiAction.Nexus(arg("screen"))
                        "play_media" -> actions += AiAction.PlayMedia(arg("title"), arg("where"))
                        "play_music" -> actions += AiAction.PlayMusic(arg("query"), arg("type").ifBlank { "any" })
                        "play_anything" -> actions += AiAction.PlayAnything(arg("title"), arg("where"))
                    }
                }
            }
        }
        return AiReply(text.toString().trim(), actions)
    }

    private fun tools() = buildJsonArray {
        fun tool(name: String, description: String, prop: String, propDesc: String, options: List<String>? = null) = add(
            buildJsonObject {
                put("name", name)
                put("description", description)
                putJsonObject("input_schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject(prop) {
                            put("type", "string")
                            put("description", propDesc)
                            if (options != null) putJsonArray("enum") { options.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive(prop)) }
                }
            },
        )
        tool("open_app", "Open an installed app on the phone.", "name", "The app's name, as listed")
        tool(
            "pc", "Control the user's PC running Nexus.", "action", "What to do",
            listOf("wake", "sleep", "restart", "shutdown", "open_remote", "open_tv", "quick_menu"),
        )
        tool("go_to_page", "Switch the launcher's home page.", "page", "\"main\", \"next\", \"prev\" or a page name")
        tool(
            "phone", "Phone shortcuts.", "action", "What to open",
            listOf("notifications", "quick_settings", "lock_screen", "wifi", "bluetooth", "display", "sound", "battery", "settings"),
        )
        add(
            buildJsonObject {
                put("name", "play_media")
                put("description", "Play or continue a MOVIE or TV SHOW (the next episode for series) from the user's Nexus TV section. Only for video: never songs, artists or albums.")
                putJsonObject("input_schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "string"); put("description", "Movie or show name") }
                        putJsonObject("where") {
                            put("type", "string"); put("description", "phone, or tv for the living-room PC")
                            putJsonArray("enum") { add(kotlinx.serialization.json.JsonPrimitive("phone")); add(kotlinx.serialization.json.JsonPrimitive("tv")) }
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("title")) }
                }
            },
        )
        add(
            buildJsonObject {
                put("name", "play_music")
                put(
                    "description",
                    "Play MUSIC on Spotify: a song, an artist, an album, one of the user's playlists, or \"liked songs\". " +
                        "Use this when they mention a song/track/album/playlist/artist/band/music, say \"listen to\", \"by <artist>\" or \"on Spotify\", " +
                        "or name something that is clearly a song or musician.",
                )
                putJsonObject("input_schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "string"); put("description", "What to search for, e.g. \"mr brightside the killers\", \"gym\", \"liked songs\"") }
                        putJsonObject("type") {
                            put("type", "string"); put("description", "What kind of thing it is, if known")
                            putJsonArray("enum") { listOf("song", "artist", "album", "playlist", "any").forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
                }
            },
        )
        add(
            buildJsonObject {
                put("name", "play_anything")
                put("description", "\"Play <name>\" when you genuinely can't tell if <name> is a show/film or music. Nexus checks the TV section and Spotify and plays the better match.")
                putJsonObject("input_schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "string"); put("description", "The name they said") }
                        putJsonObject("where") {
                            put("type", "string"); put("description", "phone, or tv if they said on the TV")
                            putJsonArray("enum") { add(kotlinx.serialization.json.JsonPrimitive("phone")); add(kotlinx.serialization.json.JsonPrimitive("tv")) }
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("title")) }
                }
            },
        )
        tool("web_search", "Search the web in the browser. Never use this to play a show, film or song.", "query", "Search terms")
        tool(
            "open_nexus", "Open part of the Nexus launcher.", "screen", "Which part",
            listOf("tv", "customise", "settings", "app_drawer", "pc_controls"),
        )
    }
}
