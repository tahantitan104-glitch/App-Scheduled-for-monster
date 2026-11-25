package com.example.presentation.applock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ResolveApplication
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveTheme
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.presentation.components.MascotRenderer
import com.example.presentation.components.ResolveIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated mascot-centered full-screen App Lock authentication Activity.
 * Displays ONLY the single active mascot (Baby Cloud OR RESOLVE Bunny) centered above
 * the active authentication input (Pattern, 4-Digit PIN, 6-Digit PIN, or 6-Cell Sequence).
 */
class AppLockAuthActivity : ComponentActivity() {

    private var targetPackageState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        targetPackageState = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()

        val app = application as? ResolveApplication ?: run {
            finish()
            return
        }
        val container = app.container

        setContent {
            ResolveTheme {
                BackHandler {
                    navigateHomeAndFinish()
                }

                val mascotPrefs by container.mascotSelectionManager.preferencesFlow
                    .collectAsStateWithLifecycle(initialValue = MascotPreferences())
                val authMethod by container.appLockCredentialManager.authMethodFlow
                    .collectAsStateWithLifecycle(initialValue = AppLockAuthMethod.PIN)
                val pinLength by container.appLockCredentialManager.pinLengthFlow
                    .collectAsStateWithLifecycle(initialValue = AppLockPinLength.FOUR)

                val lifecycleOwner = LocalLifecycleOwner.current
                var isPaused by remember { mutableStateOf(false) }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> isPaused = false
                            Lifecycle.Event.ON_PAUSE,
                            Lifecycle.Event.ON_STOP -> isPaused = true
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                var appLabel by remember(targetPackageState) { mutableStateOf(targetPackageState) }
                var appIcon by remember(targetPackageState) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(targetPackageState) {
                    if (targetPackageState.isNotBlank()) {
                        val pm = packageManager
                        appLabel = runCatching {
                            val info = pm.getApplicationInfo(targetPackageState, 0)
                            pm.getApplicationLabel(info).toString()
                        }.getOrElse { targetPackageState.substringAfterLast('.') }
                        appIcon = container.installedAppsRepository.loadAppIcon(targetPackageState)
                    }
                }

                AppLockAuthScreen(
                    targetAppName = appLabel.ifBlank { "Protected App" },
                    targetAppIcon = appIcon,
                    mascotType = mascotPrefs.selectedMascot,
                    authMethod = authMethod,
                    pinLength = pinLength,
                    reducedMotion = mascotPrefs.reducedMotionEnabled,
                    isPaused = isPaused,
                    onVerifyCredential = { raw -> container.appLockCredentialManager.verifyCredential(raw) },
                    onUnlocked = {
                        val unlockedPkg = targetPackageState
                        if (unlockedPkg.isNotBlank()) {
                            container.appLockSessionGuard.grantTemporaryUnlock(unlockedPkg)
                            val launchIntent = packageManager.getLaunchIntentForPackage(unlockedPkg)?.apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                )
                            }
                            if (launchIntent != null) {
                                runCatching { startActivity(launchIntent) }
                            } else {
                                runCatching { moveTaskToBack(true) }
                            }
                        } else {
                            runCatching { moveTaskToBack(true) }
                        }
                        safeFinishAndRemoveTask()
                    },
                    onOpenEmergencyPhone = {
                        container.phoneAccessManager.launchSystemDialer()
                    },
                    onGoHome = {
                        navigateHomeAndFinish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        if (newPkg.isNotBlank()) {
            targetPackageState = newPkg
        }
    }

    private fun navigateHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(homeIntent) }
        safeFinishAndRemoveTask()
    }

    private fun safeFinishAndRemoveTask() {
        if (isFinishing) return
        runCatching { finishAndRemoveTask() }.onFailure { runCatching { finish() } }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}

@Composable
internal fun AppLockAuthScreen(
    targetAppName: String,
    targetAppIcon: ImageBitmap?,
    mascotType: MascotType,
    authMethod: AppLockAuthMethod,
    pinLength: AppLockPinLength,
    reducedMotion: Boolean,
    isPaused: Boolean,
    onVerifyCredential: suspend (String) -> AppLockVerificationResult,
    onUnlocked: () -> Unit,
    onOpenEmergencyPhone: () -> Unit,
    onGoHome: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableStateOf(0) }
    var throttleSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(throttleSeconds) {
        if (throttleSeconds > 0L) {
            delay(1000L)
            throttleSeconds = (throttleSeconds - 1L).coerceAtLeast(0L)
        }
    }

    val promptText = when (authMethod) {
        AppLockAuthMethod.PATTERN -> "Draw pattern to open $targetAppName"
        AppLockAuthMethod.PIN -> "Enter ${pinLength.digits}-digit PIN for $targetAppName"
        AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "Tap your secret sequence for $targetAppName"
    }
    val effectiveError = if (throttleSeconds > 0L) {
        "Locked temporarily • Retry in ${throttleSeconds}s"
    } else {
        errorMsg
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("applock_auth_screen")
    ) {
        val isLandscape = maxWidth > maxHeight
        val availableHeight = maxHeight
        val compact = availableHeight < 640.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * if (isLandscape) 0.5f else 0.30f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accentSecondary.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = center
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppLockTargetHeader(appName = targetAppName, appIcon = targetAppIcon)
                    MascotRenderer(
                        mascot = mascotType,
                        size = if (availableHeight < 420.dp) 118.dp else 152.dp,
                        showFocusAura = true,
                        animationState = MascotAnimationState.IDLE,
                        reducedMotion = reducedMotion,
                        isPaused = isPaused
                    )
                    AppLockBottomActions(
                        onOpenEmergencyPhone = onOpenEmergencyPhone,
                        onGoHome = onGoHome
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    AppLockAuthChallengePanel(
                        method = authMethod,
                        pinLength = pinLength,
                        isSetupMode = false,
                        promptMessage = promptText,
                        errorMessage = effectiveError,
                        compactMode = true,
                        resetKey = resetKey,
                        onCredentialSubmitted = { raw ->
                            if (throttleSeconds > 0L) return@AppLockAuthChallengePanel
                            scope.launch {
                                when (val res = onVerifyCredential(raw)) {
                                    AppLockVerificationResult.Verified -> onUnlocked()
                                    AppLockVerificationResult.NotConfigured -> onUnlocked()
                                    is AppLockVerificationResult.InvalidCredential -> {
                                        errorMsg = "Incorrect • ${res.remainingAttemptsBeforeCooldown} tries before cooldown"
                                        resetKey++
                                    }
                                    is AppLockVerificationResult.Throttled -> {
                                        throttleSeconds = res.retryAfterSeconds
                                        errorMsg = null
                                        resetKey++
                                    }
                                }
                            }
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AppLockTargetHeader(appName = targetAppName, appIcon = targetAppIcon)

                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
                ) {
                    MascotRenderer(
                        mascot = mascotType,
                        size = if (compact) 124.dp else 160.dp,
                        showFocusAura = true,
                        animationState = MascotAnimationState.IDLE,
                        reducedMotion = reducedMotion,
                        isPaused = isPaused
                    )

                    AppLockAuthChallengePanel(
                        method = authMethod,
                        pinLength = pinLength,
                        isSetupMode = false,
                        promptMessage = promptText,
                        errorMessage = effectiveError,
                        compactMode = compact,
                        resetKey = resetKey,
                        onCredentialSubmitted = { raw ->
                            if (throttleSeconds > 0L) return@AppLockAuthChallengePanel
                            scope.launch {
                                when (val res = onVerifyCredential(raw)) {
                                    AppLockVerificationResult.Verified -> onUnlocked()
                                    AppLockVerificationResult.NotConfigured -> onUnlocked()
                                    is AppLockVerificationResult.InvalidCredential -> {
                                        errorMsg = "Incorrect • ${res.remainingAttemptsBeforeCooldown} tries before cooldown"
                                        resetKey++
                                    }
                                    is AppLockVerificationResult.Throttled -> {
                                        throttleSeconds = res.retryAfterSeconds
                                        errorMsg = null
                                        resetKey++
                                    }
                                }
                            }
                        }
                    )
                }

                AppLockBottomActions(
                    onOpenEmergencyPhone = onOpenEmergencyPhone,
                    onGoHome = onGoHome
                )
            }
        }
    }
}

@Composable
private fun AppLockTargetHeader(
    appName: String,
    appIcon: ImageBitmap?
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.surfaceSecondary)
            .border(1.dp, palette.cardBorder, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("applock_auth_target_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "$appName icon",
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "RESOLVE APP LOCK • $appName",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.9.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.accentStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppLockBottomActions(
    onOpenEmergencyPhone: () -> Unit,
    onGoHome: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onOpenEmergencyPhone,
            shape = CircleShape,
            modifier = Modifier.testTag("applock_emergency_call_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Call,
                contentDescription = "Emergency Phone",
                tint = palette.accentStrong,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Phone", color = palette.accentStrong)
        }

        OutlinedButton(
            onClick = onGoHome,
            shape = CircleShape,
            modifier = Modifier.testTag("applock_go_home_button")
        ) {
            Icon(
                imageVector = ResolveIcons.Shield,
                contentDescription = "Leave locked app",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Home", color = palette.textSecondary)
        }
    }
}
