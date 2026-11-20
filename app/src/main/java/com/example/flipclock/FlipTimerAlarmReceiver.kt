package com.example.flipclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ResolveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlipTimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val app = context.applicationContext as? ResolveApplication ?: return
        val action = intent.action ?: return
        if (action != FlipTimerNotificationHelper.ACTION_TIMER_COMPLETE &&
            action != FlipTimerNotificationHelper.ACTION_TIMER_AUTO_REPEAT
        ) {
            return
        }

        val pendingResult = goAsync()
        val expectedWallMs = intent.getLongExtra(FlipTimerNotificationHelper.EXTRA_EXPECTED_WALL_MS, -1L)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                app.container.flipClockManager.initialize()
                if (action == FlipTimerNotificationHelper.ACTION_TIMER_COMPLETE) {
                    app.container.flipClockManager.onTimerAlarmFired(
                        expectedWallMs = expectedWallMs.takeIf { it > 0L }
                    )
                } else {
                    app.container.flipClockManager.onAutoRepeatAlarmFired(
                        expectedWallMs = expectedWallMs.takeIf { it > 0L }
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
