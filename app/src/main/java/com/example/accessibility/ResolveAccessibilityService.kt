package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.example.ResolveApplication
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.EnforcementMode
import com.example.domain.model.SessionState
import com.example.presentation.applock.AppLockAuthActivity
import com.example.presentation.guard.MindfulPauseActivity
import com.example.presentation.guard.QuotaReachedActivity
import com.example.presentation.lockscreen.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

class ResolveAccessibilityService : AccessibilityService() {

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastRedirectElapsedMs: Long = 0L
    private var lastContentCheckElapsedMs: Long = 0L
    private var lastContentBlockElapsedMs: Long = 0L
    private var lastToastElapsedMs: Long = 0L
    private var lastMindfulCheckPkg: String = ""
    private var lastMindfulCheckElapsedMs: Long = 0L
    private var lastQuotaCheckPkg: String = ""
    private var lastQuotaCheckElapsedMs: Long = 0L

    private var cachedImePackages: Set<String> = emptySet()
    private var lastImeRefreshElapsedMs: Long = 0L
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnectedFlow.value = true
        registerScreenOffReceiver()
        runCatching {
            val app = applicationContext as? ResolveApplication
            app?.container?.sessionEngine?.refreshCapabilities()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        runCatching {
            val eventType = event.eventType
            val isContentChange = eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                !isContentChange
            ) {
                return
            }

            val app = applicationContext as? ResolveApplication ?: return
            val container = app.container
            val nowElapsed = SystemClock.elapsedRealtime()

            val foregroundPkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
                ?: runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return
            val foregroundClass = event.className?.toString().orEmpty()

            if (isContentChange) {
                val cfg = container.contentBlockerConfigState.value
                if (!cfg.enabled || !isInspectableContentPackage(foregroundPkg)) {
                    return
                }
                if (nowElapsed - lastContentCheckElapsedMs < CONTENT_CHECK_THROTTLE_MS) {
                    return
                }
                lastContentCheckElapsedMs = nowElapsed
            }

            // Do not fight OS keyguard or screen-off state
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isInteractive == false) return
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguard?.isKeyguardLocked == true) return

            val engine = container.sessionEngine
            val session = engine.activeSession.value
            val isFocusLockActive = session != null && (
                session.state == SessionState.LOCK_ENFORCEMENT_ACTIVE ||
                    session.state == SessionState.LOCK_TRANSITION_PENDING
                )

            if (isFocusLockActive) {
                val activeSession = session!!
                // If true Device Owner Lock Task is already enforcing at OS level, no redirect needed
                if (activeSession.enforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK &&
                    container.devicePolicyController.getLockTaskModeState() == 1
                ) {
                    return
                }

                // Allow Resolve itself
                if (foregroundPkg == packageName) return

                // Allow emergency safety access unconditionally
                val emergencyPackages = container.phoneAccessManager.resolveEmergencyPackages()
                if (emergencyPackages.contains(foregroundPkg)) return

                // Allow essential Dialer / InCallUI if Phone Access is enabled
                if (activeSession.phoneAccessEnabled) {
                    val dialerPackages = container.phoneAccessManager.resolveEssentialDialerPackages()
                    if (dialerPackages.contains(foregroundPkg)) return
                }

                // Strict Mode Anti-Bypass Guard: block Settings, Accessibility, App Info, Date/Time,
                // and Package Installer tamper attempts even before allowlists.
                if (activeSession.isStrictMode && container.antiBypassStrictGuardState.value) {
                    val isTamperApp = STRICT_ANTI_BYPASS_PACKAGES.contains(foregroundPkg) ||
                        foregroundClass.contains("AccessibilitySettings", ignoreCase = true) ||
                        foregroundClass.contains("DateTimeSettings", ignoreCase = true) ||
                        foregroundClass.contains("InstalledAppDetails", ignoreCase = true) ||
                        foregroundClass.contains("ManageApplications", ignoreCase = true) ||
                        foregroundClass.contains("DeviceAdminAdd", ignoreCase = true)
                    if (isTamperApp) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        if (redirectToLockScreenDebounced()) {
                            container.recordInterceptedAppAsync(foregroundPkg)
                            showThrottledToast("Settings locked during Strict Focus Mode")
                        }
                        return
                    }
                }

                // Allow active input methods (keyboards) inside allowed apps
                if (getImePackages().contains(foregroundPkg)) return

                // Allow explicitly user-selected allowed applications (still enforce Content Blocker if active)
                if (activeSession.allowedPackages.contains(foregroundPkg)) {
                    val contentCfg = container.contentBlockerConfigState.value
                    if (contentCfg.enabled) {
                        checkAndBlockDistractingContent(foregroundPkg, contentCfg, container)
                    }
                    return
                }

                if (isContentChange) return

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

                // Any other foreground package -> redirect to Focus LockScreenActivity
                if (redirectToLockScreenDebounced()) {
                    container.recordInterceptedAppAsync(foregroundPkg)
                }
                return
            }

            // Secondary protections when Focus Lock is not actively enforcing
            if (foregroundPkg == packageName ||
                foregroundPkg == "android" ||
                foregroundPkg == "com.android.systemui" ||
                getImePackages().contains(foregroundPkg)
            ) {
                return
            }

            val emergencyPackages = container.phoneAccessManager.resolveEmergencyPackages()
            if (emergencyPackages.contains(foregroundPkg)) return
            val dialerPackages = container.phoneAccessManager.resolveEssentialDialerPackages()
            if (dialerPackages.contains(foregroundPkg)) return

            if (!isContentChange) {
                // 1. Genuine App Lock
                val guard = container.appLockSessionGuard
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    guard.onForegroundAppChanged(foregroundPkg)
                }
                if (guard.shouldLaunchPromptFor(foregroundPkg)) {
                    launchAppLockAuthScreen(foregroundPkg)
                    return
                }

                // 2. Daily App Usage Quota (offload UsageStats + DataStore check to background)
                val quotaRule = container.dailyQuotaRulesState.value.firstOrNull {
                    it.enabled && it.packageName == foregroundPkg
                }
                if (quotaRule != null &&
                    (lastQuotaCheckPkg != foregroundPkg || nowElapsed - lastQuotaCheckElapsedMs >= QUOTA_CHECK_COOLDOWN_MS)
                ) {
                    lastQuotaCheckPkg = foregroundPkg
                    lastQuotaCheckElapsedMs = nowElapsed
                    serviceScope.launch {
                        val nowWall = container.clockProvider.wallClockMs()
                        val todayEpoch = Instant.ofEpochMilli(nowWall)
                            .atZone(container.clockProvider.zoneId())
                            .toLocalDate()
                            .toEpochDay()
                        val overridden = runCatching {
                            container.preferencesRepository.isQuotaOverriddenForDay(foregroundPkg, todayEpoch)
                        }.getOrDefault(false)
                        if (!overridden) {
                            val usedMs = runCatching {
                                container.appUsageRepository.queryTodayUsageForPackageMs(foregroundPkg)
                            }.getOrDefault(0L)
                            val limitMs = quotaRule.dailyLimitMinutes.coerceIn(5, 600) * 60_000L
                            if (usedMs >= limitMs) {
                                container.recordInterceptedAppAsync(foregroundPkg)
                                val usedMins = (usedMs / 60_000L).toInt().coerceAtLeast(quotaRule.dailyLimitMinutes)
                                launchQuotaReachedScreen(
                                    packageName = foregroundPkg,
                                    appName = quotaRule.appName,
                                    limitMinutes = quotaRule.dailyLimitMinutes,
                                    usedMinutes = usedMins
                                )
                            }
                        }
                    }
                    return
                }

                // 3. Mindful Breathing Pause
                val mindfulCfg = container.mindfulPauseConfigState.value
                if (mindfulCfg.enabled &&
                    mindfulCfg.targetPackages.contains(foregroundPkg) &&
                    (lastMindfulCheckPkg != foregroundPkg || nowElapsed - lastMindfulCheckElapsedMs >= MINDFUL_COOLDOWN_MS)
                ) {
                    lastMindfulCheckPkg = foregroundPkg
                    lastMindfulCheckElapsedMs = nowElapsed
                    serviceScope.launch {
                        val nowWall = container.clockProvider.wallClockMs()
                        val hasPass = runCatching {
                            container.preferencesRepository.hasActiveMindfulPass(foregroundPkg, nowWall)
                        }.getOrDefault(false)
                        if (!hasPass) {
                            container.recordInterceptedAppAsync(foregroundPkg)
                            val label = resolveAppLabel(foregroundPkg)
                            launchMindfulPauseScreen(
                                packageName = foregroundPkg,
                                appName = label,
                                delaySeconds = mindfulCfg.clampedDelaySeconds,
                                passMinutes = mindfulCfg.clampedPassMinutes
                            )
                        }
                    }
                    return
                }
            }

            // 4. Content Blocker (YouTube Shorts, Instagram Reels, Blocked Website Domains)
            val contentCfg = container.contentBlockerConfigState.value
            if (contentCfg.enabled && !contentCfg.onlyDuringFocusLock && isInspectableContentPackage(foregroundPkg)) {
                checkAndBlockDistractingContent(foregroundPkg, contentCfg, container)
            }
        }
    }

    private fun checkAndBlockDistractingContent(
        foregroundPkg: String,
        cfg: ContentBlockerConfig,
        container: com.example.di.AppContainer
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastContentBlockElapsedMs < CONTENT_BLOCK_DEBOUNCE_MS) return

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val matched = try {
            val state = TraversalBudget(maxNodes = 48)
            when {
                foregroundPkg == YOUTUBE_PACKAGE && cfg.blockYouTubeShorts ->
                    findYouTubeShortsNode(root, depth = 0, budget = state)
                foregroundPkg == INSTAGRAM_PACKAGE && cfg.blockInstagramReels ->
                    findInstagramReelsNode(root, depth = 0, budget = state)
                isBrowserPackage(foregroundPkg) && cfg.blockedDomains.isNotEmpty() ->
                    findBlockedDomainInBrowser(root, cfg.blockedDomains, depth = 0, budget = state)
                else -> false
            }
        } finally {
            recycleCompat(root)
        }

        if (matched) {
            lastContentBlockElapsedMs = nowElapsed
            performGlobalAction(GLOBAL_ACTION_BACK)
            container.recordInterceptedAppAsync(foregroundPkg)
            showThrottledToast("Blocked by Resolve Focus Guard")
        }
    }

    private class TraversalBudget(var maxNodes: Int)

    private fun findYouTubeShortsNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: TraversalBudget
    ): Boolean {
        if (depth > 8 || budget.maxNodes <= 0) return false
        budget.maxNodes--

        val viewId = node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
        if (viewId.contains("reel_watch") ||
            viewId.contains("reel_player") ||
            viewId.contains("reel_recycler") ||
            viewId.contains("shorts_player") ||
            viewId.contains("shorts_shelf")
        ) {
            return true
        }
        if (node.isSelected) {
            val text = node.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            if (text == "shorts" || desc == "shorts") {
                return true
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val hit = try {
                findYouTubeShortsNode(child, depth + 1, budget)
            } finally {
                recycleCompat(child)
            }
            if (hit) return true
        }
        return false
    }

    private fun findInstagramReelsNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: TraversalBudget
    ): Boolean {
        if (depth > 8 || budget.maxNodes <= 0) return false
        budget.maxNodes--

        val viewId = node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
        if (viewId.contains("clips_viewer") ||
            viewId.contains("clips_video") ||
            viewId.contains("clips_tab") && node.isSelected ||
            viewId.contains("reels_viewer")
        ) {
            return true
        }
        if (node.isSelected) {
            val text = node.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            if (text == "reels" || desc == "reels") {
                return true
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val hit = try {
                findInstagramReelsNode(child, depth + 1, budget)
            } finally {
                recycleCompat(child)
            }
            if (hit) return true
        }
        return false
    }

    private fun findBlockedDomainInBrowser(
        node: AccessibilityNodeInfo,
        blockedDomains: List<String>,
        depth: Int,
        budget: TraversalBudget
    ): Boolean {
        if (depth > 8 || budget.maxNodes <= 0) return false
        budget.maxNodes--

        val viewId = node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
        val className = node.className?.toString()?.lowercase(Locale.US).orEmpty()
        val isUrlField = viewId.contains("url_bar") ||
            viewId.contains("location_bar") ||
            viewId.contains("omnibox") ||
            viewId.contains("address_bar") ||
            viewId.contains("mozac_browser_toolbar_url_view") ||
            (className.endsWith("edittext") && !node.isFocused)

        if (isUrlField) {
            val rawUrl = node.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            if (rawUrl.isNotBlank() && !node.isFocused) {
                val hostCandidate = rawUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore('/')
                    .substringBefore('?')
                if (blockedDomains.any { d ->
                        hostCandidate == d || hostCandidate.endsWith(".$d")
                    }
                ) {
                    return true
                }
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val hit = try {
                findBlockedDomainInBrowser(child, blockedDomains, depth + 1, budget)
            } finally {
                recycleCompat(child)
            }
            if (hit) return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun recycleCompat(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { node.recycle() }
        }
    }

    private fun isInspectableContentPackage(pkg: String): Boolean {
        return pkg == YOUTUBE_PACKAGE || pkg == INSTAGRAM_PACKAGE || isBrowserPackage(pkg)
    }

    private fun isBrowserPackage(pkg: String): Boolean {
        if (KNOWN_BROWSER_PACKAGES.contains(pkg)) return true
        val lower = pkg.lowercase(Locale.US)
        return lower.contains("browser") || lower.contains("chrome")
    }

    private fun resolveAppLabel(pkg: String): String {
        return runCatching {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
    }

    private fun showThrottledToast(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastElapsedMs < TOAST_THROTTLE_MS) return
        lastToastElapsedMs = now
        mainHandler.post {
            runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnectedFlow.value = false
        unregisterScreenOffReceiver()
        runCatching {
            val app = applicationContext as? ResolveApplication
            app?.container?.sessionEngine?.refreshCapabilities()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isConnectedFlow.value = false
        unregisterScreenOffReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    val app = applicationContext as? ResolveApplication
                    app?.container?.appLockSessionGuard?.onScreenOff()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenOffReceiver = receiver
        }
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        screenOffReceiver = null
    }

    private fun launchAppLockAuthScreen(targetPkg: String) {
        val intent = Intent(this, AppLockAuthActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(AppLockAuthActivity.EXTRA_TARGET_PACKAGE, targetPkg)
        }
        runCatching { startActivity(intent) }
    }

    private fun launchMindfulPauseScreen(
        packageName: String,
        appName: String,
        delaySeconds: Int,
        passMinutes: Int
    ) {
        val intent = Intent(this, MindfulPauseActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MindfulPauseActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(MindfulPauseActivity.EXTRA_APP_NAME, appName)
            putExtra(MindfulPauseActivity.EXTRA_DELAY_SECONDS, delaySeconds)
            putExtra(MindfulPauseActivity.EXTRA_PASS_MINUTES, passMinutes)
        }
        runCatching { startActivity(intent) }
    }

    private fun launchQuotaReachedScreen(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        usedMinutes: Int
    ) {
        val intent = Intent(this, QuotaReachedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(QuotaReachedActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(QuotaReachedActivity.EXTRA_APP_NAME, appName)
            putExtra(QuotaReachedActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
            putExtra(QuotaReachedActivity.EXTRA_USED_MINUTES, usedMinutes)
        }
        runCatching { startActivity(intent) }
    }

    private fun redirectToLockScreenDebounced(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectElapsedMs < REDIRECT_DEBOUNCE_MS) return false
        lastRedirectElapsedMs = now

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(lockIntent) }
        return true
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
        private const val CONTENT_CHECK_THROTTLE_MS = 380L
        private const val CONTENT_BLOCK_DEBOUNCE_MS = 1_200L
        private const val TOAST_THROTTLE_MS = 2_500L
        private const val MINDFUL_COOLDOWN_MS = 3_500L
        private const val QUOTA_CHECK_COOLDOWN_MS = 3_000L

        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        private val STRICT_ANTI_BYPASS_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.securitycenter",
            "com.samsung.android.settings"
        )

        private val KNOWN_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser"
        )

        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()
    }
}
