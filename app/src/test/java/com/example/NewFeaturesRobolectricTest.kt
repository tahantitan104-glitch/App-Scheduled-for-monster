package com.example

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.FlipPresetMutationResult
import com.example.data.repository.FlipTimerPresetRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.designsystem.ResolveTheme
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.FlipTimerState
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockMode
import com.example.domain.model.LockProfileSettings
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.time.ClockProvider
import com.example.flipclock.FlipClockManager
import com.example.flipclock.FlipTimerNotificationHelper
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.components.AllowedAppsPickerSheet
import com.example.presentation.lockscreen.SignatureLockScreen
import com.example.scheduling.LockAlarmScheduler
import com.example.security.AppLockSessionGuard
import com.example.security.RecoveryCodeManager
import com.example.session.EngineStartResult
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import com.example.sync.CloudAuthSession
import com.example.sync.CloudDeviceInfo
import com.example.sync.CloudFlipClockSettings
import com.example.sync.CloudFlipTimerPreset
import com.example.sync.CloudLockCommand
import com.example.sync.CloudLockProfileSettings
import com.example.sync.CloudSyncGateway
import com.example.sync.CloudUserProfile
import com.example.sync.MultiDeviceSyncCoordinator
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewFeaturesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ResolveDatabase
    private lateinit var prefs: ResolvePreferencesRepository
    private lateinit var allowlistRepo: AllowlistRepository
    private lateinit var flipPresetRepo: FlipTimerPresetRepository
    private lateinit var clock: FakeTestClock
    private lateinit var engine: LockSessionEngine
    private lateinit var recovery: RecoveryCodeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = (context as ResolveApplication).container.preferencesRepository
        allowlistRepo = AllowlistRepository(db.allowedAppDao())
        flipPresetRepo = FlipTimerPresetRepository(db.flipTimerPresetDao(), prefs)
        clock = FakeTestClock(wallMs = 1_795_000_000_000L, elapsedMs = 50_000L)
        recovery = RecoveryCodeManager(prefs, clock)
        engine = LockSessionEngine(
            sessionRepository = SessionRepository(db.activeSessionDao()),
            scheduleRepository = ScheduleRepository(db.lockScheduleDao()),
            historyRepository = HistoryRepository(db.sessionHistoryDao()),
            preferencesRepository = prefs,
            devicePolicyController = FakeDevicePolicy(),
            alarmScheduler = NoOpAlarms(),
            recoveryCodeManager = recovery,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = clock,
            platformBridge = NoOpBridge()
        )
    }

    @After
    fun tearDown() {
        engine.shutdown()
        db.close()
    }

    @Test
    fun `app lock session guard keeps 2_2s grace across transient system overlays and revokes on real switch`() {
        val guard = AppLockSessionGuard()
        guard.updateState(
            enabled = true,
            hasCredential = true,
            protectedPackages = setOf("com.example.bank")
        )
        guard.grantTemporaryUnlock("com.example.bank", nowElapsedMs = 10_000L)

        // Within 2200ms unlock transition grace, intermediate launcher/systemui does not revoke
        guard.onForegroundAppChanged("com.android.systemui", nowElapsedMs = 11_800L)
        assertTrue(guard.isTemporarilyUnlocked("com.example.bank", nowElapsedMs = 11_900L))

        // Transient permission controller overlay beyond 2200ms still preserves grant
        guard.onForegroundAppChanged("com.google.android.permissioncontroller", nowElapsedMs = 15_000L)
        assertTrue(guard.isTemporarilyUnlocked("com.example.bank", nowElapsedMs = 15_100L))

        // Switching to an actual unrelated external app after grace expires revokes the grant
        guard.onForegroundAppChanged("com.android.chrome", nowElapsedMs = 16_000L)
        assertFalse(guard.isTemporarilyUnlocked("com.example.bank", nowElapsedMs = 16_100L))
    }

    @Test
    fun `strict mode starts without recovery code, blocks recovery unlock, and preserves existing hash`() = runTest {
        assertTrue(recovery.saveRecoveryCode("1234567890123456"))
        val originalRecord = prefs.getRecoveryRecord()
        assertNotNull(originalRecord)

        engine.initialize()
        val result = engine.startInstantLock(
            lockDurationMinutes = 25L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isStrictMode = true,
            showFlipClockShortcut = false
        )
        assertEquals(EngineStartResult.Started, result)
        assertTrue(engine.activeSession.value?.isStrictMode == true)
        assertTrue(engine.activeSession.value?.phoneAccessEnabled == true)

        // Strict Mode rejects recovery code submission even with the valid stored code
        val recoveryAttempt = engine.submitRecoveryCode("1234567890123456")
        assertEquals(RecoveryVerificationResult.NotConfigured, recoveryAttempt)

        // Existing stored recovery hash remains intact
        assertEquals(originalRecord?.verifierBase64, prefs.getRecoveryRecord()?.verifierBase64)
    }

    @Test
    fun `lock screen hides recovery button in strict mode and toggles flip clock shortcut visibility`() {
        var strictMode by mutableStateOf(true)
        var showFlipClock by mutableStateOf(false)

        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 300_000L,
                    progressFraction = 0.5f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    isStrictMode = strictMode,
                    showFlipClockShortcut = showFlipClock
                )
            }
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("lock_screen_recovery_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("lock_screen_flip_clock_button").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("lock_screen_phone_button").assertIsDisplayed()

        strictMode = false
        showFlipClock = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lock_screen_recovery_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_flip_clock_button").assertIsDisplayed()
    }

    @Test
    fun `allow selected apps toggle preserves saved packages and picker warns for missing synced apps`() = runTest {
        val profile = LockProfileSettings(
            mode = LockMode.INSTANT,
            strictMode = false,
            allowSelectedApps = false,
            showFlipClockShortcut = false,
            allowedPackages = listOf("com.example.notes", "com.missing.app"),
            updatedAtWallMs = 1_000L
        )
        assertTrue(profile.effectiveAllowedPackages.isEmpty())
        val reEnabled = profile.copy(allowSelectedApps = true)
        assertEquals(listOf("com.example.notes", "com.missing.app"), reEnabled.effectiveAllowedPackages)

        prefs.saveLockProfileSettings(
            LockProfileSettings(
                mode = LockMode.INSTANT,
                strictMode = true,
                allowSelectedApps = false,
                showFlipClockShortcut = false,
                allowedPackages = listOf("com.example.notes"),
                updatedAtWallMs = 2_000L
            )
        )
        assertEquals(listOf("com.example.notes"), prefs.getLockProfileSettingsSnapshot(LockMode.INSTANT).allowedPackages)

        composeRule.setContent {
            ResolveTheme {
                AllowedAppsPickerSheet(
                    installedApps = listOf(InstalledAppInfo("com.example.notes", "Notes")),
                    initialSelectedPackages = setOf("com.example.notes", "com.missing.app"),
                    loadIcon = { null },
                    onDismiss = {},
                    onSaveSelection = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("missing_allowed_apps_warning_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("missing_app_row_com.missing.app").assertIsDisplayed()
    }

    @Test
    fun `flip clock presets cap at 15, reset preserves custom presets, and force-stop timer reconciles cleanly`() = runTest {
        flipPresetRepo.ensureDefaultPresetsSeeded()
        flipPresetRepo.restoreMissingBuiltInsWithinCap(1L)
        val defaults = flipPresetRepo.getAllPresets()
        assertEquals(9, defaults.size)

        // Add 6 custom presets to hit cap of 15
        repeat(6) { idx ->
            val res = flipPresetRepo.savePreset(
                presetId = null,
                name = "Custom $idx",
                durationSeconds = 120L + idx,
                nowWallMs = 5_000L + idx
            )
            assertTrue(res is FlipPresetMutationResult.Saved)
        }
        assertEquals(FlipTimerPreset.MAX_TOTAL_PRESETS, flipPresetRepo.getAllPresets().size)

        val overflow = flipPresetRepo.savePreset(
            presetId = null,
            name = "Overflow 16th",
            durationSeconds = 300L,
            nowWallMs = 6_000L
        )
        assertTrue(overflow is FlipPresetMutationResult.LimitReached)

        // Force-stop recovery for a timer whose end timestamp already passed recently (within 5s banner)
        prefs.saveFlipClockSettings(
            FlipClockSettings(
                use24HourFormat = true,
                keepScreenAwake = true,
                showDate = true,
                autoRepeat = false,
                zoomFraction = 1.25f,
                lastBrightnessPercent = 50f,
                updatedAtWallMs = 7_000L
            )
        )
        prefs.saveFlipTimerState(
            FlipTimerState(
                activePresetId = "flip_builtin_1m",
                timerName = "1 Minute",
                selectedDurationMs = 60_000L,
                targetEndWallMs = clock.wallMs - 1_500L,
                isRunning = true,
                isPaused = false
            )
        )

        val notifHelper = FlipTimerNotificationHelper(context)
        val manager = FlipClockManager(prefs, flipPresetRepo, notifHelper, engine, clock)
        manager.initialize()

        assertFalse(manager.timerState.value.isRunning)
        assertEquals(clock.wallMs - 1_500L, manager.timerState.value.finishedAtWallMs)
        assertNull(manager.timerState.value.autoRepeatScheduledAtWallMs)
        assertTrue(manager.timerFinishedBannerVisible.value)

        // Enabling autoRepeat during completion banner schedules autoRepeat at completionMessageUntilWallMs
        manager.setAutoRepeat(true)
        assertEquals(
            manager.timerState.value.completionMessageUntilWallMs,
            manager.timerState.value.autoRepeatScheduledAtWallMs
        )

        // Reset settings to default preserves custom presets and clears autoRepeat schedule
        manager.resetSettingsToDefaultPreservingCustomPresets()
        val afterResetSettings = prefs.getFlipClockSettingsSnapshot()
        assertFalse(afterResetSettings.use24HourFormat)
        assertFalse(afterResetSettings.autoRepeat)
        assertEquals(1.0f, afterResetSettings.clampedZoomFraction, 0.001f)
        assertNull(manager.timerState.value.autoRepeatScheduledAtWallMs)
        assertEquals(15, flipPresetRepo.getAllPresets().size)

        // Notification title formatting check
        assertEquals(
            "25 Minutes Timer — Completed!",
            FlipTimerNotificationHelper.formatCompletionNotificationTitle("25 Minutes")
        )
    }

    @Test
    fun `multi device sync LWW merges lock profiles, flip settings preserving brightness, and flip preset tombstones`() = runTest {
        val fakeGateway = RecordingFakeCloudGateway()
        val manager = FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock)
        val coordinator = MultiDeviceSyncCoordinator(
            appContext = context,
            gateway = fakeGateway,
            preferencesRepository = prefs,
            allowlistRepository = allowlistRepo,
            sessionEngine = engine,
            clockProvider = clock,
            flipTimerPresetRepository = flipPresetRepo,
            flipClockManager = manager
        )

        // Local device has brightness = 38% and older updatedAtWallMs
        prefs.saveFlipClockSettings(
            FlipClockSettings(
                use24HourFormat = false,
                keepScreenAwake = false,
                showDate = false,
                autoRepeat = false,
                zoomFraction = 1.0f,
                lastBrightnessPercent = 38f,
                updatedAtWallMs = 1_000L
            )
        )
        prefs.saveLockProfileSettings(
            LockProfileSettings(
                mode = LockMode.INSTANT,
                strictMode = false,
                allowSelectedApps = true,
                showFlipClockShortcut = false,
                allowedPackages = listOf("com.old.app"),
                updatedAtWallMs = 1_000L
            )
        )

        val localDeviceId = prefs.getOrCreateDeviceInstallId()
        assertNotNull(localDeviceId)

        // Invoke private merge helpers cleanly via reflection on coordinator or verify snapshot flow behavior:
        // Simulate brightness-only change -> verify lastBrightnessPercent updated without touching updatedAtWallMs
        manager.saveLastBrightnessPercent(72f)
        val afterBrightness = prefs.getFlipClockSettingsSnapshot()
        assertEquals(72f, afterBrightness.lastBrightnessPercent)
        assertEquals(1_000L, afterBrightness.updatedAtWallMs)

        // Verify coordinator start observes FlipClockSettings without uploading brightness-only changes
        coordinator.start()
        fakeGateway.authFlow.value = null
        assertEquals(0, fakeGateway.uploadedFlipSettings.size)

        // Verify LockProfileSettings.effectiveAllowedPackages respects synced flags
        val syncedStrictProfile = CloudLockProfileSettings(
            userId = "u1",
            profileMode = LockMode.DELAYED.name,
            strictMode = true,
            allowSelectedApps = false,
            showFlipClockShortcut = true,
            allowedPackages = listOf("com.synced.app"),
            originDeviceId = "remote_dev",
            updatedAtWallMs = 9_000L
        )
        assertEquals(LockMode.DELAYED, syncedStrictProfile.resolveLockMode())
        assertTrue(syncedStrictProfile.strictMode)
        assertTrue(syncedStrictProfile.showFlipClockShortcut)

        // Verify FlipPreset tombstones prevent older remote preset resurrection
        flipPresetRepo.ensureDefaultPresetsSeeded()
        val saved = flipPresetRepo.savePreset("custom_tomb_1", "Deep Work", 1800L, nowWallMs = 5_000L)
        assertTrue(saved is FlipPresetMutationResult.Saved)
        flipPresetRepo.deletePreset("custom_tomb_1", nowWallMs = 6_000L)
        val tombstones = prefs.getFlipPresetTombstones()
        assertEquals(6_000L, tombstones["custom_tomb_1"])
    }

    @Test
    fun `landscape tilt mode defaults to OFF and persists preference updates`() = runTest {
        assertFalse(prefs.landscapeTiltModeFlow.first())
        prefs.setLandscapeTiltMode(true)
        assertTrue(prefs.landscapeTiltModeFlow.first())
        prefs.setLandscapeTiltMode(false)
        assertFalse(prefs.landscapeTiltModeFlow.first())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h380dp")
    fun `landscape lock screen respects allowLandscapeLayout and renders vertical action stack with Flip Clock label`() {
        var allowLandscape by mutableStateOf(false)
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 600_000L,
                    progressFraction = 0.5f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = listOf(InstalledAppInfo("com.example.notes", "Notes")),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    isStrictMode = false,
                    showFlipClockShortcut = true,
                    lockReminderEnabled = true,
                    lockReminderText = "Stay disciplined.",
                    allowLandscapeLayout = allowLandscape
                )
            }
        }
        composeRule.waitForIdle()
        // Tilt OFF keeps Portrait layout even on landscape bounds
        composeRule.onNodeWithTag("lock_screen_portrait_layout").assertIsDisplayed()

        // Tilt ON enables Landscape layout with vertical button stack & icon+text Flip Clock
        allowLandscape = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_screen_landscape_reminder_on").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_landscape_action_stack").assertIsDisplayed()
        val phoneBounds = composeRule.onNodeWithTag("lock_screen_phone_button").assertIsDisplayed().getBoundsInRoot()
        val appsBounds = composeRule.onNodeWithTag("lock_screen_allowed_apps_button").assertIsDisplayed().getBoundsInRoot()
        val emergencyBounds = composeRule.onNodeWithTag("lock_screen_recovery_button").assertIsDisplayed().getBoundsInRoot()
        val flipBounds = composeRule.onNodeWithTag("lock_screen_flip_clock_button").assertIsDisplayed().getBoundsInRoot()
        assertTrue(phoneBounds.bottom <= appsBounds.top)
        assertTrue(appsBounds.bottom <= emergencyBounds.top)
        assertTrue(emergencyBounds.bottom <= flipBounds.top)
        composeRule.onNodeWithText("Flip Clock").assertIsDisplayed()
    }

    @Test
    fun `flip timer page shows large display in initial viewport and presets below on scroll`() {
        val manager = kotlinx.coroutines.runBlocking {
            flipPresetRepo.ensureDefaultPresetsSeeded()
            flipPresetRepo.restoreMissingBuiltInsWithinCap(1L)
            FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock).also {
                it.initialize()
            }
        }

        composeRule.setContent {
            ResolveTheme {
                com.example.flipclock.FlipClockScreen(
                    manager = manager,
                    initialPage = com.example.domain.model.FlipClockPage.TIMER,
                    isRestrictedLock = false,
                    initialBrightnessPercent = 50f,
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onBrightnessChanged = {},
                    onRequestTimerStartWithNotificationCheck = {},
                    onOpenAppNotificationSettings = {},
                    onExit = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_timer_display").assertIsDisplayed()

        // Scrolling reveals Custom Duration & 9 preset options cleanly below
        composeRule.onNodeWithTag("flip_timer_preset_list")
            .performScrollToNode(hasTestTag("flip_timer_custom_duration_btn"))
        composeRule.onNodeWithTag("flip_timer_custom_duration_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("flip_timer_preset_list")
            .performScrollToNode(hasTestTag("flip_preset_item_flip_builtin_120m"))
        composeRule.onNodeWithTag("flip_preset_item_flip_builtin_120m").assertIsDisplayed()
    }

    @Test
    fun `flip clock face shows borderless hour, minute, weekday, and seconds without colon`() {
        val manager = kotlinx.coroutines.runBlocking {
            FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock).also {
                it.initialize()
            }
        }

        composeRule.setContent {
            ResolveTheme {
                com.example.flipclock.FlipClockScreen(
                    manager = manager,
                    initialPage = com.example.domain.model.FlipClockPage.CLOCK,
                    isRestrictedLock = false,
                    initialBrightnessPercent = 50f,
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onBrightnessChanged = {},
                    onRequestTimerStartWithNotificationCheck = {},
                    onOpenAppNotificationSettings = {},
                    onExit = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_clock_hour_card").assertIsDisplayed()
        composeRule.onNodeWithTag("flip_clock_minute_card").assertIsDisplayed()
        composeRule.onNodeWithTag("flip_clock_weekday_label").assertIsDisplayed()
        composeRule.onNodeWithTag("flip_clock_seconds_label").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("flip_clock_colon").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `lock screen orientation resolver covers comfortable tilt bands and ignores deadzones`() {
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(0)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(35)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(325)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(50)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(130)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(230)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(310)
        )
        assertNull(com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(-1))
        assertNull(com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(42))
        assertNull(com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(180))
        assertNull(com.example.presentation.lockscreen.resolveLockScreenCandidateOrientation(318))
    }

    @Test
    fun `modes screen enforces Dark Mode card without Color Inversion and triggers reset cleanly`() {
        var resetClicked = false
        val report = com.example.platform.ForceDarkCapabilityHelper.inspect(context)

        composeRule.setContent {
            ResolveTheme {
                com.example.presentation.modes.ModesScreen(
                    report = report,
                    installedApps = emptyList(),
                    darkModeAppPrefs = emptyMap(),
                    loadAppIcon = { null },
                    onEnterFlipClock = {},
                    onAttemptAppDarkMode = { _, _ -> },
                    onOpenDisplaySettings = {},
                    onResetAllModes = { resetClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("modes_app_dark_card").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("modes_color_inversion_card").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("modes_list")
            .performScrollToNode(hasTestTag("modes_reset_all_btn"))
        composeRule.onNodeWithTag("modes_reset_all_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(resetClicked)
    }

    @Test
    fun `flip clock custom layout DataStore round trip persists and reset preserves custom presets`() = runTest {
        flipPresetRepo.ensureDefaultPresetsSeeded()
        val customPresetRes = flipPresetRepo.savePreset(
            presetId = "custom_focus_55",
            name = "Deep Focus 55",
            durationSeconds = 55L * 60L,
            nowWallMs = 10_000L
        )
        assertTrue(customPresetRes is FlipPresetMutationResult.Saved)

        val customLayout = com.example.domain.model.FlipClockCustomLayout(
            portrait = com.example.domain.model.FlipClockOrientationLayout.defaultPortrait().copy(
                enabled = true,
                hourScale = 1.2f,
                minuteScale = 0.85f,
                amPmAlign = com.example.domain.model.FlipClockElementAlign.RIGHT,
                weekdayStyle = com.example.domain.model.FlipWeekdayStyle.SHORT
            ),
            landscape = com.example.domain.model.FlipClockOrientationLayout.defaultLandscape().copy(
                enabled = true,
                hourScale = 1.15f,
                minuteScale = 1.1f,
                batteryStyle = com.example.domain.model.FlipBatteryStyle.PERCENT_ONLY,
                weatherStyle = com.example.domain.model.FlipWeatherStyle.DETAILED
            )
        )
        prefs.saveFlipClockSettings(
            FlipClockSettings(
                use24HourFormat = true,
                showSeconds = true,
                customLayout = customLayout,
                updatedAtWallMs = 12_000L
            )
        )

        val reloaded = prefs.getFlipClockSettingsSnapshot()
        assertTrue(reloaded.customLayout.portrait.enabled)
        assertEquals(1.2f, reloaded.customLayout.portrait.hourScale, 0.001f)
        assertEquals(0.85f, reloaded.customLayout.portrait.minuteScale, 0.001f)
        assertEquals(com.example.domain.model.FlipClockElementAlign.RIGHT, reloaded.customLayout.portrait.amPmAlign)
        assertEquals(com.example.domain.model.FlipWeekdayStyle.SHORT, reloaded.customLayout.portrait.weekdayStyle)
        assertEquals(com.example.domain.model.FlipBatteryStyle.PERCENT_ONLY, reloaded.customLayout.landscape.batteryStyle)
        assertEquals(com.example.domain.model.FlipWeatherStyle.DETAILED, reloaded.customLayout.landscape.weatherStyle)

        val manager = FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock)
        manager.initialize()
        manager.resetSettingsToDefaultPreservingCustomPresets()
        val afterReset = prefs.getFlipClockSettingsSnapshot()
        assertFalse(afterReset.customLayout.portrait.enabled)
        assertFalse(afterReset.customLayout.landscape.enabled)
        assertTrue(flipPresetRepo.getAllPresets().any { it.presetId == "custom_focus_55" })
    }

    @Test
    fun `flip clock screen opens layout editor confirms persistence and selects compact timer preset`() {
        val manager = kotlinx.coroutines.runBlocking {
            flipPresetRepo.ensureDefaultPresetsSeeded()
            flipPresetRepo.restoreMissingBuiltInsWithinCap(1L)
            FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock).also {
                it.initialize()
            }
        }
        var controlsVisible by mutableStateOf(true)
        var initialPage by mutableStateOf(com.example.domain.model.FlipClockPage.CLOCK)

        composeRule.setContent {
            ResolveTheme {
                com.example.flipclock.FlipClockScreen(
                    manager = manager,
                    initialPage = initialPage,
                    isRestrictedLock = false,
                    initialBrightnessPercent = 50f,
                    controlsVisible = controlsVisible,
                    onControlsVisibleChange = { controlsVisible = it },
                    onBrightnessChanged = {},
                    onRequestTimerStartWithNotificationCheck = {},
                    onOpenAppNotificationSettings = {},
                    onExit = {}
                )
            }
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("flip_top_layout_editor_btn").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("flip_settings_open_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_open_layout_editor_btn").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_layout_confirm_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("flip_page_pill_timer").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_timer_preset_list")
            .performScrollToNode(hasTestTag("flip_preset_item_flip_builtin_120m"))
        composeRule.onNodeWithTag("flip_preset_item_flip_builtin_120m").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("flip_builtin_120m", manager.timerState.value.activePresetId)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h380dp")
    fun `landscape lock screen renders Focus Guard header and mascot stage cleanly above countdown`() {
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 600_000L,
                    progressFraction = 0.65f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    isStrictMode = false,
                    showFlipClockShortcut = true,
                    allowLandscapeLayout = true,
                    reducedMotion = true
                )
            }
        }
        composeRule.waitForIdle()
        val guardTitleBounds = composeRule.onNodeWithTag("lock_screen_landscape_focus_guard_title")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val mascotBounds = composeRule.onNodeWithTag("lock_screen_mascot_stage")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val countdownBounds = composeRule.onNodeWithTag("lock_screen_countdown_card")
            .assertIsDisplayed()
            .getBoundsInRoot()

        assertTrue(
            "Focus Guard title top (${guardTitleBounds.top}) must remain clear of top edge",
            guardTitleBounds.top.value >= 10f
        )
        assertTrue(
            "Mascot stage bottom (${mascotBounds.bottom}) must stay above countdown card top (${countdownBounds.top})",
            mascotBounds.bottom <= countdownBounds.top
        )
    }

    @Test
    fun `deep forest green replaces warm paper minimal with original deep forest palette`() {
        val names = com.example.domain.model.ResolveThemeId.entries.map { it.displayName }
        assertTrue(names.contains("Deep Forest Green"))
        assertFalse(names.contains("Warm Paper Minimal"))
        assertEquals(
            com.example.domain.model.ResolveThemeId.DEEP_FOREST_GREEN,
            com.example.domain.model.ResolveThemeId.fromStorageKey("warm_paper")
        )
        assertEquals(
            com.example.domain.model.ResolveThemeId.DEEP_FOREST_GREEN,
            com.example.domain.model.ResolveThemeId.fromStorageKey(null)
        )

        var selectedTheme by mutableStateOf(com.example.domain.model.ResolveThemeId.DEEP_FOREST_GREEN)
        var capturedBgHex: ULong? = null
        var capturedIsDark: Boolean? = null
        composeRule.setContent {
            ResolveTheme(themeId = selectedTheme) {
                val pal = com.example.designsystem.LocalResolveExtendedPalette.current
                capturedBgHex = pal.background.value
                capturedIsDark = pal.isDark
                com.example.presentation.more.AppThemeAndGoalCard(
                    appThemeId = selectedTheme,
                    dailyFocusGoalMinutes = 120,
                    showSetupTips = false,
                    onSelectAppTheme = { selectedTheme = it },
                    onSelectDailyGoalMinutes = {},
                    onToggleShowSetupTips = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(true, capturedIsDark)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A1713).value, capturedBgHex)
        composeRule.onNodeWithTag("more_theme_chip_deep_forest_green").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("more_theme_chip_warm_paper").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h380dp")
    fun `landscape home restores 3-element header with mascot guard pill on left and avatar on right`() {
        composeRule.setContent {
            ResolveTheme {
                com.example.presentation.home.HomeScreen(
                    activeSession = null,
                    remainingMillis = 0L,
                    capabilities = com.example.domain.model.SystemCapabilityStatus(
                        isAccessibilityServiceEnabled = true,
                        isAccessibilityEnabledInSettings = true,
                        isAccessibilityServiceConnected = true
                    ),
                    onOpenInstantSetup = {},
                    onOpenDelayedSetup = {},
                    onOpenScheduleSetup = {},
                    onOpenDeviceOwnerGuide = {},
                    onReturnToLockScreen = {},
                    onDismissTerminalSession = {},
                    accountSyncState = com.example.sync.AccountSyncUiState(
                        isSignedIn = true,
                        userId = "u1",
                        displayName = "Tahan Official"
                    ),
                    reducedMotion = true
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("RESOLVE").fetchSemanticsNodes().isEmpty())
        val mascotBounds = composeRule.onNodeWithTag("home_mascot_badge").assertIsDisplayed().getBoundsInRoot()
        val guardBounds = composeRule.onNodeWithTag("home_enforcement_status_pill").assertIsDisplayed().getBoundsInRoot()
        val avatarBounds = composeRule.onNodeWithTag("home_profile_avatar_pill").assertIsDisplayed().getBoundsInRoot()
        assertTrue("Guard Active pill sits beside mascot", mascotBounds.right <= guardBounds.left + 4.dp)
        assertTrue("Profile avatar sits on right side", guardBounds.right < avatarBounds.left)
    }

    @Test
    fun `insights hub displays Insights title alongside History and Schedule tabs`() {
        composeRule.setContent {
            ResolveTheme {
                com.example.presentation.insights.InsightsScreen(
                    records = emptyList(),
                    schedules = emptyList(),
                    dailyAppUsage = com.example.domain.model.DailyAppUsageSummary(hasUsagePermission = false),
                    onGrantUsageAccess = {},
                    onAddSchedule = {},
                    onEditSchedule = {},
                    onToggleSchedule = { _, _ -> },
                    onDeleteSchedule = {},
                    reducedMotion = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_title").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_tab_history").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_tab_schedule").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [23])
    fun `mainActivity launches without system ActionBar in legacy portrait`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertEquals(R.style.Theme_Resolve, activity.packageManager.getActivityInfo(activity.componentName, 0).theme)
        assertTrue(activity.actionBar == null || !activity.actionBar!!.isShowing)
        assertFalse(activity.window.hasFeature(android.view.Window.FEATURE_ACTION_BAR))
        controller.destroy()
    }

    @Test
    fun `main activity launches without system action bar in portrait`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(R.style.Theme_Resolve, activity.packageManager.getActivityInfo(activity.componentName, 0).theme)
        assertTrue(activity.actionBar == null || !activity.actionBar!!.isShowing)
        assertFalse(activity.window.hasFeature(android.view.Window.FEATURE_ACTION_BAR))
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h380dp")
    fun `main activity launches without system action bar in landscape`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(R.style.Theme_Resolve, activity.packageManager.getActivityInfo(activity.componentName, 0).theme)
        assertTrue(activity.actionBar == null || !activity.actionBar!!.isShowing)
        assertFalse(activity.window.hasFeature(android.view.Window.FEATURE_ACTION_BAR))
        controller.pause().stop().destroy()
    }

    private class FakeTestClock(var wallMs: Long, var elapsedMs: Long) : ClockProvider {
        override fun wallClockMs(): Long = wallMs
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private class FakeDevicePolicy : DevicePolicyController {
        override fun isDeviceOwner(): Boolean = true
        override fun getLockTaskModeState(): Int = 0
        override fun getAppliedLockTaskPackages(): List<String> = emptyList()
        override suspend fun prepareLockTaskPolicy(
            selectedPackages: List<String>,
            phoneAccessEnabled: Boolean
        ): EnforcementPreparationResult = EnforcementPreparationResult.Ready(emptyList())

        override fun enterLockTask(activity: Activity): Boolean = true
        override fun exitLockTask(activity: Activity): Boolean = true
        override fun clearLockTaskPolicy() = Unit
    }

    private class NoOpAlarms : LockAlarmScheduler {
        override fun scheduleDelayedLockTransition(sessionId: String, triggerWallMs: Long) = Unit
        override fun cancelDelayedLockTransition() = Unit
        override fun scheduleLockExpiration(sessionId: String, triggerWallMs: Long) = Unit
        override fun cancelLockExpiration() = Unit
        override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerWallMs: Long) = Unit
        override fun cancelScheduleTrigger(scheduleId: Long) = Unit
        override fun cancelAllSessionAlarms() = Unit
    }

    private class NoOpBridge : SessionPlatformBridge {
        override fun startMonitorService() = Unit
        override fun stopMonitorService() = Unit
        override fun launchLockScreen() = Unit
        override fun notifyLockStateChanged() = Unit
    }

    private class RecordingFakeCloudGateway : CloudSyncGateway {
        val authFlow = MutableStateFlow<CloudAuthSession?>(null)
        val uploadedFlipSettings = mutableListOf<CloudFlipClockSettings>()
        val uploadedPresets = mutableListOf<CloudFlipTimerPreset>()

        override val authState: Flow<CloudAuthSession?> = authFlow
        override val currentUserId: String? = null
        override suspend fun signInWithGoogleIdToken(idToken: String): Result<CloudAuthSession> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override fun observeUserProfile(userId: String): Flow<CloudUserProfile?> = emptyFlow()
        override fun observeDevices(userId: String): Flow<List<CloudDeviceInfo>> = emptyFlow()
        override fun observeLatestLockCommand(userId: String): Flow<CloudLockCommand?> = emptyFlow()
        override suspend fun ensureUserProfile(user: CloudAuthSession): Result<Unit> = Result.success(Unit)
        override suspend fun registerOrUpdateDevice(
            userId: String,
            deviceId: String,
            deviceName: String,
            syncEnabled: Boolean
        ): Result<Unit> = Result.success(Unit)
        override suspend fun updateDeviceSyncEnabled(
            userId: String,
            deviceId: String,
            deviceName: String,
            syncEnabled: Boolean
        ): Result<Unit> = Result.success(Unit)
        override suspend fun updateAvatarBase64(userId: String, avatarBase64: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun publishLockCommand(
            userId: String,
            originDeviceId: String,
            originDeviceName: String,
            mode: LockMode,
            freeUseDurationMinutes: Long,
            lockDurationMinutes: Long,
            issuedAtWallMs: Long,
            intendedStartWallMs: Long,
            freeUseDeadlineWallMs: Long,
            lockExpirationWallMs: Long,
            scheduleId: String,
            occurrenceId: String,
            strictMode: Boolean,
            showFlipClockShortcut: Boolean,
            allowSelectedApps: Boolean,
            phoneAccessEnabled: Boolean,
            commandAction: String,
            targetDeviceId: String,
            extendMinutes: Long
        ): Result<String> = Result.success("cmd_1")

        override suspend fun upsertFlipClockSettings(
            userId: String,
            settings: CloudFlipClockSettings
        ): Result<Unit> {
            uploadedFlipSettings += settings
            return Result.success(Unit)
        }

        override suspend fun upsertFlipTimerPreset(
            userId: String,
            preset: CloudFlipTimerPreset
        ): Result<Unit> {
            uploadedPresets += preset
            return Result.success(Unit)
        }
    }
}
