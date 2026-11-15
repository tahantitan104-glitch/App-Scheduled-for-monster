package com.example.devicepolicy

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.example.domain.model.EnforcementPreparationResult
import com.example.platform.InstalledAppsRepository
import com.example.telephony.PhoneAccessManager

interface DevicePolicyController {
    fun isDeviceOwner(): Boolean
    fun getLockTaskModeState(): Int
    fun getAppliedLockTaskPackages(): List<String>
    suspend fun prepareLockTaskPolicy(
        selectedPackages: List<String>,
        phoneAccessEnabled: Boolean
    ): EnforcementPreparationResult
    fun enterLockTask(activity: Activity): Boolean
    fun exitLockTask(activity: Activity): Boolean
    fun clearLockTaskPolicy()
}

class AndroidDevicePolicyController(
    private val context: Context,
    private val phoneAccessManager: PhoneAccessManager,
    private val installedAppsRepository: InstalledAppsRepository
) : DevicePolicyController {

    private val dpm: DevicePolicyManager?
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private val activityManager: ActivityManager?
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private val adminComponent: ComponentName
        get() = ComponentName(context, ResolveDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean =
        runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)

    override fun getLockTaskModeState(): Int =
        runCatching {
            activityManager?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
        }.getOrDefault(ActivityManager.LOCK_TASK_MODE_NONE)

    override fun getAppliedLockTaskPackages(): List<String> =
        runCatching {
            if (!isDeviceOwner()) emptyList()
            else dpm?.getLockTaskPackages(adminComponent)?.toList().orEmpty()
        }.getOrDefault(emptyList())

    override suspend fun prepareLockTaskPolicy(
        selectedPackages: List<String>,
        phoneAccessEnabled: Boolean
    ): EnforcementPreparationResult {
        val policyManager = dpm
            ?: return EnforcementPreparationResult.Failed("DevicePolicyManager unavailable on this device.")

        if (!isDeviceOwner()) {
            return EnforcementPreparationResult.Unavailable(
                "Resolve is not provisioned as Device Owner. Android requires Device Owner authority to enforce Lock Task app restriction."
            )
        }

        val validUserPackages = installedAppsRepository.filterExistingLaunchablePackages(selectedPackages)
        val finalAllowlist = linkedSetOf(context.packageName).apply {
            addAll(validUserPackages)
            if (phoneAccessEnabled) {
                addAll(phoneAccessManager.resolveEssentialDialerPackages())
            }
        }.toList()

        return runCatching {
            policyManager.setLockTaskPackages(adminComponent, finalAllowlist.toTypedArray())
            // Keep Global Actions enabled so emergency/power safety controls are never obstructed,
            // while disabling Home, Overview/Recents, Notifications, System Info, and Keyguard.
            policyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            )
            if (policyManager.isLockTaskPermitted(context.packageName)) {
                EnforcementPreparationResult.Ready(finalAllowlist)
            } else {
                EnforcementPreparationResult.Failed("Lock Task permission could not be confirmed after applying policy.")
            }
        }.getOrElse { error ->
            EnforcementPreparationResult.Failed(error.message ?: "Failed to apply DevicePolicyManager Lock Task policy.")
        }
    }

    override fun enterLockTask(activity: Activity): Boolean {
        if (!isDeviceOwner()) return false
        return runCatching {
            activity.startLockTask()
            getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED
        }.getOrDefault(false)
    }

    override fun exitLockTask(activity: Activity): Boolean {
        return runCatching {
            activity.stopLockTask()
            clearLockTaskPolicy()
            getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE
        }.getOrDefault(false)
    }

    override fun clearLockTaskPolicy() {
        if (!isDeviceOwner()) return
        runCatching {
            dpm?.setLockTaskPackages(adminComponent, emptyArray())
            dpm?.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
    }
}
