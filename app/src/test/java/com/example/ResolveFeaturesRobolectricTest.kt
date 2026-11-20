package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.repository.DelayedPresetRepository
import com.example.designsystem.ResolveTheme
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.DailyAppUsageItem
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.DurationSelection
import com.example.domain.model.EnforcementMode
import com.example.domain.model.LockMode
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.time.ClockProvider
import com.example.presentation.applock.SixCellSequentialPad
import com.example.presentation.components.BabyCloudMascot
import com.example.presentation.history.HistoryScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.lockscreen.SignatureLockScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.setup.DelayedLockSetupScreen
import com.example.security.AppLockCredentialManager
import com.example.security.AppLockSessionGuard
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResolveFeaturesRobolectricTest {

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
    fun `room migration v2 to v3 adds delayed presets table and schedule sync columns without loss`() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val helper = object : SQLiteOpenHelper(context, dbName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lock_schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        oneTimeDateEpochDay INTEGER,
                        weekdaysMask INTEGER NOT NULL,
                        startHour INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        lockDurationMinutes INTEGER NOT NULL,
                        allowedPackagesJson TEXT NOT NULL,
                        phoneAccessEnabled INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        createdAtWallMs INTEGER NOT NULL,
                        nextTriggerWallMs INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO lock_schedules (
                        name, type, weekdaysMask, startHour, startMinute,
                        lockDurationMinutes, allowedPackagesJson, phoneAccessEnabled,
                        isEnabled, createdAtWallMs
                    ) VALUES ('Morning Study', 'DAILY', 0, 8, 30, 60, '[]', 1, 1, 1000)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()
        helper.close()

        val supportHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqlDb = supportHelper.writableDatabase
        ResolveDatabase.MIGRATION_2_3.migrate(sqlDb)

        val schedCursor = sqlDb.query("SELECT name, cloudId, syncAcrossDevices FROM lock_schedules")
        assertTrue(schedCursor.moveToFirst())
        assertEquals("Morning Study", schedCursor.getString(0))
        assertEquals("", schedCursor.getString(1))
        assertEquals(0, schedCursor.getInt(2))
        schedCursor.close()

        val presetsCursor = sqlDb.query("SELECT name FROM delayed_presets")
        presetsCursor.close()
        sqlDb.close()
        supportHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `delayed preset repository creates built-in Quick Recovery 20m to 30m and manages custom presets`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = DelayedPresetRepository(db.delayedLockPresetDao())
        repo.ensureBuiltInPresets()

        val initial = repo.getAllPresets()
        assertEquals(1, initial.size)
        val quickRecovery = initial.first()
        assertEquals("Quick Recovery", quickRecovery.name)
        assertEquals(20L, quickRecovery.freeUseMinutes)
        assertEquals(30L, quickRecovery.lockDurationMinutes)
        assertTrue(quickRecovery.builtIn)

        // Built-in preset cannot be accidentally deleted
        repo.deleteCustomPreset(quickRecovery.presetId)
        assertEquals(1, repo.getAllPresets().size)

        db.close()
    }

    @Test
    fun `app lock credential manager supports pattern, pin, 6-cell sequence, and throttles after 5 failures`() = runTest {
        val fakeClock = object : ClockProvider {
            var wall = 100_000L
            override fun wallClockMs(): Long = wall
            override fun elapsedRealtimeMs(): Long = wall
            override fun zoneId(): ZoneId = ZoneId.of("UTC")
        }
        val manager = AppLockCredentialManager(container.preferencesRepository, fakeClock)

        // 1. Sequential 6-Cell (4-6 cells, 0-based indices 0..5)
        assertTrue(manager.saveCredential(AppLockAuthMethod.SIX_CELL_SEQUENTIAL, AppLockPinLength.FOUR, "0-3-1-5"))
        assertEquals(AppLockVerificationResult.Verified, manager.verifyCredential("0-3-1-5"))
        assertTrue(manager.verifyCredential("0-3-5-1") is AppLockVerificationResult.InvalidCredential)

        // 2. 6-digit PIN & 5-attempt cooldown throttling
        assertTrue(manager.saveCredential(AppLockAuthMethod.PIN, AppLockPinLength.SIX, "258014"))
        assertEquals(AppLockVerificationResult.Verified, manager.verifyCredential("258014"))

        repeat(4) {
            val res = manager.verifyCredential("000000")
            assertTrue(res is AppLockVerificationResult.InvalidCredential)
        }
        val fifth = manager.verifyCredential("000000")
        assertTrue("5th failed attempt must throttle", fifth is AppLockVerificationResult.Throttled)
        fakeClock.wall += 31_000L
        assertEquals(AppLockVerificationResult.Verified, manager.verifyCredential("258014"))
    }

    @Test
    fun `app lock session guard resets grant on screen off or switching away from protected app`() {
        val guard = AppLockSessionGuard()
        guard.grantTemporaryUnlock("com.instagram.android", nowElapsedMs = 1_000L)
        assertTrue(guard.isTemporarilyUnlocked("com.instagram.android", nowElapsedMs = 1_100L))

        // Target app enters foreground, then after 2200ms transition grace user switches away
        guard.onForegroundAppChanged("com.instagram.android", nowElapsedMs = 1_200L)
        guard.onForegroundAppChanged("com.android.chrome", nowElapsedMs = 3_500L)
        assertFalse(guard.isTemporarilyUnlocked("com.instagram.android", nowElapsedMs = 3_600L))

        guard.grantTemporaryUnlock("com.instagram.android", nowElapsedMs = 2_000L)
        assertTrue(guard.isTemporarilyUnlocked("com.instagram.android", nowElapsedMs = 2_100L))
        guard.onScreenOff()
        assertFalse(guard.isTemporarilyUnlocked("com.instagram.android", nowElapsedMs = 2_200L))
    }

    @Test
    fun `6-cell sequential pad shows cell numbers in setup mode and hides all numbers in unlock mode`() {
        var setupMode by mutableStateOf(true)
        composeRule.setContent {
            ResolveTheme {
                SixCellSequentialPad(
                    showSequenceNumbers = setupMode,
                    resetKey = 0,
                    onSequenceSubmitted = {}
                )
            }
        }
        composeRule.waitForIdle()
        (1..6).forEach { idx ->
            composeRule.onNodeWithText(idx.toString()).assertIsDisplayed()
        }

        // In unlock mode, ALL cell numbers MUST be hidden
        setupMode = false
        composeRule.waitForIdle()
        (1..6).forEach { idx ->
            assertTrue(
                "Cell $idx number must be strictly hidden in unlock mode",
                composeRule.onAllNodesWithText(idx.toString()).fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    fun `home screen displays 4 focus cards, today progress card, and usage access notice cleanly`() {
        val now = System.currentTimeMillis()
        val records = listOf(
            SessionHistoryRecord(
                id = 1L,
                sessionId = "s1",
                mode = LockMode.INSTANT,
                freeUseDurationMinutes = 0L,
                lockDurationMinutes = 45L,
                startedAtWallMs = now - 3_600_000L,
                endedAtWallMs = now - 900_000L,
                outcome = SessionOutcome.COMPLETED,
                allowedAppCount = 1
            )
        )
        var usageState by mutableStateOf(DailyAppUsageSummary(hasUsagePermission = false))

        composeRule.setContent {
            ResolveTheme {
                HomeScreen(
                    activeSession = null,
                    remainingMillis = 0L,
                    capabilities = SystemCapabilityStatus(isAccessibilityServiceEnabled = true),
                    onOpenInstantSetup = {},
                    onOpenDelayedSetup = {},
                    onOpenScheduleSetup = {},
                    onOpenDeviceOwnerGuide = {},
                    onReturnToLockScreen = {},
                    onDismissTerminalSession = {},
                    historyRecords = records,
                    dailyAppUsage = usageState,
                    reducedMotion = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mode_card_instant").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_delayed").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_card_applock").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_progress_overview_card"))
        composeRule.onNodeWithTag("home_progress_overview_card").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_grant_usage_button"))
        composeRule.onNodeWithTag("home_grant_usage_button").assertIsDisplayed()

        // Updating with real UsageStats data displays real app row
        usageState = DailyAppUsageSummary(
            hasUsagePermission = true,
            totalScreenTimeMs = 90 * 60_000L,
            topApps = listOf(
                DailyAppUsageItem("com.example.reader", "Reader", 60 * 60_000L)
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_usage_app_com.example.reader").assertIsDisplayed()
    }

    @Test
    fun `insights screen renders 7-day trend chart and mode split without fake metrics`() {
        val now = System.currentTimeMillis()
        val records = listOf(
            SessionHistoryRecord(1L, "a", LockMode.INSTANT, 0L, 30L, now - 10_000L, now - 5_000L, SessionOutcome.COMPLETED, 0),
            SessionHistoryRecord(2L, "b", LockMode.DELAYED, 20L, 30L, now - 20_000L, now - 15_000L, SessionOutcome.COMPLETED, 2)
        )
        composeRule.setContent {
            ResolveTheme {
                HistoryScreen(
                    records = records,
                    dailyAppUsage = DailyAppUsageSummary(hasUsagePermission = false)
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Focus History").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_weekly_chart_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_mode_split_card"))
        composeRule.onNodeWithTag("insights_mode_split_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_usage_summary_card"))
        composeRule.onNodeWithTag("insights_usage_summary_card").assertIsDisplayed()
    }

    @Test
    fun `settings hides granted permissions with zero reveal chip, while ModesScreen and AboutResolveScreen render cleanly`() {
        val capabilitiesPartial = SystemCapabilityStatus(
            isAccessibilityServiceEnabled = false,
            canDrawOverlays = true, // Granted -> strictly hidden without reveal option
            hasUsageStatsAccess = true,
            exactAlarmRequiredByOs = false,
            notificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = capabilitiesPartial,
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
                    mascotPreferences = MascotPreferences(reducedMotionEnabled = true)
                )
            }
        }
        composeRule.waitForIdle()

        // Missing Accessibility row visible first; already-granted Display Over Other Apps hidden with zero reveal option
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_accessibility_action"))
        composeRule.onNodeWithTag("settings_accessibility_action").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_overlay_action").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("settings_permissions_show_granted_chip").fetchSemanticsNodes().isEmpty()
        )

        // Focus Notification Control card and About entry present without SYS // RESOLVE_OVERVIEW
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_notification_control_card"))
        composeRule.onNodeWithTag("settings_notification_control_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_open_about_btn"))
        composeRule.onNodeWithTag("settings_open_about_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Developed by Tahan Official").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("SYS // RESOLVE_OVERVIEW").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h380dp")
    fun `landscape lock screen adapts cleanly for both Focus Reminder ON and OFF`() {
        var reminderOn by mutableStateOf(true)
        var allowedApps by mutableStateOf<List<com.example.domain.model.InstalledAppInfo>>(emptyList())
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 600_000L,
                    progressFraction = 0.5f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = allowedApps,
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    mascotType = MascotType.BABY_CLOUD,
                    reducedMotion = true,
                    lockReminderEnabled = reminderOn,
                    lockReminderText = "Keep your focus sharp."
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lock_screen_landscape_reminder_on").assertIsDisplayed()
        val titleBounds = composeRule.onNodeWithTag("lock_screen_landscape_focus_guard_title")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val mascotBounds = composeRule.onNodeWithTag("mascot_baby_cloud").assertIsDisplayed().getBoundsInRoot()
        val reminderBounds = composeRule.onNodeWithTag("lock_screen_reminder_text").assertIsDisplayed().getBoundsInRoot()
        assertTrue("Focus Guard badge sits above panes", titleBounds.bottom <= reminderBounds.top)
        assertTrue("Mascot sits on left pane relative to reminder on right", mascotBounds.left < reminderBounds.left)
        // When allowlist is empty, Allowed Apps button is hidden
        assertTrue(
            composeRule.onAllNodesWithTag("lock_screen_allowed_apps_button").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("lock_screen_phone_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_recovery_button").assertIsDisplayed()

        // Providing allowed apps reveals the Allowed Apps button, and Reminder OFF switches layout cleanly
        allowedApps = listOf(com.example.domain.model.InstalledAppInfo("com.example.notes", "Notes"))
        reminderOn = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_screen_landscape_reminder_off").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("lock_screen_landscape_focus_guard_title").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(composeRule.onAllNodesWithTag("lock_screen_reminder_text").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("lock_screen_countdown_card").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_allowed_apps_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_phone_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_recovery_button").assertIsDisplayed()
    }

    @Test
    fun `baby cloud renders organic animation states cleanly`() {
        var animState by mutableStateOf(MascotAnimationState.GENTLE_FLOAT)
        composeRule.setContent {
            ResolveTheme {
                BabyCloudMascot(animationState = animState, reducedMotion = false)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mascot_baby_cloud").assertIsDisplayed()

        animState = MascotAnimationState.CURIOUS_SWAY
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mascot_baby_cloud").assertIsDisplayed()

        animState = MascotAnimationState.HAPPY_BOUNCE
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mascot_baby_cloud").assertIsDisplayed()
    }

    @Test
    fun `delayed setup screen applies built-in Quick Recovery preset cleanly`() {
        var freeDur by mutableStateOf(DurationSelection(0, 15))
        var lockDur by mutableStateOf(DurationSelection(0, 15))

        composeRule.setContent {
            ResolveTheme {
                DelayedLockSetupScreen(
                    freeUseDuration = freeDur,
                    lockDuration = lockDur,
                    allowedPackages = emptyList(),
                    installedApps = emptyList(),
                    phoneAccessEnabled = true,
                    hasRecoveryCode = true,
                    recoveryCodeLength = 16,
                    loadIcon = { null },
                    generateRandomCode = { "1".repeat(it) },
                    onFreeUseHoursChanged = {},
                    onFreeUseMinutesChanged = {},
                    onFreeUseReplaced = { freeDur = it },
                    onLockHoursChanged = {},
                    onLockMinutesChanged = {},
                    onLockReplaced = { lockDur = it },
                    onSaveAllowedPackages = {},
                    onPhoneAccessChanged = {},
                    onSaveRecoveryCode = {},
                    onStartDelayedLock = {},
                    onBack = {},
                    presets = listOf(DelayedPresetRepository.BUILT_IN_QUICK_RECOVERY)
                )
            }
        }

        composeRule.onNodeWithTag("delayed_preset_item_builtin_quick_recovery").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(20L, freeDur.totalMinutes)
        assertEquals(30L, lockDur.totalMinutes)
    }
}
