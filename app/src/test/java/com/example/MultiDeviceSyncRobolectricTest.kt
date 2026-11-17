package com.example

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.designsystem.ResolveTheme
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.DurationSelection
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.LockMode
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.time.ClockProvider
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.home.HomeScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.setup.InstantLockSetupScreen
import com.example.scheduling.LockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.session.EngineStartResult
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import com.example.sync.AccountSyncUiState
import com.example.sync.AvatarImageProcessor
import com.example.sync.CloudDeviceInfo
import com.example.sync.CloudLockCommand
import com.example.sync.CloudSyncGateway
import com.example.sync.CloudUserProfile
import com.example.sync.MultiDeviceSyncCoordinator
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiDeviceSyncRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ResolveDatabase
    private lateinit var prefs: ResolvePreferencesRepository
    private lateinit var allowlistRepo: AllowlistRepository
    private lateinit var clock: FakeSyncClock
    private lateinit var engine: LockSessionEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = (context as ResolveApplication).container.preferencesRepository
        allowlistRepo = AllowlistRepository(db.allowedAppDao())
        clock = FakeSyncClock(wallMs = 1_794_800_000_000L, elapsedMs = 20_000L)

        val recovery = RecoveryCodeManager(prefs, clock)
        engine = LockSessionEngine(
            sessionRepository = SessionRepository(db.activeSessionDao()),
            scheduleRepository = ScheduleRepository(db.lockScheduleDao()),
            historyRepository = HistoryRepository(db.sessionHistoryDao()),
            preferencesRepository = prefs,
            devicePolicyController = FakeSyncDevicePolicyController(),
            alarmScheduler = NoOpAlarmScheduler(),
            recoveryCodeManager = recovery,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = clock,
            platformBridge = NoOpPlatformBridge()
        )
    }

    @After
    fun tearDown() {
        engine.shutdown()
        db.close()
    }

    @Test
    fun `remote instant lock syncs cleanly using local allowlist and rejects stale expired commands`() = runTest {
        val recovery = RecoveryCodeManager(prefs, clock)
        assertTrue(recovery.saveRecoveryCode(recovery.generateNumericCode(16)))
        engine.initialize()

        // Expired command must be ignored cleanly
        val staleCommand = CloudLockCommand(
            userId = "u1",
            commandId = "cmd_stale",
            originDeviceId = "other_device",
            originDeviceName = "Pixel 8",
            mode = "INSTANT",
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = 30L,
            issuedAtWallMs = clock.wallMs - 40 * 60_000L,
            freeUseDeadlineWallMs = 0L,
            lockExpirationWallMs = clock.wallMs - 10 * 60_000L
        )
        val staleResult = engine.applyRemoteLockCommand(
            command = staleCommand,
            allowedPackages = listOf("com.local.app"),
            phoneAccessEnabled = false
        )
        assertTrue(staleResult is EngineStartResult.Blocked)
        assertNull(engine.activeSession.value)

        // Fresh synced Instant Lock command engages with this phone's own local allowlist & phone access
        val freshExpire = clock.wallMs + 45 * 60_000L
        val freshCommand = CloudLockCommand(
            userId = "u1",
            commandId = "cmd_fresh_1",
            originDeviceId = "other_device",
            originDeviceName = "Pixel 8",
            mode = "INSTANT",
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = 45L,
            issuedAtWallMs = clock.wallMs,
            freeUseDeadlineWallMs = 0L,
            lockExpirationWallMs = freshExpire
        )
        val freshResult = engine.applyRemoteLockCommand(
            command = freshCommand,
            allowedPackages = listOf("com.local.notes", "com.local.dialer"),
            phoneAccessEnabled = true
        )
        assertEquals(EngineStartResult.Started, freshResult)
        val session = engine.activeSession.value
        assertNotNull(session)
        assertEquals(SessionState.LOCK_TRANSITION_PENDING, session?.state)
        assertEquals(LockMode.INSTANT, session?.mode)
        assertEquals(listOf("com.local.notes", "com.local.dialer"), session?.allowedPackages)
        assertEquals(true, session?.phoneAccessEnabled)
        assertEquals(freshExpire, session?.lockExpirationWallMs)
    }

    @Test
    fun `remote delayed lock preserves exact free use deadline across phones`() = runTest {
        val recovery = RecoveryCodeManager(prefs, clock)
        assertTrue(recovery.saveRecoveryCode(recovery.generateNumericCode(16)))
        engine.initialize()

        val freeDeadline = clock.wallMs + 15 * 60_000L
        val delayedCmd = CloudLockCommand(
            userId = "u1",
            commandId = "cmd_delayed_1",
            originDeviceId = "phone_b",
            originDeviceName = "Galaxy S24",
            mode = "DELAYED",
            freeUseDurationMinutes = 15L,
            lockDurationMinutes = 60L,
            issuedAtWallMs = clock.wallMs,
            freeUseDeadlineWallMs = freeDeadline,
            lockExpirationWallMs = freeDeadline + 60 * 60_000L
        )
        val result = engine.applyRemoteLockCommand(
            command = delayedCmd,
            allowedPackages = listOf("com.device.a.only"),
            phoneAccessEnabled = false
        )
        assertEquals(EngineStartResult.Started, result)
        val active = engine.activeSession.value
        assertNotNull(active)
        assertEquals(SessionState.DELAYED_FREE_USE_ACTIVE, active?.state)
        assertEquals(LockMode.DELAYED, active?.mode)
        assertEquals(freeDeadline, active?.freeUseDeadlineWallMs)
        assertEquals(60L, active?.lockDurationMinutes)
        assertEquals(listOf("com.device.a.only"), active?.allowedPackages)
        assertEquals(false, active?.phoneAccessEnabled)
    }

    @Test
    fun `avatar processor decodes valid encoded bitmap safely`() {
        val sample = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        sample.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val decoded = AvatarImageProcessor.decodeAvatarBitmap(encoded)
        assertNotNull(decoded)
        assertEquals(64, decoded?.width)
        assertNull(AvatarImageProcessor.decodeAvatarBitmap(""))
        assertNull(AvatarImageProcessor.decodeAvatarBitmap("not-valid-base64!!!"))
    }

    @Test
    fun `home banner explains multi-device sync when signed out and hides automatically after sign in`() {
        var syncState by mutableStateOf(AccountSyncUiState(isSignedIn = false))
        var dismissed by mutableStateOf(false)

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
                    accountSyncState = syncState,
                    syncPromoDismissed = dismissed,
                    onConnectGoogleAccount = {
                        syncState = AccountSyncUiState(
                            isSignedIn = true,
                            userId = "u1",
                            displayName = "Tahan Official",
                            email = "tahan@example.com"
                        )
                    },
                    onDismissSyncPromo = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("home_optional_sync_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Stay Offline").assertIsDisplayed()
        composeRule.onNodeWithTag("home_sync_connect_button").performClick()
        composeRule.waitForIdle()

        // Promo banner must disappear once connected, and avatar pill appears
        assertEquals(0, composeRule.onAllNodesWithTag("home_optional_sync_banner").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("home_profile_avatar_pill").assertIsDisplayed()
    }

    @Test
    fun `settings card hides promotional text after sign in and exposes avatar and sync toggle`() {
        var signedInState by mutableStateOf(AccountSyncUiState(isSignedIn = false))

        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = SystemCapabilityStatus(isAccessibilityServiceEnabled = true),
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
                    accountSyncState = signedInState,
                    onSignInGoogle = {
                        signedInState = AccountSyncUiState(
                            isSignedIn = true,
                            userId = "uid_1",
                            displayName = "Tahan Official",
                            email = "tahan@example.com",
                            thisDeviceId = "dev_1",
                            thisDeviceName = "Pixel 8",
                            syncLockEnabled = true,
                            connectedDevices = listOf(
                                CloudDeviceInfo(deviceId = "dev_1", userId = "uid_1", deviceName = "Pixel 8", syncEnabled = true),
                                CloudDeviceInfo(deviceId = "dev_2", userId = "uid_1", deviceName = "Galaxy S24", syncEnabled = true)
                            )
                        )
                    },
                    onToggleDeviceSync = { enabled ->
                        signedInState = signedInState.copy(syncLockEnabled = enabled)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("settings_account_signed_out_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_google_signin_button").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("settings_account_signed_out_card").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("settings_account_signed_in_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_profile_avatar_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_device_sync_switch").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 8 (This)").assertIsDisplayed()
        composeRule.onNodeWithText("Galaxy S24").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_google_signout_button").assertIsDisplayed()
    }

    @Test
    fun `instant lock setup lets signed in user choose all devices or this device only`() {
        var syncThisLock by mutableStateOf(true)

        composeRule.setContent {
            ResolveTheme {
                InstantLockSetupScreen(
                    duration = DurationSelection(0, 30),
                    allowedPackages = emptyList(),
                    installedApps = emptyList(),
                    phoneAccessEnabled = true,
                    hasRecoveryCode = true,
                    recoveryCodeLength = 16,
                    loadIcon = { null },
                    generateRandomCode = { "1".repeat(it) },
                    onHoursChanged = {},
                    onMinutesChanged = {},
                    onDurationReplaced = {},
                    onSaveAllowedPackages = {},
                    onPhoneAccessChanged = {},
                    onSaveRecoveryCode = {},
                    onStartInstantLock = {},
                    onBack = {},
                    isAccountSignedIn = true,
                    syncAcrossDevices = syncThisLock,
                    onSyncAcrossDevicesChanged = { syncThisLock = it }
                )
            }
        }

        composeRule.onNodeWithTag("instant_sync_switch").assertIsDisplayed()
        composeRule.onNodeWithText("Device Sync: All Linked Devices").assertIsDisplayed()

        composeRule.onNodeWithTag("instant_sync_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Device Sync: This Phone Only").assertIsDisplayed()
    }

    private class FakeSyncClock(var wallMs: Long, var elapsedMs: Long) : ClockProvider {
        override fun wallClockMs(): Long = wallMs
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private class FakeSyncDevicePolicyController : DevicePolicyController {
        override fun isDeviceOwner(): Boolean = true
        override fun isAdminActive(): Boolean = true
        override fun isLockTaskPermitted(packageName: String): Boolean = true
        override fun getLockTaskModeState(): Int = 0
        override fun prepareLockTaskPolicy(
            allowedPackages: List<String>,
            phoneAccessEnabled: Boolean
        ): EnforcementPreparationResult = EnforcementPreparationResult.Ready(emptyList())

        override fun enterLockTask(activity: Activity): Boolean = true
        override fun exitLockTask(activity: Activity): Boolean = true
    }

    private class NoOpAlarmScheduler : LockAlarmScheduler {
        override fun scheduleDelayedLockTransition(sessionId: String, triggerWallMs: Long) = Unit
        override fun cancelDelayedLockTransition() = Unit
        override fun scheduleLockExpiration(sessionId: String, triggerWallMs: Long) = Unit
        override fun cancelLockExpiration() = Unit
        override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerWallMs: Long) = Unit
        override fun cancelScheduleTrigger(scheduleId: Long) = Unit
        override fun cancelAllSessionAlarms() = Unit
    }

    private class NoOpPlatformBridge : SessionPlatformBridge {
        override fun startMonitorService() = Unit
        override fun stopMonitorService() = Unit
        override fun launchLockScreen() = Unit
        override fun notifyLockStateChanged() = Unit
    }
}
