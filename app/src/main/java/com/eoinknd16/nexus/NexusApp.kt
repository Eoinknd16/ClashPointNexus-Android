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

class NexusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
        kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.builtins.serializer<String>(), kotlinx.serialization.builtins.serializer<Long>()),
        emptyMap(), scope,
    )

    val weather = WeatherRepository(app)
    val widgets = WidgetHost(app)
    val updater = com.eoinknd16.nexus.update.Updater(app)

    init {
        // Push every saved change into the live UI state.
        scope.launch(Dispatchers.Main) { lookStore.state.collect { LookState.apply(it) } }
        scope.launch { updater.checkIfDue() }
    }

    /** Convenience for "change one thing in the look". */
    fun editLook(transform: (Look) -> Look) {
        scope.launch { lookStore.update(transform) }
    }
}
