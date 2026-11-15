package com.example.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                    when (intent.action) {
                        Intent.ACTION_BOOT_COMPLETED,
                        Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                            engine.onSystemBootOrTimeChange(isBoot = true)
                        }
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED,
                        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                            engine.onSystemBootOrTimeChange(isBoot = false)
                        }
                    }
                }
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}
