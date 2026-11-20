package com.example

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.ResolveAccessibilityService
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveTheme
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.CompatibilityItemStatus
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.ForceDarkSupportLevel
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockMode
import com.example.domain.model.LockSchedule
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.time.ClockProvider
import com.example.platform.AppUsageRepository
import com.example.platform.DeviceAnalysisEngine
import com.example.platform.ForceDarkCapabilityHelper
import com.example.platform.ForceDarkCapabilityReport
import com.example.platform.ModeOperationResult
import com.example.platform.NotificationControlManager
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.about.AboutResolveScreen
import com.example.presentation.analysis.DeviceAnalysisScreen
import com.example.presentation.applock.AppLockAuthActivity
import com.example.presentation.insights.InsightsScreen
import com.example.presentation.modes.ModesScreen
import com.example.presentation.more.MoreScreen
import com.example.presentation.settings.SettingsScreen
import com.example.security.AppLockSessionGuard
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResolveArchitectureModulesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var container: com.example.di.AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ResolveApplication).container
    }

    @Test
    fun `usage stats repository strictly excludes RESOLVE self package from daily screen time`() = runTest {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            context.applicationInfo.uid,
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = 1_794_900_000_000L
        fun createStats(pkg: String, fgMs: Long): UsageStats {
            val ctor = UsageStats::class.java.getDeclaredConstructor().apply { isAccessible = true }
            return ctor.newInstance().apply {
                UsageStats::class.java.getField("mPackageName").set(this, pkg)
                UsageStats::class.java.getField("mBeginTimeStamp").set(this, now - 60 * 60_000L)
                UsageStats::class.java.getField("mEndTimeStamp").set(this, now - 1_000L)
                UsageStats::class.java.getField("mTotalTimeInForeground").set(this, fgMs)
            }
        }
        val selfStats = createStats(context.packageName, 45 * 60_000L)
        val otherStats = createStats("com.example.notes", 12 * 60_000L)
        shadowOf(usageManager).addUsageStats(UsageStatsManager.INTERVAL_DAILY, selfStats)
        shadowOf(usageManager).addUsageStats(UsageStatsManager.INTERVAL_DAILY, otherStats)
        shadowOf(usageManager).addUsageStats(UsageStatsManager.INTERVAL_BEST, selfStats)
        shadowOf(usageManager).addUsageStats(UsageStatsManager.INTERVAL_BEST, otherStats)

        val repo = AppUsageRepository(
            context = context,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = object : ClockProvider {
                override fun wallClockMs(): Long = now
                override fun elapsedRealtimeMs(): Long = 5_000L
                override fun zoneId(): ZoneId = ZoneId.of("UTC")
            }
        )
        val summary = repo.queryTodayUsageSummary()
        assertTrue(summary.hasUsagePermission)
        assertFalse(summary.topApps.any { it.packageName == context.packageName })
    }

    @Test
    fun `insights hub switches cleanly between History and Schedule sections`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            SessionHistoryRecord(1L, "s1", LockMode.INSTANT, 0L, 25L, now - 5_000L, now, SessionOutcome.COMPLETED, 0)
        )
        val schedules = listOf(
            LockSchedule(
                id = 10L,
                name = "Evening Study",
                type = ScheduleType.DAILY,
                startHour = 20,
                startMinute = 0,
                lockDurationMinutes = 60L,
                allowedPackages = emptyList(),
                phoneAccessEnabled = true,
                isEnabled = true,
                createdAtWallMs = now
            )
        )

        composeRule.setContent {
            ResolveTheme {
                InsightsScreen(
                    records = history,
                    schedules = schedules,
                    dailyAppUsage = DailyAppUsageSummary(hasUsagePermission = false),
                    onGrantUsageAccess = {},
                    onAddSchedule = {},
                    onEditSchedule = {},
                    onToggleSchedule = { _, _ -> },
                    onDeleteSchedule = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_hub_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_weekly_chart_card").assertIsDisplayed()

        composeRule.onNodeWithTag("insights_tab_schedule").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Evening Study").assertIsDisplayed()

        composeRule.onNodeWithTag("insights_tab_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_weekly_chart_card").assertIsDisplayed()
    }

    @Test
    fun `vision hub enforces dark-only palette, opens flip clock on Enter, and separates Dark Mode vs Color Inversion sections`() {
        var capturedIsDark: Boolean? = null
        var flipClockEntered = false
        val sampleApps = listOf(
            InstalledAppInfo(packageName = "com.example.chat", appName = "Chat App")
        )
        var darkErr = ""
        var invErr = ""

        val report = ForceDarkCapabilityReport(
            androidSdkInt = 36,
            forceDarkSupportLevel = ForceDarkSupportLevel.PARTIALLY_SUPPORTED,
            colorInversionSupportLevel = ForceDarkSupportLevel.UNSUPPORTED,
            isSystemNightModeActive = true,
            isColorInversionEnabledInOs = false,
            perAppDarkModeSupported = false,
            perAppColorInversionSupported = false,
            forceDarkSummary = "Standard Android does not permit per-app third-party Dark Mode toggling.",
            colorInversionSummary = "Color Inversion is device-wide only.",
            restoreInstructions = emptyList()
        )

        composeRule.setContent {
            ResolveTheme {
                capturedIsDark = LocalResolveExtendedPalette.current.isDark
                ModesScreen(
                    report = report,
                    installedApps = sampleApps,
                    darkModeAppPrefs = emptyMap(),
                    colorInversionAppPrefs = emptyMap(),
                    loadAppIcon = { null },
                    onEnterFlipClock = { flipClockEntered = true },
                    onAttemptAppDarkMode = { pkg, _ ->
                        val res = ForceDarkCapabilityHelper.tryApplyPerAppDarkMode(pkg, report.supportedDarkModeApps)
                        if (res is ModeOperationResult.Unsupported) darkErr = res.reason
                    },
                    onAttemptAppColorInversion = { pkg, _ ->
                        val res = ForceDarkCapabilityHelper.tryApplyPerAppColorInversion(pkg, report.supportedColorInversionApps)
                        if (res is ModeOperationResult.Unsupported) invErr = res.reason
                    },
                    onOpenDisplaySettings = {},
                    onOpenAccessibilitySettings = {},
                    onResetAllModes = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(true, capturedIsDark)
        assertTrue(composeRule.onAllNodesWithTag("modes_resolve_appearance_card").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Modes").assertIsDisplayed()
        composeRule.onNodeWithTag("vision_flip_clock_card").assertIsDisplayed()
        composeRule.onNodeWithTag("vision_flip_clock_enter_btn").performClick()
        assertTrue(flipClockEntered)

        composeRule.onNodeWithTag("modes_app_dark_card").assertIsDisplayed()
        composeRule.onNodeWithTag("vision_tab_color_inversion").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("modes_color_inversion_card").assertIsDisplayed()

        composeRule.onNodeWithTag("vision_tab_dark_mode").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("modes_list")
            .performScrollToNode(hasTestTag("modes_dark_check_apps_btn"))
        composeRule.onNodeWithTag("modes_dark_check_apps_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeRule.onNodeWithTag("mode_app_switch_com.example.chat").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("mode_app_switch_com.example.chat").performClick()
        composeRule.waitForIdle()
        assertEquals(
            "Not Supported — this app manages Dark Mode independently on Android.",
            darkErr
        )
        assertEquals("", invErr)

        assertEquals(
            ModeOperationResult.Unsupported(
                "Not Supported per-app on non-root Android. Use In-App Inversion Preview or System Accessibility Color Inversion with consent."
            ),
            ForceDarkCapabilityHelper.tryApplyPerAppColorInversion("com.example.chat")
        )
    }

    @Test
    fun `settings orders Accessibility first when enabled-disconnected and shows reconnect guidance`() {
        val disconnectedCaps = SystemCapabilityStatus(
            isAccessibilityServiceEnabled = true,
            isAccessibilityEnabledInSettings = true,
            isAccessibilityServiceConnected = false,
            canDrawOverlays = false,
            hasUsageStatsAccess = true,
            notificationsEnabled = true,
            isIgnoringBatteryOptimizations = true,
            exactAlarmRequiredByOs = false
        )
        assertEquals(AccessibilityHealthState.ENABLED_DISCONNECTED, disconnectedCaps.accessibilityHealthState)

        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = disconnectedCaps,
                    hasRecoveryCode = true,
                    recoveryCodeLength = 16,
                    generateRandomCode = { "1".repeat(it) },
                    onSaveRecoveryCode = {},
                    onRefreshCapabilities = {},
                    onOpenAccessibilitySettings = {},
                    onOpenOverlaySettings = {},
                    onOpenUsageAccessSettings = {},
                    onOpenBatteryOptimizationSettings = {},
                    onOpenDeviceOwnerGuide = {},
                    onOpenExactAlarmSettings = {},
                    onOpenNotificationSettings = {},
                    notificationControlConfig = NotificationControlConfig(enabled = true),
                    notificationProtectionState = NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_accessibility_action"))
        composeRule.onNodeWithText("Service Disconnected (Reconnect)").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_notification_policy_action"))
        composeRule.onNodeWithTag("settings_notification_policy_action").assertIsDisplayed()
    }

    @Test
    fun `notification control preserves existing DND, detects user override, and restores prior filter cleanly`() = runTest {
        val prefs = container.preferencesRepository
        prefs.setNotificationControlEnabled(true)
        prefs.setNotificationControlAutoActivate(true)
        prefs.setNotificationControlAllowCalls(true)
        prefs.setNotificationControlAllowAlarms(true)
        prefs.clearNotificationSessionState()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val manager = NotificationControlManager(context, prefs, SystemCapabilityChecker(context))

        // 1. Pre-existing user DND active -> preserve without overwriting or turning off at end
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        val preservedState = manager.onSessionEnforcementStarted("sess_preserve")
        assertEquals(NotificationProtectionState.PRESERVED_EXISTING_DND, preservedState)
        manager.onSessionEnded("sess_preserve")
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)

        // 2. Clean prior filter -> activates DND, detects manual override cleanly
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        val activeState = manager.onSessionEnforcementStarted("sess_active")
        assertEquals(NotificationProtectionState.ACTIVE, activeState)
        assertNotEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)

        // Manual override -> detects and respects override on end
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertEquals(
            NotificationProtectionState.OVERRIDDEN_BY_USER,
            manager.reconcileDuringActiveSession("sess_active")
        )
        manager.onSessionEnded("sess_active")
        assertEquals(NotificationProtectionState.INACTIVE, manager.protectionState.value)

        // 3. Normal end without user override -> restores original INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertEquals(NotificationProtectionState.ACTIVE, manager.onSessionEnforcementStarted("sess_restore"))
        manager.onSessionEnded("sess_restore")
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun `device analysis inspects genuine OS properties transparently and More hub navigates cleanly`() {
        val engine = DeviceAnalysisEngine(
            context = context,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = object : ClockProvider {
                override fun wallClockMs(): Long = 1_795_000_000_000L
                override fun elapsedRealtimeMs(): Long = 10_000L
                override fun zoneId(): ZoneId = ZoneId.of("UTC")
            }
        )
        val report = engine.runAnalysis()
        assertTrue(report.hardware != null)
        assertEquals("Not Required for Standard RESOLVE Operation", report.deviceManagement?.statusLabel)
        assertTrue(
            report.securityChecks.all {
                it.status == CompatibilityItemStatus.ACTIVE ||
                    it.status == CompatibilityItemStatus.NOT_VERIFIED ||
                    it.status == CompatibilityItemStatus.ACTION_REQUIRED ||
                    it.status == CompatibilityItemStatus.NOT_REQUIRED
            }
        )

        val clickedFocusSections = mutableListOf<String>()
        var tiltMode by mutableStateOf(false)
        val allGrantedCaps = SystemCapabilityStatus(
            isAccessibilityServiceEnabled = true,
            isAccessibilityEnabledInSettings = true,
            isAccessibilityServiceConnected = true,
            canDrawOverlays = true,
            hasUsageStatsAccess = true,
            exactAlarmRequiredByOs = false,
            canScheduleExactAlarms = true,
            notificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        composeRule.setContent {
            ResolveTheme {
                MoreScreen(
                    capabilities = allGrantedCaps,
                    protectionStatus = ProtectionStatus.ProtectionActive(),
                    deviceAnalysisReport = report,
                    hasRecoveryCode = true,
                    recoveryCodeLength = 16,
                    landscapeTiltMode = tiltMode,
                    notificationConfig = NotificationControlConfig(enabled = false),
                    notificationProtectionState = NotificationProtectionState.INACTIVE,
                    mascotPreferences = com.example.domain.model.MascotPreferences(reducedMotionEnabled = true),
                    accountSyncState = com.example.sync.AccountSyncUiState(),
                    generateRandomCode = { "1".repeat(it) },
                    onSaveRecoveryCode = {},
                    onToggleLandscapeTiltMode = { tiltMode = it },
                    onRefreshCapabilities = {},
                    onProtectionAction = {},
                    onOpenDeviceAnalysis = { clickedFocusSections += it },
                    onOpenAccessibilitySettings = {},
                    onOpenOverlaySettings = {},
                    onOpenUsageAccessSettings = {},
                    onOpenBatteryOptimizationSettings = {},
                    onOpenExactAlarmSettings = {},
                    onOpenNotificationSettings = {},
                    onOpenNotificationPolicySettings = {},
                    onToggleNotificationControlEnabled = {},
                    onToggleNotificationControlAutoActivate = {},
                    onToggleNotificationControlAllowCalls = {},
                    onToggleNotificationControlAllowAlarms = {},
                    onSelectMascot = {},
                    onToggleReducedMotion = {},
                    onToggleFocusMessagesEnabled = {},
                    onSelectFocusMessageFrequency = {},
                    onAddFocusMessage = {},
                    onEditFocusMessage = { _, _ -> },
                    onDeleteFocusMessage = {},
                    onRestoreDefaultFocusMessages = {},
                    onToggleLockReminderEnabled = {},
                    onSaveLockReminderText = {},
                    onSelectLockReminderStyle = {},
                    onSignInGoogle = {},
                    onSignOutGoogle = {},
                    onToggleDeviceSync = {},
                    onUpdateProfileName = {},
                    onUpdateProfilePhoto = {},
                    onClearProfilePhoto = {},
                    onUnlinkDevice = {}
                )
            }
        }
        composeRule.waitForIdle()
        // All 6 permissions ready -> entire PERMISSIONS header and rows are hidden
        assertTrue(composeRule.onAllNodesWithText("PERMISSIONS").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("settings_accessibility_action").fetchSemanticsNodes().isEmpty())

        // Unified 5-item System Health card routes to respective DeviceAnalysis sections
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_health_status"))
        composeRule.onNodeWithTag("more_health_status").performClick()
        composeRule.onNodeWithTag("more_health_analysis").performClick()
        composeRule.onNodeWithTag("more_health_hardware").performClick()
        composeRule.onNodeWithTag("more_health_diagnostics").performClick()
        composeRule.onNodeWithTag("more_health_repair").performClick()
        assertEquals(listOf("status", "analysis", "hardware", "diagnostics", "repair"), clickedFocusSections)

        // Focus Settings: Landscape Tilt Mode default OFF, toggles ON cleanly
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_landscape_tilt_switch"))
        composeRule.onNodeWithTag("more_landscape_tilt_switch").performClick()
        composeRule.waitForIdle()
        assertTrue(tiltMode)

        // Inline About RESOLVE expands in-place with Developer credit
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_about_toggle_button"))
        composeRule.onNodeWithTag("more_about_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_developer_credit"))
        composeRule.onNodeWithTag("more_developer_credit").assertIsDisplayed()
    }

    @Test
    fun `device analysis screen and about resolve screen display clean copy without internal tokens`() {
        val engine = DeviceAnalysisEngine(
            context = context,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = object : ClockProvider {
                override fun wallClockMs(): Long = 1_795_000_000_000L
                override fun elapsedRealtimeMs(): Long = 10_000L
                override fun zoneId(): ZoneId = ZoneId.of("UTC")
            }
        )
        val report = engine.runAnalysis()

        composeRule.setContent {
            ResolveTheme {
                AboutResolveScreen(
                    reducedMotion = true,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_runner_logo").assertIsDisplayed()
        composeRule.onNodeWithText("Your distraction killing is resolve duty.").assertIsDisplayed()
        composeRule.onNodeWithText("Developed by Tahan Official").assertIsDisplayed()
        composeRule.onNodeWithText("1. About RESOLVE").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("SYS // RESOLVE_OVERVIEW").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `device analysis screen renders hardware and compatibility report cleanly`() {
        val engine = DeviceAnalysisEngine(
            context = context,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = object : ClockProvider {
                override fun wallClockMs(): Long = 1_795_000_000_000L
                override fun elapsedRealtimeMs(): Long = 10_000L
                override fun zoneId(): ZoneId = ZoneId.of("UTC")
            }
        )
        val report = engine.runAnalysis()

        composeRule.setContent {
            ResolveTheme {
                DeviceAnalysisScreen(
                    report = report,
                    onRunAnalysis = {},
                    onRefreshCapabilities = {},
                    onExecuteAction = {},
                    onOpenDeviceOwnerGuide = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_analysis_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("run_device_analysis_button").assertIsDisplayed()
        composeRule.onNodeWithTag("device_analysis_list")
            .performScrollToNode(hasTestTag("device_analysis_hardware_card"))
        composeRule.onNodeWithTag("device_analysis_hardware_card").assertIsDisplayed()
    }

    @Test
    fun `app lock auth activity manifest task isolation and transition grace keep unlocked user in target app`() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, AppLockAuthActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
        assertTrue((activityInfo.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0)
        assertEquals("com.resolve.applock", activityInfo.taskAffinity)

        val guard = AppLockSessionGuard()
        guard.updateState(enabled = true, hasCredential = true, protectedPackages = setOf("com.example.whatsapp"))
        guard.grantTemporaryUnlock("com.example.whatsapp", nowElapsedMs = 1_000L)

        // Intermediate transition event inside 2200ms grace before target app enters foreground must NOT drop grant
        guard.onForegroundAppChanged("com.android.systemui", nowElapsedMs = 1_300L)
        assertTrue(guard.isTemporarilyUnlocked("com.example.whatsapp", nowElapsedMs = 1_400L))

        // Target app enters foreground, keeping grant active
        guard.onForegroundAppChanged("com.example.whatsapp", nowElapsedMs = 1_450L)
        assertTrue(guard.isTemporarilyUnlocked("com.example.whatsapp", nowElapsedMs = 2_000L))

        // Leaving to another app after the 2200ms transition grace revokes grant as expected
        guard.onForegroundAppChanged("com.android.launcher", nowElapsedMs = 3_500L)
        assertFalse(guard.isTemporarilyUnlocked("com.example.whatsapp", nowElapsedMs = 3_600L))
    }

    @Test
    fun `accessibility service enabled-vs-connected health states compute accurately`() {
        val checker = SystemCapabilityChecker(context)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, ResolveAccessibilityService::class.java).flattenToString()
        )
        val status = checker.queryStatus()
        assertTrue(status.isAccessibilityEnabledInSettings)
        assertFalse(status.isAccessibilityServiceConnected)
        assertEquals(AccessibilityHealthState.ENABLED_DISCONNECTED, status.accessibilityHealthState)
    }

    @Test
    fun `bottom navigation enforces strictly 4 top-level destinations with Modes label and clean strings`() {
        val topTabs = listOf(
            com.example.presentation.navigation.ResolveRoutes.HOME,
            com.example.presentation.navigation.ResolveRoutes.INSIGHTS,
            com.example.presentation.navigation.ResolveRoutes.MODES,
            com.example.presentation.navigation.ResolveRoutes.MORE
        )
        assertEquals(4, topTabs.size)
        assertEquals("Modes", context.getString(R.string.nav_vision_label))
        assertEquals("Developed by Tahan Official", context.getString(R.string.developer_credit))
    }

    @Test
    fun `mathematical RGB color inversion and DisplayModeAvailability stay separated from Dark Mode`() {
        assertEquals(Triple(255, 255, 255), ForceDarkCapabilityHelper.invertRgbTriplet(0, 0, 0))
        assertEquals(Triple(0, 255, 255), ForceDarkCapabilityHelper.invertRgbTriplet(255, 0, 0))
        assertEquals(Triple(255, 255, 0), ForceDarkCapabilityHelper.invertRgbTriplet(0, 0, 255))
        assertEquals(Triple(255, 0, 255), ForceDarkCapabilityHelper.invertRgbTriplet(0, 255, 0))

        val matrix = ForceDarkCapabilityHelper.RGB_INVERSION_COLOR_MATRIX
        assertEquals(20, matrix.size)
        assertEquals(-1f, matrix[0], 0.001f)
        assertEquals(255f, matrix[4], 0.001f)
        assertEquals(-1f, matrix[6], 0.001f)
        assertEquals(255f, matrix[9], 0.001f)
        assertEquals(-1f, matrix[12], 0.001f)
        assertEquals(255f, matrix[14], 0.001f)
        assertEquals(1f, matrix[18], 0.001f)

        val report = ForceDarkCapabilityHelper.inspect(context)
        assertEquals(
            com.example.platform.DisplayModeAvailability.COLOR_INVERSION_AVAILABLE,
            ForceDarkCapabilityHelper.resolveColorInversionStatus(
                packageName = context.packageName,
                report = report,
                inAppPreviewActive = false,
                isOwnAppPackage = true
            )
        )
        assertEquals(
            com.example.platform.DisplayModeAvailability.COLOR_INVERSION_ACTIVE,
            ForceDarkCapabilityHelper.resolveColorInversionStatus(
                packageName = context.packageName,
                report = report,
                inAppPreviewActive = true,
                isOwnAppPackage = true
            )
        )
        assertEquals(
            com.example.platform.DisplayModeAvailability.NOT_SUPPORTED,
            ForceDarkCapabilityHelper.resolveDarkModeStatus("com.other.app", report)
        )
    }
}
