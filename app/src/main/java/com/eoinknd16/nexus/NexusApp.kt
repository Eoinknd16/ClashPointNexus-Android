package com.eoinknd16.nexus

import android.app.Application
import com.eoinknd16.nexus.core.JsonStore
import com.eoinknd16.nexus.core.nexusDataStore
import com.eoinknd16.nexus.launcher.AppsRepository
import com.eoinknd16.nexus.launcher.LauncherPrefs
import com.eoinknd16.nexus.link.PcLinkClient
import com.eoinknd16.nexus.link.PcLinkConfig
import com.eoinknd16.nexus.media.MediaRepository
import com.eoinknd16.nexus.media.PlaybackSession
import com.eoinknd16.nexus.media.StremioAccountState
import com.eoinknd16.nexus.look.Look
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.weather.WeatherRepository
import com.eoinknd16.nexus.widgets.WidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer

class NexusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.eoinknd16.nexus.ui.fx.TvMode.enabled = BuildConfig.FLAVOR == "tv" ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        CrashReports.install(this)
        container = AppContainer(this)
    }
}

/**
 * If Nexus crashes, the reason is saved to a file and shown on home the next
 * time it opens, with a Copy button, so it can be sent over and fixed.
 */
object CrashReports {
    private const val FILE = "last_crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                val text = buildString {
                    appendLine("Nexus $version · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Thread: ${thread.name} · ${java.util.Date()}")
                    appendLine(android.util.Log.getStackTraceString(error).take(12_000))
                }
                java.io.File(app.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: android.content.Context): String? =
        runCatching { java.io.File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: android.content.Context) {
        runCatching { java.io.File(context.filesDir, FILE).delete() }
    }
}

/** Hand-rolled dependency container -- the app is small enough not to need Hilt. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ds = app.nexusDataStore

    val pcStore = JsonStore(ds, "pc_link", PcLinkConfig.serializer(), PcLinkConfig(), scope)
    /** Every paired PC (the active one is [pcStore]). */
    val pcSaved = JsonStore(ds, "pc_saved", com.eoinknd16.nexus.link.SavedPcs.serializer(), com.eoinknd16.nexus.link.SavedPcs(), scope)
    val pc = PcLinkClient(pcStore, pcSaved)
    val spotify = com.eoinknd16.nexus.spotify.Spotify(
        app, JsonStore(ds, "spotify", com.eoinknd16.nexus.spotify.SpotifyPrefs.serializer(), com.eoinknd16.nexus.spotify.SpotifyPrefs(), scope), scope,
    )

    val media = MediaRepository(
        addonStore = JsonStore(ds, "addons", MediaRepository.addonListSerializer, emptyList(), scope),
        progressStore = JsonStore(ds, "progress", MediaRepository.progressSerializer, emptyMap(), scope),
        libraryStore = JsonStore(ds, "library", MediaRepository.librarySerializer, emptyList(), scope),
        accountStore = JsonStore(ds, "stremio_account", StremioAccountState.serializer(), StremioAccountState(), scope),
        pcStore = pcStore,
        pc = pc,
    )

    val playback = PlaybackSession()

    val apps = AppsRepository(
        context = app,
        prefs = JsonStore(ds, "launcher", LauncherPrefs.serializer(), LauncherPrefs(), scope),
        scope = scope,
    )

    /** The whole customisation tree (see look/Look.kt). */
    val lookStore = JsonStore(ds, "look", Look.serializer(), Look(), scope)

    /** Set by Customise > "Edit home screen"; HomeScreen consumes it. */
    val homeEditRequest = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Spotify, YouTube and anything else playing, for the Resume block. */
    val externalMedia = com.eoinknd16.nexus.media.ExternalMedia(
        app, JsonStore(ds, "external_media", com.eoinknd16.nexus.media.ExternalMedia.recentSerializer, emptyList(), scope), scope,
    )

    /** Nexus resume cards hidden from home: title id -> when (re-shown once watched again). */
    val resumeHidden = JsonStore(
        ds, "resume_hidden",
        kotlinx.serialization.builtins.MapSerializer(String.serializer(), Long.serializer()),
        emptyMap<String, Long>(), scope,
    )

    /** Your Claude API key for "Ask Nexus" (stays on the phone). */
    val aiPrefs = JsonStore(ds, "ai_prefs", com.eoinknd16.nexus.ai.AiPrefs.serializer(), com.eoinknd16.nexus.ai.AiPrefs(), scope)

    /** Saved whole-home setups and the automatic backups (see look/Setups.kt). */
    val setups = com.eoinknd16.nexus.look.SetupStore(app, scope)

    /** Save everything as it is now under [name]. */
    suspend fun saveSetup(name: String, kind: String = "manual"): com.eoinknd16.nexus.look.SavedSetup {
        val p = apps.prefs.get()
        return setups.save(name, lookStore.get(), p.iconPack, p.hidden, kind)
    }

    /** Overwrite a saved setup with how things are now. */
    suspend fun updateSetup(id: String) {
        val p = apps.prefs.get()
        setups.overwrite(id, lookStore.get(), p.iconPack, p.hidden)
    }

    /** Switch to a saved setup. What you had is saved first ("Before loading …"), and it can be undone too. */
    suspend fun loadSetup(id: String): Boolean {
        val s = setups.load(id) ?: return false
        saveSetup("Before loading ${s.name}", kind = "before")
        editLook("“${s.name}” loaded") { s.look }
        apps.prefs.update { it.copy(iconPack = s.iconPack, hidden = s.hidden) }
        return true
    }

    val weather = WeatherRepository(app)
    val widgets = WidgetHost(app)
    val updater = com.eoinknd16.nexus.update.Updater(app)

    init {
        // Push every saved change into the live UI state.
        scope.launch(Dispatchers.Main) { lookStore.state.collect { LookState.apply(it) } }
        scope.launch { updater.checkIfDue() }
        // Autosave: a backup of the whole setup when it first starts (so nothing you have now can be
        // lost), then again whenever it's changed and the last autosave is 15+ minutes old.
        scope.launch {
            kotlinx.coroutines.delay(4_000) // let the saved look load first
            fun stamp() = "Autosave · " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            setups.refresh()
            if (setups.list.value.none { it.kind == "autosave" }) saveSetup(stamp(), "autosave")
            var lastSaved = lookStore.get()
            var lastAt = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(60_000)
                val now = lookStore.get()
                if (now != lastSaved && System.currentTimeMillis() - lastAt > 15 * 60_000L) {
                    saveSetup(stamp(), "autosave")
                    lastSaved = now
                    lastAt = System.currentTimeMillis()
                }
            }
        }
    }

    // ---- Undo ---------------------------------------------------------------
    private val lookHistory = ArrayDeque<Look>()
    private var lastSnapshotAt = 0L
    /** True when there's a change to undo (drives Undo buttons). */
    val canUndo = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** "Tile deleted" etc: shows a snackbar with Undo for a few seconds. */
    val undoPrompt = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /**
     * Change the look. Every change can be undone; quick bursts (typing,
     * nudging) collapse into one undo step. [undoLabel] also shows the Undo
     * snackbar -- use it for deletes and other big changes.
     */
    fun editLook(undoLabel: String? = null, transform: (Look) -> Look) {
        scope.launch {
            val before = lookStore.get()
            val after = transform(before)
            if (after == before) return@launch
            val now = System.currentTimeMillis()
            if (undoLabel != null || now - lastSnapshotAt > 1_200 || lookHistory.isEmpty()) {
                lookHistory.addLast(before)
                while (lookHistory.size > 60) lookHistory.removeFirst()
            }
            lastSnapshotAt = now
            lookStore.set(after)
            canUndo.value = true
            if (undoLabel != null) undoPrompt.value = undoLabel
        }
    }

    fun undoLook() {
        scope.launch {
            val prev = lookHistory.removeLastOrNull() ?: return@launch
            lookStore.set(prev)
            lastSnapshotAt = 0L
            canUndo.value = lookHistory.isNotEmpty()
            undoPrompt.value = null
        }
    }
}
