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
    /** The PC's stable id, so it can be found again on the LAN if its IP changes. */
    val pcId: String = "",
    /** Your own name for it ("Living room", "Office"); blank = the PC's name. */
    val nickname: String = "",
) {
    val isPaired: Boolean get() = host.isNotBlank() && token.isNotBlank()
    val baseUrl: String get() = "http://$host:$port"
    /** Stable identity for the saved list. */
    val key: String get() = pcId.ifBlank { "$host:$port" }
    val displayName: String get() = nickname.ifBlank { name.ifBlank { "PC" } }

    companion object {
        /** Must match REMOTE_API_PORT in the PC build (src/main/remote/server.ts). */
        const val DEFAULT_PORT = 11480
        /** Must match REMOTE_DISCOVERY_PORT on the PC. */
        const val DISCOVERY_PORT = 11481
    }
}

/** Every PC you've paired. The active one also lives in its own store (what everything else uses). */
@Serializable
data class SavedPcs(val pcs: List<PcLinkConfig> = emptyList())

/** Active first, then the rest, without duplicates. */
fun allPcs(active: PcLinkConfig, saved: SavedPcs): List<PcLinkConfig> =
    (listOf(active).filter { it.isPaired } + saved.pcs.filter { it.isPaired }).distinctBy { it.key }

// ---- Wire types for the PC's remote API (src/main/remote/server.ts) --------

@Serializable
data class PcStatus(
    val name: String,
    val version: String,
    val screen: String? = null,
    val macs: List<String> = emptyList(),
    val pcId: String? = null,
    val nowPlaying: PcNowPlaying? = null,
)

@Serializable
data class PcNowPlaying(val title: String, val positionSeconds: Double? = null, val durationSeconds: Double? = null)

@Serializable
data class PairRequest(val code: String, val deviceName: String)

@Serializable
data class PairResponse(val token: String, val name: String, val macs: List<String> = emptyList(), val pcId: String? = null)

@Serializable
data class PairRequestBody(val deviceName: String)

@Serializable
data class PairRequestResponse(val requestId: String, val code: String, val name: String, val pcId: String? = null)

@Serializable
data class PairStatus(
    val state: String,
    val token: String? = null,
    val name: String? = null,
    val macs: List<String> = emptyList(),
    val pcId: String? = null,
)

/** A Nexus PC that answered a LAN discovery broadcast. */
@Serializable
data class DiscoveredPc(val host: String, val port: Int, val name: String, val pcId: String, val version: String = "")

@Serializable
private data class DiscoveryReply(val nexus: Int = 0, val pcId: String = "", val name: String = "", val port: Int = PcLinkConfig.DEFAULT_PORT, val version: String = "")

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
data class PowerRequest(val action: String)

@Serializable
data class OpenUrlRequest(val url: String)

@Serializable
data class OkResponse(val ok: Boolean = true, val error: String? = null)

/** HTTP client for the PC's remote API. Every call needs a paired config. */
class PcLinkClient(private val store: JsonStore<PcLinkConfig>, val saved: JsonStore<SavedPcs>) {

    /** Keep the saved list in step with a PC's latest details. */
    private suspend fun remember(cfg: PcLinkConfig) {
        if (!cfg.isPaired) return
        saved.update { s ->
            val old = s.pcs.firstOrNull { it.key == cfg.key }
            val merged = if (old != null && cfg.nickname.isBlank()) cfg.copy(nickname = old.nickname) else cfg
            SavedPcs(if (old == null) s.pcs + merged else s.pcs.map { if (it.key == cfg.key) merged else it })
        }
    }

    private suspend fun setActive(cfg: PcLinkConfig) {
        store.set(cfg)
        remember(cfg)
    }

    suspend fun all(): List<PcLinkConfig> = allPcs(store.get(), saved.get())

    /** Make [key] the PC the remote, Send to TV and Wake talk to. */
    suspend fun switchTo(key: String): PcLinkConfig? {
        val current = store.get()
        val target = all().firstOrNull { it.key == key } ?: return null
        remember(current)
        store.set(target)
        return target
    }

    /** "next" cycles through your PCs. */
    suspend fun switchNext(): PcLinkConfig? {
        val list = all()
        if (list.size < 2) return list.firstOrNull()
        val i = list.indexOfFirst { it.key == store.get().key }
        return switchTo(list[(i + 1).mod(list.size)].key)
    }

    suspend fun rename(key: String, nickname: String) {
        saved.update { s -> SavedPcs(s.pcs.map { if (it.key == key) it.copy(nickname = nickname) else it }) }
        if (store.get().key == key) store.update { it.copy(nickname = nickname) }
    }

    /** Forget one PC; if it was active, the next saved one takes over. */
    suspend fun forget(key: String) {
        saved.update { s -> SavedPcs(s.pcs.filterNot { it.key == key }) }
        if (store.get().key == key) store.set(saved.get().pcs.firstOrNull() ?: PcLinkConfig())
    }

    /** Quick reachability check for any saved PC (for the switcher's online dots). */
    suspend fun isOnline(cfg: PcLinkConfig): Boolean = runCatching {
        Http.getText("${cfg.baseUrl}/api/status", auth(cfg), timeoutMs = 2_500)
    }.isSuccess

    /** Wake a specific PC (or the active one). */
    suspend fun wake(cfg: PcLinkConfig? = null): Int {
        val c = cfg ?: store.get()
        if (c.macs.isEmpty()) return -1
        val subnet = Regex("^(\\d+\\.\\d+\\.\\d+)\\.\\d+$").find(c.host)?.groupValues?.get(1)?.let { "$it.255" }
        return runCatching { WakeOnLan.wake(c.macs, listOfNotNull(subnet)) }.getOrDefault(0)
    }

    private suspend fun config(): PcLinkConfig = store.get()

    private fun auth(c: PcLinkConfig) = mapOf("Authorization" to "Bearer ${c.token}")

    /** Pairs with the 6-digit code the PC shows in Settings > Phone Link. */
    suspend fun pair(host: String, port: Int, code: String, deviceName: String): PcLinkConfig {
        val base = "http://${host.trim()}:$port"
        val body = Http.json.encodeToString(PairRequest.serializer(), PairRequest(code.trim(), deviceName))
        val res = Http.json.decodeFromString(PairResponse.serializer(), Http.postJson("$base/api/pair", body, timeoutMs = 6_000))
        val config = PcLinkConfig(host = host.trim(), port = port, token = res.token, name = res.name, macs = res.macs, pcId = res.pcId ?: "")
        setActive(config)
        return config
    }

    /**
     * Automatic pairing: asks the PC to show an Allow / Deny prompt on the TV,
     * then waits (up to ~2 minutes) for an answer. [onCode] gets the 4-digit
     * code the TV is showing too, so the user can match them.
     */
    suspend fun pairAutomatically(pc: DiscoveredPc, deviceName: String, onCode: (String) -> Unit): PcLinkConfig {
        val base = "http://${pc.host}:${pc.port}"
        val body = Http.json.encodeToString(PairRequestBody.serializer(), PairRequestBody(deviceName))
        val req = Http.json.decodeFromString(PairRequestResponse.serializer(), Http.postJson("$base/api/pair-request", body, timeoutMs = 6_000))
        onCode(req.code)
        val deadline = System.currentTimeMillis() + 125_000
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(1_500)
            val st = runCatching {
                Http.json.decodeFromString(PairStatus.serializer(), Http.getText("$base/api/pair-status?id=${req.requestId}", timeoutMs = 5_000))
            }.getOrNull() ?: continue
            when (st.state) {
                "approved" -> {
                    val config = PcLinkConfig(
                        host = pc.host, port = pc.port, token = st.token ?: error("No token"),
                        name = st.name ?: pc.name, macs = st.macs, pcId = st.pcId ?: pc.pcId,
                    )
                    setActive(config)
                    return config
                }
                "denied" -> error("The PC said no")
                "expired" -> error("Timed out waiting for the PC")
            }
        }
        error("Timed out waiting for the PC")
    }

    /** Forget the active PC (the next saved one, if any, becomes active). */
    suspend fun unpair() = forget(store.get().key)

    suspend fun status(): PcStatus {
        val c = config()
        val status = runCatching { fetchStatus(c) }.getOrElse { first ->
            // The PC may just have a new IP (DHCP). Find it by its id and carry on.
            relocate(c) ?: throw first
            fetchStatus(config())
        }
        // Keep wake-on-LAN targets fresh (a new network adapter, say).
        if (status.macs.isNotEmpty() && status.macs != c.macs) {
            store.update { it.copy(macs = status.macs, name = status.name) }
            remember(store.get())
        }
        return status
    }

    private suspend fun fetchStatus(c: PcLinkConfig): PcStatus = Http.json.decodeFromString(
        PcStatus.serializer(),
        Http.getText("${c.baseUrl}/api/status", auth(c), timeoutMs = 4_000),
    )

    /** Re-finds a paired PC on the LAN by pcId; updates the saved address if it moved. */
    private suspend fun relocate(c: PcLinkConfig): PcLinkConfig? {
        if (c.pcId.isBlank()) return null
        val found = PcDiscovery.discover(1_800).firstOrNull { it.pcId == c.pcId } ?: return null
        if (found.host == c.host && found.port == c.port) return null
        store.update { it.copy(host = found.host, port = found.port) }
        remember(store.get())
        return config()
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
    fun torrentStreamUrl(c: PcLinkConfig, infoHash: String, fileIdx: Int?): String =
        // -1 = "addon didn't say", which the Stremio server reads as "pick the main video file".
        "${c.baseUrl}/stremio/$infoHash/${fileIdx ?: -1}?token=${URLEncoder.encode(c.token, "UTF-8")}"

    suspend fun cast(request: CastRequest): OkResponse = post("/api/cast", Http.json.encodeToString(CastRequest.serializer(), request))

    suspend fun nav(action: String): OkResponse = post("/api/nav", Http.json.encodeToString(NavRequest.serializer(), NavRequest(action)))

    /** "shutdown" / "restart" (20 s countdown on the PC), "cancel", or "sleep". */
    suspend fun power(action: String): OkResponse = try {
        post("/api/power", Http.json.encodeToString(PowerRequest.serializer(), PowerRequest(action)))
    } catch (e: com.eoinknd16.nexus.core.HttpException) {
        if (e.code == 404) OkResponse(false, "Update Nexus on the PC to use this") else throw e
    }

    /** Open a web link on the PC (Nexus's Browse screen). */
    suspend fun openUrl(url: String): OkResponse = try {
        post("/api/open-url", Http.json.encodeToString(OpenUrlRequest.serializer(), OpenUrlRequest(url)))
    } catch (e: com.eoinknd16.nexus.core.HttpException) {
        if (e.code == 404) OkResponse(false, "Update Nexus on the PC to use this") else throw e
    }

    suspend fun openScreen(screen: String): OkResponse =
        post("/api/screen", Http.json.encodeToString(ScreenRequest.serializer(), ScreenRequest(screen)))

    private suspend fun post(path: String, body: String): OkResponse {
        val c = config()
        return Http.json.decodeFromString(OkResponse.serializer(), Http.postJson("${c.baseUrl}$path", body, auth(c), timeoutMs = 6_000))
    }
}

/** Finds Nexus PCs on the local network: broadcast, then collect replies. */
object PcDiscovery {
    suspend fun discover(timeoutMs: Long = 2_500): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DiscoveredPc>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val probe = "NEXUS_DISCOVER".toByteArray()
                val targets = (listOf("255.255.255.255") + subnetBroadcasts()).distinct()
                // A couple of rounds: UDP can drop on busy Wi-Fi.
                val deadline = System.currentTimeMillis() + timeoutMs
                var nextSend = 0L
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() >= nextSend) {
                        for (t in targets) runCatching {
                            socket.send(DatagramPacket(probe, probe.size, InetAddress.getByName(t), PcLinkConfig.DISCOVERY_PORT))
                        }
                        nextSend = System.currentTimeMillis() + 800
                    }
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val reply = runCatching { Http.json.decodeFromString(DiscoveryReply.serializer(), text) }.getOrNull() ?: continue
                    if (reply.nexus != 1 || reply.pcId.isBlank()) continue
                    val host = packet.address.hostAddress ?: continue
                    found[reply.pcId] = DiscoveredPc(host, reply.port, reply.name, reply.pcId, reply.version)
                }
            }
        }
        found.values.toList()
    }

    /** x.y.z.255 for each of this phone's IPv4 networks (some routers drop 255.255.255.255). */
    private fun subnetBroadcasts(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast?.hostAddress }
    }.getOrDefault(emptyList())
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
