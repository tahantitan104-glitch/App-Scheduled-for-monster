package com.example.flipclock

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.FlipClockPage

class FlipTimerNotificationHelper(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Flip Clock Timer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a Resolve Flip Clock countdown timer completes."
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showTimerCompletedNotification(
        timerName: String,
        isFocusLockActive: Boolean
    ) {
        if (!hasNotificationPermission()) return
        ensureChannel()
        val displayTitle = formatCompletionNotificationTitle(timerName)

        val tapIntent = FlipClockActivity.createIntent(
            context = context,
            restrictedLock = isFocusLockActive,
            initialPage = FlipClockPage.TIMER,
            fromNotification = true
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentIntent = PendingIntent.getActivity(
            context,
            REQ_NOTIFICATION_TAP,
            tapIntent,
            piFlags
        )

        // Part 23: No custom notification action buttons (no Restart/Dismiss buttons), preserve normal tap & swipe dismissal.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_resolve)
            .setColor(0xFF176B5B.toInt())
            .setContentTitle(displayTitle)
            .setContentText("Tap to open Timer")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancelCompletionNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    fun playShortCompletionAlertAndVibrate() {
        triggerShortVibration()
        playShortAlertTone()
    }

    private fun playShortAlertTone() {
        val playedRingtone = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return@runCatching false
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
            true
        }.getOrDefault(false)

        if (!playedRingtone) {
            runCatching {
                val tone = ToneGenerator(android.media.AudioManager.STREAM_ALARM, 80)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 650)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerShortVibration() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 280L, 140L, 320L),
                            -1
                        )
                    )
                } else {
                    vibrator.vibrate(longArrayOf(0L, 280L, 140L, 320L), -1)
                }
            }
        }
    }

    fun scheduleTimerAlarm(targetEndWallMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildTimerAlarmPendingIntent(targetEndWallMs, isAutoRepeatTrigger = false)
        scheduleBestEffort(manager, targetEndWallMs, pi)
    }

    fun scheduleAutoRepeatAlarm(restartAtWallMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildTimerAlarmPendingIntent(restartAtWallMs, isAutoRepeatTrigger = true)
        scheduleBestEffort(manager, restartAtWallMs, pi)
    }

    fun cancelAllTimerAlarms() {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        listOf(REQ_TIMER_END to false, REQ_AUTO_REPEAT to true).forEach { (reqCode, isRepeat) ->
            val intent = Intent(context, FlipTimerAlarmReceiver::class.java).apply {
                action = if (isRepeat) ACTION_TIMER_AUTO_REPEAT else ACTION_TIMER_COMPLETE
            }
            val flags = PendingIntent.FLAG_NO_CREATE or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, reqCode, intent, flags)
            if (pi != null) {
                manager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun buildTimerAlarmPendingIntent(wallMs: Long, isAutoRepeatTrigger: Boolean): PendingIntent {
        val intent = Intent(context, FlipTimerAlarmReceiver::class.java).apply {
            action = if (isAutoRepeatTrigger) ACTION_TIMER_AUTO_REPEAT else ACTION_TIMER_COMPLETE
            putExtra(EXTRA_EXPECTED_WALL_MS, wallMs)
        }
        val reqCode = if (isAutoRepeatTrigger) REQ_AUTO_REPEAT else REQ_TIMER_END
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }

    private fun scheduleBestEffort(manager: AlarmManager, triggerAtWallMs: Long, operation: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
        if (canExact) {
            val done = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtWallMs, operation)
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, triggerAtWallMs, operation)
                }
                true
            }.getOrDefault(false)
            if (done) return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtWallMs, operation)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtWallMs, operation)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "resolve_flip_timer"
        const val NOTIFICATION_ID = 4401
        const val ACTION_TIMER_COMPLETE = "com.resolve.focus.ACTION_FLIP_TIMER_COMPLETE"
        const val ACTION_TIMER_AUTO_REPEAT = "com.resolve.focus.ACTION_FLIP_TIMER_AUTO_REPEAT"
        const val EXTRA_EXPECTED_WALL_MS = "extra_expected_wall_ms"

        private const val REQ_TIMER_END = 4402
        private const val REQ_AUTO_REPEAT = 4403
        private const val REQ_NOTIFICATION_TAP = 4404

        fun formatCompletionNotificationTitle(rawTimerName: String): String {
            val trimmed = rawTimerName.trim().ifBlank { "Flip" }
            val withTimer = if (trimmed.contains("timer", ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed Timer"
            }
            return "$withTimer — Completed!"
        }
    }
}
