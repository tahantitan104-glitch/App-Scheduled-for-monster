package com.example.flipclock

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

data class FlipBatteryTelemetry(
    val percent: Int,
    val isCharging: Boolean
) {
    fun formatLabel(): String = if (isCharging) "$percent% ⚡" else "$percent%"
}

sealed interface FlipWeatherResult {
    data object NeedsCity : FlipWeatherResult
    data object Loading : FlipWeatherResult
    data class Ready(
        val cityLabel: String,
        val temperatureCelsius: Int,
        val conditionLabel: String,
        val isCachedOffline: Boolean = false,
        val fetchedAtWallMs: Long = 0L
    ) : FlipWeatherResult {
        val temperatureFahrenheit: Int
            get() = ((temperatureCelsius * 9.0) / 5.0 + 32.0).roundToInt()

        fun temperatureFormatted(useFahrenheit: Boolean): String =
            if (useFahrenheit) "${temperatureFahrenheit}°F" else "${temperatureCelsius}°C"

        fun formatLabel(): String = formatLabel(useFahrenheit = false, compact = false)

        fun formatLabel(useFahrenheit: Boolean, compact: Boolean = false): String {
            val temp = temperatureFormatted(useFahrenheit)
            val base = if (compact) "$temp · $conditionLabel" else "$temp · $conditionLabel · $cityLabel"
            return if (isCachedOffline) "$base (cached)" else base
        }
    }

    data object Unavailable : FlipWeatherResult
}

object FlipClockTelemetry {

    fun readBattery(context: Context): FlipBatteryTelemetry? = runCatching {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: return null
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        FlipBatteryTelemetry(percent = percent, isCharging = charging)
    }.getOrNull()

    fun readUpcomingAlarmLabel(context: Context, use24Hour: Boolean): String? = runCatching {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val triggerMs = alarmManager.nextAlarmClock?.triggerTime ?: return null
        val pattern = if (use24Hour) "EEE HH:mm" else "EEE h:mm a"
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        "Alarm ${formatter.format(Date(triggerMs))}"
    }.getOrNull()
}

object FlipWeatherRepository {
    private const val CACHE_TTL_MS = 30 * 60_000L
    private const val PREFS_NAME = "resolve_weather_cache"
    private const val PREF_KEY_LAST_WEATHER = "last_weather_json"

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedResult: FlipWeatherResult.Ready? = null

    internal fun clearMemoryCacheForTests() {
        cachedKey = null
        cachedAtMs = 0L
        cachedResult = null
    }

    internal fun extractCityFromTimeZoneId(zoneId: String): String? {
        val clean = zoneId.trim()
        if (!clean.contains('/')) return null
        val segment = clean.substringAfterLast('/').replace('_', ' ').trim()
        if (segment.isBlank() || segment.equals("GMT", ignoreCase = true) || segment.equals("UTC", ignoreCase = true)) {
            return null
        }
        return segment
    }

    internal fun parseAutoDetectedCityJson(body: String): String? = runCatching {
        val obj = JSONObject(body)
        val city = obj.optString("city").trim()
        if (city.isNotEmpty() && !city.equals("null", ignoreCase = true)) {
            city
        } else {
            obj.optString("regionName").trim().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    internal fun encodeWeatherCacheJson(queryKey: String, result: FlipWeatherResult.Ready): String {
        return JSONObject().apply {
            put("queryKey", queryKey)
            put("cityLabel", result.cityLabel)
            put("temperatureCelsius", result.temperatureCelsius)
            put("conditionLabel", result.conditionLabel)
            put("fetchedAtWallMs", result.fetchedAtWallMs)
        }.toString()
    }

    internal fun decodeWeatherCacheJson(raw: String?): Pair<String, FlipWeatherResult.Ready>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val queryKey = obj.optString("queryKey").trim()
            val cityLabel = obj.optString("cityLabel").trim()
            if (cityLabel.isEmpty()) return null
            val tempC = obj.getInt("temperatureCelsius")
            val cond = obj.optString("conditionLabel").ifBlank { "Fair" }
            val fetchedAt = obj.optLong("fetchedAtWallMs", 0L)
            queryKey to FlipWeatherResult.Ready(
                cityLabel = cityLabel,
                temperatureCelsius = tempC,
                conditionLabel = cond,
                isCachedOffline = false,
                fetchedAtWallMs = fetchedAt
            )
        }.getOrNull()
    }

    private fun saveDiskCache(context: Context?, queryKey: String, result: FlipWeatherResult.Ready) {
        val appCtx = context?.applicationContext ?: return
        runCatching {
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_LAST_WEATHER, encodeWeatherCacheJson(queryKey, result))
                .apply()
        }
    }

    private fun loadDiskCache(context: Context?): Pair<String, FlipWeatherResult.Ready>? {
        val appCtx = context?.applicationContext ?: return null
        return runCatching {
            val raw = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY_LAST_WEATHER, null)
            decodeWeatherCacheJson(raw)
        }.getOrNull()
    }

    private fun detectCityFromNetworkOrTimezone(): String? {
        val ipSources = listOf(
            "https://ipwho.is/",
            "https://ipapi.co/json/"
        )
        for (url in ipSources) {
            val detected = runCatching {
                val json = httpGetJson(url) ?: return@runCatching null
                parseAutoDetectedCityJson(json.toString())
            }.getOrNull()
            if (!detected.isNullOrBlank()) return detected
        }
        return extractCityFromTimeZoneId(java.util.TimeZone.getDefault().id)
    }

    suspend fun fetchWeather(
        cityQuery: String,
        autoDetect: Boolean = false,
        context: Context? = null,
        forceRefresh: Boolean = false
    ): FlipWeatherResult {
        val manualCity = cityQuery.trim()
        if (manualCity.isEmpty() && !autoDetect) return FlipWeatherResult.NeedsCity

        val now = System.currentTimeMillis()
        if (cachedResult == null) {
            loadDiskCache(context)?.let { (diskKey, diskWeather) ->
                cachedKey = diskKey
                cachedAtMs = diskWeather.fetchedAtWallMs
                cachedResult = diskWeather
            }
        }

        val lookupKey = if (manualCity.isNotEmpty()) manualCity else "__AUTO__"
        val existing = cachedResult
        if (!forceRefresh &&
            existing != null &&
            cachedKey.equals(lookupKey, ignoreCase = true) &&
            (now - cachedAtMs) < CACHE_TTL_MS
        ) {
            return existing.copy(isCachedOffline = false)
        }

        return withContext(Dispatchers.IO) {
            val resolvedQueryCity = manualCity.ifEmpty { detectCityFromNetworkOrTimezone().orEmpty() }
            if (resolvedQueryCity.isEmpty()) {
                val fallback = existing?.takeIf {
                    cachedKey.equals(lookupKey, ignoreCase = true) || manualCity.isEmpty()
                }
                return@withContext fallback?.copy(isCachedOffline = true) ?: FlipWeatherResult.Unavailable
            }

            runCatching {
                val encoded = URLEncoder.encode(resolvedQueryCity, "UTF-8")
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
                val geoJson = httpGetJson(geoUrl) ?: throw IllegalStateException("Geo failed")
                val results = geoJson.optJSONArray("results") ?: throw IllegalStateException("No geo results")
                if (results.length() == 0) throw IllegalStateException("City not found")

                val first = results.getJSONObject(0)
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")
                val resolvedName = first.optString("name").ifBlank { resolvedQueryCity }

                val forecastUrl =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
                val forecastJson = httpGetJson(forecastUrl) ?: throw IllegalStateException("Forecast failed")
                val current = forecastJson.optJSONObject("current") ?: throw IllegalStateException("No current")
                val temp = kotlin.math.round(current.getDouble("temperature_2m")).toInt()
                val code = current.optInt("weather_code", -1)
                val fetchedAt = System.currentTimeMillis()
                val ready = FlipWeatherResult.Ready(
                    cityLabel = resolvedName,
                    temperatureCelsius = temp,
                    conditionLabel = wmoCodeLabel(code),
                    isCachedOffline = false,
                    fetchedAtWallMs = fetchedAt
                )
                cachedKey = lookupKey
                cachedAtMs = fetchedAt
                cachedResult = ready
                saveDiskCache(context, lookupKey, ready)
                ready
            }.getOrElse {
                val diskOrMemFallback = cachedResult ?: loadDiskCache(context)?.second
                val matchesManual = manualCity.isEmpty() ||
                    cachedKey.equals(lookupKey, ignoreCase = true) ||
                    diskOrMemFallback?.cityLabel.equals(manualCity, ignoreCase = true)
                if (diskOrMemFallback != null && matchesManual) {
                    diskOrMemFallback.copy(isCachedOffline = true)
                } else {
                    FlipWeatherResult.Unavailable
                }
            }
        }
    }

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as? HttpURLConnection) ?: return null
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun wmoCodeLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Mostly Clear"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow"
        95, 96, 99 -> "Storm"
        else -> "Fair"
    }
}

/**
 * Generates and plays a soft mechanical clock-tick PCM cue using Android AudioTrack
 * without bundled audio files or extra permissions. Respects ringer mode.
 */
class FlipTickerAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val sampleRate = 22_050
    private val pcmSamples: ShortArray = buildSoftTickPcm(sampleRate)

    fun playTickIfPermitted() {
        runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return
            }
            val byteSize = pcmSamples.size * 2
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttrs)
                    .setAudioFormat(audioFormat)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(byteSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    audioAttrs,
                    audioFormat,
                    byteSize,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }

            track.write(pcmSamples, 0, pcmSamples.size)
            track.setVolume(0.22f)
            track.setNotificationMarkerPosition(pcmSamples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        runCatching {
                            t?.stop()
                            t?.release()
                        }
                    }

                    override fun onPeriodicNotification(t: AudioTrack?) = Unit
                }
            )
            track.play()
        }
    }

    private fun buildSoftTickPcm(rate: Int): ShortArray {
        val durationSec = 0.018
        val count = (rate * durationSec).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / rate
            val env = exp(-t * 210.0)
            val wave = sin(2.0 * PI * 980.0 * t) * 0.65 + sin(2.0 * PI * 1950.0 * t) * 0.35
            (wave * env * 9_000.0).toInt().coerceIn(-32_767, 32_767).toShort()
        }
    }
}
