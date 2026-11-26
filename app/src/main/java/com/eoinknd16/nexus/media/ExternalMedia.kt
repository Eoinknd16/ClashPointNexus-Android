package com.eoinknd16.nexus.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import com.eoinknd16.nexus.core.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Only here so Android lets Nexus read the media sessions other apps publish
 * (Spotify, YouTube, podcasts…). It never reads or stores notifications.
 */
class NexusMediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance?.refresh()
    }
    companion object {
        @Volatile var instance: ExternalMedia? = null
    }
}

/** Something another app is playing (or played recently). */
data class ExternalMediaItem(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val art: android.graphics.Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Null when the app's session has gone (we resume it with a media button). */
    val controller: MediaController?,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Serializable
data class ExternalRecent(
    val packageName: String,
    val title: String,
    val subtitle: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)

/**
 * Watches every app's media session through Android's MediaSessionManager.
 * Live sessions get play/pause/skip; sessions that have ended are remembered
 * (with their artwork cached) so "resume" still works after the app closes.
 */
class ExternalMedia(
    private val context: Context,
    private val store: JsonStore<List<ExternalRecent>>,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, NexusMediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val artDir = File(context.cacheDir, "media_art").apply { mkdirs() }

    private val _items = MutableStateFlow<List<ExternalMediaItem>>(emptyList())
    val items: StateFlow<List<ExternalMediaItem>> = _items

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private var live: List<MediaController> = emptyList()
    private var registered = false

    init {
        NexusMediaListener.instance = this
        scope.launch { store.state.collect { publish() } }
    }

    fun accessGranted(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == listener }
    }

    fun openAccessSettings(from: Context) {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { from.startActivity(i) }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> main.post { track(list.orEmpty()) } }

    /** Call when home appears (cheap): picks up access granted in Settings. */
    fun refresh() = main.post {
        _hasAccess.value = accessGranted()
        if (!_hasAccess.value) return@post
        runCatching {
            if (!registered) {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
                registered = true
            }
            track(manager.getActiveSessions(listener))
        }
    }

    private fun track(list: List<MediaController>) {
        (callbacks.keys - list.toSet()).forEach { c -> callbacks.remove(c)?.let { c.unregisterCallback(it) } }
        list.filter { it !in callbacks }.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = remember(c)
                override fun onMetadataChanged(metadata: MediaMetadata?) = remember(c)
                override fun onSessionDestroyed() { main.post { track(live - c) } }
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
        live = list.filter { it.packageName != context.packageName }
        live.forEach { remember(it) }
        publish()
    }

    /** Save what a session is playing so it can be resumed once the app has gone. */
    private fun remember(c: MediaController) {
        val md = c.metadata ?: return publish()
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return publish()
        val sub = listOfNotNull(
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
        ).joinToString(" · ")
        art(md)?.let { bmp ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { File(artDir, "${c.packageName}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
        }
        val entry = ExternalRecent(c.packageName, title, sub, position(c), md.getLong(MediaMetadata.METADATA_KEY_DURATION), System.currentTimeMillis())
        scope.launch {
            store.update { old -> (listOf(entry) + old.filterNot { it.packageName == c.packageName }).take(12) }
        }
        publish()
    }

    private fun art(md: MediaMetadata): Bitmap? =
        md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun position(c: MediaController): Long {
        val s = c.playbackState ?: return 0
        var p = s.position
        if (s.state == PlaybackState.STATE_PLAYING) p += ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        return p.coerceAtLeast(0)
    }

    private fun publish() {
        val liveByPkg = live.associateBy { it.packageName }
        val recents = store.value
        val out = mutableListOf<ExternalMediaItem>()
        live.forEach { c ->
            val md = c.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return@forEach
            out += ExternalMediaItem(
                packageName = c.packageName,
                title = title,
                subtitle = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
                art = art(md) ?: cachedArt(c.packageName),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                positionMs = position(c),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                updatedAt = recents.firstOrNull { it.packageName == c.packageName }?.updatedAt ?: System.currentTimeMillis(),
                controller = c,
            )
        }
        recents.filter { it.packageName !in liveByPkg }.forEach { r ->
            out += ExternalMediaItem(r.packageName, r.title, r.subtitle, cachedArt(r.packageName), false, r.positionMs, r.durationMs, r.updatedAt, null)
        }
        _items.value = out.sortedWith(compareByDescending<ExternalMediaItem> { it.playing }.thenByDescending { it.updatedAt })
    }

    private val artCache = mutableMapOf<String, Pair<Long, Bitmap?>>()
    private fun cachedArt(pkg: String): Bitmap? {
        val f = File(artDir, "$pkg.jpg")
        if (!f.exists()) return null
        val stamp = f.lastModified()
        artCache[pkg]?.takeIf { it.first == stamp }?.let { return it.second }
        val bmp = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
        artCache[pkg] = stamp to bmp
        return bmp
    }

    fun playPause(item: ExternalMediaItem) {
        val c = item.controller
        if (c != null) {
            if (item.playing) c.transportControls.pause() else c.transportControls.play()
        } else resumeDead(item.packageName)
    }

    fun next(item: ExternalMediaItem) = item.controller?.transportControls?.skipToNext() ?: Unit
    fun previous(item: ExternalMediaItem) = item.controller?.transportControls?.skipToPrevious() ?: Unit

    /** The app's session is gone: wake it with a Play media button, like headphones do. */
    private fun resumeDead(pkg: String) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
            runCatching { context.sendBroadcast(i) }
        }
        main.postDelayed({ refresh() }, 1500)
    }

    /** Opens the app itself (tapping the card). */
    fun open(item: ExternalMediaItem, from: Context) {
        val pending = item.controller?.sessionActivity
        if (pending != null && runCatching { pending.send() }.isSuccess) return
        context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
            runCatching { from.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun forget(pkg: String) = scope.launch { store.update { it.filterNot { r -> r.packageName == pkg } } }

    companion object {
        val recentSerializer = ListSerializer(ExternalRecent.serializer())
    }
}
