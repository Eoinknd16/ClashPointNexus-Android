package com.eoinknd16.nexus.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Hosts real Android app widgets (the Google search bar, calendars, music
 * players...) inside Nexus' home screen, the same way any launcher does.
 *
 * Adding one is: allocate an id -> get the user's OK to bind it (Android
 * shows its own "allow Nexus to create widgets" dialog the first time) ->
 * run the widget's own setup screen if it has one -> place it.
 */
class WidgetHost(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    /** Set while a widget's configure activity is open; MainActivity forwards the result. */
    var pendingConfigure: ((Boolean) -> Unit)? = null

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedWith(compareBy({ appLabel(it).lowercase() }, { it.loadLabel(context.packageManager).lowercase() }))

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    /** Android's own permission dialog for binding this widget. */
    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    fun needsConfigure(id: Int): Boolean {
        val info = manager.getAppWidgetInfo(id) ?: return false
        if (info.configure == null) return false
        // Android 12+: widgets can say setup is optional -- skip it then.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        ) return false
        return true
    }

    /** Goes through the host so non-exported configure activities still open. */
    fun startConfigure(activity: Activity, id: Int, onDone: (Boolean) -> Unit) {
        pendingConfigure = onDone
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null) }
            .onFailure {
                pendingConfigure = null
                onDone(true) // can't open setup -- place it anyway; most widgets cope
            }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQUEST_CONFIGURE) return
        pendingConfigure?.invoke(resultCode == Activity.RESULT_OK)
        pendingConfigure = null
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(viewContext, id, info)
    }

    @Suppress("DEPRECATION")
    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        runCatching { view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp) }
    }

    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    companion object {
        const val HOST_ID = 4711
        const val REQUEST_CONFIGURE = 4712
    }
}
