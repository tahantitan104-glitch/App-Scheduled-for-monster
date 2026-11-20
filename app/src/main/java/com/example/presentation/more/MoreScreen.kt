package com.example.presentation.more

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.DeviceAnalysisReport
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.OverallCompatibilityResult
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.KeyboardTypingText
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.ResolveRunnerLogo
import com.example.presentation.components.SettingsAccountSyncCard
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.settings.FocusMessagesManagerSheet
import com.example.presentation.settings.FocusNotificationControlCard
import com.example.presentation.settings.LockReminderEditorSheet
import com.example.presentation.settings.MascotAppearanceCard
import com.example.presentation.settings.PermissionItemCard
import com.example.presentation.settings.SettingsSectionHeader
import com.example.sync.AccountSyncUiState

@Composable
fun MoreScreen(
    capabilities: SystemCapabilityStatus,
    protectionStatus: ProtectionStatus,
    deviceAnalysisReport: DeviceAnalysisReport,
    hasRecoveryCode: Boolean,
    recoveryCodeLength: Int,
    landscapeTiltMode: Boolean,
    notificationConfig: NotificationControlConfig,
    notificationProtectionState: NotificationProtectionState,
    mascotPreferences: MascotPreferences,
    accountSyncState: AccountSyncUiState,
    generateRandomCode: (Int) -> String,
    onSaveRecoveryCode: (String) -> Unit,
    onToggleLandscapeTiltMode: (Boolean) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onProtectionAction: (ProtectionRecoveryAction) -> Unit,
    onOpenDeviceAnalysis: (focusSection: String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onToggleNotificationControlEnabled: (Boolean) -> Unit,
    onToggleNotificationControlAutoActivate: (Boolean) -> Unit,
    onToggleNotificationControlAllowCalls: (Boolean) -> Unit,
    onToggleNotificationControlAllowAlarms: (Boolean) -> Unit,
    onSelectMascot: (MascotType) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onToggleFocusMessagesEnabled: (Boolean) -> Unit,
    onSelectFocusMessageFrequency: (FocusMessageFrequency) -> Unit,
    onAddFocusMessage: (String) -> Unit,
    onEditFocusMessage: (String, String) -> Unit,
    onDeleteFocusMessage: (String) -> Unit,
    onRestoreDefaultFocusMessages: () -> Unit,
    onToggleLockReminderEnabled: (Boolean) -> Unit,
    onSaveLockReminderText: (String) -> Unit,
    onSelectLockReminderStyle: (LockReminderStyle) -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onToggleDeviceSync: (Boolean) -> Unit,
    onUpdateProfileName: (String) -> Unit,
    onUpdateProfilePhoto: (Uri) -> Unit,
    onClearProfilePhoto: () -> Unit,
    onUnlinkDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshCapabilities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            onRefreshCapabilities()
            if (!granted) {
                onOpenNotificationSettings()
            }
        }
    )

    var showRecoverySetup by remember { mutableStateOf(false) }
    var showFocusMessagesSheet by remember { mutableStateOf(false) }
    var showLockReminderSheet by remember { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var headerTypingDone by rememberSaveable { mutableStateOf(false) }
    var devTypingDone by rememberSaveable { mutableStateOf(false) }

    val accHealth = capabilities.accessibilityHealthState
    val accReady = accHealth == AccessibilityHealthState.ENABLED_CONNECTED
    val overlayEnabled = capabilities.canDrawOverlays
    val usageEnabled = capabilities.hasUsageStatsAccess
    val exactReady = !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms
    val notifEnabled = capabilities.notificationsEnabled
    val unrestricted = capabilities.isIgnoringBatteryOptimizations
    val allCorePermissionsReady =
        accReady && overlayEnabled && usageEnabled && exactReady && notifEnabled && unrestricted

    val listState = rememberLazyListState()
    val isAboutVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "more_about_card" }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("more_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("more_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "more_title") {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary
                )
            }

            // 1. PROFILE & ACCOUNT SYNC
            item(key = "more_section_profile_header") {
                SettingsSectionHeader(
                    title = "PROFILE & ACCOUNT SYNC",
                    icon = ResolveIcons.Sync
                )
            }

            item(key = "more_profile_card") {
                SettingsAccountSyncCard(
                    state = accountSyncState,
                    onSignInClick = onSignInGoogle,
                    onSignOutClick = onSignOutGoogle,
                    onToggleSyncEnabled = onToggleDeviceSync,
                    onPickAvatarUri = onUpdateProfilePhoto,
                    onClearAvatar = onClearProfilePhoto,
                    onUnlinkDevice = onUnlinkDevice,
                    onUpdateProfileName = onUpdateProfileName
                )
            }

            // 2. PERMISSIONS (Strictly hidden — including header — when all 6 required permissions are ready)
            if (!allCorePermissionsReady) {
                item(key = "more_section_permissions_header") {
                    SettingsSectionHeader(
                        title = "PERMISSIONS",
                        icon = ResolveIcons.Shield
                    )
                }

                if (!accReady) {
                    item(key = "more_perm_accessibility") {
                        val disconnected = accHealth == AccessibilityHealthState.ENABLED_DISCONNECTED
                        PermissionItemCard(
                            name = "Accessibility Service",
                            reason = if (disconnected) {
                                "Accessibility is enabled in settings, but Android disconnected the service. Re-toggle RESOLVE in Accessibility Settings."
                            } else {
                                "Primary guard required to monitor foreground apps and enforce Focus Lock."
                            },
                            status = if (disconnected) "Disconnected" else "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = if (disconnected) "Reconnect Accessibility" else "Enable Accessibility",
                            onAction = onOpenAccessibilitySettings,
                            testTag = "settings_accessibility_action"
                        )
                    }
                }

                if (!overlayEnabled) {
                    item(key = "more_perm_overlay") {
                        PermissionItemCard(
                            name = "Display Over Other Apps",
                            reason = "Required to show Focus Lock automatically over blocked apps.",
                            status = "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = "Enable Overlay",
                            onAction = onOpenOverlaySettings,
                            testTag = "settings_overlay_action"
                        )
                    }
                }

                if (!usageEnabled) {
                    item(key = "more_perm_usage") {
                        PermissionItemCard(
                            name = "Usage Access",
                            reason = "Enables foreground fallback detection and daily focus insights.",
                            status = "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = "Enable Usage Access",
                            onAction = onOpenUsageAccessSettings,
                            testTag = "settings_usage_access_action"
                        )
                    }
                }

                if (!exactReady) {
                    item(key = "more_perm_exact_alarm") {
                        PermissionItemCard(
                            name = "Alarms & Reminders",
                            reason = "Required for exact Delayed Lock and Schedule Lock timing.",
                            status = "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = "Enable Access",
                            onAction = onOpenExactAlarmSettings,
                            testTag = "settings_exact_alarm_action"
                        )
                    }
                }

                if (!notifEnabled) {
                    item(key = "more_perm_notifications") {
                        PermissionItemCard(
                            name = "Notifications",
                            reason = "Required to display active focus countdown notifications reliably.",
                            status = "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = "Enable Notifications",
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    runCatching {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }.onFailure {
                                        onOpenNotificationSettings()
                                    }
                                } else {
                                    onOpenNotificationSettings()
                                }
                            },
                            testTag = "settings_notifications_action"
                        )
                    }
                }

                if (!unrestricted) {
                    item(key = "more_perm_battery") {
                        PermissionItemCard(
                            name = "Unrestricted Battery",
                            reason = "Prevents Android from pausing timers or Focus Guard in the background.",
                            status = "Optimized",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = "Allow Unrestricted",
                            onAction = onOpenBatteryOptimizationSettings,
                            testTag = "settings_battery_action"
                        )
                    }
                }
            }

            // 3. SYSTEM HEALTH (One unified card with 5 items)
            item(key = "more_section_health_header") {
                SettingsSectionHeader(
                    title = "SYSTEM HEALTH",
                    icon = ResolveIcons.Devices
                )
            }

            item(key = "more_system_health_card") {
                val overallSummary = when {
                    deviceAnalysisReport.analyzedAtWallMs == 0L -> protectionStatus.title
                    deviceAnalysisReport.overallResult == OverallCompatibilityResult.READY -> "All guards active"
                    else -> deviceAnalysisReport.overallResult.label
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 22.dp,
                            borderColor = palette.cardBorder
                        )
                        .padding(vertical = 6.dp)
                        .testTag("more_system_health_card")
                ) {
                    SystemHealthRow(
                        icon = ResolveIcons.Shield,
                        title = "System Status",
                        description = overallSummary,
                        onClick = { onOpenDeviceAnalysis("status") },
                        testTag = "more_health_status"
                    )
                    HealthDivider()
                    SystemHealthRow(
                        icon = ResolveIcons.History,
                        title = "Device Analysis",
                        description = "Inspect app & background service readiness",
                        onClick = { onOpenDeviceAnalysis("analysis") },
                        testTag = "more_health_analysis"
                    )
                    HealthDivider()
                    SystemHealthRow(
                        icon = ResolveIcons.Devices,
                        title = "Hardware Check",
                        description = "Verify OS version, memory, and build support",
                        onClick = { onOpenDeviceAnalysis("hardware") },
                        testTag = "more_health_hardware"
                    )
                    HealthDivider()
                    SystemHealthRow(
                        icon = Icons.Outlined.CheckCircle,
                        title = "Readiness Diagnostics",
                        description = "Review Android security and compatibility checks",
                        onClick = { onOpenDeviceAnalysis("diagnostics") },
                        testTag = "more_health_diagnostics"
                    )
                    HealthDivider()
                    SystemHealthRow(
                        icon = Icons.Outlined.Settings,
                        title = "System Repair",
                        description = "Fix disconnected services or missing OS permissions",
                        onClick = { onOpenDeviceAnalysis("repair") },
                        testTag = "more_health_repair"
                    )
                }
            }

            // 4. FOCUS SETTINGS (Recovery Code + Landscape Tilt Mode)
            item(key = "more_section_focus_header") {
                SettingsSectionHeader(
                    title = "FOCUS SETTINGS",
                    icon = ResolveIcons.Key
                )
            }

            item(key = "more_focus_settings_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(cornerRadius = 22.dp)
                        .padding(16.dp)
                        .testTag("more_focus_settings_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Recovery Code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.background.copy(alpha = 0.55f))
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { showRecoverySetup = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("settings_recovery_action"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSoftContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.Key,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recovery Code",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (hasRecoveryCode) {
                                    "$recoveryCodeLength-digit emergency unlock code active"
                                } else {
                                    "Set emergency unlock code for restricted sessions"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasRecoveryCode) palette.accent else palette.warn
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Configure Recovery Code",
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Landscape Tilt Mode (Default OFF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.background.copy(alpha = 0.55f))
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onToggleLandscapeTiltMode(!landscapeTiltMode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSoftContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.ScreenRotation,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Landscape Tilt Mode",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Allow the Focus Guard screen to switch to Landscape mode when you tilt your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Switch(
                            checked = landscapeTiltMode,
                            onCheckedChange = onToggleLandscapeTiltMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onAccent,
                                checkedTrackColor = palette.accent
                            ),
                            modifier = Modifier.testTag("more_landscape_tilt_switch")
                        )
                    }
                }
            }

            // 5. MASCOT SELECTION (Inline, no page navigation)
            item(key = "more_section_mascot_header") {
                SettingsSectionHeader(
                    title = "MASCOT SELECTION",
                    icon = ResolveIcons.Tune
                )
            }

            item(key = "more_mascot_selection_card") {
                MascotAppearanceCard(
                    preferences = mascotPreferences,
                    onSelectMascot = onSelectMascot,
                    onToggleReducedMotion = onToggleReducedMotion,
                    onOpenFocusMessagesManager = { showFocusMessagesSheet = true },
                    onToggleLockReminderEnabled = onToggleLockReminderEnabled,
                    onOpenLockReminderEditor = { showLockReminderSheet = true }
                )
            }

            // 6. NOTIFICATION SETTINGS (Inline, no page navigation)
            item(key = "more_section_notifications_header") {
                SettingsSectionHeader(
                    title = "NOTIFICATION SETTINGS",
                    icon = Icons.Outlined.Notifications
                )
            }

            item(key = "more_notifications_card") {
                FocusNotificationControlCard(
                    config = notificationConfig,
                    protectionState = notificationProtectionState,
                    hasPolicyAccess = capabilities.hasNotificationPolicyAccess,
                    onToggleEnabled = onToggleNotificationControlEnabled,
                    onToggleAutoActivate = onToggleNotificationControlAutoActivate,
                    onToggleAllowCalls = onToggleNotificationControlAllowCalls,
                    onToggleAllowAlarms = onToggleNotificationControlAllowAlarms,
                    onGrantPolicyAccess = onOpenNotificationPolicySettings
                )
            }

            // 7. ABOUT RESOLVE (Inline Expandable/Collapsible Card at absolute bottom)
            item(key = "more_section_about_header") {
                SettingsSectionHeader(
                    title = "ABOUT RESOLVE",
                    icon = Icons.Outlined.Info
                )
            }

            item(key = "more_about_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 22.dp,
                            borderColor = palette.accent.copy(alpha = 0.38f)
                        )
                        .padding(18.dp)
                        .testTag("more_about_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { aboutExpanded = !aboutExpanded }
                            .testTag("more_about_toggle_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ResolveRunnerLogo(
                                size = 52.dp,
                                animateRunner = isAboutVisible || aboutExpanded,
                                reducedMotion = mascotPreferences.reducedMotionEnabled,
                                testTagName = "more_runner_logo"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "RESOLVE",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.6.sp
                                        ),
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        color = palette.accent,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(palette.accentSoftContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                KeyboardTypingText(
                                    fullText = "Your distraction killing is resolve duty.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = palette.accent,
                                    startTyping = isAboutVisible || aboutExpanded,
                                    startDelayMillis = 120,
                                    typingDurationMillis = 1600,
                                    reducedMotion = mascotPreferences.reducedMotionEnabled,
                                    alreadyCompleted = headerTypingDone,
                                    onTypingCompleted = { headerTypingDone = true }
                                )
                            }
                        }

                        Icon(
                            imageVector = if (aboutExpanded) ResolveIcons.ExpandLess else ResolveIcons.ExpandMore,
                            contentDescription = if (aboutExpanded) "Collapse About RESOLVE" else "Expand About RESOLVE",
                            tint = palette.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = aboutExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("more_about_expanded_content"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                palette.accent.copy(alpha = 0.42f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PulsingStatusDot(color = palette.accentStrong, size = 5.dp)
                                KeyboardTypingText(
                                    fullText = "Developed by Tahan Official",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.35.sp
                                    ),
                                    color = palette.accentStrong,
                                    startTyping = aboutExpanded,
                                    startDelayMillis = 180,
                                    typingDurationMillis = 1400,
                                    reducedMotion = mascotPreferences.reducedMotionEnabled,
                                    alreadyCompleted = devTypingDone,
                                    onTypingCompleted = { devTypingDone = true },
                                    modifier = Modifier.testTag("more_developer_credit")
                                )
                            }

                            InlineAboutSection(
                                title = "About RESOLVE",
                                body = "RESOLVE is a calm, offline-first digital discipline shield designed to help you reclaim uninterrupted attention without nagging subscriptions or noisy feeds."
                            )
                            InlineAboutSection(
                                title = "What RESOLVE Offers",
                                body = "• Instant Lock: Restrict distracting apps immediately for your chosen focus timer.\n" +
                                    "• Delayed Lock: Enjoy a fixed free-use window followed by automatic Focus Lock.\n" +
                                    "• Schedule Lock: Automate One-Time, Daily, or Custom Weekly focus sessions.\n" +
                                    "• App Lock: Guard sensitive apps using PIN, Pattern, or 6-Cell Sequence.\n" +
                                    "• Flip Clock: Dedicated full-screen split-flap Clock, Timer, and Stopwatch.\n" +
                                    "• Focus Notification Control: Quiet interruptions during focus locks while preserving your prior DND state."
                            )
                            InlineAboutSection(
                                title = "How RESOLVE Works",
                                body = "Accessibility Focus Guard and Overlay permission let RESOLVE detect when a restricted app is opened and bring the Focus Lock or App Lock screen forward smoothly. Persistent session deadlines survive device restarts and process stops."
                            )
                            InlineAboutSection(
                                title = "How to Use RESOLVE Effectively",
                                body = "• Allowlist only essential tools (Dialer, Calculator, Notes) before starting a lock.\n" +
                                    "• Save your Recovery Code securely so you can unlock early in genuine emergencies.\n" +
                                    "• Use Delayed Lock when you want a mindful finish-up countdown before focus begins."
                            )
                            InlineAboutSection(
                                title = "Practical Tips & Multi-Device Sync",
                                body = "Connect a Google account on two or more devices only if you want active focus locks synced automatically across them. Mascots, Allowed Apps, Recovery Codes, Screen Time, and Insights stay private on each phone."
                            )
                            InlineAboutSection(
                                title = "Developer Information",
                                body = "Developed by Tahan Official\n" +
                                    "Package: ${BuildConfig.APPLICATION_ID}\n" +
                                    "Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecoverySetup) {
        RecoveryCodeSetupDialog(
            generateRandomCode = generateRandomCode,
            onDismiss = { showRecoverySetup = false },
            onSaveCode = { code ->
                onSaveRecoveryCode(code)
                showRecoverySetup = false
            }
        )
    }

    if (showFocusMessagesSheet) {
        FocusMessagesManagerSheet(
            preferences = mascotPreferences,
            onDismiss = { showFocusMessagesSheet = false },
            onToggleMessagesEnabled = onToggleFocusMessagesEnabled,
            onSelectFrequency = onSelectFocusMessageFrequency,
            onAddMessage = onAddFocusMessage,
            onEditMessage = onEditFocusMessage,
            onDeleteMessage = onDeleteFocusMessage,
            onRestoreDefaults = onRestoreDefaultFocusMessages
        )
    }

    if (showLockReminderSheet) {
        LockReminderEditorSheet(
            preferences = mascotPreferences,
            onDismiss = { showLockReminderSheet = false },
            onSelectStyle = onSelectLockReminderStyle,
            onSaveText = onSaveLockReminderText
        )
    }
}

@Composable
private fun SystemHealthRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accentSoftContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "Open $title",
            tint = palette.accent,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HealthDivider() {
    val palette = LocalResolveExtendedPalette.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = palette.cardBorder.copy(alpha = 0.55f)
    )
}

@Composable
private fun InlineAboutSection(
    title: String,
    body: String
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.background.copy(alpha = 0.52f))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            lineHeight = 19.sp
        )
    }
}

