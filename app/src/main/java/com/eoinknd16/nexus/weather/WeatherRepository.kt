package com.eoinknd16.nexus.weather

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.look.TempUnit
import com.eoinknd16.nexus.look.WeatherPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class DayForecast(val date: String, val code: Int, val max: Int, val min: Int)

data class WeatherNow(
    val place: String,
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int?,
    val windKph: Int?,
    val unit: TempUnit,
    val days: List<DayForecast>,
    val fetchedAt: Long,
    /** Next hour in the coming 6 with a good chance of rain ("15:00"), and that chance. */
    val rainAt: String? = null,
    val rainChance: Int = 0,
) {
    val summary: String get() = describe(code)

    companion object {
        /** WMO weather codes, as used by Open-Meteo. */
        fun describe(code: Int): String = when (code) {
            0 -> "Clear"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63 -> "Rain"
            65 -> "Heavy rain"
            66, 67 -> "Freezing rain"
            71, 73, 75, 77 -> "Snow"
            80, 81 -> "Showers"
            82 -> "Heavy showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunder & hail"
            else -> "—"
        }
    }
}

data class Place(val name: String, val detail: String, val latitude: Double, val longitude: Double)

/**
 * Weather from Open-Meteo (free, no API key, no account). Location is either
 * a city you pick in Customise > Weather, or -- if you turn it on -- the
 * phone's coarse network location. Cached for 20 minutes.
 */
class WeatherRepository(private val context: Context) {
    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now: StateFlow<WeatherNow?> = _now
    private val lock = Mutex()
    private var lastKey: String? = null

    suspend fun refresh(prefs: WeatherPrefs, force: Boolean = false): Result<WeatherNow> = lock.withLock {
        runCatching {
            val (lat, lon, place) = resolveLocation(prefs) ?: error("Set a location in Customise › Weather")
            val key = "%.2f,%.2f,${prefs.unit}".format(lat, lon)
            val cached = _now.value
            if (!force && cached != null && key == lastKey && System.currentTimeMillis() - cached.fetchedAt < 20 * 60_000) {
                return@runCatching cached
            }
            val unitParam = if (prefs.unit == TempUnit.F) "&temperature_unit=fahrenheit" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto$unitParam" +
                "&hourly=precipitation_probability&forecast_hours=7"
            val root = Http.json.parseToJsonElement(Http.getText(url, timeoutMs = 10_000)).jsonObject
            val cur = root["current"] as? JsonObject ?: error("No current weather")
            val daily = root["daily"] as? JsonObject
            fun arr(k: String) = (daily?.get(k) as? JsonArray) ?: JsonArray(emptyList())
            val dates = arr("time")
            val days = dates.indices.map { i ->
                DayForecast(
                    date = (dates[i] as? JsonPrimitive)?.contentOrNull ?: "",
                    code = (arr("weather_code").getOrNull(i) as? JsonPrimitive)?.intOrNull ?: 0,
                    max = ((arr("temperature_2m_max").getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: 0.0).roundToInt(),
                    min = ((arr("temperature_2m_min").getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: 0.0).roundToInt(),
                )
            }
            fun num(k: String) = (cur[k] as? JsonPrimitive)?.doubleOrNull
            // First upcoming hour (skipping the current one) with >= 50% chance of rain.
            val hourly = root["hourly"] as? JsonObject
            val hTimes = (hourly?.get("time") as? JsonArray).orEmpty()
            val hProb = (hourly?.get("precipitation_probability") as? JsonArray).orEmpty()
            val rainIdx = (1 until minOf(hTimes.size, hProb.size)).firstOrNull { i -> ((hProb[i] as? JsonPrimitive)?.intOrNull ?: 0) >= 50 }
            WeatherNow(
                place = place,
                temp = (num("temperature_2m") ?: 0.0).roundToInt(),
                feelsLike = (num("apparent_temperature") ?: 0.0).roundToInt(),
                code = num("weather_code")?.toInt() ?: 0,
                isDay = (num("is_day") ?: 1.0) > 0.5,
                humidity = num("relative_humidity_2m")?.roundToInt(),
                windKph = num("wind_speed_10m")?.roundToInt(),
                unit = prefs.unit,
                days = days,
                fetchedAt = System.currentTimeMillis(),
                rainAt = rainIdx?.let { (hTimes[it] as? JsonPrimitive)?.contentOrNull?.substringAfter('T') },
                rainChance = rainIdx?.let { (hProb[it] as? JsonPrimitive)?.intOrNull } ?: 0,
            ).also {
                _now.value = it
                lastKey = key
            }
        }
    }

    private fun resolveLocation(prefs: WeatherPrefs): Triple<Double, Double, String>? {
        if (prefs.useDeviceLocation) deviceLocation()?.let { return Triple(it.first, it.second, prefs.city.ifBlank { "Here" }) }
        val lat = prefs.latitude ?: return null
        val lon = prefs.longitude ?: return null
        return Triple(lat, lon, prefs.city)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun deviceLocation(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return best.latitude to best.longitude
    }

    /** City search for the location picker. */
    suspend fun searchPlaces(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val root = Http.json.parseToJsonElement(
            Http.getText("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&format=json", timeoutMs = 8_000),
        ).jsonObject
        return (root["results"] as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            Place(
                name = s("name") ?: return@mapNotNull null,
                detail = listOfNotNull(s("admin1"), s("country")).joinToString(", "),
                latitude = (o["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
                longitude = (o["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
            )
        } ?: emptyList()
    }
}
