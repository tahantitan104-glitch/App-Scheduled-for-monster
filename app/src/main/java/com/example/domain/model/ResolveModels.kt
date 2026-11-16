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
    val failureReason: String? = null
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
    val nextTriggerWallMs: Long? = null
) {
    fun selectedDays(): Set<DayOfWeek> =
        DayOfWeek.entries.filter { (weekdaysMask and (1 shl (it.value - 1))) != 0 }.toSet()

    companion object {
        fun maskFromDays(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
    }
}

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

data class SystemCapabilityStatus(
    val deviceOwnerState: DeviceOwnerState = DeviceOwnerState.NOT_PROVISIONED,
    val isLockTaskPermittedForApp: Boolean = false,
    val lockTaskModeState: Int = 0,
    val isAccessibilityServiceEnabled: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    val exactAlarmRequiredByOs: Boolean = false,
    val notificationsEnabled: Boolean = false,
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
