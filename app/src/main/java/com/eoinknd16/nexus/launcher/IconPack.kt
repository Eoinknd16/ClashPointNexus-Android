package com.eoinknd16.nexus.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Standard launcher icon packs (the ADW / Nova format most packs on the Play
 * Store ship): an appfilter.xml mapping each app's component to a drawable in
 * the pack. Apps the pack doesn't cover keep their normal icon.
 */
class IconPack(private val context: Context, val packageName: String) {
    private val res = context.packageManager.getResourcesForApplication(packageName)
    private val map: Map<String, String> = runCatching { parse() }.getOrDefault(emptyMap())

    val size: Int get() = map.size

    fun icon(component: ComponentName): Drawable? {
        val name = map[component.flattenToString()] ?: map["${component.packageName}/"] ?: return null
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun parse(): Map<String, String> {
        val parser: XmlPullParser = run {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) res.getXml(id)
            else {
                val stream = context.createPackageContext(packageName, 0).assets.open("appfilter.xml")
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
            }
        }
        val out = HashMap<String, String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null) {
                    // "ComponentInfo{com.app/com.app.MainActivity}"
                    val inner = comp.substringAfter('{', "").substringBefore('}', "")
                    val pkg = inner.substringBefore('/', "")
                    var cls = inner.substringAfter('/', "")
                    if (pkg.isNotEmpty() && cls.isNotEmpty()) {
                        if (cls.startsWith(".")) cls = pkg + cls
                        out.putIfAbsent(ComponentName(pkg, cls).flattenToString(), drawable)
                        out.putIfAbsent("$pkg/", drawable) // fallback for the app's other activities
                    }
                }
            }
            ev = parser.next()
        }
        return out
    }

    companion object {
        private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "org.adw.ActivityStarter.THEMES", "com.gau.go.launcherex.theme")

        /** Installed icon packs: package name to label. */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return ACTIONS.flatMap { a ->
                runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
}
