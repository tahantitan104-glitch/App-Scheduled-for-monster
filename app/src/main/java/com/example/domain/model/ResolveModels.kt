package com.example.domain.model

import java.time.DayOfWeek

enum class LockMode {
    INSTANT,
    DELAYED,
    SCHEDULED
}

enum class EnforcementMode {
    NONE,
    DEVICE_OWNER_LOCK_TASK,
    ACCESSIBILITY_GUARD
}

enum class SessionState {
    IDLE,
    PREPARING,
    DELAYED_FREE_USE_ACTIVE,
    SCHEDULE_WAITING,
    LOCK_TRANSITION_PENDING,
    LOCK_ENFORCEMENT_ACTIVE,
    PHONE_CALL_ACTIVE,
    COMPLETED,
    RECOVERY_IN_PROGRESS,
    RECOVERED,
    ENFORCEMENT_UNAVAILABLE,
    ENFORCEMENT_FAILED,
    INTERRUPTED
}

enum class ScheduleType {
    ONE_TIME,
    DAILY,
    CUSTOM_WEEKLY
}

enum class SessionOutcome {
    COMPLETED,
    RECOVERED,
    INTERRUPTED,
    ENFORCEMENT_FAILED
}

data class DurationSelection(
    val hours: Int = 0,
    val minutes: Int = 30
) {
    val normalizedHours: Int get() = hours.coerceIn(0, MAX_HOURS)
    val normalizedMinutes: Int get() = minutes.coerceIn(0, MAX_MINUTES)
    val totalMinutes: Long get() = normalizedHours * 60L + normalizedMinutes
    val totalMillis: Long get() = totalMinutes * 60_000L
    val isValid: Boolean get() = totalMinutes >= 1L

    fun withHours(newHours: Int): DurationSelection =
        DurationSelection(
            hours = newHours.coerceIn(0, MAX_HOURS),
            minutes = normalizedMinutes
        )

    fun withMinutes(newMinutes: Int): DurationSelection =
        DurationSelection(
            hours = normalizedHours,
            minutes = newMinutes.coerceIn(0, MAX_MINUTES)
        )

    fun formattedDisplay(): String =
        "%02d : %02d".format(normalizedHours, normalizedMinutes)

    fun formattedCompact(): String {
        val h = normalizedHours
        val m = normalizedMinutes
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    companion object {
        const val MAX_HOURS = 23
        const val MAX_MINUTES = 59
        const val MAX_TOTAL_MINUTES = 23L * 60L + 59L

        fun fromTotalMinutes(totalMinutes: Long): DurationSelection {
            val clamped = totalMinutes.coerceIn(0L, MAX_TOTAL_MINUTES)
            return DurationSelection(
                hours = (clamped / 60L).toInt(),
                minutes = (clamped % 60L).toInt()
            )
        }
    }
}

data class ActiveSession(
    val sessionId: String,
    val mode: LockMode,
    val state: SessionState,
    val enforcementMode: EnforcementMode = EnforcementMode.NONE,
    val allowedPackages: List<String>,
    val phoneAccessEnabled: Boolean,
    val freeUseDurationMinutes: Long,
    val lockDurationMinutes: Long,
    val createdAtWallMs: Long,
    val freeUseStartedElapsedMs: Long? = null,
    val freeUseDeadlineWallMs: Long? = null,
    val freeUseDeadlineElapsedMs: Long? = null,
    val lockStartedWallMs: Long? = null,
    val lockStartedElapsedMs: Long? = null,
    val lockExpirationWallMs: Long? = null,
    val lockExpirationElapsedMs: Long? = null,
    val bootCountAtStart: Int = 0,
    val scheduleId: Long? = null,
    val recoveryCodeLength: Int = 16,
    val failureReason: String? = null,
    val isStrictMode: Boolean = false,
    val showFlipClockShortcut: Boolean = false
) {
    val isTerminal: Boolean
        get() = state == SessionState.COMPLETED ||
            state == SessionState.RECOVERED ||
            state == SessionState.ENFORCEMENT_FAILED ||
            state == SessionState.ENFORCEMENT_UNAVAILABLE ||
            state == SessionState.INTERRUPTED ||
            state == SessionState.IDLE

    val isEnforcementPhase: Boolean
        get() = state == SessionState.LOCK_ENFORCEMENT_ACTIVE ||
            state == SessionState.PHONE_CALL_ACTIVE ||
            state == SessionState.RECOVERY_IN_PROGRESS
}

data class LockSchedule(
    val id: Long = 0L,
    val name: String,
    val type: ScheduleType,
    val oneTimeDateEpochDay: Long? = null,
    val weekdaysMask: Int = 0,
    val startHour: Int,
    val startMinute: Int,
    val lockDurationMinutes: Long,
    val allowedPackages: List<String>,
    val phoneAccessEnabled: Boolean,
    val isEnabled: Boolean,
    val createdAtWallMs: Long,
    val nextTriggerWallMs: Long? = null,
    val cloudId: String = "",
    val syncAcrossDevices: Boolean = false,
    val isStrictMode: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val showFlipClockShortcut: Boolean = false
) {
    fun selectedDays(): Set<DayOfWeek> =
        DayOfWeek.entries.filter { (weekdaysMask and (1 shl (it.value - 1))) != 0 }.toSet()

    companion object {
        fun maskFromDays(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
    }
}

data class LockProfileSettings(
    val mode: LockMode,
    val strictMode: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val showFlipClockShortcut: Boolean = false,
    val allowedPackages: List<String> = emptyList(),
    val updatedAtWallMs: Long = 0L
) {
    val effectiveAllowedPackages: List<String>
        get() = if (allowSelectedApps) allowedPackages.distinct() else emptyList()
}

enum class FlipClockPage {
    TIMER,
    CLOCK,
    STOPWATCH
}

data class FlipClockSettings(
    val use24HourFormat: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val showDate: Boolean = false,
    val autoRepeat: Boolean = false,
    val zoomFraction: Float = DEFAULT_ZOOM_FRACTION,
    val lastBrightnessPercent: Float? = null,
    val updatedAtWallMs: Long = 0L
) {
    val clampedZoomFraction: Float
        get() = zoomFraction.coerceIn(MIN_ZOOM_FRACTION, MAX_ZOOM_FRACTION)

    companion object {
        const val MIN_ZOOM_FRACTION = 0.50f
        const val MAX_ZOOM_FRACTION = 1.40f
        const val DEFAULT_ZOOM_FRACTION = 1.00f
        const val MIN_BRIGHTNESS_PERCENT = 15f
        const val MAX_BRIGHTNESS_PERCENT = 85f
    }
}

data class FlipTimerPreset(
    val presetId: String,
    val name: String,
    val durationSeconds: Long,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAtWallMs: Long = 0L
) {
    companion object {
        const val MAX_TOTAL_PRESETS = 15
    }
}

data class FlipTimerState(
    val activePresetId: String? = null,
    val timerName: String = "25 Minutes Timer",
    val selectedDurationMs: Long = 25L * 60_000L,
    val targetEndWallMs: Long? = null,
    val remainingWhenPausedMs: Long? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val finishedAtWallMs: Long? = null,
    val completionMessageUntilWallMs: Long? = null,
    val autoRepeatScheduledAtWallMs: Long? = null,
    val notificationPermissionRequestedOnce: Boolean = false,
    val notificationPermissionDeniedNotice: Boolean = false
)

data class FlipStopwatchState(
    val isRunning: Boolean = false,
    val accumulatedMs: Long = 0L,
    val startedElapsedMs: Long? = null,
    val startedWallMs: Long? = null
)

data class DelayedLockPreset(
    val presetId: String,
    val name: String,
    val freeUseMinutes: Long,
    val lockDurationMinutes: Long,
    val builtIn: Boolean = false,
    val updatedAtWallMs: Long = 0L
)

enum class AppLockAuthMethod {
    PATTERN,
    PIN,
    SIX_CELL_SEQUENTIAL
}

enum class AppLockPinLength(val digits: Int) {
    FOUR(4),
    SIX(6)
}

sealed interface AppLockVerificationResult {
    data object Verified : AppLockVerificationResult
    data object NotConfigured : AppLockVerificationResult
    data class InvalidCredential(val remainingAttemptsBeforeCooldown: Int) : AppLockVerificationResult
    data class Throttled(val retryAfterSeconds: Long) : AppLockVerificationResult
}

data class DailyAppUsageItem(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long
)

data class DailyAppUsageSummary(
    val hasUsagePermission: Boolean = false,
    val totalScreenTimeMs: Long = 0L,
    val topApps: List<DailyAppUsageItem> = emptyList(),
    val refreshedAtWallMs: Long = 0L
)

enum class ForceDarkSupportLevel {
    SUPPORTED,
    PARTIALLY_SUPPORTED,
    UNSUPPORTED
}

enum class ForceDarkModeApproach {
    FORCE_DARK,
    COLOR_INVERSION
}

data class ForceDarkPreferences(
    val enabled: Boolean = false,
    val approach: ForceDarkModeApproach = ForceDarkModeApproach.FORCE_DARK,
    val appOverrides: Map<String, Boolean> = emptyMap()
)

data class SessionHistoryRecord(
    val id: Long = 0L,
    val sessionId: String,
    val mode: LockMode,
    val freeUseDurationMinutes: Long,
    val lockDurationMinutes: Long,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long,
    val outcome: SessionOutcome,
    val allowedAppCount: Int,
    val scheduleId: Long? = null
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isDialerOrTelecom: Boolean = false
)

enum class DeviceOwnerState {
    DEVICE_OWNER_ACTIVE,
    DEVICE_ADMIN_ONLY,
    NOT_PROVISIONED
}

enum class AccessibilityHealthState {
    ENABLED_CONNECTED,
    ENABLED_DISCONNECTED,
    DISABLED
}

data class NotificationControlConfig(
    val enabled: Boolean = false,
    val autoActivateDuringSessions: Boolean = true,
    val allowCalls: Boolean = true,
    val allowAlarms: Boolean = true
)

enum class NotificationProtectionState {
    INACTIVE,
    ACTIVE,
    PRESERVED_EXISTING_DND,
    OVERRIDDEN_BY_USER,
    UNAVAILABLE_NO_PERMISSION
}

enum class ProtectionRecoveryAction {
    NONE,
    OPEN_ACCESSIBILITY,
    OPEN_OVERLAY,
    OPEN_USAGE_ACCESS,
    OPEN_NOTIFICATION_POLICY,
    OPEN_BATTERY_OPTIMIZATION,
    OPEN_EXACT_ALARM,
    OPEN_NOTIFICATION_PERMISSION,
    RETURN_TO_LOCK_SCREEN,
    RUN_DEVICE_ANALYSIS
}

sealed interface ProtectionStatus {
    val title: String
    val whatHappened: String
    val affectedFunctionality: String
    val recommendedActionLabel: String?
    val actionType: ProtectionRecoveryAction

    data class ProtectionActive(
        override val title: String = "Protection Active",
        override val whatHappened: String = "All required system guards are connected and ready.",
        override val affectedFunctionality: String = "Focus Lock, App Lock, and session timers are operating normally.",
        override val recommendedActionLabel: String? = null,
        override val actionType: ProtectionRecoveryAction = ProtectionRecoveryAction.NONE,
        val notificationState: NotificationProtectionState = NotificationProtectionState.INACTIVE
    ) : ProtectionStatus

    data class ProtectionLimited(
        override val title: String,
        override val whatHappened: String,
        override val affectedFunctionality: String,
        override val recommendedActionLabel: String? = null,
        override val actionType: ProtectionRecoveryAction = ProtectionRecoveryAction.NONE
    ) : ProtectionStatus

    data class ActionRequired(
        override val title: String,
        override val whatHappened: String,
        override val affectedFunctionality: String,
        override val recommendedActionLabel: String?,
        override val actionType: ProtectionRecoveryAction
    ) : ProtectionStatus

    data class Recovering(
        override val title: String,
        override val whatHappened: String,
        override val affectedFunctionality: String,
        override val recommendedActionLabel: String? = "Resume Lock Screen",
        override val actionType: ProtectionRecoveryAction = ProtectionRecoveryAction.RETURN_TO_LOCK_SCREEN
    ) : ProtectionStatus

    data class SessionCompleted(
        override val title: String = "Focus Session Completed",
        override val whatHappened: String = "Your recent focus session finished on schedule.",
        override val affectedFunctionality: String = "Normal device access and restored notification policy are active.",
        override val recommendedActionLabel: String? = null,
        override val actionType: ProtectionRecoveryAction = ProtectionRecoveryAction.NONE
    ) : ProtectionStatus
}

enum class CompatibilityItemStatus(val label: String) {
    ACTIVE("Active"),
    ACTION_REQUIRED("Action Required"),
    UNAVAILABLE("Unavailable"),
    NOT_REQUIRED("Not Required"),
    NOT_VERIFIED("Not Verified")
}

enum class OverallCompatibilityResult(val label: String) {
    READY("READY"),
    ACTION_REQUIRED("ACTION REQUIRED"),
    LIMITED_COMPATIBILITY("LIMITED COMPATIBILITY"),
    ANALYSIS_INCOMPLETE("ANALYSIS INCOMPLETE")
}

data class DeviceHardwareProfile(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildId: String,
    val cpuAbi: String,
    val totalRamLabel: String,
    val availableRamLabel: String,
    val resolveVersion: String
)

data class CompatibilityCheckItem(
    val id: String,
    val name: String,
    val status: CompatibilityItemStatus,
    val detail: String,
    val actionLabel: String? = null,
    val actionType: ProtectionRecoveryAction = ProtectionRecoveryAction.NONE
)

data class SecurityCheckItem(
    val id: String,
    val name: String,
    val status: CompatibilityItemStatus,
    val detail: String
)

data class DeviceManagementOverview(
    val ownerState: DeviceOwnerState,
    val statusLabel: String,
    val explanation: String,
    val isRequiredForStandardUse: Boolean = false
)

data class DeviceAnalysisReport(
    val analyzedAtWallMs: Long = 0L,
    val overallResult: OverallCompatibilityResult = OverallCompatibilityResult.ANALYSIS_INCOMPLETE,
    val summaryMessage: String = "Tap Run Device Analysis to inspect system readiness.",
    val hardware: DeviceHardwareProfile? = null,
    val compatibilityChecks: List<CompatibilityCheckItem> = emptyList(),
    val securityChecks: List<SecurityCheckItem> = emptyList(),
    val setupRepairItems: List<CompatibilityCheckItem> = emptyList(),
    val deviceManagement: DeviceManagementOverview? = null
)

data class SystemCapabilityStatus(
    val deviceOwnerState: DeviceOwnerState = DeviceOwnerState.NOT_PROVISIONED,
    val isLockTaskPermittedForApp: Boolean = false,
    val lockTaskModeState: Int = 0,
    val isAccessibilityServiceEnabled: Boolean = false,
    val isAccessibilityEnabledInSettings: Boolean = isAccessibilityServiceEnabled,
    val isAccessibilityServiceConnected: Boolean = isAccessibilityServiceEnabled,
    val accessibilityHealthState: AccessibilityHealthState = when {
        isAccessibilityServiceConnected -> AccessibilityHealthState.ENABLED_CONNECTED
        isAccessibilityServiceEnabled -> AccessibilityHealthState.ENABLED_DISCONNECTED
        else -> AccessibilityHealthState.DISABLED
    },
    val canDrawOverlays: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    val exactAlarmRequiredByOs: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val hasNotificationPolicyAccess: Boolean = false,
    val androidSdkInt: Int = 28,
    val manufacturer: String = "",
    val model: String = ""
) {
    val activeEnforcementMode: EnforcementMode
        get() = when {
            deviceOwnerState == DeviceOwnerState.DEVICE_OWNER_ACTIVE -> EnforcementMode.DEVICE_OWNER_LOCK_TASK
            isAccessibilityServiceEnabled -> EnforcementMode.ACCESSIBILITY_GUARD
            else -> EnforcementMode.NONE
        }

    val canEnforceRealLock: Boolean
        get() = activeEnforcementMode != EnforcementMode.NONE

    val isLockTaskCurrentlyActive: Boolean
        get() = lockTaskModeState == 1 // ActivityManager.LOCK_TASK_MODE_LOCKED
}

sealed interface EnforcementPreparationResult {
    data class Ready(val appliedPackages: List<String>) : EnforcementPreparationResult
    data class Unavailable(val reason: String) : EnforcementPreparationResult
    data class Failed(val reason: String) : EnforcementPreparationResult
}

sealed interface RecoveryVerificationResult {
    data object Verified : RecoveryVerificationResult
    data object NotConfigured : RecoveryVerificationResult
    data class InvalidCode(val remainingAttemptsBeforeCooldown: Int) : RecoveryVerificationResult
    data class Throttled(val retryAfterSeconds: Long) : RecoveryVerificationResult
}

data class ScheduleConflict(
    val conflictingScheduleName: String,
    val description: String
)
