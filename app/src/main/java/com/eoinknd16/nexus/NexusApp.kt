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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val pc = PcLinkClient(pcStore)

    val media = MediaRepository(
        addonStore = JsonStore(ds, "addons", MediaRepository.addonListSerializer, emptyList(), scope),
        progressStore = JsonStore(ds, "progress", MediaRepository.progressSerializer, emptyMap(), scope),
        libraryStore = JsonStore(ds, "library", MediaRepository.librarySerializer, emptyList(), scope),
        pcStore = pcStore,
        pc = pc,
    )

    val playback = PlaybackSession()

    val apps = AppsRepository(
        context = app,
        prefs = JsonStore(ds, "launcher", LauncherPrefs.serializer(), LauncherPrefs(), scope),
        scope = scope,
    )
}
