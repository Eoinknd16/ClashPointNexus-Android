package com.eoinknd16.nexus.ui.home

import com.eoinknd16.nexus.core.describe

import com.eoinknd16.nexus.look.allBlocks

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import com.eoinknd16.nexus.core.NexusAccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.navigation.NavController
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.link.WakeOnLan
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.SearchEngine
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.ui.Routes
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Nexus screens a tile can open, by the key stored in TileAction.arg. */
val SCREEN_TARGETS = listOf(
    "tv" to "TV",
    "search" to "Search",
    "pc" to "PC remote",
    "apps" to "App drawer",
    "settings" to "Settings",
    "customize" to "Customise",
    "addons" to "Addons",
)

/** Android settings pages a tile can open. */
val SETTINGS_TARGETS = listOf(
    "all" to "All settings",
    "wifi" to "Wi-Fi",
    "bluetooth" to "Bluetooth",
    "display" to "Display",
    "sound" to "Sound",
    "battery" to "Battery",
    "apps" to "Apps",
    "location" to "Location",
    "home" to "Home app",
)

/** PC remote buttons (must match the PC's NAV_ACTIONS whitelist). */
val PC_NAV_TARGETS = listOf(
    "up" to "Up", "down" to "Down", "left" to "Left", "right" to "Right", "confirm" to "OK", "back" to "Back",
    "menu" to "Menu", "quickMenu" to "Quick menu", "search" to "Search", "volumeUp" to "Volume up",
    "volumeDown" to "Volume down", "toggleSubtitles" to "Subtitles", "skipNext" to "Next episode",
)

val PC_SCREEN_TARGETS = listOf(
    "home" to "Home", "tv" to "TV", "games" to "Games", "apps" to "Apps", "library" to "Library",
    "store" to "Store", "browse" to "Browser", "files" to "Files", "settings" to "Settings",
)

fun screenRoute(key: String): String? = when (key) {
    "tv" -> Routes.TV
    "search" -> Routes.SEARCH
    "pc" -> Routes.PC
    "apps" -> Routes.APPS
    "settings" -> Routes.SETTINGS
    "customize" -> Routes.CUSTOMIZE
    "addons" -> Routes.ADDONS
    else -> null
}

/** Runs what a tile/dock slot is set to do. Folders are opened by the caller (they need UI state). */
class TileRunner(
    private val context: Context,
    private val c: AppContainer,
    private val nav: NavController,
    private val toast: (String) -> Unit,
    /** The Compose host view, needed to anchor the app-opening animation. */
    private val view: android.view.View? = null,
) {
    /** Opens an app with Customise › Motion › App opening animation, grown from [from] (root coords). */
    fun launch(app: LaunchableApp, from: androidx.compose.ui.geometry.Rect? = null) {
        val motion = LookState.look.motion
        val r = from?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        val v = view
        val opts = if (!motion.animations || v == null) null else when (motion.appLaunch) {
            com.eoinknd16.nexus.look.AppLaunch.Zoom -> r?.let { android.app.ActivityOptions.makeScaleUpAnimation(v, it.left, it.top, it.width(), it.height()) }
            com.eoinknd16.nexus.look.AppLaunch.Reveal -> r?.let { android.app.ActivityOptions.makeClipRevealAnimation(v, it.left, it.top, it.width(), it.height()) }
            com.eoinknd16.nexus.look.AppLaunch.Fade -> android.app.ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            com.eoinknd16.nexus.look.AppLaunch.System -> null
        }
        c.apps.launch(app, r, opts?.toBundle())
    }

    fun app(key: String): LaunchableApp? = c.apps.apps.value.firstOrNull { it.key == key }

    fun appIcon(tile: TileConfig): ImageBitmap? =
        if (tile.action.kind == TileAction.Kind.LaunchApp && tile.useAppIcon) app(tile.action.arg)?.icon else null

    fun run(tile: TileConfig, from: androidx.compose.ui.geometry.Rect? = null, swipe: Int = 0) {
        val a = tile.action
        when (a.kind) {
            TileAction.Kind.Screen -> screenRoute(a.arg)?.let { nav.navigate(it) } ?: toast("Unknown screen")
            TileAction.Kind.LaunchApp -> app(a.arg)?.let { launch(it, from) } ?: toast("App not installed")
            TileAction.Kind.WakePc -> c.scope.launch {
                // arg = a saved PC's key, blank = whichever PC is active.
                val cfg = c.pc.all().firstOrNull { it.key == a.arg } ?: c.pcStore.get()
                when (val sent = c.pc.wake(cfg)) {
                    -1 -> toast(if (cfg.isPaired) "No MAC address on file for ${cfg.displayName}" else "Link your PC first")
                    0 -> toast("Couldn't send wake packet")
                    else -> toast("Waking ${cfg.displayName}")
                }
            }
            TileAction.Kind.PcHub -> PcHub.open = true
            TileAction.Kind.PcPower -> c.scope.launch {
                val what = a.arg.ifBlank { "shutdown" }
                runCatching { c.pc.power(what) }.fold(
                    onSuccess = { r ->
                        toast(
                            if (!r.ok) r.error ?: "PC refused" else when (what) {
                                "shutdown" -> "Shutting down in 20 s"
                                "restart" -> "Restarting in 20 s"
                                "cancel" -> "Cancelled"
                                else -> "PC going to sleep"
                            },
                        )
                    },
                    onFailure = { toast("PC didn't respond (${it.describe()})") },
                )
            }
            TileAction.Kind.SwitchPc -> c.scope.launch {
                val to = if (a.arg.isBlank() || a.arg == "next") c.pc.switchNext() else c.pc.switchTo(a.arg)
                toast(if (to == null) "Link a PC first" else "Now controlling ${to.displayName}")
            }
            TileAction.Kind.PcNav -> c.scope.launch {
                runCatching { c.pc.nav(a.arg) }.onFailure { toast("PC didn't respond") }
            }
            TileAction.Kind.PcScreen -> c.scope.launch {
                runCatching { c.pc.openScreen(a.arg) }.onFailure { toast("PC didn't respond") }
            }
            TileAction.Kind.Url -> openUrl(a.arg)
            TileAction.Kind.WebSearch -> webSearch(a.arg)
            TileAction.Kind.Settings -> openSettings(a.arg)
            TileAction.Kind.Notifications -> expandNotifications(context)
            TileAction.Kind.QuickSettings -> {
                if (!NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) expandQuickSettings(context)
            }
            TileAction.Kind.LockScreen -> if (!NexusAccessibilityService.lockScreen()) needsAccessibility()
            TileAction.Kind.Recents -> if (!NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_RECENTS)) needsAccessibility()
            TileAction.Kind.Page -> com.eoinknd16.nexus.look.HomePager.go(a.arg.ifBlank { "next" }, swipe)
            TileAction.Kind.Folder, TileAction.Kind.None -> {}
        }
    }

    private fun needsAccessibility() {
        toast("Turn on \"Nexus gestures\" in Accessibility to use this")
        NexusAccessibilityService.openSettings(context)
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return toast("No link set")
        val fixed = if (Regex("^[a-z]+://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url else "https://$url"
        start(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    }

    fun webSearch(query: String, engine: SearchEngine? = null, customUrl: String? = null) {
        val bar = LookState.look.allBlocks.filterIsInstance<HomeBlock.SearchBar>().firstOrNull()
        val e = engine ?: bar?.engine ?: SearchEngine.Google
        val template = if (e == SearchEngine.Custom) (customUrl ?: bar?.customUrl ?: SearchEngine.Google.url) else e.url
        if (query.isBlank()) {
            // No query: open the engine's front page.
            openUrl(template.substringBefore("?").substringBefore("%s"))
            return
        }
        openUrl(template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8")))
    }

    fun openSettings(key: String) {
        val action = when (key) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "home" -> Settings.ACTION_HOME_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        if (!start(Intent(action))) start(Intent(Settings.ACTION_SETTINGS))
    }

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess.also { if (!it) toast("Nothing on this phone can open that") }

    companion object {
        /** Pulls the notification shade down (needs EXPAND_STATUS_BAR, granted at install). */
        @SuppressLint("WrongConstant")
        fun expandQuickSettings(context: Context) {
            runCatching {
                val sbm = context.getSystemService("statusbar")
                Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sbm)
            }
        }

        @SuppressLint("WrongConstant")
        fun expandNotifications(context: Context) {
            runCatching {
                val sbm = context.getSystemService("statusbar")
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
            }
        }
    }
}
