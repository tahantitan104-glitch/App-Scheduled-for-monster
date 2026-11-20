package com.example.presentation.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.domain.model.EnforcementMode
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.KeyboardTypingText
import com.example.presentation.components.ProtectionStatusCard
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.ResolveRunnerLogo
import com.example.presentation.components.SettingsAccountSyncCard
import com.example.presentation.components.resolveGlassCard
import com.example.sync.AccountSyncUiState

@Composable
fun SettingsScreen(
    capabilities: SystemCapabilityStatus,
    hasRecoveryCode: Boolean,
    recoveryCodeLength: Int,
    generateRandomCode: (Int) -> String,
    onSaveRecoveryCode: (String) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenDeviceOwnerGuide: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    mascotPreferences: MascotPreferences = MascotPreferences(),
    onSelectMascot: (MascotType) -> Unit = {},
    onToggleReducedMotion: (Boolean) -> Unit = {},
    onToggleFocusMessagesEnabled: (Boolean) -> Unit = {},
    onSelectFocusMessageFrequency: (FocusMessageFrequency) -> Unit = {},
    onAddFocusMessage: (String) -> Unit = {},
    onEditFocusMessage: (String, String) -> Unit = { _, _ -> },
    onDeleteFocusMessage: (String) -> Unit = {},
    onRestoreDefaultFocusMessages: () -> Unit = {},
    onToggleLockReminderEnabled: (Boolean) -> Unit = {},
    onSaveLockReminderText: (String) -> Unit = {},
    onSelectLockReminderStyle: (LockReminderStyle) -> Unit = {},
    accountSyncState: AccountSyncUiState = AccountSyncUiState(),
    onSignInGoogle: () -> Unit = {},
    onSignOutGoogle: () -> Unit = {},
    onToggleDeviceSync: (Boolean) -> Unit = {},
    onUpdateProfilePhoto: (Uri) -> Unit = {},
    onClearProfilePhoto: () -> Unit = {},
    onUnlinkDevice: (String) -> Unit = {},
    protectionStatus: ProtectionStatus? = null,
    onProtectionAction: (ProtectionRecoveryAction) -> Unit = {},
    notificationControlConfig: NotificationControlConfig = NotificationControlConfig(),
    notificationProtectionState: NotificationProtectionState = NotificationProtectionState.INACTIVE,
    onToggleNotificationControlEnabled: (Boolean) -> Unit = {},
    onToggleNotificationControlAutoActivate: (Boolean) -> Unit = {},
    onToggleNotificationControlAllowCalls: (Boolean) -> Unit = {},
    onToggleNotificationControlAllowAlarms: (Boolean) -> Unit = {},
    onOpenNotificationPolicySettings: () -> Unit = {},
    onOpenModes: () -> Unit = {},
    onOpenAppLock: () -> Unit = {},
    onOpenDeviceAnalysis: () -> Unit = {},
    onOpenAboutResolve: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val palette = LocalResolveExtendedPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

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

    val accHealth = capabilities.accessibilityHealthState
    val accReady = accHealth == AccessibilityHealthState.ENABLED_CONNECTED
    val overlayEnabled = capabilities.canDrawOverlays
    val usageEnabled = capabilities.hasUsageStatsAccess
    val exactReady = !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms
    val notifEnabled = capabilities.notificationsEnabled
    val unrestricted = capabilities.isIgnoringBatteryOptimizations

    val readyCount = listOf(
        accReady,
        overlayEnabled,
        usageEnabled,
        exactReady,
        notifEnabled,
        unrestricted
    ).count { it }
    val allCorePermissionsReady = readyCount == 6

    val listState = rememberLazyListState()
    val isAboutVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "about_section_card" }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.textPrimary
                            )
                        }
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary
                    )
                }
            }

            if (protectionStatus != null) {
                item {
                    ProtectionStatusCard(
                        status = protectionStatus,
                        onActionClick = onProtectionAction
                    )
                }
            } else {
                item {
                    val isGuardActive = capabilities.canEnforceRealLock && accHealth != AccessibilityHealthState.ENABLED_DISCONNECTED
                    val tierLabel = when {
                        accHealth == AccessibilityHealthState.ENABLED_DISCONNECTED ->
                            "Reconnect Accessibility Service"
                        capabilities.activeEnforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK ->
                            "Tier 1: Device Owner Lock Task"
                        capabilities.activeEnforcementMode == EnforcementMode.ACCESSIBILITY_GUARD ->
                            "Tier 2: Accessibility Focus Guard"
                        else -> "Action Required: Enable Accessibility Service"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isGuardActive) {
                                        listOf(palette.accentSoftContainer, palette.cardBottom)
                                    } else {
                                        listOf(palette.warnContainer.copy(alpha = 0.7f), palette.cardBottom)
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                if (isGuardActive) palette.accent.copy(alpha = 0.45f)
                                else palette.warn.copy(alpha = 0.45f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.background.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.Shield,
                                contentDescription = null,
                                tint = if (isGuardActive) palette.accent else palette.warn,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isGuardActive) {
                                    PulsingStatusDot(color = palette.accent, size = 6.dp)
                                }
                                Text(
                                    text = tierLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$readyCount / 6 system permissions ready",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = if (isGuardActive) palette.accent else palette.warn
                            )
                        }
                    }
                }
            }

            // Section A — Account & Multi-Device Sync
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "A · ACCOUNT & MULTI-DEVICE SYNC",
                    icon = ResolveIcons.Sync
                )
            }

            item {
                SettingsAccountSyncCard(
                    state = accountSyncState,
                    onSignInClick = onSignInGoogle,
                    onSignOutClick = onSignOutGoogle,
                    onToggleSyncEnabled = onToggleDeviceSync,
                    onPickAvatarUri = onUpdateProfilePhoto,
                    onClearAvatar = onClearProfilePhoto,
                    onUnlinkDevice = onUnlinkDevice
                )
            }

            // Section B — Protection & Permissions (Strict granted hiding, Accessibility first, zero reveal toggle)
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "B · PROTECTION & PERMISSIONS",
                    icon = ResolveIcons.Shield
                )
            }

            if (allCorePermissionsReady) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 20.dp,
                                borderColor = palette.accent.copy(alpha = 0.40f)
                            )
                            .padding(16.dp)
                            .testTag("settings_all_permissions_ready_banner"),
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
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All required system access is ready.",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "6 / 6 core guards active & connected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.accent
                            )
                        }
                    }
                }
            } else {
                // Step 1 ALWAYS FIRST when missing or disconnected: Accessibility Service
                if (!accReady) {
                    item(key = "perm_accessibility") {
                        val disconnected = accHealth == AccessibilityHealthState.ENABLED_DISCONNECTED
                        PermissionItemCard(
                            name = "Accessibility Service",
                            reason = if (disconnected) {
                                "Accessibility is enabled in settings, but Android has not connected the background service. Re-toggle RESOLVE in Accessibility Settings."
                            } else {
                                "Required primary guard to detect foreground apps during Lock Mode and protect locked apps."
                            },
                            status = if (disconnected) "Service Disconnected (Reconnect)" else "Not Enabled",
                            isReady = false,
                            statusColor = palette.warn,
                            actionLabel = if (disconnected) "Reconnect Accessibility" else "Enable Accessibility",
                            onAction = onOpenAccessibilitySettings,
                            testTag = "settings_accessibility_action"
                        )
                    }
                }

                if (!overlayEnabled) {
                    item(key = "perm_overlay") {
                        PermissionItemCard(
                            name = "Display Over Other Apps",
                            reason = "Required on Android 10+ to show the Lock Screen automatically over blocked apps.",
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
                    item(key = "perm_usage") {
                        PermissionItemCard(
                            name = "Usage Access",
                            reason = "Provides foreground detection fallback and daily screen time statistics.",
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
                    item(key = "perm_exact_alarm") {
                        PermissionItemCard(
                            name = "Alarms & Reminders",
                            reason = "Required for exact Delayed Lock and Schedule Lock start/end triggers.",
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
                    item(key = "perm_notifications") {
                        PermissionItemCard(
                            name = "Notifications",
                            reason = "Required to keep active lock countdown notifications running reliably.",
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
                    item(key = "perm_battery") {
                        PermissionItemCard(
                            name = "Unrestricted Battery",
                            reason = "Prevents the system from stopping timers or Focus Guard in the background.",
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

            item {
                SettingsLinkRow(
                    title = "Device Analysis & Compatibility",
                    subtitle = "Inspect device hardware profile, readiness checks & Device Owner info",
                    icon = ResolveIcons.Devices,
                    onClick = onOpenDeviceAnalysis,
                    testTag = "settings_open_device_analysis_row"
                )
            }

            // Section C — App Access & Restrictions
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "C · APP ACCESS & RESTRICTIONS",
                    icon = ResolveIcons.Key
                )
            }

            item {
                PermissionItemCard(
                    name = "Recovery Code",
                    reason = "Ends an active restricted focus session only when verified.",
                    status = if (hasRecoveryCode) "$recoveryCodeLength-digit Configured" else "Not Configured",
                    isReady = hasRecoveryCode,
                    statusColor = if (hasRecoveryCode) palette.accent else palette.warn,
                    actionLabel = if (hasRecoveryCode) "Update Code" else "Configure Code",
                    onAction = { showRecoverySetup = true },
                    testTag = "settings_recovery_action"
                )
                Spacer(Modifier.height(6.dp))
                FeatureTipNote(
                    text = "Tip: Allowed apps and optional phone call access are configured cleanly per focus mode.",
                    icon = ResolveIcons.Key
                )
            }

            item {
                SettingsLinkRow(
                    title = "App Lock",
                    subtitle = "Protect selected apps with Pattern, 4/6-digit PIN, or 6-Cell Sequence",
                    icon = ResolveIcons.Shield,
                    onClick = onOpenAppLock,
                    testTag = "settings_open_applock_row"
                )
            }

            // Section D — Focus & Notification Behavior
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "D · FOCUS & NOTIFICATION BEHAVIOR",
                    icon = Icons.Outlined.Notifications
                )
            }

            item {
                FocusNotificationControlCard(
                    config = notificationControlConfig,
                    protectionState = notificationProtectionState,
                    hasPolicyAccess = capabilities.hasNotificationPolicyAccess,
                    onToggleEnabled = onToggleNotificationControlEnabled,
                    onToggleAutoActivate = onToggleNotificationControlAutoActivate,
                    onToggleAllowCalls = onToggleNotificationControlAllowCalls,
                    onToggleAllowAlarms = onToggleNotificationControlAllowAlarms,
                    onGrantPolicyAccess = onOpenNotificationPolicySettings
                )
            }

            // Section E — Companion & Appearance
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "E · COMPANION & APPEARANCE",
                    icon = ResolveIcons.Tune
                )
            }

            item {
                MascotAppearanceCard(
                    preferences = mascotPreferences,
                    onSelectMascot = onSelectMascot,
                    onToggleReducedMotion = onToggleReducedMotion,
                    onOpenFocusMessagesManager = { showFocusMessagesSheet = true },
                    onToggleLockReminderEnabled = onToggleLockReminderEnabled,
                    onOpenLockReminderEditor = { showLockReminderSheet = true }
                )
            }

            item {
                SettingsLinkRow(
                    title = "Vision & Visual Modes",
                    subtitle = "Enter Flip Clock Mode and inspect OS Dark Mode or Color Inversion support",
                    icon = ResolveIcons.Vision,
                    onClick = onOpenModes,
                    testTag = "settings_open_modes_row"
                )
            }

            // Section F — About & Application Information
            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "F · ABOUT & APPLICATION INFORMATION",
                    icon = Icons.Outlined.Info
                )
            }

            item(key = "about_section_card") {
                CraftedSignatureCard(
                    versionName = BuildConfig.VERSION_NAME,
                    reducedMotion = mascotPreferences.reducedMotionEnabled,
                    isInViewport = isAboutVisible,
                    onOpenAboutResolve = onOpenAboutResolve
                )
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
fun FocusNotificationControlCard(
    config: NotificationControlConfig,
    protectionState: NotificationProtectionState,
    hasPolicyAccess: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAutoActivate: (Boolean) -> Unit,
    onToggleAllowCalls: (Boolean) -> Unit,
    onToggleAllowAlarms: (Boolean) -> Unit,
    onGrantPolicyAccess: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val (stateLabel, stateColor, stateBg) = when {
        !hasPolicyAccess ->
            Triple("Access Needed", palette.warn, palette.warnContainer)
        protectionState == NotificationProtectionState.ACTIVE ->
            Triple("Quiet Focus Active", palette.accent, palette.accentSoftContainer)
        protectionState == NotificationProtectionState.PRESERVED_EXISTING_DND ->
            Triple("Preserved Existing DND", palette.accent, palette.accentSoftContainer)
        protectionState == NotificationProtectionState.OVERRIDDEN_BY_USER ->
            Triple("Overridden by User", palette.warn, palette.warnContainer)
        protectionState == NotificationProtectionState.UNAVAILABLE_NO_PERMISSION ->
            Triple("Access Needed", palette.warn, palette.warnContainer)
        else ->
            Triple("Inactive", palette.textSecondary, palette.background.copy(alpha = 0.6f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 22.dp,
                borderColor = if (config.enabled) palette.accent.copy(alpha = 0.36f) else palette.cardBorder
            )
            .padding(18.dp)
            .testTag("settings_notification_control_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Focus Notification Control",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(stateBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("notif_control_state_badge")
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Silences distracting interruptions via Android Do Not Disturb during focus locks, then restores your previous state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent
                ),
                modifier = Modifier.testTag("notif_control_enabled_switch")
            )
        }

        SettingSwitchRow(
            label = "Auto-activate during lock sessions",
            subtitle = "Applies Focus Quiet Mode when enforcement starts",
            checked = config.autoActivateDuringSessions,
            onCheckedChange = onToggleAutoActivate,
            testTag = "notif_control_auto_switch"
        )
        SettingSwitchRow(
            label = "Allow phone calls",
            subtitle = "Permits incoming phone calls through focus DND",
            checked = config.allowCalls,
            onCheckedChange = onToggleAllowCalls,
            testTag = "notif_control_calls_switch"
        )
        SettingSwitchRow(
            label = "Allow alarms",
            subtitle = "Keeps system alarms and reminders audible",
            checked = config.allowAlarms,
            onCheckedChange = onToggleAllowAlarms,
            testTag = "notif_control_alarms_switch"
        )

        if (!hasPolicyAccess) {
            OutlinedButton(
                onClick = onGrantPolicyAccess,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.warn),
                border = BorderStroke(1.dp, palette.warn.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("settings_notification_policy_action")
            ) {
                Text("Grant Notification Policy Access")
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.background.copy(alpha = 0.55f))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.accent
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 20.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accentSoftContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "Open $title",
            tint = palette.accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun PermissionItemCard(
    name: String,
    reason: String,
    status: String,
    isReady: Boolean,
    statusColor: Color,
    actionLabel: String?,
    onAction: () -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 20.dp,
                borderColor = if (isReady) palette.cardBorder else palette.warn.copy(alpha = 0.28f)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
        if (actionLabel != null) {
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.25.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.accent
        )
    }
}

@Composable
private fun CraftedSignatureCard(
    versionName: String,
    reducedMotion: Boolean,
    isInViewport: Boolean,
    onOpenAboutResolve: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val hasEnteredView = isInViewport

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 22.dp,
                borderColor = palette.accent.copy(alpha = 0.38f)
            )
            .padding(20.dp)
            .testTag("settings_about_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    animateRunner = hasEnteredView,
                    reducedMotion = reducedMotion,
                    testTagName = "settings_about_runner_logo"
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "RESOLVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        ),
                        color = palette.textPrimary
                    )
                    KeyboardTypingText(
                        fullText = "Your distraction killing is resolve duty.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.accent,
                        startTyping = hasEnteredView,
                        startDelayMillis = 80,
                        typingDurationMillis = 820,
                        reducedMotion = reducedMotion
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = palette.accentStrong,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.accentSoftContainer)
                    .border(1.dp, palette.accent.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                PulsingStatusDot(color = palette.accentStrong, size = 5.dp)
                KeyboardTypingText(
                    fullText = "Developed by Tahan Official",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.35.sp
                    ),
                    color = palette.accentStrong,
                    startTyping = hasEnteredView,
                    startDelayMillis = 920,
                    typingDurationMillis = 680,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.testTag("settings_developer_credit")
                )
            }

            OutlinedButton(
                onClick = onOpenAboutResolve,
                shape = CircleShape,
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("settings_open_about_btn")
            ) {
                Text(
                    text = "About RESOLVE",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
