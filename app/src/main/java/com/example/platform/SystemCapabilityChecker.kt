package com.example.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.example.accessibility.ResolveAccessibilityService
import com.example.devicepolicy.ResolveDeviceAdminReceiver
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.SystemCapabilityStatus

class SystemCapabilityChecker(private val context: Context) {

    private val dpm: DevicePolicyManager?
        get() = runCatching {
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        }.getOrNull()

    private val activityManager: ActivityManager?
        get() = runCatching {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull()

    private val alarmManager: AlarmManager?
        get() = runCatching {
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        }.getOrNull()

    private val powerManager: PowerManager?
        get() = runCatching {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }.getOrNull()

    private val notificationManager: NotificationManager?
        get() = runCatching {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        }.getOrNull()

    fun queryStatus(): SystemCapabilityStatus {
        val packageName = context.packageName
        val adminComponent = ComponentName(context, ResolveDeviceAdminReceiver::class.java)
        val devicePolicy = dpm
        val isDeviceOwner = runCatching { devicePolicy?.isDeviceOwnerApp(packageName) == true }.getOrDefault(false)
        val isAdminActive = runCatching { devicePolicy?.isAdminActive(adminComponent) == true }.getOrDefault(false)

        val ownerState = when {
            isDeviceOwner -> DeviceOwnerState.DEVICE_OWNER_ACTIVE
            isAdminActive -> DeviceOwnerState.DEVICE_ADMIN_ONLY
            else -> DeviceOwnerState.NOT_PROVISIONED
        }

        val lockTaskPermitted = runCatching {
            devicePolicy?.isLockTaskPermitted(packageName) == true
        }.getOrDefault(false)

        val lockTaskModeState = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityManager?.lockTaskModeState ?: 0
            } else {
                @Suppress("DEPRECATION")
                if (activityManager?.isInLockTaskMode == true) 1 else 0
            }
        }.getOrDefault(0)

        val exactAlarmRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val canScheduleExactAlarms = if (exactAlarmRequired) {
            runCatching { alarmManager?.canScheduleExactAlarms() == true }.getOrDefault(false)
        } else {
            true
        }

        val notificationsEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)

        val isConnected = isAccessibilityServiceConnected()
        val isEnabledInSettings = isAccessibilityEnabledInSettings()
        val accHealthState = when {
            isConnected || isEnabledInSettings -> AccessibilityHealthState.ENABLED_CONNECTED
            else -> AccessibilityHealthState.DISABLED
        }
        val notificationPolicySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

        return SystemCapabilityStatus(
            deviceOwnerState = ownerState,
            isLockTaskPermittedForApp = lockTaskPermitted,
            lockTaskModeState = lockTaskModeState,
            isAccessibilityServiceEnabled = isConnected || isEnabledInSettings,
            isAccessibilityEnabledInSettings = isEnabledInSettings,
            isAccessibilityServiceConnected = isConnected,
            accessibilityHealthState = accHealthState,
            canDrawOverlays = canDrawOverlays(),
            hasUsageStatsAccess = hasUsageStatsAccess(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            canScheduleExactAlarms = canScheduleExactAlarms,
            exactAlarmRequiredByOs = exactAlarmRequired,
            notificationsEnabled = notificationsEnabled,
            hasNotificationPolicyAccess = hasNotificationPolicyAccess(),
            androidSdkInt = Build.VERSION.SDK_INT,
            isNotificationPolicySupported = notificationPolicySupported,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty()
        )
    }

    fun isAccessibilityServiceConnected(): Boolean = ResolveAccessibilityService.isConnectedFlow.value

    fun isAccessibilityEnabledInSettings(): Boolean {
        val expectedComponent = ComponentName(context, ResolveAccessibilityService::class.java)
        val expectedFlat = expectedComponent.flattenToString()
        val expectedShort = expectedComponent.flattenToShortString()

        val enabledInManager = runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()
            enabledList.any { info ->
                val serviceId = info.id.orEmpty()
                serviceId.equals(expectedFlat, ignoreCase = true) ||
                    serviceId.equals(expectedShort, ignoreCase = true) ||
                    serviceId.contains(ResolveAccessibilityService::class.java.name)
            }
        }.getOrDefault(false)
        if (enabledInManager) return true

        return runCatching {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            settingValue.split(':').any { entry ->
                entry.equals(expectedFlat, ignoreCase = true) ||
                    entry.equals(expectedShort, ignoreCase = true) ||
                    entry.contains(ResolveAccessibilityService::class.java.name)
            }
        }.getOrDefault(false)
    }

    fun isAccessibilityServiceEnabled(): Boolean =
        isAccessibilityServiceConnected() || isAccessibilityEnabledInSettings()

    fun getAccessibilityHealthState(): AccessibilityHealthState = when {
        isAccessibilityServiceConnected() || isAccessibilityEnabledInSettings() ->
            AccessibilityHealthState.ENABLED_CONNECTED
        else -> AccessibilityHealthState.DISABLED
    }

    fun hasNotificationPolicyAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            notificationManager?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)
    }

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        } else {
            true
        }

    fun hasUsageStatsAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return@runCatching false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun isIgnoringBatteryOptimizations(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }.getOrDefault(false)
        } else {
            true
        }

    fun openAccessibilitySettings(): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent) || openGeneralSettings()
    }

    fun openNotificationPolicySettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return openNotificationSettings()
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent) || openGeneralSettings()
    }

    fun openOverlaySettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return openAppDetailsSettings()
        }
        val specific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val generic = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(specific) || startSafe(generic) || openAppDetailsSettings()
    }

    fun openUsageAccessSettings(): Boolean {
        val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val generic = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(specific) || startSafe(generic) || openGeneralSettings()
    }

    fun openBatteryOptimizationSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return openAppDetailsSettings()
        }
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(requestIntent) || startSafe(listIntent) || openAppDetailsSettings()
    }

    fun openExactAlarmSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent) || openAppDetailsSettings()
    }

    fun openNotificationSettings(): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return startSafe(intent) || openAppDetailsSettings()
    }

    fun openAppDetailsSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent)
    }

    private fun openGeneralSettings(): Boolean {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent)
    }

    private fun startSafe(intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
}
