package com.eoinknd16.nexus.ai

import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ask Nexus using the phone's built-in Gemini Nano (Android AICore via ML Kit's
 * Prompt API). Runs entirely on the phone: free, private, works offline. Only
 * on supported phones (Galaxy S25/S26, recent Pixels, …).
 */
object OnDeviceAi {
    enum class State(val label: String) {
        Checking("Checking…"), Unavailable("Not supported on this phone"), Downloadable("Ready to download"),
        Downloading("Downloading…"), Available("Ready"),
    }

    private val _state = MutableStateFlow(State.Checking)
    val state: StateFlow<State> = _state
    private val _progress = MutableStateFlow(0L)
    /** Bytes downloaded so far while [State.Downloading]. */
    val progress: StateFlow<Long> = _progress

    private val model by lazy { Generation.getClient() }

    suspend fun refresh(): State {
        val s = runCatching {
            when (model.checkStatus()) {
                com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> State.Available
                com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE -> State.Downloadable
                com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING -> State.Downloading
                else -> State.Unavailable
            }
        }.getOrDefault(State.Unavailable)
        _state.value = s
        return s
    }

    /** Fetch the model (a one-off download managed by Android). */
    suspend fun download(): Boolean {
        _state.value = State.Downloading
        var ok = false
        runCatching {
            model.download().collect { st ->
                when (st) {
                    is com.google.mlkit.genai.common.DownloadStatus.DownloadProgress -> _progress.value = st.totalBytesDownloaded
                    com.google.mlkit.genai.common.DownloadStatus.DownloadCompleted -> ok = true
                    is com.google.mlkit.genai.common.DownloadStatus.DownloadFailed -> ok = false
                    else -> {}
                }
            }
        }
        refresh()
        return ok
    }

    suspend fun ask(question: String, apps: List<String>, pages: List<String>, pcs: List<String>): AiReply {
        if (refresh() != State.Available) throw IllegalStateException("The phone's AI isn't ready. Set it up in Nexus settings › Ask Nexus.")
        val prompt = buildString {
            appendLine("You are Nexus, the assistant inside an Android launcher. Decide what the user wants and reply with ONLY a JSON object, no other text:")
            appendLine("""{"say": "<one short sentence>", "actions": [{"tool": "<tool>", "arg": "<argument>"}]}""")
            appendLine("Tools:")
            appendLine("- open_app: arg = app name from the list below")
            appendLine("- pc: arg = one of wake, sleep, restart, shutdown, open_remote, open_tv, quick_menu")
            appendLine("- go_to_page: arg = main, next, prev or a page name")
            appendLine("- phone: arg = one of notifications, quick_settings, lock_screen, wifi, bluetooth, display, sound, battery, settings")
            appendLine("- web_search: arg = search terms")
            appendLine("- open_nexus: arg = one of tv, customise, settings, app_drawer, pc_controls")
            appendLine("Use an empty actions list if the user only asked a question; then answer it in say.")
            appendLine("Apps: ${apps.take(120).joinToString(", ")}")
            if (pages.isNotEmpty()) appendLine("Pages: main, ${pages.joinToString(", ")}")
            if (pcs.isNotEmpty()) appendLine("PCs: ${pcs.joinToString(", ")}")
            appendLine()
            appendLine("User: $question")
            append("JSON:")
        }
        val response = model.generateContent(prompt)
        val text = response.candidates.firstOrNull()?.text.orEmpty()
        return parse(text)
    }

    /** Pull the JSON out of the reply, tolerating extra text or code fences around it. */
    private fun parse(raw: String): AiReply {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return AiReply(raw.trim().ifBlank { "Sorry, I didn't get that." }, emptyList())
        val obj = runCatching { com.eoinknd16.nexus.core.Http.json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }.getOrNull()
            ?: return AiReply(raw.trim(), emptyList())
        val say = obj["say"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val actions = (obj["actions"] as? JsonArray).orEmpty().mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            val tool = a["tool"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val arg = a["arg"]?.jsonPrimitive?.contentOrNull.orEmpty()
            when (tool) {
                "open_app" -> AiAction.OpenApp(arg)
                "pc" -> AiAction.Pc(arg)
                "go_to_page" -> AiAction.Page(arg)
                "phone" -> AiAction.Phone(arg)
                "web_search" -> AiAction.Web(arg)
                "open_nexus" -> AiAction.Nexus(arg)
                else -> null
            }
        }
        return AiReply(say, actions)
    }
}
