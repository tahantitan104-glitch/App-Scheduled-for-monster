package com.example.platform

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.BuildConfig
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.CompatibilityCheckItem
import com.example.domain.model.CompatibilityItemStatus
import com.example.domain.model.DeviceAnalysisReport
import com.example.domain.model.DeviceHardwareProfile
import com.example.domain.model.DeviceManagementOverview
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.OverallCompatibilityResult
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.SecurityCheckItem
import com.example.domain.time.ClockProvider
import java.io.File
import java.util.Locale

/**
 * Real device inspection engine verifying OS hardware properties, RESOLVE system compatibility,
 * limited on-device security checks (transparently falling back to Not Verified), setup/repair
 * items, and optional Device Owner status without inventing percentages or hardware identifiers.
 */
class DeviceAnalysisEngine(
    private val context: Context,
    private val capabilityChecker: SystemCapabilityChecker,
    private val clockProvider: ClockProvider
) {

    fun runAnalysis(): DeviceAnalysisReport {
        val status = capabilityChecker.queryStatus()
        val nowWall = clockProvider.wallClockMs()

        val hardware = inspectHardwareProfile()
        val compatibilityChecks = buildCompatibilityChecks(status)
        val securityChecks = inspectSecurityChecks(status)
        val setupRepairItems = compatibilityChecks.filter {
            it.status == CompatibilityItemStatus.ACTION_REQUIRED
        }
        val deviceManagement = buildDeviceManagementOverview(status.deviceOwnerState)

        val overallResult = evaluateOverallResult(status, hardware, compatibilityChecks)
        val summary = when (overallResult) {
            OverallCompatibilityResult.READY ->
                "All essential focus guards and system permissions are verified and ready."
            OverallCompatibilityResult.ACTION_REQUIRED ->
                "${setupRepairItems.size} system setting(s) need attention for reliable protection."
            OverallCompatibilityResult.LIMITED_COMPATIBILITY ->
                "Core enforcement is ready, though some optional system capabilities are restricted on this OS build."
            OverallCompatibilityResult.ANALYSIS_INCOMPLETE ->
                "System check partially completed; some platform properties were unavailable."
        }

        return DeviceAnalysisReport(
            analyzedAtWallMs = nowWall,
            overallResult = overallResult,
            summaryMessage = summary,
            hardware = hardware,
            compatibilityChecks = compatibilityChecks,
            securityChecks = securityChecks,
            setupRepairItems = setupRepairItems,
            deviceManagement = deviceManagement
        )
    }

    private fun inspectHardwareProfile(): DeviceHardwareProfile {
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Not Available"
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Not Available"
        val deviceName = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else null
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.DEVICE?.takeIf { it.isNotBlank() }
            ?: "Not Available"

        val androidVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "Not Available"
        val buildId = Build.DISPLAY?.takeIf { it.isNotBlank() }
            ?: Build.ID?.takeIf { it.isNotBlank() }
            ?: "Not Available"
        val cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Not Available"

        val memInfo = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        }.getOrNull()

        val totalRamLabel = if (memInfo != null && memInfo.totalMem > 0L) {
            formatMemoryGb(memInfo.totalMem)
        } else {
            "Not Available"
        }
        val availableRamLabel = if (memInfo != null && memInfo.availMem > 0L) {
            formatMemoryGb(memInfo.availMem)
        } else {
            "Not Available"
        }

        return DeviceHardwareProfile(
            manufacturer = manufacturer,
            model = model,
            deviceName = deviceName,
            androidVersion = "Android $androidVersion",
            apiLevel = Build.VERSION.SDK_INT,
            buildId = buildId,
            cpuAbi = cpuAbi,
            totalRamLabel = totalRamLabel,
            availableRamLabel = availableRamLabel,
            resolveVersion = "v${BuildConfig.VERSION_NAME}"
        )
    }

    private fun buildCompatibilityChecks(
        status: com.example.domain.model.SystemCapabilityStatus
    ): List<CompatibilityCheckItem> {
        val accItem = when (status.accessibilityHealthState) {
            AccessibilityHealthState.ENABLED_CONNECTED -> CompatibilityCheckItem(
                id = "accessibility",
                name = "Accessibility Service (Focus Guard)",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Enabled in Android Settings and actively connected."
            )
            AccessibilityHealthState.ENABLED_DISCONNECTED -> CompatibilityCheckItem(
                id = "accessibility",
                name = "Accessibility Service (Focus Guard)",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Enabled in Settings, but service is temporarily disconnected. Reconnect in Accessibility Settings.",
                actionLabel = "Fix Now",
                actionType = ProtectionRecoveryAction.OPEN_ACCESSIBILITY
            )
            AccessibilityHealthState.DISABLED -> CompatibilityCheckItem(
                id = "accessibility",
                name = "Accessibility Service (Focus Guard)",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Disabled. Required to detect restricted foreground apps during Focus Lock and App Lock.",
                actionLabel = "Fix Now",
                actionType = ProtectionRecoveryAction.OPEN_ACCESSIBILITY
            )
        }

        val overlayItem = CompatibilityCheckItem(
            id = "overlay",
            name = "Display Over Other Apps",
            status = if (status.canDrawOverlays) CompatibilityItemStatus.ACTIVE else CompatibilityItemStatus.ACTION_REQUIRED,
            detail = if (status.canDrawOverlays) {
                "Granted for bringing Lock Screen forward smoothly."
            } else {
                "Not granted. Android 10+ can block Lock Screen background launches without overlay permission."
            },
            actionLabel = if (status.canDrawOverlays) null else "Fix Now",
            actionType = if (status.canDrawOverlays) ProtectionRecoveryAction.NONE else ProtectionRecoveryAction.OPEN_OVERLAY
        )

        val usageItem = CompatibilityCheckItem(
            id = "usage",
            name = "Usage Access",
            status = if (status.hasUsageStatsAccess) CompatibilityItemStatus.ACTIVE else CompatibilityItemStatus.ACTION_REQUIRED,
            detail = if (status.hasUsageStatsAccess) {
                "Granted for fallback app detection and real screen time telemetry."
            } else {
                "Not granted. Enables real screen time tracking and OEM fallback protection."
            },
            actionLabel = if (status.hasUsageStatsAccess) null else "Fix Now",
            actionType = if (status.hasUsageStatsAccess) ProtectionRecoveryAction.NONE else ProtectionRecoveryAction.OPEN_USAGE_ACCESS
        )

        val batteryItem = when {
            status.androidSdkInt < Build.VERSION_CODES.M -> CompatibilityCheckItem(
                id = "battery",
                name = "Battery Optimization",
                status = CompatibilityItemStatus.NOT_REQUIRED,
                detail = "Not applicable on Android ${Build.VERSION.RELEASE} (Doze introduced in Android 6.0)."
            )
            status.isIgnoringBatteryOptimizations -> CompatibilityCheckItem(
                id = "battery",
                name = "Battery Optimization",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Unrestricted background execution permitted."
            )
            else -> CompatibilityCheckItem(
                id = "battery",
                name = "Battery Optimization",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Battery optimization active. OS may kill session timers or Accessibility Guard after screen off.",
                actionLabel = "Fix Now",
                actionType = ProtectionRecoveryAction.OPEN_BATTERY_OPTIMIZATION
            )
        }

        val alarmItem = when {
            !status.exactAlarmRequiredByOs -> CompatibilityCheckItem(
                id = "exact_alarm",
                name = "Alarms & Reminders",
                status = CompatibilityItemStatus.NOT_REQUIRED,
                detail = "Granted automatically on Android ${Build.VERSION.RELEASE}."
            )
            status.canScheduleExactAlarms -> CompatibilityCheckItem(
                id = "exact_alarm",
                name = "Alarms & Reminders",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Exact alarm scheduling enabled for Delayed and Scheduled locks."
            )
            else -> CompatibilityCheckItem(
                id = "exact_alarm",
                name = "Alarms & Reminders",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Required for exact Delayed Lock and Schedule Lock transitions.",
                actionLabel = "Fix Now",
                actionType = ProtectionRecoveryAction.OPEN_EXACT_ALARM
            )
        }

        val notifItem = CompatibilityCheckItem(
            id = "notifications",
            name = "App Notifications",
            status = if (status.notificationsEnabled) {
                CompatibilityItemStatus.ACTIVE
            } else {
                CompatibilityItemStatus.ACTION_REQUIRED
            },
            detail = if (status.notificationsEnabled) {
                "Active for live countdown & session recovery alerts."
            } else {
                "Disabled. Active focus timer notification may be hidden."
            },
            actionLabel = if (status.notificationsEnabled) null else "Fix Now",
            actionType = if (status.notificationsEnabled) {
                ProtectionRecoveryAction.NONE
            } else {
                ProtectionRecoveryAction.OPEN_NOTIFICATION_PERMISSION
            }
        )

        val notifPolicyItem = when {
            !status.isNotificationPolicySupported -> CompatibilityCheckItem(
                id = "notification_policy",
                name = "Notification Policy Access (DND)",
                status = CompatibilityItemStatus.UNAVAILABLE,
                detail = "Not Supported on This Device — Do Not Disturb Policy Access requires Android 6.0+."
            )
            status.hasNotificationPolicyAccess -> CompatibilityCheckItem(
                id = "notification_policy",
                name = "Notification Policy Access (DND)",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Granted for Focus Session Do Not Disturb control."
            )
            else -> CompatibilityCheckItem(
                id = "notification_policy",
                name = "Notification Policy Access (DND)",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Optional unless using Focus Session Notification Control.",
                actionLabel = "Fix Now",
                actionType = ProtectionRecoveryAction.OPEN_NOTIFICATION_POLICY
            )
        }

        return listOf(
            accItem,
            overlayItem,
            usageItem,
            alarmItem,
            notifItem,
            batteryItem,
            notifPolicyItem
        )
    }

    private fun inspectSecurityChecks(
        status: com.example.domain.model.SystemCapabilityStatus
    ): List<SecurityCheckItem> {
        val keyguard = runCatching {
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        }.getOrNull()
        val hasDeviceScreenLock = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguard?.isDeviceSecure
            } else {
                keyguard?.isKeyguardSecure
            }
        }.getOrNull()

        val lockScreenItem = when (hasDeviceScreenLock) {
            true -> SecurityCheckItem(
                id = "screen_lock",
                name = "Device Screen Lock",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Protected with PIN, pattern, or biometric lock."
            )
            false -> SecurityCheckItem(
                id = "screen_lock",
                name = "Device Screen Lock",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "No secure device lock screen detected."
            )
            null -> SecurityCheckItem(
                id = "screen_lock",
                name = "Device Screen Lock",
                status = CompatibilityItemStatus.NOT_VERIFIED,
                detail = "Could not verify OS keyguard state."
            )
        }

        val dpm = runCatching {
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        }.getOrNull()
        val encStatus = runCatching { dpm?.storageEncryptionStatus }.getOrNull()
        val encryptionItem = when (encStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> SecurityCheckItem(
                id = "encryption",
                name = "Storage Encryption",
                status = CompatibilityItemStatus.ACTIVE,
                detail = "Android file-based or full-disk encryption active."
            )
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> SecurityCheckItem(
                id = "encryption",
                name = "Storage Encryption",
                status = CompatibilityItemStatus.ACTION_REQUIRED,
                detail = "Device reports storage encryption inactive."
            )
            else -> SecurityCheckItem(
                id = "encryption",
                name = "Storage Encryption",
                status = CompatibilityItemStatus.NOT_VERIFIED,
                detail = "Not reported directly by OS policy manager."
            )
        }

        val patchLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() }
        } else null
        val patchItem = SecurityCheckItem(
            id = "security_patch",
            name = "Android Security Patch",
            status = if (patchLevel != null) CompatibilityItemStatus.ACTIVE else CompatibilityItemStatus.NOT_VERIFIED,
            detail = patchLevel ?: "Not Verified"
        )

        val ownerItem = SecurityCheckItem(
            id = "device_admin",
            name = "Device Owner / Admin Authority",
            status = when (status.deviceOwnerState) {
                DeviceOwnerState.DEVICE_OWNER_ACTIVE -> CompatibilityItemStatus.ACTIVE
                DeviceOwnerState.DEVICE_ADMIN_ONLY -> CompatibilityItemStatus.NOT_REQUIRED
                DeviceOwnerState.NOT_PROVISIONED -> CompatibilityItemStatus.NOT_REQUIRED
            },
            detail = when (status.deviceOwnerState) {
                DeviceOwnerState.DEVICE_OWNER_ACTIVE -> "Provisioned as Device Owner (Lock Task capable)."
                DeviceOwnerState.DEVICE_ADMIN_ONLY -> "Device Admin enabled; optional Device Owner not provisioned."
                DeviceOwnerState.NOT_PROVISIONED -> "Not provisioned (optional; standard RESOLVE runs via Accessibility Guard)."
            }
        )

        val rootHeuristic = runCatching {
            val testKeys = Build.TAGS?.contains("test-keys") == true
            val suFound = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any { File(it).exists() }
            when {
                suFound -> CompatibilityItemStatus.NOT_VERIFIED
                testKeys -> CompatibilityItemStatus.NOT_VERIFIED
                else -> CompatibilityItemStatus.ACTIVE
            }
        }.getOrNull()
        val rootCheckItem = SecurityCheckItem(
            id = "environment_integrity",
            name = "OS Integrity (Heuristic)",
            status = if (rootHeuristic == CompatibilityItemStatus.ACTIVE) {
                CompatibilityItemStatus.ACTIVE
            } else {
                CompatibilityItemStatus.NOT_VERIFIED
            },
            detail = if (rootHeuristic == CompatibilityItemStatus.ACTIVE) {
                "Standard release environment detected (basic heuristic check only; hardware attestation not run)."
            } else {
                "Not Verified — custom/test build keys or root binary indicators detected."
            }
        )

        return listOf(lockScreenItem, encryptionItem, patchItem, ownerItem, rootCheckItem)
    }

    private fun buildDeviceManagementOverview(ownerState: DeviceOwnerState): DeviceManagementOverview =
        when (ownerState) {
            DeviceOwnerState.DEVICE_OWNER_ACTIVE -> DeviceManagementOverview(
                ownerState = ownerState,
                statusLabel = "Active (Optional Enterprise Mode)",
                explanation = "Device Owner Lock Task Mode is provisioned on this device.",
                isRequiredForStandardUse = false
            )
            DeviceOwnerState.DEVICE_ADMIN_ONLY -> DeviceManagementOverview(
                ownerState = ownerState,
                statusLabel = "Not Required for Standard RESOLVE Operation",
                explanation = "Device Admin alone does not enable Lock Task pinning; standard Accessibility Guard handles daily locks cleanly.",
                isRequiredForStandardUse = false
            )
            DeviceOwnerState.NOT_PROVISIONED -> DeviceManagementOverview(
                ownerState = ownerState,
                statusLabel = "Not Required for Standard RESOLVE Operation",
                explanation = "RESOLVE operates cleanly on everyday phones without ADB Device Owner setup.",
                isRequiredForStandardUse = false
            )
        }

    private fun evaluateOverallResult(
        status: com.example.domain.model.SystemCapabilityStatus,
        hardware: DeviceHardwareProfile,
        checks: List<CompatibilityCheckItem>
    ): OverallCompatibilityResult {
        val coreIds = setOf("accessibility", "overlay", "exact_alarm", "notifications", "battery")
        val coreIssues = checks.count { it.id in coreIds && it.status == CompatibilityItemStatus.ACTION_REQUIRED }
        val hardwareUnknown = hardware.manufacturer == "Not Available" || hardware.model == "Not Available"
        return when {
            hardwareUnknown -> OverallCompatibilityResult.ANALYSIS_INCOMPLETE
            !status.canEnforceRealLock || status.accessibilityHealthState == AccessibilityHealthState.DISABLED ->
                OverallCompatibilityResult.ACTION_REQUIRED
            coreIssues > 0 ->
                OverallCompatibilityResult.ACTION_REQUIRED
            checks.any { it.status == CompatibilityItemStatus.UNAVAILABLE } ->
                OverallCompatibilityResult.LIMITED_COMPATIBILITY
            else -> OverallCompatibilityResult.READY
        }
    }

    private fun formatMemoryGb(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB", gb)
    }
}
