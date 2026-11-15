package com.example.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.presentation.lockscreen.LockScreenActivity

/**
 * Crash-safe helper for:
 * 1) Querying recent foreground app transitions via UsageStatsManager (fallback when OEM throttles Accessibility events).
 * 2) Showing/removing a brief protective TYPE_APPLICATION_OVERLAY barrier when overlay permission is granted.
 */
class ForegroundEnforcementGuard(
    private val context: Context,
    private val capabilityChecker: SystemCapabilityChecker
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var shieldView: View? = null
    private val detachRunnable = Runnable { detachShieldSafe() }

    fun queryLatestForegroundPackage(windowMs: Long = 4_000L): String? {
        if (!capabilityChecker.hasUsageStatsAccess()) return null
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@runCatching null
            val end = System.currentTimeMillis()
            val start = (end - windowMs).coerceAtLeast(0L)
            val events = usm.queryEvents(start, end) ?: return@runCatching null
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResumed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else false
                @Suppress("DEPRECATION")
                val isLegacyForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                val isForeground = isResumed || isLegacyForeground
                if (isForeground && event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            latestPkg
        }.getOrNull()
    }

    fun showBriefBarrierAndLaunchLockScreen() {
        val hasOverlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        if (hasOverlay) {
            mainHandler.post {
                mainHandler.removeCallbacks(detachRunnable)
                attachShieldSafe()
                launchLockScreenSafe()
                mainHandler.postDelayed(detachRunnable, 900L)
            }
        } else {
            launchLockScreenSafe()
        }
    }

    fun clearBarrier() {
        mainHandler.post {
            mainHandler.removeCallbacks(detachRunnable)
            detachShieldSafe()
        }
    }

    private fun launchLockScreenSafe() {
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { context.startActivity(intent) }
    }

    private fun attachShieldSafe() {
        val hasOverlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        if (shieldView != null || !hasOverlay) return
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val view = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#FF07110E"))
                isClickable = true
                isFocusable = true
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            wm.addView(view, params)
            shieldView = view
        }
    }

    private fun detachShieldSafe() {
        val current = shieldView ?: return
        shieldView = null
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (current.isAttachedToWindow) {
                wm?.removeViewImmediate(current)
            }
        }
    }
}
