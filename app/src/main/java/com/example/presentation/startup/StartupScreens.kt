package com.example.presentation.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.PlusJakartaSansFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.FocusSymbolMark
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.TactilePrimaryButton
import com.example.presentation.components.resolveGlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Minimal Brand Intro (0.5–0.8s, ~600ms total):
 * Stage 1: Clean Deep Forest Green (#10251F) canvas seamlessly matching Theme.Resolve.Starting
 * Stage 2: Smooth pure-flat Focus Ring reveal + crisp Center Dot entrance
 * Stage 3: Brief "RESOLVE" wordmark fade-in
 * Stage 4: Fast, non-blocking handoff to Home / First-Time Welcome / Required Setup
 */
@Composable
fun BrandIntroScreen(
    reducedMotion: Boolean = false,
    onIntroCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val ringAlpha = remember { Animatable(if (reducedMotion) 1f else 0.25f) }
    val dotAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val brandAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val latestOnIntroCompleted by rememberUpdatedState(onIntroCompleted)

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            delay(220L)
        } else {
            // Subtle Focus Ring sweep + central dot reveal + concise wordmark fade (~600ms total)
            launch {
                ringAlpha.animateTo(1f, tween(durationMillis = 200, easing = EaseOutCubic))
            }
            ringProgress.animateTo(1f, tween(durationMillis = 280, easing = EaseOutCubic))

            launch {
                dotAlpha.animateTo(1f, tween(durationMillis = 150, easing = EaseOutCubic))
            }
            delay(60L)
            brandAlpha.animateTo(1f, tween(durationMillis = 180, easing = EaseOutCubic))
            delay(80L)
        }
        latestOnIntroCompleted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResolveColors.ForestBrandDeep)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("brand_intro_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            FocusSymbolMark(
                size = 92.dp,
                ringProgress = ringProgress.value,
                ringAlpha = ringAlpha.value,
                dotAlpha = dotAlpha.value,
                glowAlpha = 0f,
                testTagName = "brand_intro_focus_symbol"
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "RESOLVE",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 5.sp
                ),
                color = ResolveColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(brandAlpha.value)
                    .testTag("brand_intro_wordmark")
            )
        }
    }
}

/**
 * First-Launch Only Welcome Experience:
 * Shown once on initial install (never repeated after onboarding completes or on restarts).
 */
@Composable
fun FirstTimeWelcomeScreen(
    reducedMotion: Boolean = false,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            contentAlpha.animateTo(1f, tween(durationMillis = 360, easing = EaseOutCubic))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ResolveColors.ForestBrandDeep,
                        ResolveColors.ForestDeep
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("first_time_welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                FocusSymbolMark(
                    size = 96.dp,
                    testTagName = "welcome_focus_symbol"
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "RESOLVE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.5.sp
                    ),
                    color = ResolveColors.FocusMint
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Welcome to RESOLVE.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ResolveColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("welcome_title")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Build focus. Stay consistent. Take control.",
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = ResolveColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("welcome_subtitle")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TactilePrimaryButton(
                    onClick = onContinue,
                    modifier = Modifier.testTag("welcome_get_started_button")
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * First-Run Required Setup Flow:
 * Guides user through Recovery Code + Enforcement Permission cleanly without marking onboarding
 * complete prematurely if required steps are incomplete.
 */
@Composable
fun RequiredSetupScreen(
    capabilities: SystemCapabilityStatus,
    hasRecoveryCode: Boolean,
    generateRandomCode: (Int) -> String,
    onSaveRecoveryCode: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCompleteSetup: () -> Unit,
    onDeferSetupToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRecoverySetupDialog by remember { mutableStateOf(false) }
    val setupReady = hasRecoveryCode && capabilities.canEnforceRealLock

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ResolveColors.ForestBrandDeep,
                        ResolveColors.ForestDeep
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("required_setup_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FocusSymbolMark(size = 44.dp, testTagName = "setup_focus_symbol")
                Column {
                    Text(
                        text = "Essential Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "Prepare RESOLVE for reliable focus sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResolveColors.TextSecondary
                    )
                }
            }

            SetupRequirementCard(
                stepNumber = "1",
                title = "Recovery Code",
                description = "Create a 16 or 32-digit emergency code so you can always exit an active lock if needed.",
                icon = Icons.Outlined.Lock,
                isReady = hasRecoveryCode,
                actionLabel = if (hasRecoveryCode) "Configured" else "Set Recovery Code",
                onAction = { showRecoverySetupDialog = true },
                actionTestTag = "setup_recovery_button"
            )

            SetupRequirementCard(
                stepNumber = "2",
                title = "Focus Guard Permission",
                description = "Enable Resolve Focus Guard in Android Accessibility Settings (or Device Owner) so lock rules stay enforced.",
                icon = ResolveIcons.Shield,
                isReady = capabilities.canEnforceRealLock,
                actionLabel = if (capabilities.canEnforceRealLock) "Active" else "Enable Focus Guard",
                onAction = onOpenAccessibilitySettings,
                actionTestTag = "setup_guard_button"
            )

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCompleteSetup,
                    enabled = setupReady,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.FocusMint,
                        contentColor = ResolveColors.ForestDeep,
                        disabledContainerColor = ResolveColors.ForestSurfaceElevated,
                        disabledContentColor = ResolveColors.TextDisabled
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("required_setup_complete_button")
                ) {
                    Text(
                        text = if (setupReady) "Complete Setup & Continue" else "Complete Both Steps to Finish",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(
                    onClick = onDeferSetupToHome,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("required_setup_defer_button")
                ) {
                    Text(
                        text = "Continue to Home (Finish Setup Later)",
                        style = MaterialTheme.typography.labelLarge,
                        color = ResolveColors.TextSecondary
                    )
                }
            }
        }
    }

    if (showRecoverySetupDialog) {
        RecoveryCodeSetupDialog(
            generateRandomCode = generateRandomCode,
            onDismiss = { showRecoverySetupDialog = false },
            onSaveCode = { code ->
                onSaveRecoveryCode(code)
                showRecoverySetupDialog = false
            }
        )
    }
}

@Composable
private fun SetupRequirementCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isReady: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    actionTestTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 22.dp,
                borderColor = if (isReady) {
                    ResolveColors.FocusMint.copy(alpha = 0.50f)
                } else {
                    ResolveColors.ForestOutline
                }
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) ResolveColors.MintSoftContainer else ResolveColors.ForestSurfaceElevated
                    )
                    .border(
                        1.dp,
                        if (isReady) ResolveColors.FocusMint.copy(alpha = 0.45f) else ResolveColors.ForestOutline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isReady) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "$title complete",
                        tint = ResolveColors.FocusMint,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelLarge,
                        color = ResolveColors.TextPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isReady) ResolveColors.FocusMint else ResolveColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ResolveColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ResolveColors.TextSecondary
                )
            }
        }

        if (!isReady) {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(actionTestTag)
            ) {
                Text(text = actionLabel, color = ResolveColors.FocusMint)
            }
        } else {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = ResolveColors.FocusMint,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
