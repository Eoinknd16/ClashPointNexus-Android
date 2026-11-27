package com.eoinknd16.nexus.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.launcher.AppShortcut
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.look.HomePager
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.search.QuickAnswers
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.apps.fuzzyScore
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.plainTap
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.fx.rememberHaptics
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Opens the search-everything layer over home. */
object UniversalSearchHub {
    var open by mutableStateOf(false)
    var initialQuery by mutableStateOf("")
    fun show(query: String = "") { initialQuery = query; open = true }
}

private data class Command(val label: String, val keywords: String, val icon: ImageVector, val run: () -> Unit)
private data class Contact(val id: Long, val lookup: String, val name: String, val number: String?)

/**
 * One box for everything: apps (and their shortcuts), commands ("wake pc",
 * "page 2", "wifi"), maths and unit conversions, contacts, movies & shows
 * from your Nexus addons, and the web with live suggestions. Enter runs the
 * top result.
 */
@Composable
fun UniversalSearch(runner: TileRunner, onNavigate: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val allApps by c.apps.apps.collectAsStateWithLifecycle()
    val launcherPrefs by c.apps.prefs.state.collectAsStateWithLifecycle()
    // Hidden apps stay hidden from search too.
    val apps = remember(allApps, launcherPrefs.hidden) { allApps.filter { it.key !in launcherPrefs.hidden } }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val open = UniversalSearchHub.open

    fun close() {
        keyboard?.hide()
        UniversalSearchHub.open = false
    }
    LaunchedEffect(open) {
        if (open) {
            query = UniversalSearchHub.initialQuery
            delay(60)
            runCatching { focus.requestFocus() }
        }
    }
    BackHandler(enabled = open) { close() }

    // ---- results ---------------------------------------------------------------
    val q = query.trim()
    val appHits: List<LaunchableApp> = remember(q, apps) {
        if (q.isEmpty()) emptyList() else apps.map { it to fuzzyScore(it.label, q) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { it.first }
    }
    val topApp = appHits.firstOrNull()
    val shortcuts by produceState<List<AppShortcut>>(emptyList(), topApp?.key) {
        value = topApp?.let { a -> withContext(Dispatchers.IO) { c.apps.shortcuts(a, 4) } }.orEmpty()
    }
    val commands = remember(runner) { buildCommands(runner, onNavigate) }
    val cmdHits = remember(q, commands, LookState.look.home.pages) {
        if (q.isEmpty()) emptyList() else {
            val pageCmds = LookState.look.home.pages.map { p -> Command("Go to ${p.name}", "page", Icons.Filled.ViewCarousel) { HomePager.go(p.id) } }
            (commands + pageCmds).map { it to maxOf(fuzzyScore(it.label, q), fuzzyScore(it.keywords, q) / 2) }
                .filter { it.second > 150 }.sortedByDescending { it.second }.take(4).map { it.first }
        }
    }
    val answer = remember(q) { QuickAnswers.answer(q) }

    // Contacts (only with permission; offered as a one-tap grant).
    var contactsAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    val contacts by produceState<List<Contact>>(emptyList(), q, contactsAllowed) {
        value = if (!contactsAllowed || q.length < 2) emptyList() else withContext(Dispatchers.IO) { findContacts(context, q) }
    }
    // Nexus catalog + web suggestions, debounced so typing stays instant.
    val media by produceState<List<CatalogItem>>(emptyList(), q) {
        value = emptyList()
        if (q.length < 3) return@produceState
        delay(350)
        value = runCatching { c.media.search(q).take(10) }.getOrDefault(emptyList())
    }
    val suggestions by produceState<List<String>>(emptyList(), q) {
        value = emptyList()
        if (q.length < 2) return@produceState
        delay(180)
        value = runCatching {
            val body = Http.getText("https://suggestqueries.google.com/complete/search?client=firefox&q=${Uri.encode(q)}", emptyMap(), timeoutMs = 2500)
            (Http.json.parseToJsonElement(body).jsonArray[1] as JsonArray).map { it.jsonPrimitive.content }.filter { !it.equals(q, true) }.take(4)
        }.getOrDefault(emptyList())
    }

    // ---- Ask Nexus (Claude) ----
    val aiPrefs by c.aiPrefs.state.collectAsStateWithLifecycle()
    val deviceAi by com.eoinknd16.nexus.ai.OnDeviceAi.state.collectAsStateWithLifecycle()
    LaunchedEffect(open) { if (open) com.eoinknd16.nexus.ai.OnDeviceAi.refresh() }
    // Show Ask Nexus when the chosen engine can actually answer.
    val aiOn = if (aiPrefs.engine == "claude") aiPrefs.claudeReady else deviceAi == com.eoinknd16.nexus.ai.OnDeviceAi.State.Available
    val ai = aiPrefs
    var aiBusy by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf<String?>(null) }
    var aiConfirm by remember { mutableStateOf<String?>(null) } // pending PC restart/shutdown
    val aiScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(q) { aiText = null; aiConfirm = null }
    fun runAi(action: com.eoinknd16.nexus.ai.AiAction): String = when (action) {
        is com.eoinknd16.nexus.ai.AiAction.OpenApp -> {
            val app = apps.map { it to fuzzyScore(it.label, action.name) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
            if (app != null) { c.apps.launch(app); "Opened ${app.label}" } else "Couldn't find ${action.name}"
        }
        is com.eoinknd16.nexus.ai.AiAction.Pc -> when (action.action) {
            "wake" -> { runner.runAction(TileAction(TileAction.Kind.WakePc)); "Waking the PC" }
            "sleep" -> { runner.runAction(TileAction(TileAction.Kind.PcPower, "sleep")); "PC going to sleep" }
            "restart", "shutdown" -> { aiConfirm = action.action; "Confirm below" }
            "open_remote" -> { onNavigate(Routes.PC); "Opened the remote" }
            "open_tv" -> { runner.runAction(TileAction(TileAction.Kind.PcScreen, "tv")); "Opened TV on the PC" }
            "quick_menu" -> { runner.runAction(TileAction(TileAction.Kind.PcNav, "quickMenu")); "Opened the quick menu" }
            else -> ""
        }
        is com.eoinknd16.nexus.ai.AiAction.Page -> {
            val pg = LookState.look.home.pages.firstOrNull { it.name.equals(action.page, true) }
            HomePager.go(pg?.id ?: action.page.lowercase()); "Switched page"
        }
        is com.eoinknd16.nexus.ai.AiAction.Phone -> {
            when (action.action) {
                "notifications" -> runner.runAction(TileAction(TileAction.Kind.Notifications))
                "quick_settings" -> runner.runAction(TileAction(TileAction.Kind.QuickSettings))
                "lock_screen" -> runner.runAction(TileAction(TileAction.Kind.LockScreen))
                "settings" -> runner.runAction(TileAction(TileAction.Kind.Settings, "all"))
                else -> runner.runAction(TileAction(TileAction.Kind.Settings, action.action))
            }
            "Opened ${action.action.replace('_', ' ')}"
        }
        is com.eoinknd16.nexus.ai.AiAction.Web -> { runner.webSearch(action.query); "Searching the web" }
        is com.eoinknd16.nexus.ai.AiAction.Nexus -> {
            when (action.screen) {
                "tv" -> onNavigate(Routes.TV)
                "customise" -> onNavigate(Routes.CUSTOMIZE)
                "settings" -> onNavigate(Routes.SETTINGS)
                "app_drawer" -> HomeDrawerHub.open()
                "pc_controls" -> PcHub.open = true
            }
            "Opened ${action.screen.replace('_', ' ')}"
        }
    }
    fun askAi() {
        if (aiBusy || q.isEmpty()) return
        aiBusy = true
        aiText = null
        aiScope.launch {
            val result = runCatching {
                val pcs = com.eoinknd16.nexus.link.allPcs(c.pcStore.value, c.pcSaved.value).map { it.displayName }
                com.eoinknd16.nexus.ai.NexusAssistant.ask(ai, q, apps.map { it.label }, LookState.look.home.pages.map { it.name }, pcs)
            }
            aiBusy = false
            result.fold(
                onSuccess = { r ->
                    val done = r.actions.map { runAi(it) }.filter { it.isNotBlank() && it != "Confirm below" }
                    aiText = (listOf(r.text) + done).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Done." }
                    // Leave the panel open only if there's something to read or confirm.
                    if (r.actions.isNotEmpty() && r.text.isBlank() && aiConfirm == null) close()
                },
                onFailure = { aiText = it.message ?: "Couldn't reach Claude" },
            )
        }
    }

    fun runTop() {
        when {
            cmdHits.isNotEmpty() && (appHits.isEmpty() || fuzzyScore(cmdHits.first().label, q) >= fuzzyScore(appHits.first().label, q)) -> { cmdHits.first().run(); close() }
            topApp != null -> { c.apps.launch(topApp); close() }
            q.isNotEmpty() -> { runner.webSearch(q); close() }
        }
    }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + slideInVertically { -it / 12 },
        exit = fadeOut() + slideOutVertically { -it / 12 },
    ) {
        Box(
            Modifier.fillMaxSize().background(Nx.Void.copy(alpha = 0.72f)).plainTap(::close),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 14.dp)) {
                // Search field
                val shape = nexusShape(0.6f)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).plainTap(null).clip(shape).background(Nx.PanelSolid)
                        .border(1.dp, Nx.Cyan, shape).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Nx.Cyan)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(if (aiOn) "Search or ask Nexus anything…" else "Apps, contacts, commands, maths, shows, web…", color = Nx.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Nx.Text, fontSize = 18.sp),
                            cursorBrush = SolidColor(Nx.Cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { runTop() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Box(Modifier.size(32.dp).nexusPress({ if (query.isEmpty()) close() else query = "" }), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, if (query.isEmpty()) "Close" else "Clear", tint = Nx.Muted)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (q.isEmpty()) {
                        Hint2("Try “spot”, “wake pc”, “wifi”, “12*7”, “10 km in miles”, a name, or a film.")
                        // Recent-ish: first row of the tray for quick launching.
                        val dock = LookState.look.home.dock.items.mapNotNull { t -> apps.firstOrNull { it.key == t.action.arg } }
                        if (dock.isNotEmpty()) {
                            Group("Your apps") { AppRow(dock) { c.apps.launch(it); close() } }
                        }
                    }
                    if (aiOn && q.length >= 3) {
                        Group("Ask Nexus") {
                            ResultRow(Icons.Filled.AutoAwesome, if (aiBusy) "Thinking…" else "Ask: “$q”", if (aiText == null) (if (aiPrefs.engine == "claude") "Claude" else "On-device AI") + " · opens apps, controls the PC, switches pages…" else null, accent = Nx.Magenta) { askAi() }
                            aiText?.let { t -> Text(t, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.padding(vertical = 4.dp)) }
                            aiConfirm?.let { act ->
                                ResultRow(Icons.Filled.PowerSettingsNew, "Confirm: ${if (act == "shutdown") "shut down" else "restart"} the PC", "Has a 20 s cancel on the PC", accent = Nx.Danger) {
                                    runner.runAction(TileAction(TileAction.Kind.PcPower, act))
                                    aiConfirm = null
                                    close()
                                }
                            }
                        }
                    }
                    answer?.let { a ->
                        Group("Answer") {
                            ResultRow(Icons.Filled.Calculate, a.result, a.expression, accent = Nx.Lime, big = true) {
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                clip?.setPrimaryClip(android.content.ClipData.newPlainText("Result", a.result.replace(",", "")))
                                haptics.click()
                            }
                            Hint2("Tap to copy")
                        }
                    }
                    if (appHits.isNotEmpty()) {
                        Group("Apps") {
                            AppRow(appHits) { c.apps.launch(it); close() }
                            shortcuts.forEach { s ->
                                ResultRow(null, s.label, s.app.label, image = s.icon ?: s.app.icon) { c.apps.startShortcut(s); close() }
                            }
                        }
                    }
                    if (cmdHits.isNotEmpty()) {
                        Group("Do") { cmdHits.forEach { cmd -> ResultRow(cmd.icon, cmd.label, null) { haptics.click(); cmd.run(); close() } } }
                    }
                    if (q.length >= 2) {
                        if (contacts.isNotEmpty()) {
                            Group("Contacts") {
                                contacts.forEach { ct ->
                                    ContactRow(ct, onOpen = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(ct.id, ct.lookup)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                        close()
                                    }, onCall = ct.number?.let { n ->
                                        {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                            close()
                                        }
                                    })
                                }
                            }
                        } else if (!contactsAllowed) {
                            ResultRow(Icons.Filled.Contacts, "Include contacts", "Let search find people", accent = Nx.Muted) { askContacts.launch(Manifest.permission.READ_CONTACTS) }
                        }
                    }
                    if (media.isNotEmpty()) {
                        Group("Movies & shows") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                media.forEach { m ->
                                    Column(Modifier.width(92.dp).nexusPress({ onNavigate(Routes.detail(m.type.name, m.id)); close() })) {
                                        AsyncImage(m.poster, m.name, modifier = Modifier.width(92.dp).height(136.dp).clip(nexusShape(0.4f)).background(Nx.Deep))
                                        Text(m.name, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    if (q.isNotEmpty()) {
                        Group("Web") {
                            ResultRow(Icons.Filled.Public, "Search for “$q”", null) { runner.webSearch(q); close() }
                            suggestions.forEach { s -> ResultRow(Icons.Filled.Search, s, null, accent = Nx.Muted) { runner.webSearch(s); close() } }
                        }
                    }
                }
            }
        }
    }
}

private fun buildCommands(runner: TileRunner, nav: (String) -> Unit): List<Command> = buildList {
    fun act(kind: TileAction.Kind, arg: String = "") = { runner.runAction(TileAction(kind, arg)) }
    add(Command("Wake PC", "turn on computer start boot", Icons.Filled.PowerSettingsNew, act(TileAction.Kind.WakePc)))
    add(Command("PC controls", "remote computer power", Icons.Filled.SettingsRemote) { PcHub.open = true })
    add(Command("Sleep PC", "suspend computer", Icons.Filled.Bedtime, act(TileAction.Kind.PcPower, "sleep")))
    add(Command("Restart PC", "reboot computer", Icons.Filled.RestartAlt, act(TileAction.Kind.PcPower, "restart")))
    add(Command("Shut down PC", "turn off power off computer", Icons.Filled.PowerSettingsNew, act(TileAction.Kind.PcPower, "shutdown")))
    add(Command("Remote", "pc control tv", Icons.Filled.SettingsRemote) { nav(Routes.PC) })
    add(Command("TV · movies & shows", "films series watch", Icons.Filled.Movie) { nav(Routes.TV) })
    add(Command("Customise Nexus", "theme look colours style", Icons.Filled.Palette) { nav(Routes.CUSTOMIZE) })
    add(Command("Nexus settings", "pair stremio update", Icons.Filled.Tune) { nav(Routes.SETTINGS) })
    add(Command("Edit home screen", "layout blocks tiles", Icons.Filled.Edit) { appContainerEditHome() })
    add(Command("All apps", "drawer", Icons.Filled.Apps) { HomeDrawerHub.open() })
    add(Command("Notifications", "shade", Icons.Filled.Notifications, act(TileAction.Kind.Notifications)))
    add(Command("Quick settings", "toggles", Icons.Filled.Tune, act(TileAction.Kind.QuickSettings)))
    add(Command("Lock screen", "lock phone", Icons.Filled.Lock, act(TileAction.Kind.LockScreen)))
    SETTINGS_TARGETS.forEach { (k, l) -> add(Command("$l settings", "phone settings $k", Icons.Filled.Settings, act(TileAction.Kind.Settings, k))) }
}

/** Set by HomeScreen so the "Edit home screen" command can flip edit mode. */
var appContainerEditHome: () -> Unit = {}

private fun findContacts(context: android.content.Context, q: String): List<Contact> = runCatching {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
    val out = mutableListOf<Contact>()
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
        null, null, ContactsContract.Contacts.TIMES_CONTACTED + " DESC",
    )?.use { cur ->
        while (cur.moveToNext() && out.size < 4) {
            val id = cur.getLong(0)
            val number = if (cur.getInt(3) > 0) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id.toString()), null,
                )?.use { p -> if (p.moveToFirst()) p.getString(0) else null }
            } else null
            out += Contact(id, cur.getString(1) ?: "", cur.getString(2) ?: "", number)
        }
    }
    out
}.getOrDefault(emptyList())

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().plainTap(null).holoPanel(Nx.Cyan, 0.05f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hud(title), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        content()
    }
}

@Composable
private fun Hint2(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Nx.Muted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun AppRow(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        apps.forEach { a ->
            Column(Modifier.width(64.dp).nexusPress({ onLaunch(a) }), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(a.icon, null, Modifier.size(52.dp))
                Text(a.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    image: ImageBitmap? = null,
    accent: androidx.compose.ui.graphics.Color = Nx.Cyan,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().nexusPress(onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            image != null -> Image(image, null, Modifier.size(28.dp))
            icon != null -> Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Nx.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun ContactRow(c: Contact, onOpen: () -> Unit, onCall: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().nexusPress(onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Nx.Cyan.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(c.name.take(1).uppercase(), color = Nx.Cyan, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = Nx.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            c.number?.let { Text(it, color = Nx.Muted, style = MaterialTheme.typography.labelSmall) }
        }
        if (onCall != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Nx.Lime.copy(alpha = 0.18f)).nexusPress(onCall), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, "Call", tint = Nx.Lime)
            }
        }
    }
}
