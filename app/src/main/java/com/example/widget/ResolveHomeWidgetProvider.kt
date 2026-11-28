package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.ResolveApplication
import com.example.domain.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android 5.1.1+ (API 22+) Home Screen Widget showing focus session status and quick-start actions.
 */
class ResolveHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val container = (context.applicationContext as? ResolveApplication)?.container
        val session = runCatching { container?.sessionRepository?.getActiveSession() }.getOrNull()
        val goalMinutes = runCatching {
            container?.preferencesRepository?.dailyFocusGoalMinutesFlow?.first()
        }.getOrNull() ?: 120
        val nowMs = container?.clockProvider?.wallClockMs() ?: System.currentTimeMillis()

        val statusText = when (session?.state) {
            SessionState.LOCK_ENFORCEMENT_ACTIVE,
            SessionState.PHONE_CALL_ACTIVE,
            SessionState.RECOVERY_IN_PROGRESS -> {
                val minsLeft = session.lockExpirationWallMs
                    ?.let { ((it - nowMs).coerceAtLeast(0L) / 60_000L) + 1L }
                    ?: session.lockDurationMinutes
                "Focus Lock · ${minsLeft}m left"
            }
            SessionState.DELAYED_FREE_USE_ACTIVE -> {
                val minsLeft = session.freeUseDeadlineWallMs
                    ?.let { ((it - nowMs).coerceAtLeast(0L) / 60_000L) + 1L }
                    ?: session.freeUseDurationMinutes
                "Free Use · ${minsLeft}m until lock"
            }
            SessionState.SCHEDULE_WAITING -> "Scheduled Lock Ready"
            else -> "Ready to Focus"
        }

        val badgeText = if (session != null && !session.isTerminal) {
            "ACTIVE"
        } else {
            "GOAL ${goalMinutes}m"
        }

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_resolve_home).apply {
                setTextViewText(R.id.widget_status, statusText)
                setTextViewText(R.id.widget_goal_badge, badgeText)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    buildLaunchIntent(context, requestCode = 100, action = null)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_15m,
                    buildLaunchIntent(context, requestCode = 101, action = MainActivity.ACTION_QUICK_LOCK_15M)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_30m,
                    buildLaunchIntent(context, requestCode = 102, action = MainActivity.ACTION_QUICK_LOCK_30M)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_clock,
                    buildLaunchIntent(context, requestCode = 103, action = MainActivity.ACTION_OPEN_FLIP_CLOCK)
                )
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildLaunchIntent(context: Context, requestCode: Int, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (!action.isNullOrBlank()) {
                this.action = action
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    companion object {
        fun requestWidgetUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val component = ComponentName(appContext, ResolveHomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val updateIntent = Intent(appContext, ResolveHomeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(updateIntent)
        }
    }
}
