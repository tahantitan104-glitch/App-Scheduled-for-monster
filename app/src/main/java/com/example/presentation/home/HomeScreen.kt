package com.example.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.ActiveSession
import com.example.domain.model.EnforcementMode
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotType
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.HomeOptionalSyncBanner
import com.example.presentation.components.MascotRenderer
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.SyncedAvatarBadge
import com.example.presentation.components.TactilePrimaryButton
import com.example.presentation.components.pressScale
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.lockscreen.formatCountdownHms
import com.example.sync.AccountSyncUiState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    activeSession: ActiveSession?,
    remainingMillis: Long,
    capabilities: SystemCapabilityStatus,
    onOpenInstantSetup: () -> Unit,
    onOpenDelayedSetup: () -> Unit,
    onOpenScheduleSetup: () -> Unit,
    onOpenDeviceOwnerGuide: () -> Unit,
    onReturnToLockScreen: () -> Unit,
    onDismissTerminalSession: () -> Unit,
    modifier: Modifier = Modifier,
    mascotType: MascotType = MascotType.BABY_CLOUD,
    bunnyPeekEntranceSeen: Boolean = true,
    onBunnyPeekEntranceSeen: () -> Unit = {},
    reducedMotion: Boolean = false,
    accountSyncState: AccountSyncUiState = AccountSyncUiState(),
    syncPromoDismissed: Boolean = true,
    onConnectGoogleAccount: () -> Unit = {},
    onDismissSyncPromo: () -> Unit = {},
    onOpenAccountSettings: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResolveColors.ForestDeep)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ResolveColors.MintAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.5f, 0f)
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .testTag("home_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var homeMascotState by remember(mascotType) { mutableStateOf(MascotAnimationState.IDLE) }
                        var homePeekProgress by remember(mascotType) { mutableStateOf(1f) }
                        var replayRequestCount by remember { mutableStateOf(0) }

                        LaunchedEffect(mascotType, bunnyPeekEntranceSeen, reducedMotion, replayRequestCount) {
                            val shouldPlayPeek = mascotType == MascotType.RESOLVE_BUNNY &&
                                !reducedMotion &&
                                (!bunnyPeekEntranceSeen || replayRequestCount > 0)
                            if (shouldPlayPeek) {
                                homeMascotState = MascotAnimationState.PEEK_ENTRANCE
                                homePeekProgress = 0f
                                val steps = 70
                                for (i in 1..steps) {
                                    delay(34L)
                                    homePeekProgress = i / steps.toFloat()
                                }
                                homeMascotState = MascotAnimationState.IDLE
                                homePeekProgress = 1f
                                if (!bunnyPeekEntranceSeen) {
                                    onBunnyPeekEntranceSeen()
                                }
                            } else {
                                homeMascotState = MascotAnimationState.IDLE
                                homePeekProgress = 1f
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ResolveColors.MintSoftContainer.copy(alpha = 0.7f))
                                .border(
                                    1.dp,
                                    ResolveColors.MintAccent.copy(alpha = 0.25f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (mascotType == MascotType.RESOLVE_BUNNY) {
                                        replayRequestCount++
                                    } else if (!reducedMotion) {
                                        homeMascotState = if (homeMascotState == MascotAnimationState.HEAD_EAR_ATTENTION) {
                                            MascotAnimationState.IDLE
                                        } else {
                                            MascotAnimationState.HEAD_EAR_ATTENTION
                                        }
                                    }
                                }
                                .semantics {
                                    contentDescription = "${mascotType.displayName} companion badge"
                                }
                                .testTag("home_mascot_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            MascotRenderer(
                                mascot = mascotType,
                                size = 46.dp,
                                showFocusAura = false,
                                animationState = homeMascotState,
                                peekProgress = homePeekProgress,
                                reducedMotion = reducedMotion
                            )
                        }
                        Column {
                            Text(
                                text = "RESOLVE",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ResolveColors.TextPrimary
                            )
                            Text(
                                text = "Device Focus Control",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResolveColors.TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (accountSyncState.isSignedIn) {
                            SyncedAvatarBadge(
                                displayName = accountSyncState.displayName,
                                avatarBase64 = accountSyncState.avatarBase64,
                                size = 34.dp,
                                onClick = onOpenAccountSettings,
                                modifier = Modifier.testTag("home_profile_avatar_pill")
                            )
                        }
                        EnforcementStatusPill(
                            capabilities = capabilities,
                            onClick = onOpenDeviceOwnerGuide
                        )
                    }
                }
            }

            // Concise active or diagnostic session banner with smooth transition
            item {
                AnimatedVisibility(
                    visible = activeSession != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (activeSession != null) {
                        ActiveSessionBanner(
                            session = activeSession,
                            remainingMillis = remainingMillis,
                            onReturnToLockScreen = onReturnToLockScreen,
                            onDismiss = onDismissTerminalSession
                        )
                    }
                }
            }

            // Helpful permission readiness prompt when no enforcement tier is active yet
            item {
                AnimatedVisibility(
                    visible = !capabilities.canEnforceRealLock && activeSession == null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    SetupReadinessCallout(onClick = onOpenDeviceOwnerGuide)
                }
            }

            // Optional multi-device sync prompt (hidden automatically once signed in or dismissed)
            item {
                AnimatedVisibility(
                    visible = !accountSyncState.isSignedIn && !syncPromoDismissed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    HomeOptionalSyncBanner(
                        isAuthBusy = accountSyncState.isAuthBusy,
                        onConnectGoogle = onConnectGoogleAccount,
                        onDismiss = onDismissSyncPromo
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CHOOSE FOCUS MODE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
                    color = ResolveColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FeatureTipNote(
                    text = "Tip: Tap your companion badge above to preview.",
                    icon = Icons.Outlined.Info
                )
            }

            item {
                ModeSelectionCard(
                    title = "Instant Lock",
                    description = "Restrict the phone immediately for a chosen duration.",
                    icon = Icons.Outlined.Lock,
                    accentBadge = "Immediate",
                    onClick = onOpenInstantSetup,
                    testTag = "mode_card_instant"
                )
            }

            item {
                ModeSelectionCard(
                    title = "Delayed Lock",
                    description = "Use the phone freely first, then automatically enter Lock Mode.",
                    icon = ResolveIcons.HourglassTop,
                    accentBadge = "2-Stage",
                    onClick = onOpenDelayedSetup,
                    testTag = "mode_card_delayed"
                )
            }

            item {
                ModeSelectionCard(
                    title = "Schedule Lock",
                    description = "Start restriction automatically at a chosen date and time.",
                    icon = ResolveIcons.Event,
                    accentBadge = "Automated",
                    onClick = onOpenScheduleSetup,
                    testTag = "mode_card_schedule"
                )
            }

            item {
                FeatureTipNote(
                    text = "Note: Phone calls & chosen allowed apps stay usable during lock.",
                    icon = Icons.Outlined.Call
                )
            }
        }
    }
}

@Composable
private fun EnforcementStatusPill(
    capabilities: SystemCapabilityStatus,
    onClick: () -> Unit
) {
    val isReady = capabilities.canEnforceRealLock
    val bgColor = if (isReady) ResolveColors.MintSoftContainer else ResolveColors.AmberContainer
    val fgColor = if (isReady) ResolveColors.MintAccent else ResolveColors.AmberWarn
    val label = when (capabilities.activeEnforcementMode) {
        EnforcementMode.DEVICE_OWNER_LOCK_TASK -> "Device Owner"
        EnforcementMode.ACCESSIBILITY_GUARD -> "Guard Active"
        EnforcementMode.NONE -> "Setup Needed"
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, fgColor.copy(alpha = 0.42f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("home_enforcement_status_pill"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isReady) {
            PulsingStatusDot(color = fgColor, size = 7.dp)
        } else {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fgColor
        )
    }
}

@Composable
private fun SetupReadinessCallout(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, 0.98f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ResolveColors.AmberContainer.copy(alpha = 0.72f),
                        ResolveColors.ForestSurface
                    )
                )
            )
            .border(1.dp, ResolveColors.AmberWarn.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ResolveColors.AmberWarn.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ResolveIcons.Tune,
                contentDescription = null,
                tint = ResolveColors.AmberWarn,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable Accessibility & Permissions",
                style = MaterialTheme.typography.titleMedium,
                color = ResolveColors.TextPrimary
            )
            Text(
                text = "Tap to open Settings and enable Resolve Focus Guard so locks enforce reliably.",
                style = MaterialTheme.typography.bodySmall,
                color = ResolveColors.TextSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "Open Settings",
            tint = ResolveColors.AmberWarn,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActiveSessionBanner(
    session: ActiveSession,
    remainingMillis: Long,
    onReturnToLockScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val isWarning = session.state == SessionState.ENFORCEMENT_UNAVAILABLE ||
        session.state == SessionState.ENFORCEMENT_FAILED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isWarning) {
                    Brush.verticalGradient(
                        listOf(ResolveColors.ErrorContainer.copy(alpha = 0.85f), ResolveColors.ForestSurface)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(ResolveColors.MintSoftContainer.copy(alpha = 0.72f), ResolveColors.ForestSurface)
                    )
                }
            )
            .border(
                1.dp,
                if (isWarning) ResolveColors.ErrorCoral.copy(alpha = 0.55f)
                else ResolveColors.MintAccent.copy(alpha = 0.50f),
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (session.state) {
            SessionState.DELAYED_FREE_USE_ACTIVE -> {
                val totalFreeMs = (session.freeUseDurationMinutes.coerceAtLeast(1L)) * 60_000L
                val freeProgress = (remainingMillis.toFloat() / totalFreeMs.toFloat()).coerceIn(0f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingStatusDot(color = ResolveColors.MintAccent, size = 7.dp)
                    Text(
                        text = "DELAYED LOCK — FREE USE WINDOW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = ResolveColors.MintAccent
                    )
                }
                Text(
                    text = formatCountdownHms(remainingMillis),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ResolveColors.TextPrimary
                )
                LinearProgressIndicator(
                    progress = { freeProgress },
                    color = ResolveColors.MintAccent,
                    trackColor = ResolveColors.ForestDeep,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "Phone remains unrestricted until timer hits zero. Lock Duration (${session.lockDurationMinutes}m) starts automatically after transition.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResolveColors.TextSecondary
                )
            }
            SessionState.LOCK_ENFORCEMENT_ACTIVE,
            SessionState.LOCK_TRANSITION_PENDING -> {
                val totalLockMs = (session.lockDurationMinutes.coerceAtLeast(1L)) * 60_000L
                val lockProgress = (remainingMillis.toFloat() / totalLockMs.toFloat()).coerceIn(0f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingStatusDot(color = ResolveColors.MintBright, size = 7.dp)
                    Text(
                        text = "LOCK ENFORCEMENT ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = ResolveColors.MintBright
                    )
                }
                Text(
                    text = formatCountdownHms(remainingMillis),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ResolveColors.TextPrimary
                )
                LinearProgressIndicator(
                    progress = { lockProgress },
                    color = ResolveColors.MintAccent,
                    trackColor = ResolveColors.ForestDeep,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                TactilePrimaryButton(onClick = onReturnToLockScreen) {
                    Icon(
                        imageVector = ResolveIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Return to Lock Screen", style = MaterialTheme.typography.titleMedium)
                }
            }
            SessionState.ENFORCEMENT_UNAVAILABLE,
            SessionState.ENFORCEMENT_FAILED -> {
                Text(
                    text = "ENFORCEMENT UNAVAILABLE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = ResolveColors.ErrorCoral
                )
                Text(
                    text = session.failureReason ?: "Lock enforcement could not be activated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResolveColors.TextPrimary
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = ResolveColors.ErrorCoral)
                }
            }
            SessionState.COMPLETED,
            SessionState.RECOVERED -> {
                val label = if (session.state == SessionState.COMPLETED) "Session Completed" else "Session Recovered"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = ResolveColors.MintAccent
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Clear", color = ResolveColors.TextSecondary)
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentBadge: String,
    onClick: () -> Unit,
    testTag: String
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, pressedScale = 0.975f)
            .resolveGlassCard(
                cornerRadius = 24.dp,
                borderColor = ResolveColors.ForestBorderGlow.copy(alpha = 0.55f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(20.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ResolveColors.MintSoftContainer,
                            ResolveColors.ForestSurfaceElevated
                        )
                    )
                )
                .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.25f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = ResolveColors.MintAccent,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ResolveColors.TextPrimary
                )
                Text(
                    text = accentBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = ResolveColors.MintAccent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ResolveColors.MintSoftContainer)
                        .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.25f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ResolveColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ResolveColors.ForestDeep.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Configure $title",
                tint = ResolveColors.MintAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
