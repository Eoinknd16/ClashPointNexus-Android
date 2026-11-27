package com.eoinknd16.nexus.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.eoinknd16.nexus.core.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    val key: String get() = component.flattenToShortString()
}

/** One of an app's shortcuts. */
data class AppShortcut(
    val id: String,
    val label: String,
    val app: LaunchableApp,
    val icon: ImageBitmap?,
    val info: android.content.pm.ShortcutInfo,
)

@Serializable
data class LauncherPrefs(
    /** Component keys pinned to the home dock, in order. */
    val pinned: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    /** Package of the chosen icon pack ("" = the apps' own icons). */
    val iconPack: String = "",
    /** How often / when each app is opened through Nexus (for suggestions). Stays on the phone. */
    val usage: Map<String, AppUsage> = emptyMap(),
)

@Serializable
data class AppUsage(
    val count: Int = 0,
    /** Opens per hour of the day (24 buckets). */
    val hours: List<Int> = List(24) { 0 },
    val last: Long = 0,
)

/**
 * Installed apps via LauncherApps (the API real launchers use): includes
 * work-profile apps, and fires callbacks on install/uninstall/update so the
 * drawer stays current without polling.
 */
class AppsRepository(
    private val context: Context,
    val prefs: JsonStore<LauncherPrefs>,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps

    private val iconSizePx = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        })
        // Locale changes rename apps.
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) = refresh()
            },
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    init {
        // Reload icons once saved settings arrive (or the icon pack changes).
        scope.launch {
            var last: String? = null
            prefs.state.collect { p ->
                if (last != null && p.iconPack != last) refresh()
                if (last == null && p.iconPack.isNotBlank()) refresh()
                last = p.iconPack
            }
        }
    }

    fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.Default) { load() }
        }
    }

    private var pack: IconPack? = null

    private fun load(): List<LaunchableApp> {
        val self = context.packageName
        val packName = runCatching { prefs.value.iconPack }.getOrDefault("")
        if (pack?.packageName != packName) {
            pack = if (packName.isBlank()) null else runCatching { IconPack(context, packName) }.getOrNull()
        }
        val iconPack = pack
        return launcherApps.profiles.flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != self }
            .map { info ->
                val themed = iconPack?.let { p -> runCatching { p.icon(info.componentName) }.getOrNull() }
                LaunchableApp(
                    label = info.label?.toString() ?: info.componentName.packageName,
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = info.user,
                    icon = runCatching { (themed ?: info.getBadgedIcon(0)).toBitmap(iconSizePx, iconSizePx) }
                        .getOrElse { info.getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx) }.asImageBitmap(),
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /** [bounds] = where the icon was on screen, for the opening animation. */
    fun launch(app: LaunchableApp, bounds: android.graphics.Rect? = null, options: android.os.Bundle? = null) {
        recordLaunch(app.key)
        runCatching {
            launcherApps.startMainActivity(app.component, app.user, bounds, options)
        }.onFailure {
            context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let(context::startActivity)
        }
    }

    fun openAppInfo(app: LaunchableApp) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun recordLaunch(key: String) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        scope.launch {
            runCatching {
                prefs.update { p ->
                    val u = p.usage[key] ?: AppUsage()
                    val hours = u.hours.takeIf { it.size == 24 } ?: List(24) { 0 }
                    val updated = u.copy(count = u.count + 1, hours = hours.mapIndexed { i, n -> if (i == hour) n + 1 else n }, last = System.currentTimeMillis())
                    // Keep the list bounded: drop the least-used if it grows huge.
                    val all = p.usage + (key to updated)
                    p.copy(usage = if (all.size > 300) all.entries.sortedByDescending { it.value.count }.take(250).associate { it.toPair() } else all)
                }
            }
        }
    }

    /**
     * Apps you're likely to want right now: what you usually open around this
     * hour, plus what you open most and most recently. Hidden apps never appear.
     */
    fun suggestions(max: Int = 5, exclude: Set<String> = emptySet()): List<LaunchableApp> {
        val p = prefs.value
        if (p.usage.isEmpty()) return emptyList()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val now = System.currentTimeMillis()
        val byKey = _apps.value.associateBy { it.key }
        return p.usage.entries
            .filter { it.key in byKey && it.key !in p.hidden && it.key !in exclude }
            .map { (k, u) ->
                val h = u.hours.takeIf { it.size == 24 } ?: List(24) { 0 }
                val near = h[hour] * 3.0 + h[(hour + 23) % 24] * 1.5 + h[(hour + 1) % 24] * 1.5
                val hoursAgo = (now - u.last) / 3_600_000.0
                val recency = 4.0 / (1.0 + hoursAgo / 6.0)
                k to (near + kotlin.math.ln(1.0 + u.count) * 2 + recency)
            }
            .sortedByDescending { it.second }
            .take(max)
            .mapNotNull { byKey[it.first] }
    }

    /**
     * The app's own shortcuts ("New message", "Navigate home"...), as long as
     * Nexus is the default home app (Android only shares them with that).
     */
    fun shortcuts(app: LaunchableApp, max: Int = 5): List<AppShortcut> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val density = context.resources.displayMetrics.densityDpi
        launcherApps.getShortcuts(q, app.user).orEmpty()
            .filter { it.isEnabled && (it.activity == null || it.activity?.packageName == app.packageName) }
            .sortedBy { it.rank }
            .distinctBy { it.id }
            .take(max)
            .map { info ->
                AppShortcut(
                    id = info.id,
                    label = (info.shortLabel ?: info.longLabel ?: "").toString(),
                    app = app,
                    icon = runCatching { launcherApps.getShortcutIconDrawable(info, density)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull(),
                    info = info,
                )
            }
    }.getOrDefault(emptyList())

    fun startShortcut(s: AppShortcut, bounds: android.graphics.Rect? = null) {
        runCatching { launcherApps.startShortcut(s.info, bounds, null) }.onFailure { launch(s.app, bounds) }
    }

    suspend fun togglePin(app: LaunchableApp) = prefs.update { p ->
        if (app.key in p.pinned) p.copy(pinned = p.pinned - app.key) else p.copy(pinned = p.pinned + app.key)
    }

    fun iconPacks(): List<Pair<String, String>> = runCatching { IconPack.installed(context) }.getOrDefault(emptyList())

    /** Switch icon pack ("" = none) and reload every icon. */
    fun setIconPack(pkg: String) {
        scope.launch { prefs.update { it.copy(iconPack = pkg) } }
    }

    suspend fun toggleHidden(app: LaunchableApp) = prefs.update { p ->
        if (app.key in p.hidden) p.copy(hidden = p.hidden - app.key) else p.copy(hidden = p.hidden + app.key)
    }
}
