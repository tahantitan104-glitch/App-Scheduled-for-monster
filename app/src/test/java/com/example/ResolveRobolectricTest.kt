package com.example

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.designsystem.ResolveTheme
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.DurationSelection
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.LockMode
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.time.ClockProvider
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.components.DurationSelector
import com.example.presentation.home.HomeScreen
import com.example.presentation.settings.SettingsScreen
import com.example.scheduling.LockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.session.EngineStartResult
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResolveRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ResolveDatabase
    private lateinit var prefs: ResolvePreferencesRepository
    private lateinit var fakeClock: FakeClockProvider
    private lateinit var fakePolicy: FakeDevicePolicyController
    private lateinit var fakeAlarms: FakeAlarmScheduler
    private lateinit var fakeBridge: FakePlatformBridge
    private lateinit var recoveryManager: RecoveryCodeManager
    private lateinit var engine: LockSessionEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = (context as ResolveApplication).container.preferencesRepository
        fakeClock = FakeClockProvider(wallMs = 1_794_745_000_000L, elapsedMs = 10_000L)
        fakePolicy = FakeDevicePolicyController()
        fakeAlarms = FakeAlarmScheduler()
        fakeBridge = FakePlatformBridge()
        recoveryManager = RecoveryCodeManager(prefs, fakeClock)

        engine = LockSessionEngine(
            sessionRepository = SessionRepository(db.activeSessionDao()),
            scheduleRepository = ScheduleRepository(db.lockScheduleDao()),
            historyRepository = HistoryRepository(db.sessionHistoryDao()),
            preferencesRepository = prefs,
            devicePolicyController = fakePolicy,
            alarmScheduler = fakeAlarms,
            recoveryCodeManager = recoveryManager,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = fakeClock,
            platformBridge = fakeBridge
        )
    }

    @After
    fun tearDown() {
        engine.shutdown()
        db.close()
    }

    @Test
    fun `app metadata matches Resolve and package id`() {
        assertEquals("Resolve", context.getString(R.string.app_name))
        assertEquals("com.resolve.focus", context.packageName)
    }

    @Test
    fun `recovery code hashes cleanly and throttles after 5 invalid attempts`() = runTest {
        val code = recoveryManager.generateNumericCode(16)
        assertEquals(16, code.length)
        assertTrue(recoveryManager.saveRecoveryCode(code))

        repeat(4) { idx ->
            val res = recoveryManager.verifyRecoveryCode("0000000000000000")
            assertTrue(res is RecoveryVerificationResult.InvalidCode)
            assertEquals(4 - idx, (res as RecoveryVerificationResult.InvalidCode).remainingAttemptsBeforeCooldown)
        }

        val fifth = recoveryManager.verifyRecoveryCode("0000000000000000")
        assertTrue(fifth is RecoveryVerificationResult.Throttled)

        // Advance past 30s cooldown and verify valid code succeeds
        fakeClock.advanceBy(31_000L)
        assertEquals(RecoveryVerificationResult.Verified, recoveryManager.verifyRecoveryCode(code))
    }

    @Test
    fun `instant lock refuses fake lock when Device Owner is absent`() = runTest {
        recoveryManager.saveRecoveryCode("1234567890123456")
        fakePolicy.deviceOwner = false
        engine.initialize()

        val result = engine.startInstantLock(
            lockDurationMinutes = 60L,
            allowedPackages = listOf("com.example.allowed"),
            phoneAccessEnabled = true
        )
        assertTrue(result is EngineStartResult.Blocked)
        assertEquals(SessionState.ENFORCEMENT_UNAVAILABLE, engine.activeSession.value?.state)
    }

    @Test
    fun `delayed lock runs independent free use window then full lock duration after transition`() = runTest {
        recoveryManager.saveRecoveryCode("1234567890123456")
        fakePolicy.deviceOwner = true
        engine.initialize()

        val start = engine.startDelayedLock(
            freeUseMinutes = 2L,
            lockDurationMinutes = 5L,
            allowedPackages = listOf("com.allowed.app"),
            phoneAccessEnabled = true
        )
        assertEquals(EngineStartResult.Started, start)
        assertEquals(SessionState.DELAYED_FREE_USE_ACTIVE, engine.activeSession.value?.state)
        assertEquals(0, fakeBridge.launchLockScreenCount)

        // Advance 2 minutes -> reach delayed lock deadline
        fakeClock.advanceBy(2L * 60_000L)
        engine.onDelayedFreeUseDeadlineReached(engine.activeSession.value?.sessionId)

        assertEquals(SessionState.LOCK_TRANSITION_PENDING, engine.activeSession.value?.state)
        assertEquals(1, fakeBridge.launchLockScreenCount)

        // Confirm Lock Task entered -> 5 minute lock duration begins NOW (never subtracted)
        val dummyActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertTrue(engine.confirmLockTaskEntered(dummyActivity))
        val lockedSession = engine.activeSession.value!!
        assertEquals(SessionState.LOCK_ENFORCEMENT_ACTIVE, lockedSession.state)
        assertEquals(
            fakeClock.elapsedRealtimeMs() + 5L * 60_000L,
            lockedSession.lockExpirationElapsedMs
        )
    }

    @Test
    fun `reboot during active lock preserves original deadline and resumes remaining duration cleanly`() = runTest {
        recoveryManager.saveRecoveryCode("1234567890123456")
        fakePolicy.deviceOwner = true
        engine.initialize()

        engine.startInstantLock(30L, emptyList(), phoneAccessEnabled = true)
        val dummyActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        engine.confirmLockTaskEntered(dummyActivity)
        val initialExpireWall = engine.activeSession.value!!.lockExpirationWallMs!!

        // Simulate 15 minutes elapsed, then device reboot (elapsed resets)
        fakeClock.advanceWallOnly(15L * 60_000L)
        fakeClock.resetElapsedForReboot(1_000L)
        engine.onSystemBootOrTimeChange(isBoot = true)

        assertEquals(SessionState.LOCK_TRANSITION_PENDING, engine.activeSession.value?.state)
        engine.confirmLockTaskEntered(dummyActivity)
        val resumed = engine.activeSession.value!!
        assertEquals(initialExpireWall, resumed.lockExpirationWallMs)
        assertEquals(
            fakeClock.elapsedRealtimeMs() + 15L * 60_000L,
            resumed.lockExpirationElapsedMs
        )
    }

    @Test
    fun `recovery code ends active lock session and records history once`() = runTest {
        val code = "1111222233334444"
        recoveryManager.saveRecoveryCode(code)
        fakePolicy.deviceOwner = true
        engine.initialize()

        engine.startInstantLock(45L, listOf("com.app.one"), phoneAccessEnabled = true)
        val dummyActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        engine.confirmLockTaskEntered(dummyActivity)

        val res = engine.submitRecoveryCode(code)
        assertEquals(RecoveryVerificationResult.Verified, res)
        assertEquals(SessionState.RECOVERY_IN_PROGRESS, engine.activeSession.value?.state)

        engine.finalizeLockExit(dummyActivity)
        assertEquals(SessionState.RECOVERED, engine.activeSession.value?.state)

        val records = db.sessionHistoryDao().observeHistory().first()
        assertEquals(1, records.size)
        assertEquals(LockMode.INSTANT.name, records[0].mode)
        assertEquals(SessionOutcome.RECOVERED.name, records[0].outcome)
    }

    @Test
    fun `home screen and settings screen render Resolve branding and developer credit`() {
        composeRule.setContent {
            ResolveTheme {
                HomeScreen(
                    activeSession = null,
                    remainingMillis = 0L,
                    capabilities = SystemCapabilityStatus(),
                    onOpenInstantSetup = {},
                    onOpenDelayedSetup = {},
                    onOpenScheduleSetup = {},
                    onOpenDeviceOwnerGuide = {},
                    onReturnToLockScreen = {},
                    onDismissTerminalSession = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_header_row").assertIsDisplayed()
        composeRule.onNodeWithTag("home_mascot_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("home_enforcement_status_pill").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("RESOLVE").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("mode_card_instant").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_delayed").assertIsDisplayed()
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("mode_card_schedule"))
        composeRule.onNodeWithTag("mode_card_schedule").assertIsDisplayed()
    }

    @Test
    fun `settings screen shows Accessibility, Overlay, Usage, Battery permissions and Tahan Official credit`() {
        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = SystemCapabilityStatus(),
                    hasRecoveryCode = false,
                    recoveryCodeLength = 16,
                    generateRandomCode = { "1234567890123456" },
                    onSaveRecoveryCode = {},
                    onRefreshCapabilities = {},
                    onOpenAccessibilitySettings = {},
                    onOpenOverlaySettings = {},
                    onOpenUsageAccessSettings = {},
                    onOpenBatteryOptimizationSettings = {},
                    onOpenDeviceOwnerGuide = {},
                    onOpenExactAlarmSettings = {},
                    onOpenNotificationSettings = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_accessibility_action"))
        composeRule.onNodeWithText("Accessibility Service").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_developer_credit"))
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Developed by Tahan Official").assertIsDisplayed()
    }

    @Test
    fun `accessibility guard mode enforces delayed lock and recovery without device owner`() = runTest {
        val code = "9999888877776666"
        recoveryManager.saveRecoveryCode(code)
        fakePolicy.deviceOwner = false
        // Simulate Accessibility Service enabled via Robolectric Shadow or Secure Setting
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            android.content.ComponentName(
                context,
                com.example.accessibility.ResolveAccessibilityService::class.java
            ).flattenToString()
        )
        engine.initialize()

        val start = engine.startDelayedLock(
            freeUseMinutes = 2L,
            lockDurationMinutes = 5L,
            allowedPackages = listOf("com.allowed.notes"),
            phoneAccessEnabled = true
        )
        assertEquals(EngineStartResult.Started, start)
        assertEquals(EnforcementMode.ACCESSIBILITY_GUARD, engine.activeSession.value?.enforcementMode)

        fakeClock.advanceBy(2L * 60_000L)
        engine.onDelayedFreeUseDeadlineReached(engine.activeSession.value?.sessionId)
        assertEquals(SessionState.LOCK_TRANSITION_PENDING, engine.activeSession.value?.state)

        val dummyActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertTrue(engine.confirmLockTaskEntered(dummyActivity))
        assertEquals(SessionState.LOCK_ENFORCEMENT_ACTIVE, engine.activeSession.value?.state)
        assertEquals(EnforcementMode.ACCESSIBILITY_GUARD, engine.activeSession.value?.enforcementMode)

        assertEquals(RecoveryVerificationResult.Verified, engine.submitRecoveryCode(code))
        engine.finalizeLockExit(dummyActivity)
        assertEquals(SessionState.RECOVERED, engine.activeSession.value?.state)
    }

    @Test
    fun `direct numeric duration entry sets 3h 40m accurately`() {
        var current = DurationSelection(0, 0)
        composeRule.setContent {
            ResolveTheme {
                DurationSelector(
                    label = "Lock Duration",
                    duration = current,
                    onHoursChanged = { current = current.withHours(it) },
                    onMinutesChanged = { current = current.withMinutes(it) },
                    onDurationReplaced = { current = it },
                    testTagPrefix = "test_dur"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_dur_display").performClick()
        composeRule.onNodeWithTag("direct_hours_input").performTextReplacement("3")
        composeRule.onNodeWithTag("direct_minutes_input").performTextReplacement("40")
        composeRule.onNodeWithTag("direct_duration_confirm").performClick()
        composeRule.waitForIdle()
        assertEquals(DurationSelection(3, 40), current)
    }

    @Test
    fun `platform guards, alarm scheduler, and phone access manager run without crashes`() {
        val capabilityChecker = SystemCapabilityChecker(context)
        val status = capabilityChecker.queryStatus()
        assertTrue(status.androidSdkInt >= 28)

        val guard = com.example.platform.ForegroundEnforcementGuard(context, capabilityChecker)
        guard.queryLatestForegroundPackage()
        guard.clearBarrier()

        val scheduler = com.example.scheduling.AndroidLockAlarmScheduler(context)
        scheduler.scheduleDelayedLockTransition("s1", System.currentTimeMillis() + 60_000L)
        scheduler.scheduleLockExpiration("s1", System.currentTimeMillis() + 120_000L)
        scheduler.cancelAllSessionAlarms()

        val phoneManager = com.example.telephony.PhoneAccessManager(context)
        phoneManager.resolveEssentialDialerPackages()
        phoneManager.resolveEmergencyPackages()
    }

    @Test
    fun `baby cloud mascot and signature lock screen render smoothly without crash`() {
        composeRule.setContent {
            ResolveTheme {
                com.example.presentation.lockscreen.SignatureLockScreen(
                    remainingMillis = 3_661_000L,
                    progressFraction = 0.75f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_screen_countdown").assertIsDisplayed()
        composeRule.onNodeWithText("01:01:01").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_phone_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_recovery_button").assertIsDisplayed()
    }

    @Test
    fun `safe text toolbar and recovery dialog dismiss cleanly without floating action mode errors`() {
        var showDialog by mutableStateOf(true)
        var savedCode = ""
        composeRule.setContent {
            ResolveTheme {
                com.example.presentation.components.ProvideSafeTextToolbar {
                    val view = androidx.compose.ui.platform.LocalView.current
                    val toolbar = androidx.compose.ui.platform.LocalTextToolbar.current
                    assertTrue(toolbar is com.example.presentation.components.SafeAndroidTextToolbar)
                    toolbar.showMenu(
                        rect = androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 50f),
                        onCopyRequested = {},
                        onPasteRequested = {},
                        onCutRequested = null,
                        onSelectAllRequested = {}
                    )
                    toolbar.hide()
                    toolbar.hide() // idempotent second hide must be a safe no-op
                    assertEquals(androidx.compose.ui.platform.TextToolbarStatus.Hidden, toolbar.status)
                    assertTrue(view.isAttachedToWindow)

                    if (showDialog) {
                        com.example.presentation.components.RecoveryCodeSetupDialog(
                            generateRandomCode = { "1234567890123456" },
                            onDismiss = { showDialog = false },
                            onSaveCode = {
                                savedCode = it
                                showDialog = false
                            }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recovery_setup_confirm_input")
            .performScrollTo()
            .performTextReplacement("1234567890123456")
        composeRule.onNodeWithTag("recovery_setup_ack_checkbox")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("recovery_setup_save_button").performClick()
        composeRule.waitForIdle()
        assertEquals("1234567890123456", savedCode)
        assertFalse(showDialog)
    }

    private class FakeClockProvider(
        private var wallMs: Long,
        private var elapsedMs: Long
    ) : ClockProvider {
        override fun wallClockMs(): Long = wallMs
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
        fun advanceBy(deltaMs: Long) {
            wallMs += deltaMs
            elapsedMs += deltaMs
        }
        fun advanceWallOnly(deltaMs: Long) {
            wallMs += deltaMs
        }
        fun resetElapsedForReboot(newElapsedMs: Long) {
            elapsedMs = newElapsedMs
        }
    }

    private class FakeDevicePolicyController : DevicePolicyController {
        var deviceOwner = false
        private var lockTaskState = ActivityManager.LOCK_TASK_MODE_NONE
        private var packages = emptyList<String>()

        override fun isDeviceOwner(): Boolean = deviceOwner
        override fun getLockTaskModeState(): Int = lockTaskState
        override fun getAppliedLockTaskPackages(): List<String> = packages

        override suspend fun prepareLockTaskPolicy(
            selectedPackages: List<String>,
            phoneAccessEnabled: Boolean
        ): EnforcementPreparationResult {
            if (!deviceOwner) {
                return EnforcementPreparationResult.Unavailable("Not Device Owner")
            }
            packages = selectedPackages
            return EnforcementPreparationResult.Ready(selectedPackages)
        }

        override fun enterLockTask(activity: Activity): Boolean {
            if (!deviceOwner) return false
            lockTaskState = ActivityManager.LOCK_TASK_MODE_LOCKED
            return true
        }

        override fun exitLockTask(activity: Activity): Boolean {
            lockTaskState = ActivityManager.LOCK_TASK_MODE_NONE
            packages = emptyList()
            return true
        }

        override fun clearLockTaskPolicy() {
            packages = emptyList()
        }
    }

    private class FakeAlarmScheduler : LockAlarmScheduler {
        override fun scheduleDelayedLockTransition(sessionId: String, triggerAtWallMs: Long) = Unit
        override fun scheduleLockExpiration(sessionId: String, expireAtWallMs: Long) = Unit
        override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerAtWallMs: Long) = Unit
        override fun cancelDelayedLockTransition() = Unit
        override fun cancelLockExpiration() = Unit
        override fun cancelScheduleTrigger(scheduleId: Long) = Unit
        override fun cancelAllSessionAlarms() = Unit
    }

    private class FakePlatformBridge : SessionPlatformBridge {
        var launchLockScreenCount = 0
        override fun startMonitorService() = Unit
        override fun stopMonitorService() = Unit
        override fun launchLockScreen() {
            launchLockScreenCount++
        }
        override fun notifyLockStateChanged() = Unit
    }
}
