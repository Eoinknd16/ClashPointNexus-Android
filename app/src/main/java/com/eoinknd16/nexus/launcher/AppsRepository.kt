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

@Serializable
data class LauncherPrefs(
    /** Component keys pinned to the home dock, in order. */
    val pinned: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
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

    fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.Default) { load() }
        }
    }

    private fun load(): List<LaunchableApp> {
        val self = context.packageName
        return launcherApps.profiles.flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != self }
            .map { info ->
                LaunchableApp(
                    label = info.label?.toString() ?: info.componentName.packageName,
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = info.user,
                    icon = info.getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(app: LaunchableApp) {
        runCatching {
            launcherApps.startMainActivity(app.component, app.user, null, null)
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

    suspend fun togglePin(app: LaunchableApp) = prefs.update { p ->
        if (app.key in p.pinned) p.copy(pinned = p.pinned - app.key) else p.copy(pinned = p.pinned + app.key)
    }

    suspend fun toggleHidden(app: LaunchableApp) = prefs.update { p ->
        if (app.key in p.hidden) p.copy(hidden = p.hidden - app.key) else p.copy(hidden = p.hidden + app.key)
    }
}
