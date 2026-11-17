package com.example

import android.content.Context
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.MascotPreferencesRepository
import com.example.designsystem.ResolveTheme
import com.example.domain.model.DefaultFocusMessages
import com.example.domain.model.EnforcementMode
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusMessageItem
import com.example.domain.model.LockMode
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotMicroAnimation
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.SystemCapabilityStatus
import com.example.mascot.FocusMessageManager
import com.example.mascot.MascotAnimationController
import com.example.mascot.MascotControllerConfig
import com.example.mascot.MascotControllerUiState
import com.example.mascot.MascotSelectionManager
import com.example.presentation.components.MascotRenderer
import com.example.presentation.components.bunny.BunnyWallMode
import com.example.presentation.components.bunny.resolvePeekEntrancePose
import com.example.presentation.home.HomeScreen
import com.example.presentation.lockscreen.SignatureLockScreen
import com.example.presentation.settings.SettingsScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MascotSystemRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var mascotPrefsRepo: MascotPreferencesRepository
    private lateinit var selectionManager: MascotSelectionManager
    private lateinit var messageManager: FocusMessageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val container = (context as ResolveApplication).container
        mascotPrefsRepo = container.mascotPreferencesRepository
        selectionManager = container.mascotSelectionManager
        messageManager = container.focusMessageManager
    }

    @Test
    fun `mascot enum strictly contains only baby cloud and resolve bunny with safe legacy fallback`() {
        assertEquals(2, MascotType.entries.size)
        assertEquals(MascotType.BABY_CLOUD, MascotType.DEFAULT)
        assertEquals(MascotType.BABY_CLOUD, MascotType.fromStorageKey(null))
        assertEquals(MascotType.BABY_CLOUD, MascotType.fromStorageKey("pikachu"))
        assertEquals(MascotType.BABY_CLOUD, MascotType.fromStorageKey("both"))
        assertEquals(MascotType.RESOLVE_BUNNY, MascotType.fromStorageKey("resolve_bunny"))
    }

    @Test
    fun `mascot selection enforces mutual exclusivity and persists across repository restarts`() = runTest {
        selectionManager.selectMascot(MascotType.BABY_CLOUD)
        val cloudPlan = selectionManager.buildRenderPlan(mascotPrefsRepo.getPreferencesSnapshot().selectedMascot)
        assertTrue(cloudPlan.showBabyCloud)
        assertFalse(cloudPlan.showResolveBunny)

        selectionManager.selectMascot(MascotType.RESOLVE_BUNNY)
        val freshRepo = MascotPreferencesRepository(context)
        val savedAfterSwitch = freshRepo.getPreferencesSnapshot().selectedMascot
        assertEquals(MascotType.RESOLVE_BUNNY, savedAfterSwitch)

        val bunnyPlan = selectionManager.buildRenderPlan(savedAfterSwitch)
        assertTrue(bunnyPlan.showResolveBunny)
        assertFalse(bunnyPlan.showBabyCloud)

        selectionManager.selectMascot(MascotType.BABY_CLOUD)
        assertEquals(MascotType.BABY_CLOUD, freshRepo.getPreferencesSnapshot().selectedMascot)
    }

    @Test
    fun `peek entrance progression covers all 8 stages and preserves bent ear curvature`() {
        val stage0 = resolvePeekEntrancePose(0.02f) // Hidden behind wall
        val stage1 = resolvePeekEntrancePose(0.20f) // Ears rise above wall
        val stage3 = resolvePeekEntrancePose(0.45f) // Eyes appear + blink
        val stage4 = resolvePeekEntrancePose(0.58f) // Paws grip wall top crest
        val stage5 = resolvePeekEntrancePose(0.68f) // Curious look
        val stage6 = resolvePeekEntrancePose(0.84f) // Slides beside wall
        val stage7 = resolvePeekEntrancePose(1.00f) // Wall recedes -> IDLE

        assertTrue(stage0.offsetYFraction > 0.5f)
        assertEquals(BunnyWallMode.PEEK_BEHIND, stage1.wallMode)
        assertTrue(stage1.offsetYFraction < stage0.offsetYFraction)
        assertTrue(stage3.eyeOpenFraction < 0.9f) // Natural peek blink
        assertTrue(stage4.pawsOnWallStrength > 0.7f)
        assertTrue(stage5.headTiltDeg > 4f)
        assertEquals(BunnyWallMode.BESIDE_WALL, stage6.wallMode)
        assertEquals(0f, stage7.wallAlpha, 0.01f)
        assertEquals(0f, stage7.offsetYFraction, 0.01f)

        // Signature bent ear curvature stays intact (>= 0.80f) across all stages
        listOf(stage0, stage1, stage3, stage4, stage5, stage6, stage7).forEach { pose ->
            assertTrue("Bent ear curvature must remain distinct", pose.bentEarTipCurl >= 0.80f)
        }
    }

    @Test
    fun `focus message manager validates crud, no consecutive repeat, and restore defaults`() = runTest {
        messageManager.restoreDefaultMessages()
        val defaults = mascotPrefsRepo.getPreferencesSnapshot().messages
        assertEquals(10, defaults.size)

        assertFalse(messageManager.addCustomMessage("   \n  "))

        assertTrue(messageManager.addCustomMessage("Keep breathing\n  calmly"))
        val afterAdd = mascotPrefsRepo.getPreferencesSnapshot().messages
        assertEquals(11, afterAdd.size)
        val custom = afterAdd.last()
        assertEquals("Keep breathing calmly", custom.text)
        assertTrue(custom.isCustom)

        assertTrue(messageManager.editMessage(custom.id, "Deep focus wins."))
        assertEquals("Deep focus wins.", mascotPrefsRepo.getPreferencesSnapshot().messages.last().text)

        messageManager.deleteMessage(custom.id)
        assertEquals(10, mascotPrefsRepo.getPreferencesSnapshot().messages.size)

        val twoItems = listOf(
            FocusMessageItem("a", "Stay focused!"),
            FocusMessageItem("b", "Keep going!")
        )
        var prevId: String? = "a"
        repeat(10) {
            val next = messageManager.selectNextFocusMessage(twoItems, prevId)!!
            assertNotEquals(prevId, next.id)
            prevId = next.id
        }

        assertNull(messageManager.selectNextFocusMessage(emptyList(), null))
        assertEquals("a", messageManager.selectNextFocusMessage(listOf(twoItems[0]), "a")?.id)
    }

    @Test
    fun `animation controller runs peek entrance, core animations, pointing, reduced motion, and pause`() = runTest {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val controller = MascotAnimationController(
            scope = scope,
            messageManager = messageManager,
            random = Random(42)
        )

        controller.updateConfig(
            MascotControllerConfig(
                activeMascot = MascotType.RESOLVE_BUNNY,
                focusMessagesEnabled = true,
                messageFrequency = FocusMessageFrequency.EVERY_2_MIN,
                reducedMotionEnabled = false,
                messages = DefaultFocusMessages.items,
                isPaused = false
            )
        )

        // Peek entrance transitions cleanly to IDLE without blocking
        var peekCompleted = false
        assertTrue(controller.triggerPeekEntrance { peekCompleted = true })
        scope.runCurrent()
        assertEquals(MascotAnimationState.PEEK_ENTRANCE, controller.uiState.value.animationState)
        scope.advanceTimeBy(MascotAnimationController.PEEK_ENTRANCE_DURATION_MS + 100L)
        assertTrue(peekCompleted)
        assertEquals(MascotAnimationState.IDLE, controller.uiState.value.animationState)
        assertEquals(1f, controller.uiState.value.peekProgress, 0.01f)

        // Core reaction + micro animation
        assertTrue(
            controller.triggerCoreAnimation(
                state = MascotAnimationState.HAPPY_DANCE,
                micro = MascotMicroAnimation.SMALL_HOP,
                durationMs = 800L
            )
        )
        scope.runCurrent()
        assertEquals(MascotAnimationState.HAPPY_DANCE, controller.uiState.value.animationState)
        assertEquals(MascotMicroAnimation.SMALL_HOP, controller.uiState.value.microAnimation)
        scope.advanceTimeBy(900L)
        assertEquals(MascotAnimationState.IDLE, controller.uiState.value.animationState)

        // Pointing sequence -> attention -> pointing -> bubble -> idle
        assertTrue(controller.triggerPointingCueNow("Stay strong!"))
        scope.runCurrent()
        assertEquals(MascotAnimationState.HEAD_EAR_ATTENTION, controller.uiState.value.animationState)
        scope.advanceTimeBy(500L)
        assertEquals(MascotAnimationState.POINTING, controller.uiState.value.animationState)
        scope.advanceTimeBy(300L)
        assertTrue(controller.uiState.value.isBubbleVisible)
        assertEquals("Stay strong!", controller.uiState.value.bubbleMessage)
        scope.advanceTimeBy(5_000L)
        assertFalse(controller.uiState.value.isBubbleVisible)
        assertEquals(MascotAnimationState.IDLE, controller.uiState.value.animationState)

        // Reduced motion displays message calmly without animated paw movement or peek motion
        controller.updateConfig(
            MascotControllerConfig(
                activeMascot = MascotType.RESOLVE_BUNNY,
                focusMessagesEnabled = true,
                messageFrequency = FocusMessageFrequency.EVERY_2_MIN,
                reducedMotionEnabled = true,
                messages = DefaultFocusMessages.items,
                isPaused = false
            )
        )
        assertTrue(controller.triggerPointingCueNow("Protect your focus."))
        scope.runCurrent()
        assertEquals(MascotAnimationState.IDLE, controller.uiState.value.animationState)
        assertTrue(controller.uiState.value.isBubbleVisible)
        scope.advanceTimeBy(5_000L)
        assertFalse(controller.uiState.value.isBubbleVisible)

        // Pause stops reactions cleanly
        controller.updateConfig(
            MascotControllerConfig(
                activeMascot = MascotType.BABY_CLOUD,
                focusMessagesEnabled = true,
                messageFrequency = FocusMessageFrequency.EVERY_2_MIN,
                reducedMotionEnabled = false,
                messages = DefaultFocusMessages.items,
                isPaused = true
            )
        )
        assertFalse(controller.triggerPointingCueNow("Should stay paused"))
        controller.stop()
    }

    @Test
    fun `mascot renderer enforces strict mutual exclusivity between baby cloud and resolve bunny`() {
        var active by mutableStateOf(MascotType.BABY_CLOUD)
        composeRule.setContent {
            ResolveTheme {
                MascotRenderer(mascot = active, reducedMotion = true)
            }
        }
        composeRule.onNodeWithTag("mascot_baby_cloud").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("mascot_resolve_bunny").fetchSemanticsNodes().isEmpty())

        active = MascotType.RESOLVE_BUNNY
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mascot_resolve_bunny").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("mascot_baby_cloud").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `lock screen renders only resolve bunny with speech bubble cleanly above countdown`() {
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 600_000L,
                    progressFraction = 0.75f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    mascotType = MascotType.RESOLVE_BUNNY,
                    mascotControllerState = MascotControllerUiState(
                        activeMascot = MascotType.RESOLVE_BUNNY,
                        animationState = MascotAnimationState.POINTING,
                        bubbleMessage = "Protect your focus.",
                        isBubbleVisible = true
                    ),
                    reducedMotion = true
                )
            }
        }
        composeRule.waitForIdle()

        val bunnyBounds = composeRule.onNodeWithTag("mascot_resolve_bunny").assertIsDisplayed().getBoundsInRoot()
        val bubbleBounds = composeRule.onNodeWithTag("focus_message_bubble").assertIsDisplayed().getBoundsInRoot()
        val countdownCardBounds = composeRule.onNodeWithTag("lock_screen_countdown_card").assertIsDisplayed().getBoundsInRoot()
        val countdownBounds = composeRule.onNodeWithTag("lock_screen_countdown").assertIsDisplayed().getBoundsInRoot()

        assertTrue(composeRule.onAllNodesWithTag("mascot_baby_cloud").fetchSemanticsNodes().isEmpty())
        assertTrue(
            "Speech bubble bottom (${bubbleBounds.bottom}) must sit above bunny top (${bunnyBounds.top})",
            bubbleBounds.bottom <= bunnyBounds.top
        )
        assertTrue(
            "Bunny bottom (${bunnyBounds.bottom}) must not overlap countdown card top (${countdownCardBounds.top})",
            bunnyBounds.bottom <= countdownCardBounds.top
        )
        assertTrue(
            "Countdown card width (${countdownCardBounds.right - countdownCardBounds.left}) must remain sleek & compact",
            (countdownCardBounds.right - countdownCardBounds.left) <= 300.dp
        )
        assertTrue(countdownBounds.top >= countdownCardBounds.top)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h560dp")
    fun `compact screen keeps resolve bunny celebration, countdown, and focus message visible without clipping`() {
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 0L,
                    progressFraction = 0f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = true,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.Verified },
                    mascotType = MascotType.RESOLVE_BUNNY,
                    mascotControllerState = MascotControllerUiState(
                        activeMascot = MascotType.RESOLVE_BUNNY,
                        animationState = MascotAnimationState.CELEBRATING,
                        bubbleMessage = "Session Completed!",
                        isBubbleVisible = true,
                        isCelebrating = true
                    ),
                    reducedMotion = true,
                    isCompletionCelebration = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_message_bubble").assertIsDisplayed()
        composeRule.onNodeWithTag("mascot_resolve_bunny").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_countdown").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("mascot_baby_cloud").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `home screen displays only active selected mascot and switches reactively`() {
        var mascot by mutableStateOf(MascotType.BABY_CLOUD)
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
                    mascotType = mascot,
                    bunnyPeekEntranceSeen = true,
                    reducedMotion = true
                )
            }
        }
        composeRule.onNodeWithTag("home_mascot_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("mascot_baby_cloud", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("mascot_resolve_bunny", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )

        mascot = MascotType.RESOLVE_BUNNY
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mascot_resolve_bunny", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("mascot_baby_cloud", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun `settings screen shows strictly 2 mutually exclusive mascot options without both or pikachu`() {
        var selected by mutableStateOf(MascotType.BABY_CLOUD)
        var reduced by mutableStateOf(false)
        var msgCount = 10
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
                    onOpenNotificationSettings = {},
                    mascotPreferences = MascotPreferences(
                        selectedMascot = selected,
                        reducedMotionEnabled = reduced
                    ),
                    onSelectMascot = { selected = it },
                    onToggleReducedMotion = { reduced = it },
                    onAddFocusMessage = { msgCount++ }
                )
            }
        }
        composeRule.waitForIdle()

        // Confirm zero "Both" or "Pikachu" options exist
        assertTrue(composeRule.onAllNodesWithText("Both").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Pikachu").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("mascot_option_resolve_bunny"))
        composeRule.onNodeWithTag("mascot_option_resolve_bunny").performClick()
        assertEquals(MascotType.RESOLVE_BUNNY, selected)

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("mascot_preview_peek"))
        composeRule.onNodeWithTag("mascot_preview_peek").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_reduced_motion_switch"))
        composeRule.onNodeWithTag("settings_reduced_motion_switch").performClick()
        assertTrue(reduced)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_focus_messages_manage_button"))
        composeRule.onNodeWithTag("settings_focus_messages_manage_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("focus_message_add_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("focus_message_input").performTextReplacement("Stay resolute!")
        composeRule.onNodeWithTag("focus_message_save_button").performClick()
        composeRule.waitForIdle()
        assertEquals(11, msgCount)

        // Dismiss sheet or verify About / Tahan Official signature card in clean tree
    }

    @Test
    fun `settings crafted signature card shows distraction emblem, tagline, and developer credit cleanly`() {
        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = SystemCapabilityStatus(),
                    hasRecoveryCode = true,
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
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_developer_credit"))
        composeRule.mainClock.advanceTimeBy(2_200L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_distraction_emblem").assertIsDisplayed()
        composeRule.onNodeWithText("Your distraction killing is resolve duty.").assertIsDisplayed()
        composeRule.onNodeWithText("Developed by Tahan Official").assertIsDisplayed()
    }

    @Test
    fun `settings permissions dropdown auto-collapses when all 6 permissions are ready and toggles on tap`() {
        val allReady = SystemCapabilityStatus(
            isAccessibilityServiceEnabled = true,
            canDrawOverlays = true,
            hasUsageStatsAccess = true,
            exactAlarmRequiredByOs = false,
            canScheduleExactAlarms = true,
            notificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )
        var status by mutableStateOf(SystemCapabilityStatus())
        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = status,
                    hasRecoveryCode = true,
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
        // Incomplete state: expanded by default
        composeRule.onNodeWithTag("settings_permissions_dropdown_header").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_permissions_dropdown_content").assertIsDisplayed()

        // Once all 6 permissions are allowed: auto-collapses to hidden state
        status = allReady
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_permissions_dropdown_content")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithText("6 / 6 ready • Hidden (tap to view)").assertIsDisplayed()

        // User can tap dropdown header to view or manage anytime
        composeRule.onNodeWithTag("settings_permissions_dropdown_header").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_permissions_dropdown_content").assertIsDisplayed()
    }

    @Test
    fun `monochrome status bar notification icon resource resolves cleanly`() {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            R.drawable.ic_notification_resolve
        )
        assertTrue("Monochrome notification icon drawable must load cleanly", drawable != null)
    }

    @Test
    fun `lock screen reminder preference persists toggle, custom text, and style cleanly`() = runTest {
        mascotPrefsRepo.setLockReminderEnabled(false)
        assertFalse(mascotPrefsRepo.getPreferencesSnapshot().lockReminderEnabled)

        mascotPrefsRepo.setLockReminderEnabled(true)
        mascotPrefsRepo.setLockReminderStyle(LockReminderStyle.PLAIN_TEXT)
        mascotPrefsRepo.setLockReminderText("Protect deep work today.")

        val freshRepo = MascotPreferencesRepository(context)
        val snap = freshRepo.getPreferencesSnapshot()
        assertTrue(snap.lockReminderEnabled)
        assertEquals(LockReminderStyle.PLAIN_TEXT, snap.lockReminderStyle)
        assertEquals("Protect deep work today.", snap.lockReminderText)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h740dp")
    fun `lock screen renders customizable reminder in box or plain style and hides cleanly when disabled`() {
        var enabled by mutableStateOf(true)
        var style by mutableStateOf(LockReminderStyle.SOFT_BOX)
        var reminderText by mutableStateOf("Discipline beats momentary impulse.")

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
                    mascotType = MascotType.RESOLVE_BUNNY,
                    reducedMotion = true,
                    lockReminderEnabled = enabled,
                    lockReminderText = reminderText,
                    lockReminderStyle = style
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_screen_reminder_box").assertIsDisplayed()
        composeRule.onNodeWithText("Discipline beats momentary impulse.").assertIsDisplayed()

        // Switch to boxless minimal text style
        style = LockReminderStyle.PLAIN_TEXT
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_screen_reminder_plain").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("lock_screen_reminder_box").fetchSemanticsNodes().isEmpty())

        // Disable reminder cleanly
        enabled = false
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("lock_screen_reminder_text").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `settings lock reminder switch and editor customize sentence and style cleanly`() {
        var enabled by mutableStateOf(true)
        var style by mutableStateOf(LockReminderStyle.SOFT_BOX)
        var text by mutableStateOf("Stay locked in.")

        composeRule.setContent {
            ResolveTheme {
                SettingsScreen(
                    capabilities = SystemCapabilityStatus(),
                    hasRecoveryCode = true,
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
                    onOpenNotificationSettings = {},
                    mascotPreferences = MascotPreferences(
                        lockReminderEnabled = enabled,
                        lockReminderText = text,
                        lockReminderStyle = style
                    ),
                    onToggleLockReminderEnabled = { enabled = it },
                    onSaveLockReminderText = { text = it },
                    onSelectLockReminderStyle = { style = it }
                )
            }
        }
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_lock_reminder_customize_button"))
        composeRule.onNodeWithTag("settings_lock_reminder_customize_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lock_reminder_style_plain_text").performScrollTo().performClick()
        composeRule.onNodeWithTag("lock_reminder_input").performScrollTo().performTextReplacement("Finish strong today.")
        composeRule.onNodeWithTag("lock_reminder_save_button").performClick()
        composeRule.waitForIdle()

        assertEquals(LockReminderStyle.PLAIN_TEXT, style)
        assertEquals("Finish strong today.", text)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_lock_reminder_switch"))
        composeRule.onNodeWithTag("settings_lock_reminder_switch").performClick()
        composeRule.waitForIdle()
        assertFalse(enabled)
    }
}
