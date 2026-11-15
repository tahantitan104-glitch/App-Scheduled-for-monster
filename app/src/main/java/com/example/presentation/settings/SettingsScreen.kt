package com.example.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.EnforcementMode
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.PulsingStatusDot
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard

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
    modifier: Modifier = Modifier
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

    val readyCount = listOf(
        capabilities.isAccessibilityServiceEnabled,
        capabilities.canDrawOverlays,
        capabilities.hasUsageStatsAccess,
        !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms,
        capabilities.notificationsEnabled,
        capabilities.isIgnoringBatteryOptimizations
    ).count { it }

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
            }

            item {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ACCESS & PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = ResolveColors.MintAccent
                )
            }

            // 1. Accessibility Service (Primary Lock Enforcement for Personal APK)
            item {
                val enabled = capabilities.isAccessibilityServiceEnabled
                PermissionItemCard(
                    name = "Accessibility Service",
                    reason = "Detects foreground apps during Lock Mode and blocks restricted apps without Device Owner.",
                    status = if (enabled) "Enabled" else "Not Enabled",
                    isReady = enabled,
                    statusColor = if (enabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                    actionLabel = if (enabled) "Manage Accessibility" else "Enable Accessibility",
                    onAction = onOpenAccessibilitySettings,
                    testTag = "settings_accessibility_action"
                )
            }

            // 2. Display Over Other Apps
            item {
                val enabled = capabilities.canDrawOverlays
                PermissionItemCard(
                    name = "Display Over Other Apps",
                    reason = "Required on Android 10+ to open the Lock Screen automatically over active apps.",
                    status = if (enabled) "Enabled" else "Not Enabled",
                    isReady = enabled,
                    statusColor = if (enabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                    actionLabel = if (enabled) null else "Enable Overlay",
                    onAction = onOpenOverlaySettings,
                    testTag = "settings_overlay_action"
                )
            }

            // 3. Usage Access
            item {
                val enabled = capabilities.hasUsageStatsAccess
                PermissionItemCard(
                    name = "Usage Access",
                    reason = "Provides fallback foreground app detection if Accessibility events are delayed.",
                    status = if (enabled) "Enabled" else "Not Enabled",
                    isReady = enabled,
                    statusColor = if (enabled) ResolveColors.MintAccent else ResolveColors.AmberWarn,
                    actionLabel = if (enabled) null else "Enable Usage Access",
                    onAction = onOpenUsageAccessSettings,
                    testTag = "settings_usage_access_action"
                )
            }

            // 4. Alarms & Reminders
            item {
                val exactReady = !capabilities.exactAlarmRequiredByOs || capabilities.canScheduleExactAlarms
                val (statusText, statusColor, showAction) = when {
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
                    status = statusText,
                    isReady = exactReady,
                    statusColor = statusColor,
                    actionLabel = if (showAction) "Enable Access" else null,
                    onAction = onOpenExactAlarmSettings,
                    testTag = "settings_exact_alarm_action"
                )
            }

            // 5. Notifications
            item {
                val statusText = if (capabilities.notificationsEnabled) "Enabled" else "Not Enabled"
                val statusColor = if (capabilities.notificationsEnabled) ResolveColors.MintAccent else ResolveColors.AmberWarn
                PermissionItemCard(
                    name = "Notifications",
                    reason = "Required to keep the active session timer service running reliably.",
                    status = statusText,
                    isReady = capabilities.notificationsEnabled,
                    statusColor = statusColor,
                    actionLabel = if (capabilities.notificationsEnabled) null else "Enable Notifications",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onOpenNotificationSettings()
                        }
                    },
                    testTag = "settings_notifications_action"
                )
            }

            // 6. Unrestricted Battery
            item {
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
            }

            // 7. Device Management (Optional Device Owner Lock Task)
            item {
                val (statusText, statusColor) = when (capabilities.deviceOwnerState) {
                    DeviceOwnerState.DEVICE_OWNER_ACTIVE -> "Active" to ResolveColors.MintAccent
                    DeviceOwnerState.DEVICE_ADMIN_ONLY -> "Admin Only" to ResolveColors.AmberWarn
                    DeviceOwnerState.NOT_PROVISIONED -> "Optional (Not Set Up)" to ResolveColors.TextSecondary
                }
                PermissionItemCard(
                    name = "Device Management",
                    reason = "Optional OS-level Lock Task Mode via ADB Device Owner provisioning.",
                    status = statusText,
                    isReady = capabilities.deviceOwnerState == DeviceOwnerState.DEVICE_OWNER_ACTIVE,
                    statusColor = statusColor,
                    actionLabel = "View Setup Guide",
                    onAction = onOpenDeviceOwnerGuide,
                    testTag = "settings_device_management_action"
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "RECOVERY",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = ResolveColors.MintAccent
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
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "APPLICATION INFORMATION",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = ResolveColors.MintAccent
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(cornerRadius = 20.dp)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow(label = "Application", value = "Resolve")
                    InfoRow(label = "Version", value = BuildConfig.VERSION_NAME)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Developed by \"Tahan Official\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = ResolveColors.MintAccent,
                        modifier = Modifier.testTag("settings_developer_credit")
                    )
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = ResolveColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = ResolveColors.TextPrimary)
    }
}
