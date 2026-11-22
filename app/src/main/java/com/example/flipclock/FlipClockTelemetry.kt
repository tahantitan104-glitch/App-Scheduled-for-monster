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
        val conditionLabel: String
    ) : FlipWeatherResult {
        fun formatLabel(): String = "${temperatureCelsius}°C · $conditionLabel · $cityLabel"
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
            val bm = context.getSystemService(BatteryManager::class.java)
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: return null
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        FlipBatteryTelemetry(percent = percent, isCharging = charging)
    }.getOrNull()

    fun readUpcomingAlarmLabel(context: Context, use24Hour: Boolean): String? = runCatching {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return null
        val triggerMs = alarmManager.nextAlarmClock?.triggerTime ?: return null
        val pattern = if (use24Hour) "EEE HH:mm" else "EEE h:mm a"
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        "Alarm ${formatter.format(Date(triggerMs))}"
    }.getOrNull()
}

object FlipWeatherRepository {
    private const val CACHE_TTL_MS = 30 * 60_000L

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedResult: FlipWeatherResult.Ready? = null

    suspend fun fetchWeather(cityQuery: String): FlipWeatherResult {
        val cleanCity = cityQuery.trim()
        if (cleanCity.isEmpty()) return FlipWeatherResult.NeedsCity

        val now = System.currentTimeMillis()
        val existing = cachedResult
        if (existing != null &&
            cachedKey.equals(cleanCity, ignoreCase = true) &&
            (now - cachedAtMs) < CACHE_TTL_MS
        ) {
            return existing
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(cleanCity, "UTF-8")
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
                val geoJson = httpGetJson(geoUrl) ?: return@runCatching FlipWeatherResult.Unavailable
                val results = geoJson.optJSONArray("results") ?: return@runCatching FlipWeatherResult.Unavailable
                if (results.length() == 0) return@runCatching FlipWeatherResult.Unavailable

                val first = results.getJSONObject(0)
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")
                val resolvedName = first.optString("name").ifBlank { cleanCity }

                val forecastUrl =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
                val forecastJson = httpGetJson(forecastUrl) ?: return@runCatching FlipWeatherResult.Unavailable
                val current = forecastJson.optJSONObject("current") ?: return@runCatching FlipWeatherResult.Unavailable
                val temp = kotlin.math.round(current.getDouble("temperature_2m")).toInt()
                val code = current.optInt("weather_code", -1)
                val ready = FlipWeatherResult.Ready(
                    cityLabel = resolvedName,
                    temperatureCelsius = temp,
                    conditionLabel = wmoCodeLabel(code)
                )
                cachedKey = cleanCity
                cachedAtMs = System.currentTimeMillis()
                cachedResult = ready
                ready
            }.getOrElse { FlipWeatherResult.Unavailable }
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
            val am = appContext.getSystemService(AudioManager::class.java)
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return
            }
            val byteSize = pcmSamples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(byteSize)
                .build()

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
