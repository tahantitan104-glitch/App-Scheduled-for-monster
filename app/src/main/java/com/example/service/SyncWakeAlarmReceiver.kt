package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.example.ResolveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded wakeful sync alarm receiver that keeps cross-device lock sync responsive
 * when the screen is off or in idle/Doze across Android 5.1.1 (API 22) through Android 16.
 */
class SyncWakeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_WAKE_SYNC) return
        val app = context.applicationContext as? ResolveApplication ?: return
        val coordinator = app.container.multiDeviceSyncCoordinator
        val state = coordinator.uiState.value
        if (!state.isSignedIn || !state.syncLockEnabled) {
            cancel(context)
            return
        }

        val pendingResult = goAsync()
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = runCatching {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Resolve:SyncWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(SYNC_CHECK_TIMEOUT_MS) {
                    coordinator.performWakefulSyncCheck()
                }
                val latest = coordinator.uiState.value
                if (latest.isSignedIn && latest.syncLockEnabled) {
                    scheduleNextWake(context)
                } else {
                    cancel(context)
                }
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        const val ACTION_WAKE_SYNC = "com.resolve.focus.action.WAKE_SYNC"
        private const val REQUEST_CODE = 7105
        private const val WAKE_LOCK_TIMEOUT_MS = 5_000L
        private const val SYNC_CHECK_TIMEOUT_MS = 4_000L
        private const val LEGACY_INTERVAL_MS = 15_000L
        private const val MODERN_INTERVAL_MS = 35_000L

        fun scheduleNextWake(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            val interval = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                LEGACY_INTERVAL_MS
            } else {
                MODERN_INTERVAL_MS
            }
            val triggerAt = SystemClock.elapsedRealtime() + interval
            runCatching {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    }
                    else -> {
                        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    }
                }
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
            runCatching { am.cancel(pi) }
            runCatching { pi.cancel() }
        }

        private fun buildPendingIntent(context: Context, baseFlags: Int): PendingIntent? {
            val intent = Intent(context, SyncWakeAlarmReceiver::class.java).apply {
                action = ACTION_WAKE_SYNC
            }
            val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                baseFlags or immutableFlag
            )
        }
    }
}
