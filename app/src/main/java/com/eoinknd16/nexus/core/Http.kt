package com.eoinknd16.nexus.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

/** One shared client + JSON config for the whole app. */
object Http {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Same headers the PC build sends to third-party addon servers (see
     * PC src/main/stremio/addonHttp.ts) -- some addon hosts reject bare
     * clients with no User-Agent as bots. */
    val ADDON_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "application/json",
    )

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 10_000,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            .newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code}")
                response.body?.string() ?: ""
            }
    }

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 10_000,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            .newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code}")
                response.body?.string() ?: ""
            }
    }
}

fun Throwable.describe(): String = when (this) {
    is HttpException -> "HTTP $code"
    is java.net.SocketTimeoutException -> "timed out"
    is java.net.UnknownHostException -> "host not found"
    is java.net.ConnectException -> "connection refused"
    else -> message ?: javaClass.simpleName
}
