package com.example.presentation.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ResolveApplication
import com.example.designsystem.InterFamily
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveTheme
import com.example.domain.model.DefaultLockReminders
import com.example.domain.model.EnforcementMode
import com.example.domain.model.FlipClockPage
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockMode
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.SessionState
import com.example.flipclock.FlipClockActivity
import com.example.mascot.MascotAnimationController
import com.example.mascot.MascotControllerConfig
import com.example.mascot.MascotControllerUiState
import com.example.presentation.components.LockScreenMascotStage
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.RecoveryCodeVerifySheet
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.pressScale
import com.example.session.LockExitSignal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class LockScreenActivity : ComponentActivity() {

    private var isEngineInitialized by mutableStateOf(false)
    private var isConfirmingLock = false
    private var sawActiveLockEnforcement by mutableStateOf(false)
    private var isShowingCompletionCelebration by mutableStateOf(false)

    private var hasLoadedTiltPreference by mutableStateOf(false)
    private var isTiltModeEnabled by mutableStateOf(false)
    private var isActivityResumed = false
    private val orientationHandler = Handler(Looper.getMainLooper())
    private var orientationListener: OrientationEventListener? = null
    private var pendingTargetOrientation: Int? = null
    private var pendingOrientationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        enableEdgeToEdge()
        applyImmersiveSystemBars()

        val app = application as? ResolveApplication ?: run {
            finish()
            return
        }
        val container = app.container
        val engine = container.sessionEngine
        container.foregroundGuard.clearBarrier()

        lifecycleScope.launch {
            isConfirmingLock = true
            runCatching { engine.initialize() }
            isEngineInitialized = true
            val confirmed = runCatching { engine.confirmLockTaskEntered(this@LockScreenActivity) }
                .getOrDefault(false)
            isConfirmingLock = false
            if (!confirmed && !isFinishing && !isShowingCompletionCelebration) {
                finish()
            }
        }

        lifecycleScope.launch {
            engine.lockExitSignals.collect { signal ->
                when (signal) {
                    LockExitSignal.ExitLockTaskAndFinish -> {
                        runCatching { engine.finalizeLockExit(this@LockScreenActivity) }
                        runCatching { moveTaskToBack(true) }
                        if (!isFinishing) {
                            finishAndRemoveTask()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            container.preferencesRepository.landscapeTiltModeFlow
                .distinctUntilChanged()
                .collect { enabled ->
                    hasLoadedTiltPreference = true
                    val wasEnabled = isTiltModeEnabled
                    isTiltModeEnabled = enabled
                    reconcileOrientationMonitoring(transitionedToEnabled = enabled && !wasEnabled)
                }
        }

        setContent {
            ResolveTheme {
                // Disable Back exit ONLY while Lock Mode is still enforced; once session finishes,
                // tapping Back exits the brief celebration immediately.
                BackHandler(enabled = !isShowingCompletionCelebration) {}

                val activeSession by engine.activeSession.collectAsStateWithLifecycle()
                val remainingMillis by engine.remainingMillis.collectAsStateWithLifecycle()
                val recoveryLength by container.recoveryCodeManager.recoveryCodeLengthFlow
                    .collectAsStateWithLifecycle(initialValue = 16)
                val mascotPreferences by container.mascotSelectionManager.preferencesFlow
                    .collectAsStateWithLifecycle(initialValue = MascotPreferences())
                val landscapeTiltMode by container.preferencesRepository.landscapeTiltModeFlow
                    .collectAsStateWithLifecycle(initialValue = isTiltModeEnabled)

                var isLifecyclePaused by remember { mutableStateOf(false) }
                var isScreenOff by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> isLifecyclePaused = false
                            Lifecycle.Event.ON_PAUSE,
                            Lifecycle.Event.ON_STOP -> isLifecyclePaused = true
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                DisposableEffect(context) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    isScreenOff = pm?.isInteractive == false
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            when (intent?.action) {
                                Intent.ACTION_SCREEN_OFF -> isScreenOff = true
                                Intent.ACTION_SCREEN_ON,
                                Intent.ACTION_USER_PRESENT -> isScreenOff = false
                            }
                        }
                    }
                    val filter = IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_USER_PRESENT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(receiver, filter)
                    }
                    onDispose {
                        runCatching { context.unregisterReceiver(receiver) }
                    }
                }

                val mascotScope = rememberCoroutineScope()
                val mascotController = remember(mascotScope) {
                    MascotAnimationController(
                        scope = mascotScope,
                        messageManager = container.focusMessageManager
                    )
                }
                val isAnimationPaused = isLifecyclePaused || isScreenOff
                var hasPlayedInitialLockPeek by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(mascotPreferences, isAnimationPaused) {
                    mascotController.updateConfig(
                        MascotControllerConfig(
                            activeMascot = mascotPreferences.selectedMascot,
                            focusMessagesEnabled = mascotPreferences.focusMessagesEnabled,
                            messageFrequency = mascotPreferences.messageFrequency,
                            reducedMotionEnabled = mascotPreferences.reducedMotionEnabled,
                            messages = mascotPreferences.messages,
                            isPaused = isAnimationPaused
                        )
                    )
                    if (!hasPlayedInitialLockPeek &&
                        !isAnimationPaused &&
                        mascotPreferences.selectedMascot == MascotType.RESOLVE_BUNNY &&
                        !mascotPreferences.reducedMotionEnabled
                    ) {
                        hasPlayedInitialLockPeek = true
                        mascotController.triggerPeekEntrance()
                    }
                }
                DisposableEffect(mascotController) {
                    mascotController.start()
                    onDispose { mascotController.stop() }
                }

                val controllerUiState by mascotController.uiState.collectAsStateWithLifecycle()

                var installedAllowedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
                val allowedPackages = activeSession?.allowedPackages.orEmpty()

                LaunchedEffect(allowedPackages) {
                    val all = container.installedAppsRepository.loadLaunchableApps()
                    val pkgSet = allowedPackages.toSet()
                    installedAllowedApps = all.filter { pkgSet.contains(it.packageName) }
                }

                // Track whether this Activity genuinely witnessed an active restricted lock session
                LaunchedEffect(activeSession?.state) {
                    val state = activeSession?.state
                    if (state == SessionState.LOCK_ENFORCEMENT_ACTIVE ||
                        state == SessionState.PHONE_CALL_ACTIVE
                    ) {
                        sawActiveLockEnforcement = true
                    }
                }

                // Exit immediately on terminal or missing session state without stalling on celebration
                // or uncovering MainActivity underneath LockScreenActivity's isolated task.
                LaunchedEffect(isEngineInitialized, activeSession?.state) {
                    if (!isEngineInitialized) return@LaunchedEffect
                    val state = activeSession?.state
                    if (state == null ||
                        state == SessionState.COMPLETED ||
                        state == SessionState.RECOVERED ||
                        state == SessionState.ENFORCEMENT_FAILED ||
                        state == SessionState.ENFORCEMENT_UNAVAILABLE
                    ) {
                        runCatching { engine.finalizeLockExit(this@LockScreenActivity) }
                        runCatching { moveTaskToBack(true) }
                        if (!isFinishing) {
                            finishAndRemoveTask()
                        }
                    }
                }

                val totalDurationMs = ((activeSession?.lockDurationMinutes ?: 1L).coerceAtLeast(1L)) * 60_000L
                val progress = if (totalDurationMs > 0L) {
                    (remainingMillis.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                ProvideSafeTextToolbar {
                    SignatureLockScreen(
                        remainingMillis = remainingMillis,
                        progressFraction = progress,
                        lockMode = activeSession?.mode ?: LockMode.INSTANT,
                        enforcementMode = activeSession?.enforcementMode ?: EnforcementMode.NONE,
                        phoneAccessEnabled = activeSession?.phoneAccessEnabled == true,
                        allowedApps = installedAllowedApps,
                        expectedRecoveryLength = activeSession?.recoveryCodeLength ?: recoveryLength,
                        loadAppIcon = { pkg -> container.installedAppsRepository.loadAppIcon(pkg) },
                        onLaunchPhone = { container.phoneAccessManager.launchSystemDialer() },
                        onLaunchAllowedApp = { pkg -> container.installedAppsRepository.launchAllowedApp(pkg) },
                        onSubmitRecoveryCode = { code -> engine.submitRecoveryCode(code) },
                        mascotType = mascotPreferences.selectedMascot,
                        mascotControllerState = controllerUiState,
                        reducedMotion = mascotPreferences.reducedMotionEnabled,
                        isAnimationPaused = isAnimationPaused,
                        isCompletionCelebration = isShowingCompletionCelebration,
                        isStrictMode = activeSession?.isStrictMode == true,
                        showFlipClockShortcut = activeSession?.showFlipClockShortcut == true,
                        onOpenFlipClock = {
                            startActivity(
                                FlipClockActivity.createIntent(
                                    context = this@LockScreenActivity,
                                    restrictedLock = true,
                                    initialPage = FlipClockPage.CLOCK
                                )
                            )
                        },
                        lockReminderEnabled = mascotPreferences.lockReminderEnabled,
                        lockReminderText = mascotPreferences.lockReminderText,
                        lockReminderStyle = mascotPreferences.lockReminderStyle,
                        allowLandscapeLayout = landscapeTiltMode,
                        onDismissCelebration = {
                            runCatching { moveTaskToBack(true) }
                            if (!isFinishing) {
                                finishAndRemoveTask()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyImmersiveSystemBars()
        reconcileOrientationMonitoring(transitionedToEnabled = false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersiveSystemBars()
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        applyImmersiveSystemBars()
        reconcileOrientationMonitoring(transitionedToEnabled = false)
        val app = application as? ResolveApplication ?: return
        app.container.foregroundGuard.clearBarrier()
        if (!isEngineInitialized || isConfirmingLock || isShowingCompletionCelebration) return
        val engine = app.container.sessionEngine
        val state = engine.activeSession.value?.state
        if (state == SessionState.LOCK_TRANSITION_PENDING || state == SessionState.LOCK_ENFORCEMENT_ACTIVE) {
            isConfirmingLock = true
            lifecycleScope.launch {
                runCatching { engine.confirmLockTaskEntered(this@LockScreenActivity) }
                isConfirmingLock = false
            }
        }
    }

    override fun onPause() {
        isActivityResumed = false
        stopOrientationListener()
        super.onPause()
    }

    override fun onDestroy() {
        stopOrientationListener()
        super.onDestroy()
    }

    private fun applyImmersiveSystemBars() {
        runCatching {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun reconcileOrientationMonitoring(transitionedToEnabled: Boolean) {
        if (!hasLoadedTiltPreference) return

        if (!isTiltModeEnabled) {
            stopOrientationListener()
            if (!isFinishing && requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            return
        }

        if (transitionedToEnabled && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (!isActivityResumed) {
            stopOrientationListener()
            return
        }

        if (orientationListener == null) {
            orientationListener = object : OrientationEventListener(
                this,
                SensorManager.SENSOR_DELAY_UI
            ) {
                override fun onOrientationChanged(orientation: Int) {
                    val candidate = resolveLockScreenCandidateOrientation(orientation) ?: return
                    if (candidate == requestedOrientation) {
                        pendingOrientationRunnable?.let { orientationHandler.removeCallbacks(it) }
                        pendingOrientationRunnable = null
                        pendingTargetOrientation = null
                        return
                    }
                    if (candidate != pendingTargetOrientation) {
                        pendingOrientationRunnable?.let { orientationHandler.removeCallbacks(it) }
                        pendingTargetOrientation = candidate
                        val task = Runnable {
                            if (!isFinishing && isTiltModeEnabled && requestedOrientation != candidate) {
                                requestedOrientation = candidate
                            }
                            pendingOrientationRunnable = null
                            pendingTargetOrientation = null
                        }
                        pendingOrientationRunnable = task
                        orientationHandler.postDelayed(task, 240L)
                    }
                }
            }
        }

        val listener = orientationListener
        if (listener != null && listener.canDetectOrientation()) {
            listener.enable()
        } else if (!isFinishing && requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_FULL_USER) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    private fun stopOrientationListener() {
        pendingOrientationRunnable?.let { orientationHandler.removeCallbacks(it) }
        pendingOrientationRunnable = null
        pendingTargetOrientation = null
        orientationListener?.disable()
    }
}

internal fun resolveLockScreenCandidateOrientation(sensorDegrees: Int): Int? {
    if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN || sensorDegrees < 0) {
        return null
    }
    val normalized = sensorDegrees % 360
    return when {
        normalized >= 325 || normalized <= 35 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        normalized in 50..130 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        normalized in 230..310 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignatureLockScreen(
    remainingMillis: Long,
    progressFraction: Float,
    lockMode: LockMode,
    enforcementMode: EnforcementMode,
    phoneAccessEnabled: Boolean,
    allowedApps: List<InstalledAppInfo>,
    expectedRecoveryLength: Int,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onLaunchPhone: () -> Unit,
    onLaunchAllowedApp: (String) -> Unit,
    onSubmitRecoveryCode: suspend (String) -> RecoveryVerificationResult,
    mascotType: MascotType = MascotType.BABY_CLOUD,
    mascotControllerState: MascotControllerUiState = MascotControllerUiState(),
    reducedMotion: Boolean = false,
    isAnimationPaused: Boolean = false,
    isCompletionCelebration: Boolean = false,
    isStrictMode: Boolean = false,
    showFlipClockShortcut: Boolean = false,
    onOpenFlipClock: () -> Unit = {},
    lockReminderEnabled: Boolean = false,
    lockReminderText: String = DefaultLockReminders.DEFAULT_TEXT,
    lockReminderStyle: LockReminderStyle = LockReminderStyle.SOFT_BOX,
    allowLandscapeLayout: Boolean = true,
    onDismissCelebration: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showAllowedAppsSheet by remember { mutableStateOf(false) }
    var showRecoverySheet by remember { mutableStateOf(false) }
    var recoveryFeedback by remember { mutableStateOf<RecoveryVerificationResult?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entered = true
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lock_entrance_scale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 1f else 0.85f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 320),
        label = "lock_entrance_alpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 450),
        label = "lock_progress"
    )

    val ambientTransition = rememberInfiniteTransition(label = "lock_ambient")
    val rawSpotlightScale by ambientTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spotlight"
    )
    val spotlightScale = if (reducedMotion || isAnimationPaused) 1f else rawSpotlightScale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.background,
                        palette.surfaceSecondary,
                        palette.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(
                if (isCompletionCelebration) {
                    Modifier.pointerInput(onDismissCelebration) {
                        detectTapGestures(onTap = { onDismissCelebration() })
                    }
                } else Modifier
            )
    ) {
        val isLandscape = maxWidth > maxHeight && allowLandscapeLayout
        val availableHeight = maxHeight
        val compactHeight = availableHeight < 650.dp
        val showReminderAnchor = !isCompletionCelebration &&
            lockReminderEnabled &&
            lockReminderText.isNotBlank()
        val mascotStageSize = when {
            isLandscape && availableHeight < 390.dp -> 110.dp
            isLandscape -> 134.dp
            compactHeight -> 122.dp
            else -> 184.dp
        }
        val spotlightFraction by animateOffsetAsState(
            targetValue = if (isLandscape) Offset(0.26f, 0.52f) else Offset(0.50f, 0.37f),
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 320, easing = EaseInOutSine),
            label = "lock_spotlight_center"
        )

        // Subtle radial ambient glow spotlight behind mascot (no encircling ring)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                size.width * spotlightFraction.x,
                size.height * spotlightFraction.y
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = if (isCompletionCelebration) 0.11f else 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.54f * spotlightScale
                ),
                radius = size.minDimension * 0.54f * spotlightScale,
                center = center
            )
        }

        val modeText = when (lockMode) {
            LockMode.INSTANT -> "INSTANT FOCUS"
            LockMode.DELAYED -> "DELAYED FOCUS"
            LockMode.SCHEDULED -> "SCHEDULED FOCUS"
        }
        val tierText = if (enforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK) {
            "OS Lock"
        } else {
            "Focus Guard"
        }

        AnimatedContent(
            targetState = isLandscape,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isLandscape) 22.dp else 20.dp,
                    vertical = if (isLandscape) 10.dp else 14.dp
                ),
            transitionSpec = {
                val enterTransition = if (reducedMotion) {
                    fadeIn(animationSpec = tween(durationMillis = 80))
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 260, easing = EaseInOutSine)) +
                        scaleIn(
                            initialScale = 0.965f,
                            animationSpec = tween(durationMillis = 260, easing = EaseInOutSine)
                        )
                }
                val exitTransition = if (reducedMotion) {
                    fadeOut(animationSpec = tween(durationMillis = 80))
                } else {
                    fadeOut(animationSpec = tween(durationMillis = 180, easing = EaseInOutSine))
                }
                enterTransition togetherWith exitTransition
            },
            label = "lock_screen_orientation_transition"
        ) { landscapeMode ->
            if (landscapeMode) {
                // Balanced Two-Column Landscape Layout:
                // Left Section: Vertically centered Mascot + Focus Countdown Timer
                // Right Section: 1. Reminder Quote -> 2. Phone -> 3. Allowed Apps & Emergency -> 4. Flip Clock
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = entranceAlpha
                            scaleX = entranceScale
                            scaleY = entranceScale
                        }
                        .testTag(
                            if (showReminderAnchor) {
                                "lock_screen_landscape_reminder_on"
                            } else {
                                "lock_screen_landscape_reminder_off"
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.surfaceSecondary.copy(alpha = 0.96f))
                            .border(1.dp, palette.cardBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                            .testTag("lock_screen_landscape_focus_guard_title"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingStatusDot(
                            color = if (isCompletionCelebration) palette.success else palette.accent,
                            size = 7.dp
                        )
                        Text(
                            text = if (isCompletionCelebration) {
                                "SESSION COMPLETED"
                            } else {
                                "$modeText  •  $tierText"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.0.sp
                            ),
                            color = palette.accentStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Section: Masked Mascot and Focus Timer, vertically centered with balanced spacing
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                            ) {
                                LockScreenMascotStage(
                                    activeMascot = mascotType,
                                    controllerState = mascotControllerState,
                                    reducedMotion = reducedMotion,
                                    isPaused = isAnimationPaused,
                                    mascotStageSize = mascotStageSize
                                )
                                LockScreenCountdownCard(
                                    remainingMillis = remainingMillis,
                                    animatedProgress = animatedProgress,
                                    isCompletionCelebration = isCompletionCelebration,
                                    compactHeight = true
                                )
                            }
                        }

                        // Right Section: Reminder Quote -> Phone -> Other Actions -> Flip Clock (when enabled)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                            ) {
                                if (showReminderAnchor) {
                                    LockScreenReminderAnchor(
                                        text = lockReminderText,
                                        style = lockReminderStyle,
                                        compactHeight = true
                                    )
                                }

                                if (!isCompletionCelebration) {
                                    LandscapeVerticalActionButtons(
                                        allowedAppsCount = allowedApps.size,
                                        phoneAccessEnabled = phoneAccessEnabled,
                                        isStrictMode = isStrictMode,
                                        showFlipClockShortcut = showFlipClockShortcut,
                                        onOpenAllowedApps = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showAllowedAppsSheet = true
                                        },
                                        onLaunchPhone = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLaunchPhone()
                                        },
                                        onOpenFlipClock = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onOpenFlipClock()
                                        },
                                        onOpenRecovery = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            recoveryFeedback = null
                                            showRecoverySheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
            // Portrait Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lock_screen_portrait_layout"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            alpha = entranceAlpha
                            scaleX = entranceScale
                            scaleY = entranceScale
                        }
                        .clip(CircleShape)
                        .background(palette.surfaceSecondary.copy(alpha = 0.96f))
                        .border(1.dp, palette.cardBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingStatusDot(
                        color = if (isCompletionCelebration) palette.success else palette.accent,
                        size = 7.dp
                    )
                    Text(
                        text = if (isCompletionCelebration) {
                            "SESSION COMPLETED"
                        } else {
                            "$modeText  •  $tierText"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = palette.accentStrong
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = entranceAlpha
                                scaleX = entranceScale
                                scaleY = entranceScale
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LockScreenMascotStage(
                            activeMascot = mascotType,
                            controllerState = mascotControllerState,
                            reducedMotion = reducedMotion,
                            isPaused = isAnimationPaused,
                            mascotStageSize = mascotStageSize
                        )

                        Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 18.dp))

                        LockScreenCountdownCard(
                            remainingMillis = remainingMillis,
                            animatedProgress = animatedProgress,
                            isCompletionCelebration = isCompletionCelebration,
                            compactHeight = compactHeight
                        )

                        if (showReminderAnchor) {
                            Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 14.dp))
                            LockScreenReminderAnchor(
                                text = lockReminderText,
                                style = lockReminderStyle,
                                compactHeight = compactHeight
                            )
                        }
                    }
                }

                // Floating Frosted Bottom Dock: Allowed Apps, Phone Call, Flip Clock (optional), Recovery (hidden in Strict Mode)
                val hasPortraitDockItems = allowedApps.isNotEmpty() ||
                    phoneAccessEnabled ||
                    showFlipClockShortcut ||
                    !isStrictMode
                if (!isCompletionCelebration && hasPortraitDockItems) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 6.dp)
                            .graphicsLayer {
                                alpha = entranceAlpha
                                scaleX = entranceScale
                                scaleY = entranceScale
                            }
                            .widthIn(max = 460.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(palette.surfaceSecondary.copy(alpha = 0.96f))
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(32.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allowedApps.isNotEmpty()) {
                            val appsInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAllowedAppsSheet = true
                                },
                                interactionSource = appsInteraction,
                                shape = CircleShape,
                                color = palette.slateTealContainer,
                                border = BorderStroke(1.dp, palette.slateTeal.copy(alpha = 0.32f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .pressScale(appsInteraction, 0.95f)
                                    .testTag("lock_screen_allowed_apps_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = ResolveIcons.Apps,
                                        contentDescription = "Allowed apps",
                                        tint = palette.slateTealStrong,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = "${allowedApps.size} Apps",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }

                        if (phoneAccessEnabled) {
                            val phoneInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLaunchPhone()
                                },
                                interactionSource = phoneInteraction,
                                shape = CircleShape,
                                color = palette.primaryButton,
                                border = BorderStroke(
                                    1.dp,
                                    palette.accentStrong.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .pressScale(phoneInteraction, 0.95f)
                                    .testTag("lock_screen_phone_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Call,
                                        contentDescription = "Open phone dialer",
                                        tint = palette.onPrimaryButton,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Phone",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = palette.onPrimaryButton
                                    )
                                }
                            }
                        }

                        if (showFlipClockShortcut) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenFlipClock()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.champagneContainer)
                                    .border(1.dp, palette.champagne.copy(alpha = 0.36f), CircleShape)
                                    .testTag("lock_screen_flip_clock_button")
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.FlipClock,
                                    contentDescription = "Open Flip Clock",
                                    tint = palette.champagneStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (!isStrictMode) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    recoveryFeedback = null
                                    showRecoverySheet = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.controlSurface)
                                    .border(1.dp, palette.controlBorder, CircleShape)
                                    .testTag("lock_screen_recovery_button")
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.Key,
                                    contentDescription = "Enter recovery code",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showAllowedAppsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllowedAppsSheet = false },
            containerColor = palette.surfaceSecondary
        ) {
            ProvideSafeTextToolbar {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Allowed Applications",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Only these selected apps are accessible while Focus Lock is active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (allowedApps.isEmpty()) {
                            item {
                                Text(
                                    text = "No extra applications are allowlisted for this focus session.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(allowedApps, key = { it.packageName }) { app ->
                                AllowedAppLaunchRow(
                                    app = app,
                                    loadAppIcon = loadAppIcon,
                                    onClick = {
                                        showAllowedAppsSheet = false
                                        onLaunchAllowedApp(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecoverySheet) {
        RecoveryCodeVerifySheet(
            expectedLength = expectedRecoveryLength,
            verificationFeedback = recoveryFeedback,
            isVerifying = isVerifying,
            onDismiss = { showRecoverySheet = false },
            onSubmitCode = { code ->
                scope.launch {
                    isVerifying = true
                    val res = onSubmitRecoveryCode(code)
                    isVerifying = false
                    if (res is RecoveryVerificationResult.Verified) {
                        showRecoverySheet = false
                    } else {
                        recoveryFeedback = res
                    }
                }
            }
        )
    }
}

@Composable
private fun AllowedAppLaunchRow(
    app: InstalledAppInfo,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        icon = loadAppIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.cardTop)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Icon(
                imageVector = ResolveIcons.Apps,
                contentDescription = null,
                tint = palette.accentStrong,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatCountdownHms(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun LockScreenReminderAnchor(
    text: String,
    style: LockReminderStyle,
    compactHeight: Boolean
) {
    val palette = LocalResolveExtendedPalette.current
    when (style) {
        LockReminderStyle.SOFT_BOX -> {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceElevated.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        palette.cardBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (compactHeight) 8.dp else 10.dp
                    )
                    .testTag("lock_screen_reminder_box"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    ),
                    color = palette.textPrimary.copy(alpha = 0.94f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("lock_screen_reminder_text")
                )
            }
        }

        LockReminderStyle.PLAIN_TEXT -> {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .testTag("lock_screen_reminder_plain"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    ),
                    color = palette.accentStrong.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("lock_screen_reminder_text")
                )
            }
        }
    }
}

@Composable
private fun LockScreenCountdownCard(
    remainingMillis: Long,
    animatedProgress: Float,
    isCompletionCelebration: Boolean,
    compactHeight: Boolean
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .widthIn(max = 290.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceElevated,
                        palette.cardBottom
                    )
                )
            )
            .border(
                1.dp,
                palette.cardBorder,
                RoundedCornerShape(22.dp)
            )
            .padding(
                horizontal = 20.dp,
                vertical = if (compactHeight) 11.dp else 14.dp
            )
            .testTag("lock_screen_countdown_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = palette.accentStrong,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (isCompletionCelebration) "FOCUS COMPLETE" else "REMAINING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp
                ),
                color = palette.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formatCountdownHms(remainingMillis),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = if (compactHeight) 32.sp else 38.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                fontFeatureSettings = "tnum"
            ),
            color = palette.textPrimary,
            modifier = Modifier.testTag("lock_screen_countdown")
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            color = palette.accentStrong,
            trackColor = palette.divider,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(4.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = if (isCompletionCelebration) {
                "Tap anywhere to finish"
            } else {
                "Stay present • Unlocks at zero"
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp
            ),
            color = palette.textSecondary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LandscapeVerticalActionButtons(
    allowedAppsCount: Int,
    phoneAccessEnabled: Boolean,
    isStrictMode: Boolean = false,
    showFlipClockShortcut: Boolean = false,
    onOpenAllowedApps: () -> Unit,
    onLaunchPhone: () -> Unit,
    onOpenFlipClock: () -> Unit = {},
    onOpenRecovery: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val hasAnyButton = phoneAccessEnabled || allowedAppsCount > 0 || !isStrictMode || showFlipClockShortcut
    if (!hasAnyButton) return

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 264.dp)
            .fillMaxWidth(0.90f)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceSecondary.copy(alpha = 0.96f))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("lock_screen_landscape_action_stack"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Phone Icon & Call Action
        if (phoneAccessEnabled) {
            val phoneInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onLaunchPhone,
                interactionSource = phoneInteraction,
                shape = RoundedCornerShape(13.dp),
                color = palette.primaryButton,
                border = BorderStroke(1.dp, palette.accentStrong.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressScale(phoneInteraction, 0.96f)
                    .testTag("lock_screen_phone_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Call,
                        contentDescription = "Phone Call",
                        tint = palette.onPrimaryButton,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Phone Call",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.onPrimaryButton,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Other Action Icons: Allowed Apps & Emergency
        if (allowedAppsCount > 0) {
            val appsInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onOpenAllowedApps,
                interactionSource = appsInteraction,
                shape = RoundedCornerShape(13.dp),
                color = palette.slateTealContainer,
                border = BorderStroke(1.dp, palette.slateTeal.copy(alpha = 0.30f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressScale(appsInteraction, 0.96f)
                    .testTag("lock_screen_allowed_apps_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = ResolveIcons.Apps,
                        contentDescription = "Allowed Apps",
                        tint = palette.slateTealStrong,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Allowed Apps ($allowedAppsCount)",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!isStrictMode) {
            val recoveryInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onOpenRecovery,
                interactionSource = recoveryInteraction,
                shape = RoundedCornerShape(13.dp),
                color = palette.controlSurface,
                border = BorderStroke(1.dp, palette.controlBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressScale(recoveryInteraction, 0.96f)
                    .testTag("lock_screen_recovery_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = ResolveIcons.Key,
                        contentDescription = "Emergency",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Emergency",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Flip Clock Icon (only when Flip Clock is enabled)
        if (showFlipClockShortcut) {
            val flipInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onOpenFlipClock,
                interactionSource = flipInteraction,
                shape = RoundedCornerShape(13.dp),
                color = palette.champagneContainer,
                border = BorderStroke(1.dp, palette.champagne.copy(alpha = 0.36f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressScale(flipInteraction, 0.96f)
                    .testTag("lock_screen_flip_clock_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = ResolveIcons.FlipClock,
                        contentDescription = "Open Flip Clock",
                        tint = palette.champagneStrong,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Flip Clock",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.champagneStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

