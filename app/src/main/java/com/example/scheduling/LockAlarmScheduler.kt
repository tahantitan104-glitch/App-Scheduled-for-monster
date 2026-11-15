package com.example.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.receiver.LockTransitionAlarmReceiver

interface LockAlarmScheduler {
    fun scheduleDelayedLockTransition(sessionId: String, triggerAtWallMs: Long)
    fun scheduleLockExpiration(sessionId: String, expireAtWallMs: Long)
    fun scheduleNextScheduleTrigger(scheduleId: Long, triggerAtWallMs: Long)
    fun cancelDelayedLockTransition()
    fun cancelLockExpiration()
    fun cancelScheduleTrigger(scheduleId: Long)
    fun cancelAllSessionAlarms()
}

class AndroidLockAlarmScheduler(private val context: Context) : LockAlarmScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun scheduleDelayedLockTransition(sessionId: String, triggerAtWallMs: Long) {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_DELAYED_LOCK_DEADLINE
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_DELAYED_LOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrBestEffort(triggerAtWallMs, pi)
    }

    override fun scheduleLockExpiration(sessionId: String, expireAtWallMs: Long) {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_LOCK_EXPIRED
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_LOCK_EXPIRED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrBestEffort(expireAtWallMs, pi)
    }

    override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerAtWallMs: Long) {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_SCHEDULE_TRIGGER
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_TRIGGER_WALL_MS, triggerAtWallMs)
        }
        val requestCode = REQ_SCHEDULE_BASE + (scheduleId % 100_000L).toInt()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrBestEffort(triggerAtWallMs, pi)
    }

    override fun cancelDelayedLockTransition() {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_DELAYED_LOCK_DEADLINE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_DELAYED_LOCK,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    override fun cancelLockExpiration() {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_LOCK_EXPIRED
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_LOCK_EXPIRED,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    override fun cancelScheduleTrigger(scheduleId: Long) {
        val intent = Intent(context, LockTransitionAlarmReceiver::class.java).apply {
            action = ACTION_SCHEDULE_TRIGGER
        }
        val requestCode = REQ_SCHEDULE_BASE + (scheduleId % 100_000L).toInt()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    override fun cancelAllSessionAlarms() {
        cancelDelayedLockTransition()
        cancelLockExpiration()
    }

    private fun scheduleExactOrBestEffort(triggerAtWallMs: Long, operation: PendingIntent) {
        val manager = alarmManager ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

        if (canExact) {
            val scheduledExact = runCatching {
                val showIntent = PendingIntent.getActivity(
                    context,
                    REQ_SHOW_INTENT,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(triggerAtWallMs, showIntent)
                manager.setAlarmClock(info, operation)
                true
            }.getOrDefault(false)
            if (scheduledExact) return
        }

        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtWallMs, operation)
        }
    }

    companion object {
        const val ACTION_DELAYED_LOCK_DEADLINE = "com.resolve.focus.ACTION_DELAYED_LOCK_DEADLINE"
        const val ACTION_LOCK_EXPIRED = "com.resolve.focus.ACTION_LOCK_EXPIRED"
        const val ACTION_SCHEDULE_TRIGGER = "com.resolve.focus.ACTION_SCHEDULE_TRIGGER"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_TRIGGER_WALL_MS = "extra_trigger_wall_ms"

        private const val REQ_DELAYED_LOCK = 1001
        private const val REQ_LOCK_EXPIRED = 1002
        private const val REQ_SHOW_INTENT = 1003
        private const val REQ_SCHEDULE_BASE = 20_000
    }
}
