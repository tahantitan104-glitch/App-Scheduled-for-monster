package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.designsystem.ResolveTheme
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.ActiveSession
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.LockMode
import com.example.domain.model.LockSchedule
import com.example.domain.model.MascotType
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.startup.StartupDecisionResolver
import com.example.domain.startup.StartupPreferences
import com.example.domain.startup.StartupStage
import com.example.domain.time.ClockProvider
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.startup.BrandIntroScreen
import com.example.presentation.startup.FirstTimeWelcomeScreen
import com.example.presentation.startup.RequiredSetupScreen
import com.example.scheduling.LockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class StartupAndIconRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ResolveDatabase
    private lateinit var prefs: ResolvePreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = (context as ResolveApplication).container.preferencesRepository
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `focus symbol adaptive icon layers and splash drawable load cleanly regardless of mascot selection`() = runTest {
        val mascotManager = (context as ResolveApplication).container.mascotSelectionManager
        val appInfo = context.applicationInfo
        val initialIconRes = appInfo.icon

        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_resolve_splash))
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))

        mascotManager.selectMascot(MascotType.RESOLVE_BUNNY)
        assertEquals(initialIconRes, context.applicationInfo.icon)
        mascotManager.selectMascot(MascotType.BABY_CLOUD)
        assertEquals(initialIconRes, context.applicationInfo.icon)
    }

    @Test
    fun `startup preferences persist first launch welcome and onboarding separately`() = runTest {
        prefs.resetStartupStateForTesting()
        assertEquals(StartupPreferences(false, false), prefs.getStartupPreferencesSnapshot())

        prefs.markFirstLaunchWelcomeSeen()
        val afterWelcome = prefs.getStartupPreferencesSnapshot()
        assertTrue(afterWelcome.firstLaunchWelcomeSeen)
        assertFalse(afterWelcome.onboardingCompleted)

        prefs.markOnboardingCompleted()
        val freshInstance = ResolvePreferencesRepository(context)
        val afterRestart = freshInstance.getStartupPreferencesSnapshot()
        assertTrue(afterRestart.firstLaunchWelcomeSeen)
        assertTrue(afterRestart.onboardingCompleted)
    }

    @Test
    fun `startup decision resolver prioritizes active lock over intro and onboarding`() {
        val freshPrefs = StartupPreferences(firstLaunchWelcomeSeen = false, onboardingCompleted = false)
        val completedPrefs = StartupPreferences(firstLaunchWelcomeSeen = true, onboardingCompleted = true)
        val incompletePrefs = StartupPreferences(firstLaunchWelcomeSeen = true, onboardingCompleted = false)

        // Case A: First install -> Brand Intro -> First-Time Welcome -> Required Setup
        assertEquals(
            StartupStage.BRAND_INTRO,
            StartupDecisionResolver.resolveInitialStage(null, freshPrefs, isRequiredSetupComplete = false)
        )
        assertEquals(
            StartupStage.FIRST_TIME_WELCOME,
            StartupDecisionResolver.resolvePostIntroStage(freshPrefs, isRequiredSetupComplete = false)
        )
        assertEquals(
            StartupStage.REQUIRED_SETUP,
            StartupDecisionResolver.resolvePostIntroStage(incompletePrefs, isRequiredSetupComplete = false)
        )

        // Case B: Normal launch -> Brand Intro -> Home (never repeats welcome)
        assertEquals(
            StartupStage.BRAND_INTRO,
            StartupDecisionResolver.resolveInitialStage(null, completedPrefs, isRequiredSetupComplete = true)
        )
        assertEquals(
            StartupStage.HOME,
            StartupDecisionResolver.resolvePostIntroStage(completedPrefs, isRequiredSetupComplete = true)
        )

        // Case C: Active lock session ALWAYS overrides even on fresh install
        val activeSession = ActiveSession(
            sessionId = "s-active",
            mode = LockMode.INSTANT,
            state = SessionState.LOCK_ENFORCEMENT_ACTIVE,
            enforcementMode = EnforcementMode.DEVICE_OWNER_LOCK_TASK,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = 30L,
            createdAtWallMs = 1_000L
        )
        assertEquals(
            StartupStage.RESTORE_LOCK,
            StartupDecisionResolver.resolveInitialStage(activeSession, freshPrefs, isRequiredSetupComplete = false)
        )
    }

    @Test
    fun `case D due schedule restored on startup preserves remaining time without resetting countdown`() = runTest {
        val nowWall = 1_794_745_500_000L // e.g. 15 minutes into a 60m window
        val windowStart = nowWall - (15L * 60_000L)
        val zdt = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(windowStart),
            ZoneId.of("UTC")
        )
        val clock = object : ClockProvider {
            override fun wallClockMs(): Long = nowWall
            override fun elapsedRealtimeMs(): Long = 50_000L
            override fun zoneId(): ZoneId = ZoneId.of("UTC")
        }
        val fakePolicy = object : DevicePolicyController {
            override fun isDeviceOwner(): Boolean = true
            override fun getLockTaskModeState(): Int = 0
            override fun getAppliedLockTaskPackages(): List<String> = emptyList()
            override suspend fun prepareLockTaskPolicy(selectedPackages: List<String>, phoneAccessEnabled: Boolean) =
                EnforcementPreparationResult.Ready(selectedPackages)
            override fun enterLockTask(activity: Activity): Boolean = true
            override fun exitLockTask(activity: Activity): Boolean = true
            override fun clearLockTaskPolicy() = Unit
        }
        val fakeAlarms = object : LockAlarmScheduler {
            override fun scheduleDelayedLockTransition(sessionId: String, triggerAtWallMs: Long) = Unit
            override fun scheduleLockExpiration(sessionId: String, expireAtWallMs: Long) = Unit
            override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerAtWallMs: Long) = Unit
            override fun cancelDelayedLockTransition() = Unit
            override fun cancelLockExpiration() = Unit
            override fun cancelScheduleTrigger(scheduleId: Long) = Unit
            override fun cancelAllSessionAlarms() = Unit
        }
        var launched = 0
        val fakeBridge = object : SessionPlatformBridge {
            override fun startMonitorService() = Unit
            override fun stopMonitorService() = Unit
            override fun launchLockScreen() { launched++ }
            override fun notifyLockStateChanged() = Unit
        }

        val scheduleRepo = ScheduleRepository(db.lockScheduleDao())
        scheduleRepo.saveSchedule(
            LockSchedule(
                id = 0L,
                name = "Due Focus",
                type = ScheduleType.DAILY,
                startHour = zdt.hour,
                startMinute = zdt.minute,
                lockDurationMinutes = 60L,
                allowedPackages = emptyList(),
                phoneAccessEnabled = true,
                isEnabled = true,
                nextTriggerWallMs = windowStart,
                createdAtWallMs = windowStart - 86_400_000L
            )
        )

        val engine = LockSessionEngine(
            sessionRepository = SessionRepository(db.activeSessionDao()),
            scheduleRepository = scheduleRepo,
            historyRepository = HistoryRepository(db.sessionHistoryDao()),
            preferencesRepository = prefs,
            devicePolicyController = fakePolicy,
            alarmScheduler = fakeAlarms,
            recoveryCodeManager = RecoveryCodeManager(prefs, clock),
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = clock,
            platformBridge = fakeBridge
        )

        engine.initialize()
        val session = engine.activeSession.value!!
        assertEquals(SessionState.LOCK_TRANSITION_PENDING, session.state)
        assertEquals(1, launched)

        val dummyActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertTrue(engine.confirmLockTaskEntered(dummyActivity))
        // 60m schedule already 15m in progress -> remaining must be ~45m, NOT reset to 60m
        assertEquals(45L * 60_000L, engine.remainingMillis.value)
        engine.shutdown()
    }

    @Test
    fun `brand intro renders focus symbol and resolve without mascots and completes cleanly`() {
        var completed = false
        composeRule.setContent {
            ResolveTheme {
                BrandIntroScreen(
                    reducedMotion = true,
                    onIntroCompleted = { completed = true }
                )
            }
        }
        composeRule.onNodeWithTag("brand_intro_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("brand_intro_focus_symbol").assertIsDisplayed()
        composeRule.onNodeWithTag("brand_intro_wordmark").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("mascot_baby_cloud").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("mascot_resolve_bunny").fetchSemanticsNodes().isEmpty())

        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun `first time welcome shows exact approved copy and continues`() {
        var continued = false
        composeRule.setContent {
            ResolveTheme {
                FirstTimeWelcomeScreen(
                    reducedMotion = true,
                    onContinue = { continued = true }
                )
            }
        }
        composeRule.onNodeWithTag("welcome_focus_symbol").assertIsDisplayed()
        composeRule.onNodeWithText("RESOLVE").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to RESOLVE.").assertIsDisplayed()
        composeRule.onNodeWithText("Build focus. Stay consistent. Take control.").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_get_started_button").assertIsDisplayed().performClick()
        assertTrue(continued)
    }

    @Test
    fun `required setup keeps completion disabled when steps incomplete and enables when ready`() {
        var completedClicked = false
        var deferredClicked = false
        composeRule.setContent {
            ResolveTheme {
                RequiredSetupScreen(
                    capabilities = SystemCapabilityStatus(isAccessibilityServiceEnabled = false),
                    hasRecoveryCode = false,
                    generateRandomCode = { "1234567890123456" },
                    onSaveRecoveryCode = {},
                    onOpenAccessibilitySettings = {},
                    onCompleteSetup = { completedClicked = true },
                    onDeferSetupToHome = { deferredClicked = true }
                )
            }
        }
        composeRule.onNodeWithTag("required_setup_complete_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("required_setup_defer_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        assertFalse(completedClicked)
        assertTrue(deferredClicked)
    }
}
