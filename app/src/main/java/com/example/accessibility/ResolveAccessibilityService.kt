package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.example.ResolveApplication
import com.example.domain.model.EnforcementMode
import com.example.domain.model.SessionState
import com.example.presentation.lockscreen.LockScreenActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ResolveAccessibilityService : AccessibilityService() {

    private var lastRedirectElapsedMs: Long = 0L
    private var cachedImePackages: Set<String> = emptySet()
    private var lastImeRefreshElapsedMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnectedFlow.value = true
        runCatching {
            val app = applicationContext as? ResolveApplication
            app?.container?.sessionEngine?.refreshCapabilities()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        runCatching {
            val eventType = event.eventType
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                return
            }

            val app = applicationContext as? ResolveApplication ?: return
            val container = app.container
            val engine = container.sessionEngine
            val session = engine.activeSession.value ?: return

            if (session.state != SessionState.LOCK_ENFORCEMENT_ACTIVE &&
                session.state != SessionState.LOCK_TRANSITION_PENDING
            ) {
                return
            }

            // If true Device Owner Lock Task is already enforcing at OS level, no redirect needed
            if (session.enforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK &&
                container.devicePolicyController.getLockTaskModeState() ==
                android.app.ActivityManager.LOCK_TASK_MODE_LOCKED
            ) {
                return
            }

            val foregroundPkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
                ?: runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return
            val foregroundClass = event.className?.toString().orEmpty()

            // Do not fight OS keyguard or screen-off state
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isInteractive == false) return
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguard?.isKeyguardLocked == true) return

            // Allow Resolve itself
            if (foregroundPkg == packageName) return

            // Allow emergency safety access unconditionally
            val emergencyPackages = container.phoneAccessManager.resolveEmergencyPackages()
            if (emergencyPackages.contains(foregroundPkg)) return

            // Allow active input methods (keyboards) inside allowed apps
            if (getImePackages().contains(foregroundPkg)) return

            // Allow essential Dialer / InCallUI if Phone Access is enabled
            if (session.phoneAccessEnabled) {
                val dialerPackages = container.phoneAccessManager.resolveEssentialDialerPackages()
                if (dialerPackages.contains(foregroundPkg)) return
            }

            // Allow explicitly user-selected allowed applications
            if (session.allowedPackages.contains(foregroundPkg)) return

            // Handle SystemUI & Android platform windows carefully:
            // Preserve volume/power safety dialogs, intercept Recents & Notification Shade
            if (foregroundPkg == "android") {
                return
            }
            if (foregroundPkg == "com.android.systemui") {
                val isEscapeAttempt = foregroundClass.contains("Recents", ignoreCase = true) ||
                    foregroundClass.contains("Notification", ignoreCase = true) ||
                    foregroundClass.contains("Shade", ignoreCase = true) ||
                    foregroundClass.contains("QuickSettings", ignoreCase = true) ||
                    foregroundClass.contains("StatusBar", ignoreCase = true)
                if (!isEscapeAttempt) {
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }
                }
            }

            // Any other foreground package (Home launcher, Settings, blocked apps, etc.) -> redirect to LockScreenActivity
            redirectToLockScreenDebounced()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnectedFlow.value = false
        runCatching {
            val app = applicationContext as? ResolveApplication
            app?.container?.sessionEngine?.refreshCapabilities()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isConnectedFlow.value = false
        super.onDestroy()
    }

    private fun redirectToLockScreenDebounced() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectElapsedMs < REDIRECT_DEBOUNCE_MS) return
        lastRedirectElapsedMs = now

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(lockIntent) }
    }

    private fun getImePackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - lastImeRefreshElapsedMs < 15_000L && cachedImePackages.isNotEmpty()) {
            return cachedImePackages
        }
        lastImeRefreshElapsedMs = now
        cachedImePackages = runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.mapNotNull { it.packageName }?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        return cachedImePackages
    }

    companion object {
        private const val REDIRECT_DEBOUNCE_MS = 320L
        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()
    }
}
