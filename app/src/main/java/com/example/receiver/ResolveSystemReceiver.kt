package com.example.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.ResolveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResolveSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ResolveApplication ?: return
        val pendingResult = goAsync()
        val engine = app.container.sessionEngine

        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    engine.initialize()
                    val action = intent.action
                    when {
                        action == Intent.ACTION_BOOT_COMPLETED ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                action == Intent.ACTION_LOCKED_BOOT_COMPLETED) -> {
                            engine.onSystemBootOrTimeChange(isBoot = true)
                        }
                        action == Intent.ACTION_TIME_CHANGED ||
                            action == Intent.ACTION_TIMEZONE_CHANGED ||
                            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) -> {
                            engine.onSystemBootOrTimeChange(isBoot = false)
                        }
                    }
                    val current = engine.activeSession.value
                    val hasSchedules = app.container.scheduleRepository.getAllSchedules().any { it.isEnabled }
                    if ((current != null && !current.isTerminal) || hasSchedules) {
                        com.example.service.SessionRecoveryJobService.scheduleIfNeeded(context)
                    }
                }
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}
