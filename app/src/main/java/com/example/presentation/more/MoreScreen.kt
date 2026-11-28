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
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import com.example.designsystem.LocalResolveBottomDockClearance
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.AccountabilityPartnerConfig
import com.example.domain.model.AppUsageQuotaRule
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.DeviceAnalysisReport
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusProfile
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.MindfulPauseConfig
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.OverallCompatibilityResult
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.ResolveThemeId
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.KeyboardTypingText
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.ResolveRunnerLogo
import com.example.presentation.components.SettingsAccountSyncCard
import com.example.presentation.components.rememberEffectiveReducedMotion
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors
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
    modifier: Modifier = Modifier,
    onPickAvatarError: (String) -> Unit = {},
    appThemeId: ResolveThemeId = ResolveThemeId.DEEP_FOREST_GREEN,
    dailyFocusGoalMinutes: Int = 120,
    showSetupTips: Boolean = false,
    excludedScreenTimePackages: Set<String> = emptySet(),
    installedApps: List<InstalledAppInfo> = emptyList(),
    focusProfiles: List<FocusProfile> = FocusProfile.BUILT_IN_PRESETS,
    antiBypassStrictGuard: Boolean = true,
    emergencyCooldownSeconds: Int = 15,
    mindfulPauseConfig: MindfulPauseConfig = MindfulPauseConfig(),
    dailyQuotaRules: List<AppUsageQuotaRule> = emptyList(),
    contentBlockerConfig: ContentBlockerConfig = ContentBlockerConfig(),
    accountabilityPartnerConfig: AccountabilityPartnerConfig = AccountabilityPartnerConfig(),
    onSelectAppTheme: (ResolveThemeId) -> Unit = {},
    onSelectDailyGoalMinutes: (Int) -> Unit = {},
    onToggleShowSetupTips: (Boolean) -> Unit = {},
    onToggleExcludedScreenTimePackage: (String) -> Unit = {},
    onSaveCustomFocusProfile: (String, Long, Boolean, Boolean, Boolean, List<String>) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteCustomFocusProfile: (String) -> Unit = {},
    onToggleAntiBypassStrictGuard: (Boolean) -> Unit = {},
    onSelectEmergencyCooldownSeconds: (Int) -> Unit = {},
    onSaveMindfulPauseConfig: (MindfulPauseConfig) -> Unit = {},
    onSaveDailyQuotaRule: (AppUsageQuotaRule) -> Unit = {},
    onDeleteDailyQuotaRule: (String) -> Unit = {},
    onSaveContentBlockerConfig: (ContentBlockerConfig) -> Unit = {},
    onSaveAccountabilityPartner: (Boolean, String, String?, Boolean, Boolean) -> Unit = { _, _, _, _, _ -> },
    onClearAccountabilityPartnerPin: () -> Unit = {},
    onRefreshSync: () -> Unit = {},
    onSendRemoteExtend: (String, Long) -> Unit = { _, _ -> },
    onVerifyRecoveryForRemoteStop: suspend (String, String) -> RecoveryVerificationResult = { _, _ ->
        RecoveryVerificationResult.NotConfigured
    },
    dnsBlockedCount: Int = 0,
    isFocusLockActive: Boolean = false,
    onResetDnsBlockedCount: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val dockClearance = LocalResolveBottomDockClearance.current
    val effectiveReducedMotion = rememberEffectiveReducedMotion(mascotPreferences.reducedMotionEnabled)
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
    var permissionsExpanded by rememberSaveable { mutableStateOf(false) }
    var systemHealthExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var headerTypingDone by rememberSaveable { mutableStateOf(false) }
    var devTypingDone by rememberSaveable { mutableStateOf(false) }
    var expandedTypingStep by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(allCorePermissionsReady) {
        if (allCorePermissionsReady) {
            permissionsExpanded = false
        }
    }

    val listState = rememberLazyListState()
    val isAboutVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "more_about_card" }
        }
    }

    // Auto-scroll smoothly down when About RESOLVE expands so the expanded card & sentences fit the viewport
    LaunchedEffect(aboutExpanded) {
        if (aboutExpanded) {
            expandedTypingStep = if (effectiveReducedMotion) 6 else 0
            delay(140L)
            val aboutIndex = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "more_about_card" }
                ?.index
                ?: (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(index = aboutIndex, scrollOffset = 0)
            delay(260L)
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "more_about_card" }
            if (itemInfo != null && !listState.isScrollInProgress) {
                val overflow = (itemInfo.offset + itemInfo.size) - listState.layoutInfo.viewportEndOffset
                if (overflow > 0) {
                    listState.animateScrollBy(overflow.toFloat())
                }
            }
        } else {
            expandedTypingStep = 0
        }
    }

    LaunchedEffect(aboutExpanded, expandedTypingStep) {
        if (aboutExpanded && expandedTypingStep > 0 && !listState.isScrollInProgress) {
            delay(180L)
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "more_about_card" }
            if (itemInfo != null) {
                val overflow = (itemInfo.offset + itemInfo.size) - listState.layoutInfo.viewportEndOffset
                if (overflow > 12) {
                    listState.animateScrollBy(overflow.toFloat())
                }
            }
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
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .testTag("more_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 20.dp + dockClearance
            ),
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
                val syncReady = capabilities.canEnforceRealLock && hasRecoveryCode
                val syncReadinessHint = when {
                    !capabilities.canEnforceRealLock ->
                        "Action needed: Enable Focus Guard (Accessibility) below so synced locks enforce here."
                    !hasRecoveryCode ->
                        "Action needed: Set a Recovery Code below before standard synced locks can trigger here."
                    else ->
                        "Ready to enforce synced Instant, Delayed & Scheduled locks."
                }
                SettingsAccountSyncCard(
                    state = accountSyncState,
                    onSignInClick = onSignInGoogle,
                    onSignOutClick = onSignOutGoogle,
                    onToggleSyncEnabled = onToggleDeviceSync,
                    onPickAvatarUri = onUpdateProfilePhoto,
                    onClearAvatar = onClearProfilePhoto,
                    onUnlinkDevice = onUnlinkDevice,
                    onUpdateProfileName = onUpdateProfileName,
                    onPickAvatarError = onPickAvatarError,
                    syncReadinessHint = syncReadinessHint,
                    isSyncReady = syncReady,
                    onRefreshSync = onRefreshSync,
                    onSendRemoteExtend = onSendRemoteExtend,
                    onVerifyRecoveryForRemoteStop = onVerifyRecoveryForRemoteStop
                )
            }

            // 2. PERMISSIONS (Summary Done card when 6/6 granted; expandable checklist while < 6)
            item(key = "more_section_permissions_header") {
                SettingsSectionHeader(
                    title = "PERMISSIONS",
                    icon = ResolveIcons.Shield
                )
            }

            item(key = "more_permissions_card") {
                if (allCorePermissionsReady) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = palette.success.copy(alpha = 0.42f)
                            )
                            .padding(16.dp)
                            .testTag("more_permissions_done_card"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.success),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "All 6 permissions enabled",
                                tint = palette.onAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All 6 Permissions Enabled",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary
                            )
                            Text(
                                text = "6 / 6 guards active & ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Text(
                            text = "6 / 6 Done",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.onAccent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.success)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = palette.warn.copy(alpha = 0.4f)
                            )
                            .testTag("more_permissions_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    permissionsExpanded = !permissionsExpanded
                                }
                                .padding(16.dp)
                                .testTag("more_permissions_toggle_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.warnContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.Shield,
                                    contentDescription = "Permissions status",
                                    tint = palette.warn,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$readyCount / 6 Permissions Enabled",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Tap to enable remaining ${6 - readyCount} permission(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )
                            }
                            Text(
                                text = "$readyCount / 6",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.warn,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.warnContainer)
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                            Icon(
                                imageVector = if (permissionsExpanded) ResolveIcons.ExpandLess else ResolveIcons.ExpandMore,
                                contentDescription = if (permissionsExpanded) "Collapse Permissions" else "Expand Permissions",
                                tint = palette.warn,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = permissionsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    .testTag("more_permissions_expanded_content"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!accReady) {
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

                                if (!overlayEnabled) {
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

                                if (!usageEnabled) {
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

                                if (!exactReady) {
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

                                if (!notifEnabled) {
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

                                if (!unrestricted) {
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
                    }
                }
            }

            // 3. SYSTEM HEALTH (Collapsed by default as an expandable card with 3 focused actions)
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
                            cornerRadius = 16.dp,
                            borderColor = palette.cardBorder
                        )
                        .testTag("more_system_health_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                systemHealthExpanded = !systemHealthExpanded
                            }
                            .padding(16.dp)
                            .testTag("more_system_health_toggle_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.slateTealContainer)
                                .border(1.dp, palette.slateTeal.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.Devices,
                                contentDescription = "System Health",
                                tint = palette.slateTealStrong,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Health",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary
                            )
                            Text(
                                text = overallSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Icon(
                            imageVector = if (systemHealthExpanded) ResolveIcons.ExpandLess else ResolveIcons.ExpandMore,
                            contentDescription = if (systemHealthExpanded) "Collapse System Health" else "Expand System Health",
                            tint = palette.slateTealStrong,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = systemHealthExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .testTag("more_system_health_expanded_content")
                        ) {
                            HealthDivider()
                            SystemHealthRow(
                                icon = ResolveIcons.Shield,
                                title = "System Status",
                                description = overallSummary,
                                onClick = { onOpenDeviceAnalysis("status") },
                                testTag = "more_health_status"
                            )
                            HealthDivider()
                            SystemHealthRow(
                                icon = ResolveIcons.Devices,
                                title = "Device & Hardware Analysis",
                                description = "Inspect app, OS hardware & background readiness",
                                onClick = { onOpenDeviceAnalysis("analysis") },
                                testTag = "more_health_analysis"
                            )
                            HealthDivider()
                            SystemHealthRow(
                                icon = Icons.Outlined.Settings,
                                title = "System Repair",
                                description = "Review active guards and grant any missing OS permissions",
                                onClick = { onOpenDeviceAnalysis("repair") },
                                testTag = "more_health_repair"
                            )
                        }
                    }
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
                        .resolveGlassCard(cornerRadius = 16.dp)
                        .padding(16.dp)
                        .testTag("more_focus_settings_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Recovery Code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.controlSurface)
                            .border(1.dp, palette.controlBorder, RoundedCornerShape(14.dp))
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
                                .background(palette.champagneContainer)
                                .border(1.dp, palette.champagne.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.Key,
                                contentDescription = null,
                                tint = palette.champagneStrong,
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
                                color = if (hasRecoveryCode) palette.textSecondary else palette.warn
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Configure Recovery Code",
                            tint = palette.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Landscape Tilt Mode (Default OFF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.controlSurface)
                            .border(1.dp, palette.controlBorder, RoundedCornerShape(14.dp))
                            .clickable { onToggleLandscapeTiltMode(!landscapeTiltMode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.slateTealContainer)
                                .border(1.dp, palette.slateTeal.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.ScreenRotation,
                                contentDescription = null,
                                tint = palette.slateTealStrong,
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
                            colors = resolveSwitchColors(),
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
                    isPolicySupported = capabilities.isNotificationPolicySupported,
                    onToggleEnabled = onToggleNotificationControlEnabled,
                    onToggleAutoActivate = onToggleNotificationControlAutoActivate,
                    onToggleAllowCalls = onToggleNotificationControlAllowCalls,
                    onToggleAllowAlarms = onToggleNotificationControlAllowAlarms,
                    onGrantPolicyAccess = onOpenNotificationPolicySettings
                )
            }

            // 7. APPEARANCE & DAILY FOCUS GOAL
            item(key = "more_section_appearance_header") {
                SettingsSectionHeader(
                    title = "APPEARANCE & GOALS",
                    icon = ResolveIcons.Tune
                )
            }

            item(key = "more_appearance_goal_card") {
                AppThemeAndGoalCard(
                    appThemeId = appThemeId,
                    dailyFocusGoalMinutes = dailyFocusGoalMinutes,
                    showSetupTips = showSetupTips,
                    onSelectAppTheme = onSelectAppTheme,
                    onSelectDailyGoalMinutes = onSelectDailyGoalMinutes,
                    onToggleShowSetupTips = onToggleShowSetupTips
                )
            }

            // 8. FOCUS PROFILES & STRICT GUARD
            item(key = "more_section_profiles_guard_header") {
                SettingsSectionHeader(
                    title = "FOCUS GUARD & PROFILES",
                    icon = ResolveIcons.Shield
                )
            }

            item(key = "more_profiles_guard_card") {
                FocusProfilesAndGuardCard(
                    focusProfiles = focusProfiles,
                    antiBypassStrictGuard = antiBypassStrictGuard,
                    emergencyCooldownSeconds = emergencyCooldownSeconds,
                    installedApps = installedApps,
                    onSaveCustomProfile = onSaveCustomFocusProfile,
                    onDeleteCustomProfile = onDeleteCustomFocusProfile,
                    onToggleAntiBypassStrictGuard = onToggleAntiBypassStrictGuard,
                    onSelectEmergencyCooldownSeconds = onSelectEmergencyCooldownSeconds
                )
            }

            // 9. DIGITAL WELLBEING & CONTROLS
            item(key = "more_section_wellbeing_header") {
                SettingsSectionHeader(
                    title = "DIGITAL WELLBEING & CONTROLS",
                    icon = Icons.Outlined.Settings
                )
            }

            item(key = "more_wellbeing_controls_card") {
                AppUsageControlsAndWellbeingCard(
                    excludedPackages = excludedScreenTimePackages,
                    installedApps = installedApps,
                    dailyQuotaRules = dailyQuotaRules,
                    mindfulPauseConfig = mindfulPauseConfig,
                    contentBlockerConfig = contentBlockerConfig,
                    accountabilityPartnerConfig = accountabilityPartnerConfig,
                    onToggleExcludedPackage = onToggleExcludedScreenTimePackage,
                    onSaveDailyQuotaRule = onSaveDailyQuotaRule,
                    onDeleteDailyQuotaRule = onDeleteDailyQuotaRule,
                    onSaveMindfulPauseConfig = onSaveMindfulPauseConfig,
                    onSaveContentBlockerConfig = onSaveContentBlockerConfig,
                    onSaveAccountabilityPartner = onSaveAccountabilityPartner,
                    onClearAccountabilityPartnerPin = onClearAccountabilityPartnerPin,
                    dnsBlockedCount = dnsBlockedCount,
                    isFocusLockActive = isFocusLockActive,
                    onResetDnsBlockedCount = onResetDnsBlockedCount
                )
            }

            // 10. ABOUT RESOLVE (Inline Expandable/Collapsible Card at absolute bottom)
            item(key = "more_section_about_header") {
                SettingsSectionHeader(
                    title = "ABOUT RESOLVE",
                    icon = Icons.Outlined.Info
                )
            }

            item(key = "more_about_card") {
                val aboutSections = remember {
                    listOf(
                        "About RESOLVE" to listOf(
                            "RESOLVE is a calm, offline-first digital discipline shield designed to help you reclaim uninterrupted attention without nagging subscriptions or noisy feeds."
                        ),
                        "What RESOLVE Offers" to listOf(
                            "• Instant Lock: Restrict distracting apps immediately for your chosen focus timer.",
                            "• Delayed Lock: Enjoy a fixed free-use window followed by automatic Focus Lock.",
                            "• Schedule Lock: Automate One-Time, Daily, or Custom Weekly focus sessions.",
                            "• App Lock: Guard sensitive apps using PIN, Pattern, or 6-Cell Sequence.",
                            "• Flip Clock: Dedicated full-screen split-flap Clock, Timer, and Stopwatch.",
                            "• Focus Notification Control: Quiet interruptions during focus locks while preserving your prior DND state."
                        ),
                        "How RESOLVE Works" to listOf(
                            "Accessibility Focus Guard and Overlay permission let RESOLVE detect when a restricted app is opened and bring the Focus Lock or App Lock screen forward smoothly.",
                            "Persistent session deadlines survive device restarts and process stops."
                        ),
                        "How to Use RESOLVE Effectively" to listOf(
                            "• Allowlist only essential tools (Dialer, Calculator, Notes) before starting a lock.",
                            "• Save your Recovery Code securely so you can unlock early in genuine emergencies.",
                            "• Use Delayed Lock when you want a mindful finish-up countdown before focus begins."
                        ),
                        "Practical Tips & Multi-Device Sync" to listOf(
                            "Connect a Google account on two or more devices only if you want active focus locks synced automatically across them.",
                            "Mascots, Allowed Apps, Recovery Codes, Screen Time, and Insights stay private on each phone."
                        ),
                        "Developer Information" to listOf(
                            "Developed by Tahan Official",
                            "Package: ${BuildConfig.APPLICATION_ID}",
                            "Version: v${BuildConfig.VERSION_NAME}"
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 16.dp,
                            borderColor = palette.cardBorder
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
                                reducedMotion = effectiveReducedMotion,
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
                                        color = palette.champagneStrong,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(palette.champagneContainer)
                                            .border(1.dp, palette.champagne.copy(alpha = 0.35f), CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                KeyboardTypingText(
                                    fullText = "Your distraction killing is resolve duty.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = palette.emeraldStrong,
                                    highlightTipColor = palette.navPillContent,
                                    startTyping = isAboutVisible || aboutExpanded,
                                    startDelayMillis = 100,
                                    typingDurationMillis = 1950,
                                    reducedMotion = effectiveReducedMotion,
                                    alreadyCompleted = headerTypingDone,
                                    onTypingCompleted = { headerTypingDone = true }
                                )
                            }
                        }

                        Icon(
                            imageVector = if (aboutExpanded) ResolveIcons.ExpandLess else ResolveIcons.ExpandMore,
                            contentDescription = if (aboutExpanded) "Collapse About RESOLVE" else "Expand About RESOLVE",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Always visible directly below card header BEFORE expanding, keeping typing animation intact
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingStatusDot(color = palette.emeraldStrong, size = 5.dp, reducedMotion = effectiveReducedMotion)
                        KeyboardTypingText(
                            fullText = "Developed by Tahan Official",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.35.sp
                            ),
                            color = palette.textPrimary,
                            highlightTipColor = palette.navPillContent,
                            startTyping = isAboutVisible || aboutExpanded,
                            startDelayMillis = 2080,
                            typingDurationMillis = 1550,
                            reducedMotion = effectiveReducedMotion,
                            alreadyCompleted = devTypingDone,
                            onTypingCompleted = { devTypingDone = true },
                            modifier = Modifier.testTag("more_developer_credit")
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
                                    .background(palette.divider)
                            )

                            aboutSections.forEachIndexed { idx, (sectionTitle, sectionSentences) ->
                                val showSection = effectiveReducedMotion || expandedTypingStep >= idx
                                AnimatedVisibility(
                                    visible = showSection,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    InlineAboutSection(
                                        title = sectionTitle,
                                        sentences = sectionSentences,
                                        startTyping = aboutExpanded && expandedTypingStep >= idx,
                                        reducedMotion = effectiveReducedMotion,
                                        onSectionCompleted = {
                                            if (expandedTypingStep == idx) {
                                                expandedTypingStep = idx + 1
                                            }
                                        }
                                    )
                                }
                            }
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
                .background(palette.slateTealContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.slateTealStrong,
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
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Open $title",
            tint = palette.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun HealthDivider() {
    val palette = LocalResolveExtendedPalette.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = palette.divider
    )
}

@Composable
private fun InlineAboutSection(
    title: String,
    sentences: List<String>,
    startTyping: Boolean,
    reducedMotion: Boolean,
    onSectionCompleted: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var activeSentenceIdx by remember(sentences, startTyping) {
        mutableIntStateOf(if (reducedMotion) sentences.size else 0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceSecondary)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
        )
        sentences.forEachIndexed { idx, sentence ->
            val sentenceStarted = startTyping && (reducedMotion || activeSentenceIdx >= idx)
            val sentenceDurationMs = (sentence.length * 58).coerceIn(1650, 8200)
            KeyboardTypingText(
                fullText = sentence,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = palette.emeraldStrong,
                highlightTipColor = palette.navPillContent,
                startTyping = sentenceStarted,
                startDelayMillis = if (idx == 0) 180 else 210,
                typingDurationMillis = sentenceDurationMs,
                reducedMotion = reducedMotion,
                alreadyCompleted = reducedMotion || activeSentenceIdx > idx,
                onTypingCompleted = {
                    if (activeSentenceIdx == idx) {
                        val next = idx + 1
                        activeSentenceIdx = next
                        if (next >= sentences.size) {
                            onSectionCompleted()
                        }
                    }
                }
            )
        }
    }
}

