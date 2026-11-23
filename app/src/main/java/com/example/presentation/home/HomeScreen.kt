package com.example.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.ActiveSession
import com.example.domain.model.DailyAppUsageItem
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.DurationSelection
import com.example.domain.model.EnforcementMode
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotType
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.HomeOptionalSyncBanner
import com.example.presentation.components.MascotRenderer
import com.example.presentation.components.ProtectionStatusCard
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.SyncedAvatarBadge
import com.example.presentation.components.TactilePrimaryButton
import com.example.presentation.components.pressScale
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.lockscreen.formatCountdownHms
import com.example.sync.AccountSyncUiState
import java.time.LocalDate
import java.time.ZoneId
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
    onOpenAppLockSetup: () -> Unit = {},
    historyRecords: List<SessionHistoryRecord> = emptyList(),
    dailyAppUsage: DailyAppUsageSummary = DailyAppUsageSummary(),
    loadAppIcon: suspend (String) -> ImageBitmap? = { null },
    onGrantUsageAccess: () -> Unit = {},
    mascotType: MascotType = MascotType.BABY_CLOUD,
    bunnyPeekEntranceSeen: Boolean = true,
    onBunnyPeekEntranceSeen: () -> Unit = {},
    reducedMotion: Boolean = false,
    accountSyncState: AccountSyncUiState = AccountSyncUiState(),
    syncPromoDismissed: Boolean = true,
    onConnectGoogleAccount: () -> Unit = {},
    onDismissSyncPromo: () -> Unit = {},
    onOpenAccountSettings: () -> Unit = {},
    protectionStatus: ProtectionStatus = ProtectionStatus.ProtectionActive(),
    onProtectionAction: (ProtectionRecoveryAction) -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.08f),
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
                .widthIn(max = 680.dp)
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
                        var playedInitialSessionPeek by rememberSaveable(mascotType.storageKey) {
                            mutableStateOf(false)
                        }

                        LaunchedEffect(mascotType, bunnyPeekEntranceSeen, reducedMotion, replayRequestCount) {
                            val shouldPlayPeek = mascotType == MascotType.RESOLVE_BUNNY &&
                                !reducedMotion &&
                                ((!bunnyPeekEntranceSeen && !playedInitialSessionPeek) || replayRequestCount > 0)
                            if (shouldPlayPeek) {
                                playedInitialSessionPeek = true
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
                                .background(
                                    Brush.verticalGradient(
                                        listOf(palette.surfaceElevated, palette.cardBottom)
                                    )
                                )
                                .border(
                                    1.dp,
                                    palette.accent.copy(alpha = 0.28f),
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
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Device Focus Control",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
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

            // Helpful protection readiness prompt (no ADB prompt on Home)
            item {
                AnimatedVisibility(
                    visible = protectionStatus !is ProtectionStatus.ProtectionActive &&
                        protectionStatus !is ProtectionStatus.SessionCompleted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ProtectionStatusCard(
                        status = protectionStatus,
                        onActionClick = { action ->
                            if (action == ProtectionRecoveryAction.NONE) {
                                onOpenDeviceOwnerGuide()
                            } else {
                                onProtectionAction(action)
                            }
                        }
                    )
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
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FeatureTipNote(
                    text = "Tip: Tap your companion badge above to preview.",
                    icon = Icons.Outlined.Info
                )
            }

            // 4 Balanced Rounded Rectangular Feature Cards (Instant, Delayed, Schedule, App Lock)
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useFourColumnRow = maxWidth >= 640.dp
                    if (useFourColumnRow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModeFeatureCard(
                                title = "Instant Lock",
                                description = "Lock immediately for a chosen period.",
                                icon = Icons.Outlined.Lock,
                                accentBadge = "Immediate",
                                accentColor = palette.emeraldStrong,
                                accentContainerColor = palette.emeraldContainer,
                                onClick = onOpenInstantSetup,
                                testTag = "mode_card_instant",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            ModeFeatureCard(
                                title = "Delayed Lock",
                                description = "Free use window, then auto lock.",
                                icon = ResolveIcons.HourglassTop,
                                accentBadge = "2-Stage",
                                accentColor = palette.slateTealStrong,
                                accentContainerColor = palette.slateTealContainer,
                                onClick = onOpenDelayedSetup,
                                testTag = "mode_card_delayed",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            ModeFeatureCard(
                                title = "Schedule Lock",
                                description = "Auto restriction on set dates/times.",
                                icon = ResolveIcons.Event,
                                accentBadge = "Automated",
                                accentColor = palette.champagneStrong,
                                accentContainerColor = palette.champagneContainer,
                                onClick = onOpenScheduleSetup,
                                testTag = "mode_card_schedule",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            ModeFeatureCard(
                                title = "App Lock",
                                description = "Protect specific apps with a code.",
                                icon = ResolveIcons.Shield,
                                accentBadge = "App Guard",
                                accentColor = palette.cobaltStrong,
                                accentContainerColor = palette.cobaltContainer,
                                onClick = onOpenAppLockSetup,
                                testTag = "mode_card_applock",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ModeFeatureCard(
                                    title = "Instant Lock",
                                    description = "Restrict now for a chosen timer.",
                                    icon = Icons.Outlined.Lock,
                                    accentBadge = "Immediate",
                                    accentColor = palette.emeraldStrong,
                                    accentContainerColor = palette.emeraldContainer,
                                    onClick = onOpenInstantSetup,
                                    testTag = "mode_card_instant",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                ModeFeatureCard(
                                    title = "Delayed Lock",
                                    description = "Free use first, then auto Lock.",
                                    icon = ResolveIcons.HourglassTop,
                                    accentBadge = "2-Stage",
                                    accentColor = palette.slateTealStrong,
                                    accentContainerColor = palette.slateTealContainer,
                                    onClick = onOpenDelayedSetup,
                                    testTag = "mode_card_delayed",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ModeFeatureCard(
                                    title = "Schedule Lock",
                                    description = "Start automatically on schedule.",
                                    icon = ResolveIcons.Event,
                                    accentBadge = "Automated",
                                    accentColor = palette.champagneStrong,
                                    accentContainerColor = palette.champagneContainer,
                                    onClick = onOpenScheduleSetup,
                                    testTag = "mode_card_schedule",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                ModeFeatureCard(
                                    title = "App Lock",
                                    description = "Guard selected apps securely.",
                                    icon = ResolveIcons.Shield,
                                    accentBadge = "App Guard",
                                    accentColor = palette.cobaltStrong,
                                    accentContainerColor = palette.cobaltContainer,
                                    onClick = onOpenAppLockSetup,
                                    testTag = "mode_card_applock",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }

            // Compact Progress Overview derived strictly from real local history
            item {
                ProgressOverviewCard(records = historyRecords)
            }

            // Compact Today's App Usage Dashboard (real UsageStatsManager data or honest permission notice)
            item {
                AppUsageDashboardCard(
                    summary = dailyAppUsage,
                    loadAppIcon = loadAppIcon,
                    onGrantUsageAccess = onGrantUsageAccess
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
private fun ModeFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentBadge: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    accentContainerColor: Color = Color.Unspecified
) {
    val palette = LocalResolveExtendedPalette.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedAccent = if (accentColor == Color.Unspecified) palette.accent else accentColor
    val resolvedContainer = if (accentContainerColor == Color.Unspecified) palette.surfaceElevated else accentContainerColor

    Column(
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.97f)
            .resolveGlassCard(
                cornerRadius = 18.dp,
                borderColor = palette.cardBorder
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(15.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(resolvedContainer)
                    .border(1.dp, resolvedAccent.copy(alpha = 0.24f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = resolvedAccent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Text(
                text = accentBadge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = resolvedAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(resolvedContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressOverviewCard(
    records: List<SessionHistoryRecord>
) {
    val palette = LocalResolveExtendedPalette.current
    val todayStartMs = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val todayRecords = remember(records, todayStartMs) {
        records.filter { it.endedAtWallMs >= todayStartMs || it.startedAtWallMs >= todayStartMs }
    }
    val todayCompleted = remember(todayRecords) {
        todayRecords.count { it.outcome == SessionOutcome.COMPLETED }
    }
    val todayFocusMinutes = remember(todayRecords) {
        todayRecords.filter { it.outcome == SessionOutcome.COMPLETED }.sumOf { it.lockDurationMinutes }
    }
    val completionRatio = remember(todayRecords, todayCompleted) {
        if (todayRecords.isEmpty()) 0f else (todayCompleted.toFloat() / todayRecords.size.toFloat()).coerceIn(0f, 1f)
    }
    val animatedRatio by animateFloatAsState(
        targetValue = completionRatio,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "home_today_completion_ratio"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 18.dp,
                borderColor = palette.cardBorder
            )
            .padding(16.dp)
            .testTag("home_progress_overview_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Focus Progress",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            Text(
                text = if (todayFocusMinutes > 0L) {
                    DurationSelection.fromTotalMinutes(todayFocusMinutes).formattedCompact()
                } else {
                    "0m"
                },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.textPrimary
            )
        }

        LinearProgressIndicator(
            progress = { animatedRatio },
            color = palette.accentStrong,
            trackColor = palette.divider,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (todayRecords.isEmpty()) {
                    "No focus sessions completed today yet"
                } else {
                    "$todayCompleted of ${todayRecords.size} sessions completed today"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            Text(
                text = "All-time: ${records.size}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun AppUsageDashboardCard(
    summary: DailyAppUsageSummary,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onGrantUsageAccess: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 18.dp)
            .padding(12.dp)
            .testTag("home_usage_dashboard_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Screen Time",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            if (summary.hasUsagePermission) {
                Text(
                    text = formatUsageDuration(summary.totalScreenTimeMs),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.slateTealStrong
                )
            }
        }

        if (!summary.hasUsagePermission) {
            Text(
                text = "Usage Access permission is required to view real app screen time.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            OutlinedButton(
                onClick = onGrantUsageAccess,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.slateTeal.copy(alpha = 0.42f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.slateTealContainer.copy(alpha = 0.65f),
                    contentColor = palette.slateTealStrong
                ),
                modifier = Modifier.testTag("home_grant_usage_button")
            ) {
                Text("Grant Usage Access", style = MaterialTheme.typography.labelMedium)
            }
        } else if (summary.topApps.isEmpty()) {
            Text(
                text = "No other app usage recorded today.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        } else {
            val topList = remember(summary.topApps) { summary.topApps.take(4) }
            val maxMs = remember(topList) {
                topList.maxOfOrNull { it.foregroundTimeMs }?.coerceAtLeast(1L) ?: 1L
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                topList.forEach { item ->
                    UsageAppRow(
                        item = item,
                        fraction = (item.foregroundTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0.05f, 1f),
                        loadAppIcon = loadAppIcon
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageAppRow(
    item: DailyAppUsageItem,
    fraction: Float,
    loadAppIcon: suspend (String) -> ImageBitmap?
) {
    val palette = LocalResolveExtendedPalette.current
    var icon by remember(item.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.packageName) {
        icon = loadAppIcon(item.packageName)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "usage_bar_${item.packageName}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_usage_app_${item.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = "${item.appName} icon",
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.Apps,
                    contentDescription = null,
                    tint = palette.slateTealStrong,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatUsageDuration(item.foregroundTimeMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = palette.slateTealStrong
                )
            }
            LinearProgressIndicator(
                progress = { animatedFraction },
                color = palette.slateTeal,
                trackColor = palette.divider,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
            )
        }
    }
}

private fun formatUsageDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun EnforcementStatusPill(
    capabilities: SystemCapabilityStatus,
    onClick: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val isReady = capabilities.canEnforceRealLock
    val bgColor = if (isReady) palette.success else palette.warnContainer
    val fgColor = if (isReady) palette.onAccent else palette.warn
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
    val palette = LocalResolveExtendedPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.warnContainer.copy(alpha = 0.72f))
            .border(1.dp, palette.warn.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
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
                .background(palette.warn.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ResolveIcons.Tune,
                contentDescription = null,
                tint = palette.warn,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable Accessibility & Permissions",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            Text(
                text = "Tap to open Settings and enable Resolve Focus Guard so locks enforce reliably.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Open Settings",
            tint = palette.warn,
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
    val palette = LocalResolveExtendedPalette.current
    val isWarning = session.state == SessionState.ENFORCEMENT_UNAVAILABLE ||
        session.state == SessionState.ENFORCEMENT_FAILED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isWarning) {
                    Brush.verticalGradient(
                        listOf(palette.errorContainer.copy(alpha = 0.85f), palette.cardBottom)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(palette.surfaceElevated, palette.cardBottom)
                    )
                }
            )
            .border(
                1.dp,
                if (isWarning) palette.error.copy(alpha = 0.50f)
                else palette.accentSecondary.copy(alpha = 0.45f),
                RoundedCornerShape(18.dp)
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
                    PulsingStatusDot(color = palette.accent, size = 7.dp)
                    Text(
                        text = "DELAYED LOCK — FREE USE WINDOW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = palette.textPrimary
                    )
                }
                Text(
                    text = formatCountdownHms(remainingMillis),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = palette.textPrimary
                )
                LinearProgressIndicator(
                    progress = { freeProgress },
                    color = palette.accent,
                    trackColor = palette.divider,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "Phone remains unrestricted until timer hits zero. Lock Duration (${session.lockDurationMinutes}m) starts automatically after transition.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
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
                    PulsingStatusDot(color = palette.accent, size = 7.dp)
                    Text(
                        text = "LOCK ENFORCEMENT ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = palette.textPrimary
                    )
                }
                Text(
                    text = formatCountdownHms(remainingMillis),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = palette.textPrimary
                )
                LinearProgressIndicator(
                    progress = { lockProgress },
                    color = palette.accent,
                    trackColor = palette.divider,
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
                    color = palette.error
                )
                Text(
                    text = session.failureReason ?: "Lock enforcement could not be activated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = palette.error)
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
                        color = palette.textPrimary
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Clear", color = palette.textSecondary)
                    }
                }
            }
            else -> Unit
        }
    }
}
