package com.eoinknd16.nexus.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.eoinknd16.nexus.BuildConfig
import com.eoinknd16.nexus.NexusApp
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Installing(val version: String) : UpdateState
    /** Android needs "Install unknown apps" allowed for Nexus first. */
    data class NeedsPermission(val version: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-updates from this repo's GitHub Releases: every push to main publishes
 * vX.Y.<build> with the APK attached, signed with the same key every time, so
 * Android installs it straight over the current app and nothing is lost.
 *
 * Check -> download the APK -> hand it to Android's PackageInstaller, which
 * shows its own "Update Nexus?" confirmation.
 */
class Updater(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Auto-check at most every 6 hours. */
    suspend fun checkIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("lastCheck", 0) > 6 * 3_600_000L) check()
    }

    suspend fun check(): UpdateState {
        _state.value = UpdateState.Checking
        val result = runCatching {
            val raw = Http.getText(
                "https://api.github.com/repos/$REPO/releases/latest",
                mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Nexus-Android"),
                timeoutMs = 12_000,
            )
            val root = Http.json.parseToJsonElement(raw).jsonObject
            val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: error("No release found")
            val version = tag.removePrefix("v")
            val asset = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull {
                    val name = (it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    // The release carries the phone APK and the Fire TV one (…-tv.apk).
                    if (com.eoinknd16.nexus.BuildConfig.FLAVOR == "tv") name.endsWith("-tv.apk") else name.endsWith(".apk") && !name.endsWith("-tv.apk")
                }
                ?: error("Release $tag has no APK")
            val url = (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: error("No download link")
            val size = (asset["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            val notes = (root["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            if (isNewer(version, currentVersion)) UpdateState.Available(version, notes, url, size) else UpdateState.UpToDate(currentVersion)
        }.getOrElse { e ->
            UpdateState.Failed(if (e is HttpException && e.code == 403) "GitHub rate limit, try again later" else e.message ?: "Update check failed")
        }
        _state.value = result
        return result
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Download (with progress) then install. Safe to call again after granting the permission. */
    suspend fun downloadAndInstall(update: UpdateState.Available) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update.version)
            pending = update
            return
        }
        val apk = File(context.cacheDir, "nexus-update.apk")
        runCatching {
            _state.value = UpdateState.Downloading(update.version, 0f)
            download(update, apk)
            _state.value = UpdateState.Installing(update.version)
            install(apk)
        }.onFailure { _state.value = UpdateState.Failed("Update failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    /** The update waiting on the install-permission screen. */
    var pending: UpdateState.Available? = null
        private set

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun download(update: UpdateState.Available, target: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "Nexus-Android").build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "Download failed (HTTP ${response.code})")
            val body = response.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 256 * 1024) {
                            lastReport = read
                            _state.value = UpdateState.Downloading(update.version, (read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("nexus.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            _state.value = UpdateState.Failed(
                when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled"
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "This build is signed differently: uninstall Nexus once, then install the latest from GitHub. Future updates will work in place."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage for the update"
                    else -> "Install failed${message?.let { ": $it" } ?: ""}"
                },
            )
        }
    }

    companion object {
        const val REPO = "Eoinknd16/ClashPointNexus-Android"

        /** Dotted numeric compare: 0.2.10 > 0.2.9. Non-numeric parts count as 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** Gets PackageInstaller's answer; launches Android's confirm screen when it asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
        }
        (context.applicationContext as? NexusApp)?.container?.updater
            ?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
