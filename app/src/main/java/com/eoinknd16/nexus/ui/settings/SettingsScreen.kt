package com.eoinknd16.nexus.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.BuildConfig
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.DiscoveredPc
import com.eoinknd16.nexus.link.PcDiscovery
import com.eoinknd16.nexus.link.PcLinkConfig
import com.eoinknd16.nexus.media.StremioImportResult
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen() {
    val c = appContainer()
    val nav = LocalNav.current
    val context = LocalContext.current
    val addons by c.media.addons.collectAsStateWithLifecycle()

    NexusBackdrop(intensity = 0.4f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Settings", "Nexus ${BuildConfig.VERSION_NAME}")
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    HudLabel("Updates")
                    Spacer(Modifier.height(10.dp))
                    com.eoinknd16.nexus.ui.update.UpdateSection()
                }
                PcSection()
                StremioSection()

                Column {
                    HudLabel("Media")
                    Spacer(Modifier.height(10.dp))
                    HoloPanel(Modifier.fillMaxWidth()) {
                        Text("${addons.size} addons installed", style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                        Text(
                            "${addons.count { "stream" in it.resources }} can stream · ${addons.count { "catalog" in it.resources }} add catalogs",
                            style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        NexusButton("Manage addons", icon = Icons.Filled.Extension) { nav.navigate(Routes.ADDONS) }
                    }
                }

                Column {
                    HudLabel("Look & layout")
                    Spacer(Modifier.height(10.dp))
                    HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Magenta) {
                        Text("Colours, fonts, backdrop, home layout, widgets, tiles, folders, tray, TV and player.", style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
                        Spacer(Modifier.height(12.dp))
                        NexusButton("Customise", icon = Icons.Filled.Palette, accent = Nx.Magenta, filled = true) { nav.navigate(Routes.CUSTOMIZE) }
                    }
                }

                Column {
                    HudLabel("Launcher")
                    Spacer(Modifier.height(10.dp))
                    HoloPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "Make Nexus your home screen: Android asks which home app to use, pick Nexus. Switch back the same way any time.",
                            style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        NexusButton("Set as home app", icon = Icons.Filled.Home) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---- Living room PC -----------------------------------------------------------------

@Composable
private fun PcSection() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val pc by c.pcStore.state.collectAsStateWithLifecycle()
    val saved by c.pcSaved.state.collectAsStateWithLifecycle()
    val pcs = com.eoinknd16.nexus.link.allPcs(pc, saved)
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val online = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(pcs.map { it.key to it.host }) {
        pcs.forEach { p -> launch { online[p.key] = c.pc.isOnline(p) } }
    }

    Column {
        HudLabel(if (pcs.size > 1) "Your PCs" else "Living room PC")
        Spacer(Modifier.height(10.dp))
        HoloPanel(Modifier.fillMaxWidth()) {
            pcs.forEach { p ->
                val active = p.key == pc.key
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .nexusPress({ if (!active) scope.launch { c.pc.switchTo(p.key); status = "Now controlling ${p.displayName}" } })
                        .holoPanel(if (active) Nx.Lime else Nx.CyanDim, if (active) 0.15f else 0f).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Icon(Icons.Filled.Computer, null, tint = if (active) Nx.Lime else Nx.Cyan)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(when (online[p.key]) { true -> Nx.Lime; false -> Nx.Danger; null -> Nx.Muted }),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (renaming == p.key) {
                            var text by remember(p.key) { mutableStateOf(p.displayName) }
                            Field("Name", text, { text = it }, KeyboardType.Text)
                            Spacer(Modifier.height(6.dp))
                            NexusButton("Save name", filled = true) { scope.launch { c.pc.rename(p.key, text.trim()); renaming = null } }
                        } else {
                            Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                            Text(
                                hud("${p.host}:${p.port}" + when (online[p.key]) { true -> " · online"; false -> " · offline"; null -> "" } + if (active) " · active" else ""),
                                style = MaterialTheme.typography.labelSmall, color = if (active) Nx.Lime else Nx.Muted,
                            )
                        }
                    }
                    if (renaming != p.key) {
                        Box(Modifier.size(36.dp).nexusPress({ renaming = p.key }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Edit, "Rename", tint = Nx.Muted)
                        }
                        Box(Modifier.size(36.dp).nexusPress({ scope.launch { c.pc.forget(p.key); status = "Forgot ${p.displayName}" } }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LinkOff, "Forget this PC", tint = Nx.Danger)
                        }
                    }
                }
            }
            if (pc.isPaired) {
                Text(
                    if (pc.macs.isEmpty()) "No MAC address for wake-on-LAN yet" else "Wake targets: ${pc.macs.joinToString()}",
                    style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                )
                Text(
                    "Tap a PC to control it. The remote, Send to TV and Wake all use the active one. Wake-on-LAN not working? On that PC: Settings › Phone Link › Set Up Wake-on-LAN.",
                    style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Import addons", icon = Icons.Filled.Download, enabled = !busy) {
                        busy = true
                        scope.launch {
                            status = runCatching { c.media.importAddonsFromPc() }
                                .fold({ "Imported $it addons from ${pc.displayName}" }, { "Import failed (${it.describe()})" })
                            busy = false
                        }
                    }
                    NexusButton(if (adding) "Cancel" else "Add another PC", icon = Icons.Filled.Link, accent = Nx.Cyan) { adding = !adding }
                }
            }
            if (!pc.isPaired || adding) {
                if (pc.isPaired) Spacer(Modifier.height(12.dp))
                AutoPair(onStatus = { status = it; if (it?.startsWith("Linked") == true) adding = false })
            }
            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(hud(it), style = MaterialTheme.typography.labelSmall, color = Nx.Amber)
            }
        }
    }
}

/** Finds PCs on the Wi-Fi by itself; tap one and approve on the TV. Manual entry as a fallback. */
@Composable
private fun AutoPair(onStatus: (String?) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<DiscoveredPc>>(emptyList()) }
    var pairing by remember { mutableStateOf<DiscoveredPc?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var manual by rememberSaveable { mutableStateOf(false) }

    fun scan() {
        scanning = true
        scope.launch {
            found = PcDiscovery.discover()
            scanning = false
        }
    }
    LaunchedEffect(Unit) { scan() }

    val p = pairing
    if (p != null) {
        Text("Linking with ${p.name}", style = MaterialTheme.typography.titleMedium, color = Nx.Text)
        Spacer(Modifier.height(8.dp))
        if (code == null) {
            Text("Asking the PC…", style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
        } else {
            Text("Approve it on the TV. The code there should be:", style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
            Spacer(Modifier.height(8.dp))
            Text(code ?: "", fontSize = 44.sp, color = Nx.Cyan, letterSpacing = 12.sp, fontFamily = Nx.Mono)
        }
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator(color = Nx.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (scanning) "Looking for Nexus on your Wi-Fi…" else if (found.isEmpty()) "No PC found on this Wi-Fi" else "Found on your Wi-Fi:",
            style = MaterialTheme.typography.bodyMedium, color = Nx.Muted, modifier = Modifier.weight(1f),
        )
        if (scanning) CircularProgressIndicator(color = Nx.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Box(Modifier.size(36.dp).nexusPress({ scan() }), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Refresh, "Scan again", tint = Nx.Cyan) }
    }
    val activePc by c.pcStore.state.collectAsStateWithLifecycle()
    val savedPcs by c.pcSaved.state.collectAsStateWithLifecycle()
    val known = com.eoinknd16.nexus.link.allPcs(activePc, savedPcs)
    found.forEach { pc ->
        val already = known.firstOrNull { it.pcId.isNotBlank() && it.pcId == pc.pcId }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().nexusPress({
                if (already != null) {
                    // Already paired: just switch to it (and pick up its current address).
                    scope.launch {
                        c.pc.switchTo(already.key)
                        c.pcStore.update { it.copy(host = pc.host, port = pc.port) }
                        onStatus("Linked to ${already.displayName}")
                    }
                    return@nexusPress
                }
                pairing = pc
                code = null
                onStatus(null)
                scope.launch {
                    runCatching { c.pc.pairAutomatically(pc, Build.MODEL ?: "Android phone") { code = it } }
                        .onSuccess { onStatus("Linked to ${it.name}") }
                        .onFailure { onStatus("Not linked: ${it.message ?: it.describe()}") }
                    pairing = null
                }
            }).holoPanel(Nx.Lime, 0.2f).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Computer, null, tint = Nx.Lime)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pc.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                Text(hud("${pc.host} · Nexus ${pc.version}"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
            }
            Text(hud(if (already != null) "Paired · use" else "Link"), style = MaterialTheme.typography.labelMedium, color = Nx.Lime)
        }
    }
    if (!scanning && found.isEmpty()) {
        Text(
            "Make sure Nexus 0.3.2+ is running on the PC and both are on the same network. Over Tailscale, use manual linking.",
            style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        hud(if (manual) "Hide manual linking" else "Link manually (Tailscale / other network)"),
        style = MaterialTheme.typography.labelSmall, color = Nx.Cyan,
        modifier = Modifier.nexusPress({ manual = !manual }).padding(vertical = 6.dp),
    )
    if (manual) ManualPair(onStatus)
}

@Composable
private fun ManualPair(onStatus: (String?) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(PcLinkConfig.DEFAULT_PORT.toString()) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Text(
        "On the PC: Nexus › Settings › Phone Link › Show Pairing Code. Enter its address (or Tailscale IP) and the 6-digit code.",
        style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
    )
    Spacer(Modifier.height(8.dp))
    Field("PC address", host, { host = it.trim() }, KeyboardType.Uri)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Field("Port", port, { port = it.filter(Char::isDigit) }, KeyboardType.Number) }
        Box(Modifier.weight(1f)) { Field("Code", code, { code = it.filter(Char::isDigit).take(6) }, KeyboardType.Number) }
    }
    Spacer(Modifier.height(12.dp))
    NexusButton("Pair", icon = Icons.Filled.Link, filled = true, enabled = !busy && host.isNotBlank() && code.length == 6) {
        busy = true
        onStatus("Pairing…")
        scope.launch {
            onStatus(
                runCatching { c.pc.pair(host, port.toIntOrNull() ?: PcLinkConfig.DEFAULT_PORT, code, Build.MODEL ?: "Android") }
                    .fold({ "Linked to ${it.name}" }, { "Pairing failed (${it.describe()})" }),
            )
            busy = false
            code = ""
        }
    }
}

// ---- Stremio --------------------------------------------------------------------

@Composable
private fun StremioSection() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val account by c.media.account.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authKey by remember { mutableStateOf("") }
    var useKey by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun summary(r: StremioImportResult) = buildString {
        append("Imported ${r.addons} addons, ${r.libraryItems} library titles, ${r.inProgress} in progress.")
        if (r.skippedAddons.isNotEmpty()) append(" Skipped (desktop-only): ${r.skippedAddons.joinToString()}.")
    }

    fun run(block: suspend () -> StremioImportResult) {
        busy = true
        result = "Importing your Stremio setup…"
        scope.launch {
            result = runCatching { block() }.fold({ summary(it) }, { "Stremio: ${it.message ?: it.describe()}" })
            busy = false
            password = ""
        }
    }

    Column {
        HudLabel("Stremio")
        Spacer(Modifier.height(10.dp))
        HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Magenta) {
            if (account.isLinked) {
                Text(account.email.ifBlank { "Stremio account" }, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                Text(
                    if (account.lastSyncAt > 0) "Last synced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(account.lastSyncAt))}" else "Not synced yet",
                    style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                )
                Text(
                    "Your addons (in your order), library and continue watching come from Stremio. The TV board mirrors Stremio's.",
                    style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NexusButton("Sync now", icon = Icons.Filled.Sync, accent = Nx.Magenta, filled = true, enabled = !busy) { run { c.media.stremioSync() } }
                    NexusButton("Sign out", icon = Icons.Filled.LinkOff, accent = Nx.Danger) {
                        scope.launch { c.media.stremioUnlink() }
                        result = "Signed out. Imported addons stay until you remove them."
                    }
                }
            } else {
                Text(
                    "Sign in to bring your whole Stremio setup across: every addon in your order (so catalogs, streams and " +
                        "subtitles match your Stremio app), your library and your continue watching. Only a login key is " +
                        "saved, never your password.",
                    style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                )
                Spacer(Modifier.height(10.dp))
                if (!useKey) {
                    Field("Email", email, { email = it.trim() }, KeyboardType.Email)
                    Spacer(Modifier.height(8.dp))
                    PasswordField("Password", password) { password = it }
                    Spacer(Modifier.height(12.dp))
                    NexusButton("Sign in & import", icon = Icons.Filled.Login, accent = Nx.Magenta, filled = true, enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
                        run { c.media.stremioLogin(email, password) }
                    }
                } else {
                    Text(
                        "In Stremio Web (signed in), open the browser console and run: JSON.parse(localStorage.profile).auth.key",
                        style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Field("Auth key", authKey, { authKey = it.trim() })
                    Spacer(Modifier.height(12.dp))
                    NexusButton("Link & import", icon = Icons.Filled.Login, accent = Nx.Magenta, filled = true, enabled = !busy && authKey.isNotBlank()) {
                        run { c.media.stremioLinkWithKey(authKey) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    hud(if (useKey) "Use email & password instead" else "Use an auth key instead (signed in with Google/Facebook?)"),
                    style = MaterialTheme.typography.labelSmall, color = Nx.Magenta,
                    modifier = Modifier.nexusPress({ useKey = !useKey }).padding(vertical = 6.dp),
                )
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(color = Nx.Magenta, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            result?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Nx.Amber)
            }
        }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    val shape = nexusShape(0.57f)
    Column(
        Modifier.fillMaxWidth().background(Nx.Deep, shape).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(hud(label), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(Nx.Cyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    val shape = nexusShape(0.57f)
    Column(
        Modifier.fillMaxWidth().background(Nx.Deep, shape).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(hud(label), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(Nx.Cyan),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
