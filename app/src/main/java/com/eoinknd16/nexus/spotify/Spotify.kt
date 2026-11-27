package com.eoinknd16.nexus.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.HttpException
import com.eoinknd16.nexus.core.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Your own Spotify developer app's Client ID plus the sign-in tokens. Stored only
 * on this phone; no client secret is ever needed (PKCE sign-in).
 */
@Serializable
data class SpotifyPrefs(
    val clientId: String = "",
    val refreshToken: String = "",
    val accessToken: String = "",
    val expiresAt: Long = 0,
    val userId: String = "",
    val userName: String = "",
) {
    val connected: Boolean get() = refreshToken.isNotBlank()
}

data class SpTrack(
    val uri: String,
    val id: String,
    val name: String,
    val artists: String,
    val album: String,
    val albumUri: String?,
    val art: String?,
    val durationMs: Long,
)

data class SpCollection(
    val uri: String,
    val id: String,
    val name: String,
    val subtitle: String,
    val art: String?,
    val kind: String, // playlist, album, artist
    val ownedByYou: Boolean = false,
)

data class SpDevice(val id: String, val name: String, val type: String, val active: Boolean, val volume: Int?)

data class SpPlayer(
    val playing: Boolean,
    val progressMs: Long,
    val track: SpTrack?,
    val device: SpDevice?,
    val shuffle: Boolean,
    val repeat: String,
    val contextUri: String?,
    /** When this state was read, so progress can tick between polls. */
    val at: Long = System.currentTimeMillis(),
)

data class SpSearch(val tracks: List<SpTrack>, val collections: List<SpCollection>)

/**
 * The Spotify Web API, as Nexus uses it: sign-in, your library and the player.
 * Playback itself stays in the real Spotify app; this only remote-controls it.
 */
class Spotify(private val context: Context, val prefs: JsonStore<SpotifyPrefs>, private val scope: CoroutineScope) {

    companion object {
        const val PORT = 8898
        const val REDIRECT = "http://127.0.0.1:$PORT/callback"
        const val SPOTIFY_PKG = "com.spotify.music"
        private const val API = "https://api.spotify.com/v1"
        private val SCOPES = listOf(
            "user-read-private", "user-read-playback-state", "user-modify-playback-state", "user-read-currently-playing",
            "user-library-read", "user-library-modify", "playlist-read-private", "playlist-read-collaborative",
            "user-read-recently-played", "user-top-read",
        ).joinToString(" ")
    }

    private val _player = MutableStateFlow<SpPlayer?>(null)
    val player: StateFlow<SpPlayer?> = _player
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn

    // ---- Sign-in (PKCE + a one-shot loopback server for the redirect) ---------

    fun signIn(clientId: String) {
        val id = clientId.trim()
        if (id.isBlank()) { _error.value = "Paste your Client ID first"; return }
        scope.launch {
            prefs.update { it.copy(clientId = id) }
            _signingIn.value = true
            _error.value = null
            runCatching {
                val verifier = randomString(64)
                val challenge = Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                )
                val state = randomString(16)
                val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                    .appendQueryParameter("client_id", id)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", REDIRECT)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("scope", SCOPES)
                    .build()
                val code = withContext(Dispatchers.IO) {
                    ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                        server.soTimeout = 5 * 60_000
                        withContext(Dispatchers.Main) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        awaitCode(server, state)
                    }
                }
                val tokens = tokenRequest(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code").add("code", code)
                        .add("redirect_uri", REDIRECT).add("client_id", id).add("code_verifier", verifier).build(),
                )
                prefs.update { tokens.applyTo(it) }
                val me = get("/me")
                prefs.update { it.copy(userId = me.str("id").orEmpty(), userName = me.str("display_name") ?: me.str("id").orEmpty()) }
                // Back to Nexus, straight into the control centre.
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("nexus://spotify")).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                refreshPlayer()
            }.onFailure { _error.value = "Sign-in failed: ${it.message ?: it.javaClass.simpleName}" }
            _signingIn.value = false
        }
    }

    private fun awaitCode(server: ServerSocket, state: String): String {
        while (true) {
            server.accept().use { sock ->
                val line = sock.getInputStream().bufferedReader().readLine().orEmpty() // GET /callback?code=..&state=.. HTTP/1.1
                val path = line.split(" ").getOrNull(1).orEmpty()
                if (!path.startsWith("/callback")) {
                    sock.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return@use
                }
                val q = Uri.parse("http://x$path")
                val ok = q.getQueryParameter("code") != null && q.getQueryParameter("state") == state
                val html = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>body{background:#05070d;color:#e6f1fb;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh}
                    a{color:#05070d;background:#5dcaa5;padding:14px 28px;border-radius:10px;text-decoration:none;font-weight:bold;margin-top:20px}</style></head>
                    <body><h2>${if (ok) "Spotify connected" else "Sign-in didn't finish"}</h2>
                    <p>${if (ok) "You can go back to Nexus." else (q.getQueryParameter("error") ?: "Try again from Nexus.")}</p>
                    <a href="nexus://spotify">Back to Nexus</a></body></html>"""
                val bytes = html.toByteArray()
                sock.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                sock.getOutputStream().flush()
                q.getQueryParameter("error")?.let { error(if (it == "access_denied") "you cancelled" else it) }
                if (ok) return q.getQueryParameter("code")!!
            }
        }
    }

    fun signOut() = scope.launch {
        prefs.update { SpotifyPrefs(clientId = it.clientId) }
        _player.value = null
    }

    private data class Tokens(val access: String, val refresh: String?, val expiresIn: Long) {
        fun applyTo(p: SpotifyPrefs) = p.copy(
            accessToken = access,
            refreshToken = refresh ?: p.refreshToken,
            expiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000,
        )
    }

    private suspend fun tokenRequest(body: FormBody): Tokens = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        Http.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpException(r.code, runCatching { Http.json.parseToJsonElement(text).obj()?.str("error_description") }.getOrNull() ?: "HTTP ${r.code}")
            val o = Http.json.parseToJsonElement(text) as JsonObject
            Tokens(o.str("access_token")!!, o.str("refresh_token"), o.str("expires_in")?.toLongOrNull() ?: 3600)
        }
    }

    private val tokenLock = Mutex()
    private suspend fun token(): String = tokenLock.withLock {
        val p = prefs.get()
        if (!p.connected) error("Not connected to Spotify")
        if (p.accessToken.isNotBlank() && System.currentTimeMillis() < p.expiresAt) return p.accessToken
        val t = tokenRequest(
            FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", p.refreshToken).add("client_id", p.clientId).build(),
        )
        prefs.update { t.applyTo(it) }
        t.access
    }

    // ---- HTTP -----------------------------------------------------------------

    private suspend fun call(method: String, path: String, body: JsonElement? = null): JsonElement? = withContext(Dispatchers.IO) {
        suspend fun once(): Pair<Int, String> {
            val rb = when {
                body != null -> body.toString().toRequestBody("application/json".toMediaType())
                method == "GET" -> null
                else -> ByteArray(0).toRequestBody(null)
            }
            val req = Request.Builder().url(if (path.startsWith("http")) path else API + path)
                .header("Authorization", "Bearer ${token()}")
                .method(method, rb).build()
            return Http.client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        }
        var (code, text) = once()
        if (code == 401) { // token revoked/expired early: refresh once
            prefs.update { it.copy(accessToken = "", expiresAt = 0) }
            once().let { code = it.first; text = it.second }
        }
        if (code == 429) { delay(1500); once().let { code = it.first; text = it.second } }
        if (code !in 200..299) {
            val msg = runCatching { Http.json.parseToJsonElement(text).obj()?.obj("error")?.let { it.str("reason") ?: it.str("message") } }.getOrNull()
            throw HttpException(code, msg ?: "HTTP $code")
        }
        if (text.isBlank()) null else Http.json.parseToJsonElement(text)
    }

    private suspend fun get(path: String): JsonObject = call("GET", path)?.obj() ?: JsonObject(emptyMap())

    private fun friendly(e: Throwable): String = when {
        e is HttpException && e.code == 403 && e.message.orEmpty().contains("PREMIUM", true) -> "Spotify only allows remote control with Premium"
        e is HttpException && e.code == 403 -> "Spotify refused (${e.message}). Development apps need the owner on Premium, and you added as a user."
        e is HttpException && e.code == 404 -> "Spotify isn't active on any device. Open it once, then try again."
        else -> e.message ?: "Spotify error"
    }

    // ---- Library ----------------------------------------------------------------

    suspend fun liked(offset: Int = 0): List<SpTrack> =
        get("/me/tracks?limit=50&offset=$offset").arr("items").mapNotNull { (it.obj()?.get("track") ?: it.obj()?.get("item"))?.obj()?.let(::track) }

    suspend fun playlists(offset: Int = 0): List<SpCollection> {
        val me = prefs.value.userId
        return get("/me/playlists?limit=50&offset=$offset").arr("items").mapNotNull { it.obj() }.map { p ->
            val ownerId = p.obj("owner")?.str("id")
            val total = (p.obj("tracks") ?: p.obj("items"))?.str("total")
            SpCollection(
                uri = p.str("uri").orEmpty(), id = p.str("id").orEmpty(), name = p.str("name").orEmpty(),
                subtitle = listOfNotNull(p.obj("owner")?.str("display_name"), total?.let { "$it songs" }).joinToString(" · "),
                art = image(p), kind = "playlist", ownedByYou = ownerId == me || p.str("collaborative") == "true",
            )
        }
    }

    suspend fun albums(offset: Int = 0): List<SpCollection> =
        get("/me/albums?limit=50&offset=$offset").arr("items").mapNotNull { it.obj()?.obj("album") }.map(::album)

    /** Tracks of a playlist (Spotify only shares these for playlists you own or collaborate on). */
    suspend fun playlistTracks(id: String): List<SpTrack> {
        val out = mutableListOf<SpTrack>()
        var next: String? = "/playlists/$id/items?limit=100"
        var pages = 0
        while (next != null && pages++ < 10) {
            val page = runCatching { get(next) }.getOrElse { if (pages == 1) get("/playlists/$id/tracks?limit=100") else break }
            out += page.arr("items").mapNotNull { (it.obj()?.get("item") ?: it.obj()?.get("track"))?.obj()?.let(::track) }
            next = page.str("next")
        }
        return out
    }

    suspend fun albumTracks(id: String): List<SpTrack> {
        val a = get("/albums/$id")
        val art = image(a)
        return (a.obj("tracks")?.arr("items") ?: emptyList()).mapNotNull { it.obj() }.map { t ->
            track(t)!!.copy(album = a.str("name").orEmpty(), albumUri = a.str("uri"), art = art)
        }
    }

    suspend fun recent(): List<SpTrack> =
        get("/me/player/recently-played?limit=40").arr("items").mapNotNull { it.obj()?.obj("track")?.let(::track) }.distinctBy { it.id }

    suspend fun topTracks(): List<SpTrack> = get("/me/top/tracks?limit=20&time_range=short_term").arr("items").mapNotNull { it.obj()?.let(::track) }

    suspend fun queue(): List<SpTrack> = get("/me/player/queue").arr("queue").mapNotNull { it.obj()?.let(::track) }

    suspend fun search(q: String): SpSearch {
        val enc = Uri.encode(q)
        val r = get("/search?q=$enc&type=track,playlist,album,artist&limit=10")
        val tracks = r.obj("tracks")?.arr("items").orEmpty().mapNotNull { it.obj()?.let(::track) }
        val cols = r.obj("artists")?.arr("items").orEmpty().mapNotNull { it.obj() }.take(4).map { a ->
            SpCollection(a.str("uri").orEmpty(), a.str("id").orEmpty(), a.str("name").orEmpty(), "Artist", image(a), "artist")
        } + r.obj("albums")?.arr("items").orEmpty().mapNotNull { it.obj() }.map(::album) +
            r.obj("playlists")?.arr("items").orEmpty().mapNotNull { it.obj() }.map { p ->
                SpCollection(p.str("uri").orEmpty(), p.str("id").orEmpty(), p.str("name").orEmpty(), "Playlist · ${p.obj("owner")?.str("display_name").orEmpty()}", image(p), "playlist")
            }
        return SpSearch(tracks, cols)
    }

    suspend fun isLiked(track: SpTrack): Boolean = runCatching {
        call("GET", "/me/library/contains?uris=${Uri.encode(track.uri)}")
    }.recoverCatching { call("GET", "/me/tracks/contains?ids=${track.id}") }
        .getOrNull()?.let { (it as? JsonArray)?.firstOrNull()?.let { b -> (b as? JsonPrimitive)?.contentOrNull == "true" } } ?: false

    suspend fun setLiked(track: SpTrack, liked: Boolean) {
        val m = if (liked) "PUT" else "DELETE"
        runCatching { call(m, "/me/library?uris=${Uri.encode(track.uri)}") }
            .recoverCatching { call(m, "/me/tracks?ids=${track.id}") }
            .onFailure { _error.value = friendly(it) }
    }

    // ---- Player -------------------------------------------------------------------

    suspend fun refreshPlayer() {
        if (!prefs.value.connected) return
        runCatching { call("GET", "/me/player?additional_types=episode")?.obj() }
            .onSuccess { o ->
                _player.value = o?.let {
                    SpPlayer(
                        playing = it.str("is_playing") == "true",
                        progressMs = it.str("progress_ms")?.toLongOrNull() ?: 0,
                        track = it.obj("item")?.let(::track),
                        device = it.obj("device")?.let(::device),
                        shuffle = it.str("shuffle_state") == "true",
                        repeat = it.str("repeat_state") ?: "off",
                        contextUri = it.obj("context")?.str("uri"),
                    )
                }
                _error.value = null
            }
            .onFailure { _error.value = friendly(it) }
    }

    suspend fun devices(): List<SpDevice> = get("/me/player/devices").arr("devices").mapNotNull { it.obj()?.let(::device) }

    /** Runs a player command; on "no active device" it picks this phone (opening Spotify if needed) and retries. */
    private fun command(optimistic: (SpPlayer) -> SpPlayer = { it }, block: suspend (deviceId: String?) -> Unit) {
        _player.value = _player.value?.let(optimistic)
        scope.launch {
            runCatching { block(null) }.recoverCatching { e ->
                if (e !is HttpException || e.code != 404) throw e
                block(wakeDevice())
            }.onFailure { _error.value = friendly(it) }
            delay(350)
            refreshPlayer()
        }
    }

    private suspend fun wakeDevice(): String {
        devices().let { d -> (d.firstOrNull { it.type.equals("Smartphone", true) } ?: d.firstOrNull())?.let { return it.id } }
        // Spotify isn't running anywhere: start it on this phone, then wait for it to show up.
        context.packageManager.getLaunchIntentForPackage(SPOTIFY_PKG)?.let {
            withContext(Dispatchers.Main) { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } ?: error("Spotify isn't installed")
        repeat(16) {
            delay(500)
            devices().firstOrNull { it.type.equals("Smartphone", true) }?.let { return it.id }
        }
        error("Spotify didn't come online. Open it once, then try again.")
    }

    private fun dev(id: String?) = if (id != null) "?device_id=$id" else ""

    /** Play a playlist/album/artist, optionally starting at a track. */
    fun playContext(contextUri: String, startUri: String? = null, shuffle: Boolean = false) = command { d ->
        if (shuffle) runCatching { call("PUT", "/me/player/shuffle?state=true" + (d?.let { "&device_id=$it" } ?: "")) }
        call("PUT", "/me/player/play${dev(d)}", buildJsonObject {
            put("context_uri", contextUri)
            if (startUri != null && !contextUri.contains(":artist:")) putJsonObject("offset") { put("uri", startUri) }
        })
    }

    /** Play a list of tracks (e.g. search results), starting at [start]. */
    fun playTracks(uris: List<String>, start: Int = 0) = command { d ->
        call("PUT", "/me/player/play${dev(d)}", buildJsonObject {
            put("uris", buildJsonArray { uris.take(100).forEach { add(JsonPrimitive(it)) } })
            putJsonObject("offset") { put("position", start.coerceIn(0, 99)) }
        })
    }

    /** Liked Songs as a context (so next/previous run through your likes). */
    fun playLiked(start: SpTrack?, all: List<SpTrack>) {
        val user = prefs.value.userId
        if (user.isNotBlank()) command { d ->
            runCatching {
                call("PUT", "/me/player/play${dev(d)}", buildJsonObject {
                    put("context_uri", "spotify:user:$user:collection")
                    if (start != null) putJsonObject("offset") { put("uri", start.uri) }
                })
            }.getOrElse { e ->
                if (e is HttpException && e.code == 404) throw e
                val i = all.indexOf(start).coerceAtLeast(0)
                call("PUT", "/me/player/play${dev(d)}", buildJsonObject {
                    put("uris", buildJsonArray { all.drop(i).take(100).forEach { add(JsonPrimitive(it.uri)) } })
                })
            }
        } else playTracks(all.map { it.uri }, all.indexOf(start).coerceAtLeast(0))
    }

    fun togglePlay() {
        val playing = _player.value?.playing == true
        command({ it.copy(playing = !playing, progressMs = it.livePosition(), at = System.currentTimeMillis()) }) { d ->
            call("PUT", if (playing) "/me/player/pause${dev(d)}" else "/me/player/play${dev(d)}")
        }
    }

    fun next() = command { d -> call("POST", "/me/player/next${dev(d)}") }
    fun previous() = command { d -> call("POST", "/me/player/previous${dev(d)}") }
    fun seek(ms: Long) = command({ it.copy(progressMs = ms, at = System.currentTimeMillis()) }) { d ->
        call("PUT", "/me/player/seek?position_ms=$ms" + (d?.let { "&device_id=$it" } ?: ""))
    }
    fun setShuffle(on: Boolean) = command({ it.copy(shuffle = on) }) { call("PUT", "/me/player/shuffle?state=$on") }
    fun cycleRepeat() {
        val next = when (_player.value?.repeat) { "off" -> "context"; "context" -> "track"; else -> "off" }
        command({ it.copy(repeat = next) }) { call("PUT", "/me/player/repeat?state=$next") }
    }
    fun setVolume(pct: Int) = command({ it.copy(device = it.device?.copy(volume = pct)) }) {
        call("PUT", "/me/player/volume?volume_percent=${pct.coerceIn(0, 100)}")
    }
    fun addToQueue(t: SpTrack) = command { d -> call("POST", "/me/player/queue?uri=${Uri.encode(t.uri)}" + (d?.let { "&device_id=$it" } ?: "")) }
    fun transfer(device: SpDevice) = command { call("PUT", "/me/player", buildJsonObject { put("device_ids", buildJsonArray { add(JsonPrimitive(device.id)) }); put("play", true) }) }

    fun clearError() { _error.value = null }

    // ---- JSON -> models ---------------------------------------------------------------

    private fun track(o: JsonObject): SpTrack? {
        val uri = o.str("uri") ?: return null
        if (o.str("is_local") == "true" && o.str("id") == null) return null
        val album = o.obj("album")
        return SpTrack(
            uri = uri, id = o.str("id").orEmpty(), name = o.str("name").orEmpty(),
            artists = o.arr("artists").mapNotNull { it.obj()?.str("name") }.joinToString(", ").ifBlank { o.obj("show")?.str("name").orEmpty() },
            album = album?.str("name").orEmpty(), albumUri = album?.str("uri"),
            art = album?.let(::image) ?: image(o),
            durationMs = o.str("duration_ms")?.toLongOrNull() ?: 0,
        )
    }

    private fun album(a: JsonObject) = SpCollection(
        a.str("uri").orEmpty(), a.str("id").orEmpty(), a.str("name").orEmpty(),
        a.arr("artists").mapNotNull { it.obj()?.str("name") }.joinToString(", "), image(a), "album",
    )

    private fun device(o: JsonObject) = SpDevice(
        o.str("id").orEmpty(), o.str("name").orEmpty(), o.str("type").orEmpty(), o.str("is_active") == "true", o.str("volume_percent")?.toIntOrNull(),
    )

    /** Biggest image up to ~640px. */
    private fun image(o: JsonObject): String? = o.arr("images").mapNotNull { it.obj() }
        .sortedByDescending { it.str("width")?.toIntOrNull() ?: 0 }.firstOrNull()?.str("url")

    private fun randomString(n: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val r = SecureRandom()
        return String(CharArray(n) { chars[r.nextInt(chars.length)] })
    }
}

/** Position now, counting time since the state was read. */
fun SpPlayer.livePosition(now: Long = System.currentTimeMillis()): Long {
    val d = track?.durationMs ?: 0
    val p = if (playing) progressMs + (now - at) else progressMs
    return if (d > 0) p.coerceIn(0, d) else p
}

private fun JsonElement.obj(): JsonObject? = this as? JsonObject
private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
private fun JsonObject.arr(k: String): List<JsonElement> = (this[k] as? JsonArray) ?: emptyList()
private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
