package com.example.presentation.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.EnforcementMode
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
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
    onClearProfilePhoto: () -> Unit = {}
) {
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

    val readyCount = listOf(
        capabilities.isAccessibilityServiceEnabled,
        capabilities.canDrawOverlays,
        capabilities.hasUsageStatsAccess,
        !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms,
        capabilities.notificationsEnabled,
        capabilities.isIgnoringBatteryOptimizations
    ).count { it }
    val allCorePermissionsReady = readyCount == 6
    var permissionsExpanded by remember { mutableStateOf(!allCorePermissionsReady) }
    LaunchedEffect(allCorePermissionsReady) {
        permissionsExpanded = !allCorePermissionsReady
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResolveColors.ForestDeep),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ResolveColors.TextPrimary
                )
            }

            // Readiness Overview Hero Card
            item {
                val isGuardActive = capabilities.canEnforceRealLock
                val tierLabel = when (capabilities.activeEnforcementMode) {
                    EnforcementMode.DEVICE_OWNER_LOCK_TASK -> "Tier 1: Device Owner Lock Task"
                    EnforcementMode.ACCESSIBILITY_GUARD -> "Tier 2: Accessibility Focus Guard"
                    EnforcementMode.NONE -> "Action Required: Enable Accessibility Service"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isGuardActive) {
                                    listOf(ResolveColors.MintSoftContainer, ResolveColors.ForestSurface)
                                } else {
                                    listOf(ResolveColors.AmberContainer.copy(alpha = 0.7f), ResolveColors.ForestSurface)
                                }
                            )
                        )
                        .border(
                            1.dp,
                            if (isGuardActive) ResolveColors.MintAccent.copy(alpha = 0.45f)
                            else ResolveColors.AmberWarn.copy(alpha = 0.45f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ResolveColors.ForestDeep.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ResolveIcons.Shield,
                            contentDescription = null,
                            tint = if (isGuardActive) ResolveColors.MintAccent else ResolveColors.AmberWarn,
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
                                PulsingStatusDot(color = ResolveColors.MintAccent, size = 6.dp)
                            }
                            Text(
                                text = tierLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = ResolveColors.TextPrimary
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$readyCount / 6 system permissions ready",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (isGuardActive) ResolveColors.MintAccent else ResolveColors.AmberWarn
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FeatureTipNote(
                    text = "Tip: Accessibility & Overlay keep Lock Mode active across other apps.",
                    icon = Icons.Outlined.Info
                )
            }

            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "1 · ACCOUNT & MULTI-DEVICE SYNC",
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
                    onClearAvatar = onClearProfilePhoto
                )
            }

            item {
                Spacer(Modifier.height(2.dp))
                SettingsSectionHeader(
                    title = "2 · PROTECTION & PERMISSIONS",
                    icon = ResolveIcons.Shield
                )
            }

            item {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (permissionsExpanded) 180f else 0f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "permissions_chevron_rotation"
                )
                val headerBorderColor by animateColorAsState(
                    targetValue = if (allCorePermissionsReady) {
                        ResolveColors.MintAccent.copy(alpha = 0.40f)
                    } else {
                        ResolveColors.AmberWarn.copy(alpha = 0.42f)
                    },
                    animationSpec = tween(240),
                    label = "permissions_header_border"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(ResolveColors.ForestSurface.copy(alpha = 0.72f))
                        .border(1.dp, headerBorderColor, RoundedCornerShape(22.dp))
                        .animateContentSize(animationSpec = tween(260, easing = FastOutSlowInEasing))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(role = Role.Button) {
                                permissionsExpanded = !permissionsExpanded
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_permissions_dropdown_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (allCorePermissionsReady) {
                                        ResolveColors.MintSoftContainer.copy(alpha = 0.85f)
                                    } else {
                                        ResolveColors.AmberContainer.copy(alpha = 0.65f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (allCorePermissionsReady) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    ResolveIcons.Shield
                                },
                                contentDescription = null,
                                tint = if (allCorePermissionsReady) {
                                    ResolveColors.MintAccent
                                } else {
                                    ResolveColors.AmberWarn
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (allCorePermissionsReady) {
                                    "System Permissions Active"
                                } else {
                                    "Permissions Setup"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = ResolveColors.TextPrimary
                            )
                            Text(
                                text = when {
                                    allCorePermissionsReady && !permissionsExpanded ->
                                        "6 / 6 ready • Hidden (tap to view)"
                                    allCorePermissionsReady ->
                                        "6 / 6 ready • Tap to hide"
                                    permissionsExpanded ->
                                        "$readyCount / 6 ready • Tap to collapse"
                                    else ->
                                        "$readyCount / 6 ready • Tap to expand"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (allCorePermissionsReady) {
                                    ResolveColors.MintAccent
                                } else {
                                    ResolveColors.AmberWarn
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ResolveColors.ForestSurfaceElevated.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.ExpandMore,
                                contentDescription = if (permissionsExpanded) {
                                    "Collapse permissions list"
                                } else {
                                    "Expand permissions list"
                                },
                                tint = ResolveColors.TextPrimary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = chevronRotation }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = permissionsExpanded,
                        enter = expandVertically(
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(220)),
                        exit = shrinkVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(160))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .testTag("settings_permissions_dropdown_content"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Accessibility Service (Primary Lock Enforcement for Personal APK)
                            val accEnabled = capabilities.isAccessibilityServiceEnabled
                            PermissionItemCard(
                                name = "Accessibility Service",
                                reason = "Detects foreground apps during Lock Mode and blocks restricted apps without Device Owner.",
                                status = if (accEnabled) "Enabled" else "Not Enabled",
                                isReady = accEnabled,
                                statusColor = if (accEnabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                                actionLabel = if (accEnabled) "Manage Accessibility" else "Enable Accessibility",
                                onAction = onOpenAccessibilitySettings,
                                testTag = "settings_accessibility_action"
                            )

                            // 2. Display Over Other Apps
                            val overlayEnabled = capabilities.canDrawOverlays
                            PermissionItemCard(
                                name = "Display Over Other Apps",
                                reason = "Required on Android 10+ to open the Lock Screen automatically over active apps.",
                                status = if (overlayEnabled) "Enabled" else "Not Enabled",
                                isReady = overlayEnabled,
                                statusColor = if (overlayEnabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                                actionLabel = if (overlayEnabled) null else "Enable Overlay",
                                onAction = onOpenOverlaySettings,
                                testTag = "settings_overlay_action"
                            )

                            // 3. Usage Access
                            val usageEnabled = capabilities.hasUsageStatsAccess
                            PermissionItemCard(
                                name = "Usage Access",
                                reason = "Provides fallback foreground app detection if Accessibility events are delayed.",
                                status = if (usageEnabled) "Enabled" else "Not Enabled",
                                isReady = usageEnabled,
                                statusColor = if (usageEnabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                                actionLabel = if (usageEnabled) null else "Enable Usage Access",
                                onAction = onOpenUsageAccessSettings,
                                testTag = "settings_usage_access_action"
                            )

                            // 4. Alarms & Reminders
                            val exactReady = !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms
                            val (exactText, exactColor, showExactAction) = when {
                                !capabilities.exactAlarmRequiredByOs ->
                                    Triple("Not Required", ResolveColors.MintAccent, false)
                                capabilities.canScheduleExactAlarms ->
                                    Triple("Enabled", ResolveColors.MintAccent, false)
                                else ->
                                    Triple("Not Enabled", ResolveColors.AmberWarn, true)
                            }
                            PermissionItemCard(
                                name = "Alarms & Reminders",
                                reason = "Required for precise Delayed Lock and Schedule Lock transitions.",
                                status = exactText,
                                isReady = exactReady,
                                statusColor = exactColor,
                                actionLabel = if (showExactAction) "Enable Access" else null,
                                onAction = onOpenExactAlarmSettings,
                                testTag = "settings_exact_alarm_action"
                            )

                            // 5. Notifications
                            val notifStatus = if (capabilities.notificationsEnabled) "Enabled" else "Not Enabled"
                            val notifColor = if (capabilities.notificationsEnabled) ResolveColors.MintAccent else ResolveColors.AmberWarn
                            PermissionItemCard(
                                name = "Notifications",
                                reason = "Required to keep the active session timer service running reliably.",
                                status = notifStatus,
                                isReady = capabilities.notificationsEnabled,
                                statusColor = notifColor,
                                actionLabel = if (capabilities.notificationsEnabled) null else "Enable Notifications",
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

                            // 6. Unrestricted Battery
                            val unrestricted = capabilities.isIgnoringBatteryOptimizations
                            PermissionItemCard(
                                name = "Unrestricted Battery",
                                reason = "Prevents the system from stopping timers or Accessibility guard in the background.",
                                status = if (unrestricted) "Unrestricted" else "Optimized",
                                isReady = unrestricted,
                                statusColor = if (unrestricted) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                                actionLabel = if (unrestricted) null else "Allow Unrestricted",
                                onAction = onOpenBatteryOptimizationSettings,
                                testTag = "settings_battery_action"
                            )

                            // 7. Device Management (Optional Device Owner Lock Task)
                            val (ownerStatus, ownerColor) = when (capabilities.deviceOwnerState) {
                                DeviceOwnerState.DEVICE_OWNER_ACTIVE -> "Active" to ResolveColors.MintAccent
                                DeviceOwnerState.DEVICE_ADMIN_ONLY -> "Admin Only" to ResolveColors.AmberWarn
                                DeviceOwnerState.NOT_PROVISIONED -> "Optional (Not Set Up)" to ResolveColors.TextSecondary
                            }
                            PermissionItemCard(
                                name = "Device Management",
                                reason = "Optional OS-level Lock Task Mode via ADB Device Owner provisioning.",
                                status = ownerStatus,
                                isReady = capabilities.deviceOwnerState == DeviceOwnerState.DEVICE_OWNER_ACTIVE,
                                statusColor = ownerColor,
                                actionLabel = "View Setup Guide",
                                onAction = onOpenDeviceOwnerGuide,
                                testTag = "settings_device_management_action"
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SettingsSectionHeader(
                    title = "3 · APPEARANCE & COMPANION",
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
                Spacer(Modifier.height(6.dp))
                SettingsSectionHeader(
                    title = "4 · EMERGENCY EXIT",
                    icon = ResolveIcons.Key
                )
            }

            item {
                PermissionItemCard(
                    name = "Recovery Code",
                    reason = "Ends an active restricted session when verified.",
                    status = if (hasRecoveryCode) "$recoveryCodeLength-digit Configured" else "Not Configured",
                    isReady = hasRecoveryCode,
                    statusColor = if (hasRecoveryCode) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                    actionLabel = if (hasRecoveryCode) "Update Code" else "Configure Code",
                    onAction = { showRecoverySetup = true },
                    testTag = "settings_recovery_action"
                )
                Spacer(Modifier.height(4.dp))
                FeatureTipNote(
                    text = "Note: Save your code safely in case you need an early unlock.",
                    icon = ResolveIcons.Key
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                SettingsSectionHeader(
                    title = "5 · ABOUT",
                    icon = Icons.Outlined.Info
                )
            }

            item {
                CraftedSignatureCard(
                    versionName = BuildConfig.VERSION_NAME,
                    reducedMotion = mascotPreferences.reducedMotionEnabled
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
private fun PermissionItemCard(
    name: String,
    reason: String,
    status: String,
    isReady: Boolean,
    statusColor: Color,
    actionLabel: String?,
    onAction: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 20.dp,
                borderColor = if (isReady) ResolveColors.ForestOutline else ResolveColors.AmberWarn.copy(alpha = 0.25f)
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
                color = ResolveColors.TextPrimary
            )
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
            color = ResolveColors.TextSecondary
        )
        if (actionLabel != null) {
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ResolveColors.MintAccent),
                border = androidx.compose.foundation.BorderStroke(1.dp, ResolveColors.MintAccent.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ResolveColors.MintAccent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.25.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = ResolveColors.MintAccent
        )
    }
}

@Composable
private fun CraftedSignatureCard(
    versionName: String,
    reducedMotion: Boolean
) {
    var hasEnteredView by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (!hasEnteredView && coords.isAttached && coords.size.height > 0) {
                    hasEnteredView = true
                }
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ResolveColors.ForestSurfaceElevated,
                        ResolveColors.MintSoftContainer.copy(alpha = 0.28f),
                        ResolveColors.ForestDeep
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    colors = listOf(
                        ResolveColors.MintAccent.copy(alpha = 0.45f),
                        ResolveColors.ForestOutline,
                        ResolveColors.MintBright.copy(alpha = 0.28f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .padding(20.dp),
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
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(ResolveColors.ForestBrandDeep)
                        .border(
                            1.dp,
                            ResolveColors.MintAccent.copy(alpha = 0.36f),
                            RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FocusShieldEmblem(
                        size = 34.dp,
                        testTagName = "settings_about_distraction_emblem"
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "RESOLVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        ),
                        color = ResolveColors.TextPrimary
                    )
                    KeyboardTypingText(
                        fullText = "Your distraction killing is resolve duty.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ResolveColors.MintAccent,
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
                color = ResolveColors.MintBright,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ResolveColors.ForestDeep.copy(alpha = 0.8f))
                    .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Hairline luminous divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ResolveColors.MintAccent.copy(alpha = 0.42f),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingStatusDot(color = ResolveColors.MintBright, size = 5.dp)
                KeyboardTypingText(
                    fullText = "Developed by Tahan Official",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.35.sp
                    ),
                    color = ResolveColors.MintBright,
                    startTyping = hasEnteredView,
                    startDelayMillis = 920,
                    typingDurationMillis = 680,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.testTag("settings_developer_credit")
                )
            }
        }
    }
}

/**
 * Reveals text character-by-character like smooth keyboard typing when scrolled into view,
 * reserving final layout bounds via transparent spans so lines never jump or clip.
 */
@Composable
private fun KeyboardTypingText(
    fullText: String,
    style: TextStyle,
    color: Color,
    startTyping: Boolean,
    startDelayMillis: Int,
    typingDurationMillis: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    highlightTipColor: Color = ResolveColors.MintBright
) {
    val progress = remember(fullText) { Animatable(if (reducedMotion) 1f else 0f) }
    var showCursor by remember(fullText) { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(startTyping, reducedMotion, fullText) {
        if (reducedMotion) {
            showCursor = false
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (startTyping && progress.value < 1f) {
            if (startDelayMillis > 0) {
                delay(startDelayMillis.toLong())
            }
            showCursor = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = typingDurationMillis,
                    easing = LinearEasing
                )
            )
            delay(260L)
            showCursor = false
        }
    }

    val visibleCharCount = if (reducedMotion) {
        fullText.length
    } else {
        (progress.value * fullText.length).roundToInt().coerceIn(0, fullText.length)
    }

    val styledText = buildAnnotatedString {
        if (visibleCharCount > 0) {
            val freshStart = (visibleCharCount - 2).coerceAtLeast(0)
            if (visibleCharCount < fullText.length && freshStart > 0) {
                withStyle(SpanStyle(color = color)) {
                    append(fullText.substring(0, freshStart))
                }
                withStyle(SpanStyle(color = highlightTipColor)) {
                    append(fullText.substring(freshStart, visibleCharCount))
                }
            } else {
                withStyle(SpanStyle(color = color)) {
                    append(fullText.substring(0, visibleCharCount))
                }
            }
        }
        if (visibleCharCount < fullText.length) {
            withStyle(SpanStyle(color = Color.Transparent)) {
                append(fullText.substring(visibleCharCount))
            }
        }
    }

    Text(
        text = styledText,
        style = style,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.drawWithContent {
            drawContent()
            if (showCursor && !reducedMotion) {
                val layout = textLayoutResult ?: return@drawWithContent
                runCatching {
                    val safeIndex = visibleCharCount.coerceIn(0, fullText.length)
                    val caretRect = layout.getCursorRect(safeIndex)
                    val caretHeight = (caretRect.height - 3f).coerceAtLeast(8f)
                    drawRect(
                        color = highlightTipColor,
                        topLeft = Offset(caretRect.left, caretRect.top + 1.5f),
                        size = Size(width = 1.8.dp.toPx(), height = caretHeight)
                    )
                }
            }
        }
    )
}

/**
 * Minimalist geometric emblem featuring an octagonal guardian shield, an open precision focus ring,
 * and a luminous diagonal focus beam with a centered core point—symbolizing calm distraction defense.
 */
@Composable
private fun FocusShieldEmblem(
    size: Dp,
    testTagName: String
) {
    Canvas(
        modifier = Modifier
            .size(size)
            .testTag(testTagName)
    ) {
        val w = this.size.width
        val h = this.size.height

        // 1. Sleek geometric guardian shield crest
        val shieldPath = Path().apply {
            moveTo(w * 0.50f, h * 0.10f)
            lineTo(w * 0.84f, h * 0.24f)
            lineTo(w * 0.80f, h * 0.62f)
            cubicTo(
                w * 0.78f, h * 0.77f,
                w * 0.64f, h * 0.87f,
                w * 0.50f, h * 0.92f
            )
            cubicTo(
                w * 0.36f, h * 0.87f,
                w * 0.22f, h * 0.77f,
                w * 0.20f, h * 0.62f
            )
            lineTo(w * 0.16f, h * 0.24f)
            close()
        }
        drawPath(
            path = shieldPath,
            color = ResolveColors.MintSoftContainer.copy(alpha = 0.42f)
        )
        drawPath(
            path = shieldPath,
            color = ResolveColors.MintAccent,
            style = Stroke(width = w * 0.058f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 2. Open precision focus ring inside the shield
        drawArc(
            color = Color(0xFFEAF7F1),
            startAngle = -35f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(w * 0.31f, h * 0.32f),
            size = Size(w * 0.38f, h * 0.38f),
            style = Stroke(width = w * 0.056f, cap = StrokeCap.Round)
        )

        // 3. Ascending geometric focus spark & center core dot
        drawLine(
            color = ResolveColors.MintBright,
            start = Offset(w * 0.50f, h * 0.50f),
            end = Offset(w * 0.69f, h * 0.29f),
            strokeWidth = w * 0.056f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = ResolveColors.MintBright,
            radius = w * 0.068f,
            center = Offset(w * 0.50f, h * 0.51f)
        )
    }
}

