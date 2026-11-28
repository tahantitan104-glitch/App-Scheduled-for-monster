package com.example.flipclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ResolveApplication
import com.example.domain.model.AmbientSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight mediaPlayback foreground service for continuous, buffer-free
 * background White Noise & nature soundscape playback (screen-off & multi-hour sessions).
 */
class ResolveAmbientAudioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? ResolveApplication ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val flipManager = app.container.flipClockManager

        if (intent?.action == ACTION_STOP_AMBIENT) {
            serviceScope.launch {
                runCatching { flipManager.stopAmbientSound() }
                stopForegroundCompat()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val currentSound = flipManager.settings.value.ambientSoundType
        val label = currentSound.takeIf { it != AmbientSoundType.OFF }?.label ?: "White Noise"
        val notification = buildNotification(label)

        val startedForeground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.getOrDefault(false)

        if (!startedForeground) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        if (observerJob?.isActive != true) {
            observerJob = serviceScope.launch {
                flipManager.settings.collect { settings ->
                    val shouldRun = settings.ambientSoundType != AmbientSoundType.OFF &&
                        settings.ambientPlayInBackground
                    if (!shouldRun) {
                        releaseWakeLock()
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        runCatching {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(NOTIFICATION_ID, buildNotification(settings.ambientSoundType.label))
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Resolve:AmbientAudio").apply {
                setReferenceCounted(false)
                // Safety timeout of 12 hours max in case of force stop; renewed on restart
                acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { wl ->
                if (wl.isHeld) wl.release()
            }
        }
        wakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "White Noise & Soundscapes",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps soothing focus soundscapes playing seamlessly in the background."
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(soundLabel: String): Notification {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val openIntent = FlipClockActivity.createIntent(
            context = this,
            restrictedLock = false,
            initialPage = com.example.domain.model.FlipClockPage.CLOCK
        )
        val openPending = PendingIntent.getActivity(this, 4101, openIntent, piFlags)

        val stopIntent = Intent(this, ResolveAmbientAudioService::class.java).apply {
            action = ACTION_STOP_AMBIENT
        }
        val stopPending = PendingIntent.getService(this, 4102, stopIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_resolve)
            .setContentTitle("$soundLabel Playing")
            .setContentText("Soothing focus soundscape active · Tap to open Flip Clock")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "resolve_ambient_audio"
        private const val NOTIFICATION_ID = 4109
        const val ACTION_STOP_AMBIENT = "com.resolve.focus.ACTION_STOP_AMBIENT"

        fun reconcile(context: Context, shouldRunBackgroundService: Boolean) {
            runCatching {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, ResolveAmbientAudioService::class.java)
                if (shouldRunBackgroundService) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(appCtx, intent)
                    } else {
                        appCtx.startService(intent)
                    }
                } else {
                    appCtx.stopService(intent)
                }
            }
        }
    }
}
