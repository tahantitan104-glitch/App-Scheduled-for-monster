package com.example.presentation.lockscreen

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ResolveApplication
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.designsystem.ResolveTheme
import com.example.domain.model.EnforcementMode
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockMode
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.SessionState
import com.example.presentation.components.BabyCloudMascot
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.RecoveryCodeVerifySheet
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.pressScale
import com.example.session.LockExitSignal
import kotlinx.coroutines.launch

class LockScreenActivity : ComponentActivity() {

    private var isEngineInitialized by mutableStateOf(false)
    private var isConfirmingLock = false

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
        runCatching {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

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
            if (!confirmed && !isFinishing) {
                finish()
            }
        }

        lifecycleScope.launch {
            engine.lockExitSignals.collect { signal ->
                when (signal) {
                    LockExitSignal.ExitLockTaskAndFinish -> {
                        runCatching { engine.finalizeLockExit(this@LockScreenActivity) }
                        finish()
                    }
                }
            }
        }

        setContent {
            ResolveTheme {
                // Disable Back exit on Lock Screen
                BackHandler(enabled = true) {}

                val activeSession by engine.activeSession.collectAsStateWithLifecycle()
                val remainingMillis by engine.remainingMillis.collectAsStateWithLifecycle()
                val recoveryLength by container.recoveryCodeManager.recoveryCodeLengthFlow
                    .collectAsStateWithLifecycle(initialValue = 16)

                var installedAllowedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
                val allowedPackages = activeSession?.allowedPackages.orEmpty()

                LaunchedEffect(allowedPackages) {
                    val all = container.installedAppsRepository.loadLaunchableApps()
                    val pkgSet = allowedPackages.toSet()
                    installedAllowedApps = all.filter { pkgSet.contains(it.packageName) }
                }

                // Only exit cleanly after engine initialization has completed
                LaunchedEffect(isEngineInitialized, activeSession?.state) {
                    if (!isEngineInitialized) return@LaunchedEffect
                    val state = activeSession?.state
                    if (state == null ||
                        state == SessionState.COMPLETED ||
                        state == SessionState.RECOVERED ||
                        state == SessionState.ENFORCEMENT_FAILED ||
                        state == SessionState.ENFORCEMENT_UNAVAILABLE
                    ) {
                        engine.finalizeLockExit(this@LockScreenActivity)
                        finish()
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
                        onSubmitRecoveryCode = { code -> engine.submitRecoveryCode(code) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as? ResolveApplication ?: return
        app.container.foregroundGuard.clearBarrier()
        if (!isEngineInitialized || isConfirmingLock) return
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
    onSubmitRecoveryCode: suspend (String) -> RecoveryVerificationResult
) {
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
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lock_entrance_scale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0.85f,
        animationSpec = tween(durationMillis = 320),
        label = "lock_entrance_alpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 450),
        label = "lock_progress"
    )

    val ambientTransition = rememberInfiniteTransition(label = "lock_ambient")
    val spotlightScale by ambientTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spotlight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ResolveColors.ForestDeep,
                        ResolveColors.ForestSecondary,
                        ResolveColors.ForestDeep
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Subtle radial ambient glow canvas behind mascot
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.39f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ResolveColors.MintAccent.copy(alpha = 0.14f),
                        ResolveColors.MintSoftContainer.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.56f * spotlightScale
                ),
                radius = size.minDimension * 0.56f * spotlightScale,
                center = center
            )
        }

        // Top subtle status pill
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
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .graphicsLayer {
                    alpha = entranceAlpha
                    scaleX = entranceScale
                    scaleY = entranceScale
                }
                .clip(CircleShape)
                .background(ResolveColors.ForestGlass)
                .border(1.dp, ResolveColors.ForestBorderGlow.copy(alpha = 0.7f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingStatusDot(color = ResolveColors.MintAccent, size = 7.dp)
            Text(
                text = "$modeText  •  $tierText",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                color = ResolveColors.MintBright
            )
        }

        // Centerpiece: Baby Cloud inside animated progress arc + Elevated Glass Countdown Card
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(bottom = 52.dp)
                .graphicsLayer {
                    alpha = entranceAlpha
                    scaleX = entranceScale
                    scaleY = entranceScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sleek outer progress arc around Baby Cloud
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.5.dp.toPx()
                    drawArc(
                        color = ResolveColors.ForestOutline.copy(alpha = 0.55f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ResolveColors.SageAccent,
                                ResolveColors.MintAccent,
                                ResolveColors.MintBright,
                                ResolveColors.MintAccent
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                BabyCloudMascot(size = 198.dp, showFocusAura = true)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ResolveColors.ForestSurfaceElevated.copy(alpha = 0.82f),
                                ResolveColors.ForestSurface.copy(alpha = 0.90f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        ResolveColors.MintAccent.copy(alpha = 0.28f),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = ResolveColors.MintAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "TIME REMAINING",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = ResolveColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = formatCountdownHms(remainingMillis),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ResolveColors.TextPrimary,
                    modifier = Modifier.testTag("lock_screen_countdown")
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = ResolveColors.MintAccent,
                    trackColor = ResolveColors.ForestDeep,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Stay present. Device unlocks automatically when the timer reaches zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResolveColors.TextSecondary,
                    maxLines = 2
                )
            }
        }

        // Floating Frosted Bottom Dock: Allowed Apps, Phone Call, Discreet Recovery
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .graphicsLayer {
                    alpha = entranceAlpha
                    scaleX = entranceScale
                    scaleY = entranceScale
                }
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(ResolveColors.ForestGlass)
                .border(1.dp, ResolveColors.ForestBorderGlow.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
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
                        color = ResolveColors.ForestSurfaceElevated,
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
                                tint = ResolveColors.MintAccent,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "${allowedApps.size} Apps",
                                style = MaterialTheme.typography.labelLarge,
                                color = ResolveColors.TextPrimary
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
                        color = ResolveColors.MintSoftContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            ResolveColors.MintAccent.copy(alpha = 0.45f)
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
                                tint = ResolveColors.MintBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Phone",
                                style = MaterialTheme.typography.labelLarge,
                                color = ResolveColors.MintBright
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        recoveryFeedback = null
                        showRecoverySheet = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ResolveColors.ForestSurfaceElevated.copy(alpha = 0.85f))
                        .testTag("lock_screen_recovery_button")
                ) {
                    Icon(
                        imageVector = ResolveIcons.Key,
                        contentDescription = "Enter recovery code",
                        tint = ResolveColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
    }

    if (showAllowedAppsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllowedAppsSheet = false },
            containerColor = ResolveColors.ForestSecondary
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
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "Only these selected apps are accessible while Focus Lock is active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResolveColors.TextSecondary
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        icon = loadAppIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ResolveColors.ForestSurface)
            .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
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
                tint = ResolveColors.MintAccent,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.titleMedium,
            color = ResolveColors.TextPrimary,
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
