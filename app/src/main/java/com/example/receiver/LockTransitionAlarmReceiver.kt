package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ResolveApplication
import com.example.scheduling.AndroidLockAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockTransitionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ResolveApplication ?: return
        val pendingResult = goAsync()
        val engine = app.container.sessionEngine

        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    engine.initialize()
                    when (intent.action) {
                        AndroidLockAlarmScheduler.ACTION_DELAYED_LOCK_DEADLINE -> {
                            val sessionId = intent.getStringExtra(AndroidLockAlarmScheduler.EXTRA_SESSION_ID)
                            engine.onDelayedFreeUseDeadlineReached(sessionId)
                        }
                        AndroidLockAlarmScheduler.ACTION_LOCK_EXPIRED -> {
                            val sessionId = intent.getStringExtra(AndroidLockAlarmScheduler.EXTRA_SESSION_ID)
                            engine.onLockExpirationReached(sessionId)
                        }
                        AndroidLockAlarmScheduler.ACTION_SCHEDULE_TRIGGER -> {
                            val scheduleId = intent.getLongExtra(AndroidLockAlarmScheduler.EXTRA_SCHEDULE_ID, -1L)
                            if (scheduleId > 0L) {
                                engine.onScheduleTriggered(scheduleId)
                            }
                        }
                    }
                }
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}
