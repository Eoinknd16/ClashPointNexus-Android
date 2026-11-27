package com.eoinknd16.nexus.look

import android.content.Context
import android.net.Uri
import com.eoinknd16.nexus.core.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A whole home-screen setup: the complete look (every page, block, tile,
 * folder, tray slot, colour, font, gesture, motion and tile style) plus the
 * icon pack and hidden apps.
 */
@Serializable
data class SavedSetup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** "manual", "autosave" or "before" (saved automatically before loading another). */
    val kind: String = "manual",
    val look: Look,
    val iconPack: String = "",
    val hidden: List<String> = emptyList(),
    val nexusVersion: String = "",
)

/** What the list shows without loading every file in full. */
data class SetupInfo(
    val id: String, val name: String, val createdAt: Long, val updatedAt: Long, val kind: String,
    val colours: List<String>, val pages: Int, val tiles: Int, val preset: String,
)

/**
 * Saved setups, one JSON file each in the app's private storage. Writes are
 * atomic (temp file then rename), so a crash mid-save never corrupts one.
 */
class SetupStore(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "setups").apply { mkdirs() }
    private val lock = Mutex()
    private val _list = MutableStateFlow<List<SetupInfo>>(emptyList())
    val list: StateFlow<List<SetupInfo>> = _list

    init { scope.launch { refresh() } }

    private fun file(id: String) = File(dir, "$id.json")

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _list.value = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { info(Http.json.decodeFromString(SavedSetup.serializer(), f.readText())) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    private fun info(s: SavedSetup): SetupInfo {
        val blocks = s.look.home.blocks + s.look.home.pages.flatMap { it.blocks }
        return SetupInfo(
            s.id, s.name, s.createdAt, s.updatedAt, s.kind,
            listOf(s.look.palette.background, s.look.palette.accent, s.look.palette.accent2, s.look.palette.warn, s.look.palette.text),
            1 + s.look.home.pages.size,
            blocks.sumOf { (it as? HomeBlock.Tiles)?.tiles?.size ?: 0 },
            s.look.presetName,
        )
    }

    suspend fun load(id: String): SavedSetup? = withContext(Dispatchers.IO) {
        runCatching { Http.json.decodeFromString(SavedSetup.serializer(), file(id).readText()) }.getOrNull()
    }

    private suspend fun write(s: SavedSetup) = withContext(Dispatchers.IO) {
        lock.withLock {
            val tmp = File(dir, "${s.id}.tmp")
            tmp.writeText(Http.json.encodeToString(SavedSetup.serializer(), s))
            tmp.renameTo(file(s.id))
        }
        refresh()
    }

    /** Save [look] as a new setup. */
    suspend fun save(name: String, look: Look, iconPack: String, hidden: List<String>, kind: String = "manual"): SavedSetup {
        val now = System.currentTimeMillis()
        val s = SavedSetup(newId(), name.ifBlank { "Setup" }, now, now, kind, look, iconPack, hidden, versionName())
        write(s)
        if (kind != "manual") prune(kind)
        return s
    }

    /** Replace a saved setup's contents with [look] (keeps its name). */
    suspend fun overwrite(id: String, look: Look, iconPack: String, hidden: List<String>) {
        val old = load(id) ?: return
        write(old.copy(look = look, iconPack = iconPack, hidden = hidden, updatedAt = System.currentTimeMillis(), kind = "manual", nexusVersion = versionName()))
    }

    suspend fun rename(id: String, name: String) {
        val old = load(id) ?: return
        write(old.copy(name = name.ifBlank { old.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: String) {
        val old = load(id) ?: return
        val now = System.currentTimeMillis()
        write(old.copy(id = newId(), name = "${old.name} copy", createdAt = now, updatedAt = now, kind = "manual"))
    }

    /** Keep an automatic backup as a proper saved setup. */
    suspend fun keep(id: String) {
        val old = load(id) ?: return
        write(old.copy(kind = "manual", name = old.name.removePrefix("Autosave · ").removePrefix("Before loading ")))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        file(id).delete()
        refresh()
    }

    /** Automatic backups don't pile up forever: keep the newest 12 of each kind. */
    private suspend fun prune(kind: String) = withContext(Dispatchers.IO) {
        _list.value.filter { it.kind == kind }.drop(12).forEach { file(it.id).delete() }
        refresh()
    }

    suspend fun exportTo(id: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = file(id).readText()
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            true
        }.getOrDefault(false)
    }

    /** Import a setup file (from this or another phone). Returns its name, or null if it isn't one. */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val s = Http.json.decodeFromString(SavedSetup.serializer(), text)
            val now = System.currentTimeMillis()
            write(s.copy(id = newId(), kind = "manual", createdAt = now, updatedAt = now))
            s.name
        }.getOrNull()
    }

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
}
