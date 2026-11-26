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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.BuildConfig
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.link.PcLinkConfig
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val c = appContainer()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc by c.pcStore.state.collectAsStateWithLifecycle()
    val addons by c.media.addons.collectAsStateWithLifecycle()

    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(PcLinkConfig.DEFAULT_PORT.toString()) }
    var code by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    NexusBackdrop(intensity = 0.4f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Settings", "Nexus ${BuildConfig.VERSION_NAME}")
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // ---- PC link ----
                Column {
                    HudLabel("Living room PC")
                    Spacer(Modifier.height(10.dp))
                    HoloPanel(Modifier.fillMaxWidth()) {
                        if (pc.isPaired) {
                            Text(pc.name.ifBlank { "PC" }, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                            Text("${pc.host}:${pc.port}", style = MaterialTheme.typography.labelMedium, color = Nx.Cyan)
                            Text(
                                if (pc.macs.isEmpty()) "No MAC address for wake-on-LAN yet" else "Wake targets: ${pc.macs.joinToString()}",
                                style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NexusButton("Import addons", icon = Icons.Filled.Download, enabled = !busy) {
                                    busy = true
                                    scope.launch {
                                        status = runCatching { c.media.importAddonsFromPc() }
                                            .fold({ "Imported $it addons from the PC" }, { "Import failed (${it.describe()})" })
                                        busy = false
                                    }
                                }
                                NexusButton("Unlink", icon = Icons.Filled.LinkOff, accent = Nx.Danger) {
                                    scope.launch { c.pc.unpair() }
                                }
                            }
                        } else {
                            Text(
                                "On the PC open Nexus › Settings › Phone Link. It shows its address and a 6-digit code. " +
                                    "Over Tailscale, use the PC's Tailscale IP or name instead.",
                                style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Field("PC address", host, { host = it.trim() }, KeyboardType.Uri)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { Field("Port", port, { port = it.filter(Char::isDigit) }, KeyboardType.Number) }
                                Box(Modifier.weight(1f)) { Field("Code", code, { code = it.filter(Char::isDigit).take(6) }, KeyboardType.Number) }
                            }
                            Spacer(Modifier.height(12.dp))
                            NexusButton("Pair", icon = Icons.Filled.Link, filled = true, enabled = !busy && host.isNotBlank() && code.length == 6) {
                                busy = true
                                status = "Pairing…"
                                scope.launch {
                                    status = runCatching {
                                        c.pc.pair(host, port.toIntOrNull() ?: PcLinkConfig.DEFAULT_PORT, code, Build.MODEL ?: "Android")
                                    }.fold(
                                        { cfg -> "Linked to ${cfg.name}" },
                                        { e -> "Pairing failed (${e.describe()})" },
                                    )
                                    busy = false
                                    code = ""
                                }
                            }
                        }
                        status?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Amber)
                        }
                    }
                }

                // ---- Addons ----
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

                // ---- Launcher ----
                Column {
                    HudLabel("Launcher")
                    Spacer(Modifier.height(10.dp))
                    HoloPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "Make Nexus your home screen. Android asks which home app to use; pick Nexus. " +
                                "You can switch back the same way any time.",
                            style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        NexusButton("Set as home app", icon = Icons.Filled.Home) {
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }.onFailure {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Column(
        Modifier.fillMaxWidth().background(Nx.Deep, shape).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
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
