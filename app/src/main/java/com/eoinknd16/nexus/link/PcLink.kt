package com.eoinknd16.nexus.link

import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.JsonStore
import com.eoinknd16.nexus.media.Addon
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.media.CatalogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URLEncoder

/** The paired living-room PC. Saved once after pairing. */
@Serializable
data class PcLinkConfig(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val token: String = "",
    val name: String = "",
    /** MAC addresses reported by the PC at pairing time, for wake-on-LAN. */
    val macs: List<String> = emptyList(),
) {
    val isPaired: Boolean get() = host.isNotBlank() && token.isNotBlank()
    val baseUrl: String get() = "http://$host:$port"

    companion object {
        /** Must match REMOTE_API_PORT in the PC build (src/main/remote/server.ts). */
        const val DEFAULT_PORT = 11480
    }
}

// ---- Wire types for the PC's remote API (src/main/remote/server.ts) --------

@Serializable
data class PcStatus(
    val name: String,
    val version: String,
    val screen: String? = null,
    val macs: List<String> = emptyList(),
    val nowPlaying: PcNowPlaying? = null,
)

@Serializable
data class PcNowPlaying(val title: String, val positionSeconds: Double? = null, val durationSeconds: Double? = null)

@Serializable
data class PairRequest(val code: String, val deviceName: String)

@Serializable
data class PairResponse(val token: String, val name: String, val macs: List<String> = emptyList())

@Serializable
data class AddonsResponse(val addons: List<Addon>)

@Serializable
data class TorrentServerStatus(val available: Boolean, val reason: String? = null)

@Serializable
data class CastRequest(
    val type: CatalogType,
    val item: CatalogItem,
    val episodeId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionSeconds: Double = 0.0,
    /** Played on the PC verbatim when it's a direct URL; for torrents the PC
     * resolves from infoHash/fileIdx against its own local server. */
    val directUrl: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
)

@Serializable
data class NavRequest(val action: String)

@Serializable
data class ScreenRequest(val screen: String)

@Serializable
data class OkResponse(val ok: Boolean = true, val error: String? = null)

/** HTTP client for the PC's remote API. Every call needs a paired config. */
class PcLinkClient(private val store: JsonStore<PcLinkConfig>) {

    private suspend fun config(): PcLinkConfig = store.get()

    private fun auth(c: PcLinkConfig) = mapOf("Authorization" to "Bearer ${c.token}")

    /** Pairs with the 6-digit code the PC shows in Settings > Phone Link. */
    suspend fun pair(host: String, port: Int, code: String, deviceName: String): PcLinkConfig {
        val base = "http://${host.trim()}:$port"
        val body = Http.json.encodeToString(PairRequest.serializer(), PairRequest(code.trim(), deviceName))
        val res = Http.json.decodeFromString(PairResponse.serializer(), Http.postJson("$base/api/pair", body, timeoutMs = 6_000))
        val config = PcLinkConfig(host = host.trim(), port = port, token = res.token, name = res.name, macs = res.macs)
        store.set(config)
        return config
    }

    suspend fun unpair() = store.set(PcLinkConfig())

    suspend fun status(): PcStatus {
        val c = config()
        val status = Http.json.decodeFromString(
            PcStatus.serializer(),
            Http.getText("${c.baseUrl}/api/status", auth(c), timeoutMs = 4_000),
        )
        // Keep wake-on-LAN targets fresh (a new network adapter, say).
        if (status.macs.isNotEmpty() && status.macs != c.macs) store.update { it.copy(macs = status.macs, name = status.name) }
        return status
    }

    suspend fun addons(): List<Addon> {
        val c = config()
        return Http.json.decodeFromString(
            AddonsResponse.serializer(),
            Http.getText("${c.baseUrl}/api/addons", auth(c), timeoutMs = 6_000),
        ).addons
    }

    /** Starts the PC's Stremio torrent engine if needed (it's lazy on the PC too). */
    suspend fun ensureTorrentServer(): TorrentServerStatus {
        val c = config()
        return Http.json.decodeFromString(
            TorrentServerStatus.serializer(),
            Http.postJson("${c.baseUrl}/api/stremio/ensure", "{}", auth(c), timeoutMs = 12_000),
        )
    }

    /** URL the phone's player can open for a torrent stream -- proxied through
     * Nexus on the PC (its Stremio server only listens on the PC itself).
     * Token goes in the query because the player fetches it directly. */
    fun torrentStreamUrl(c: PcLinkConfig, infoHash: String, fileIdx: Int): String =
        "${c.baseUrl}/stremio/$infoHash/$fileIdx?token=${URLEncoder.encode(c.token, "UTF-8")}"

    suspend fun cast(request: CastRequest): OkResponse = post("/api/cast", Http.json.encodeToString(CastRequest.serializer(), request))

    suspend fun nav(action: String): OkResponse = post("/api/nav", Http.json.encodeToString(NavRequest.serializer(), NavRequest(action)))

    suspend fun openScreen(screen: String): OkResponse =
        post("/api/screen", Http.json.encodeToString(ScreenRequest.serializer(), ScreenRequest(screen)))

    private suspend fun post(path: String, body: String): OkResponse {
        val c = config()
        return Http.json.decodeFromString(OkResponse.serializer(), Http.postJson("${c.baseUrl}$path", body, auth(c), timeoutMs = 6_000))
    }
}

/** Wake-on-LAN magic packet: 6x 0xFF then the MAC 16 times, broadcast on UDP 9.
 * Only works on the same LAN as the PC (broadcasts don't cross Tailscale) and
 * needs WoL enabled in the PC's BIOS + network adapter settings. */
object WakeOnLan {
    suspend fun wake(macs: List<String>, extraTargets: List<String> = emptyList()): Int = withContext(Dispatchers.IO) {
        var sent = 0
        val targets = (listOf("255.255.255.255") + extraTargets).distinct()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (mac in macs) {
                val packet = magicPacket(mac) ?: continue
                for (target in targets) {
                    for (port in listOf(9, 7)) {
                        runCatching {
                            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                            sent++
                        }
                    }
                }
            }
        }
        sent
    }

    fun magicPacket(mac: String): ByteArray? {
        val hex = mac.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length != 12) return null
        val macBytes = ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { i -> macBytes[i % 6] }
    }
}
