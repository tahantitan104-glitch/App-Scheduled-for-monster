package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.ActiveSession
import com.example.domain.model.EnforcementMode
import com.example.domain.model.LockMode
import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SessionState
import org.json.JSONArray

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val slotId: Int = 1,
    val sessionId: String,
    val mode: String,
    val state: String,
    val enforcementMode: String = EnforcementMode.NONE.name,
    val allowedPackagesJson: String,
    val phoneAccessEnabled: Boolean,
    val freeUseDurationMinutes: Long,
    val lockDurationMinutes: Long,
    val createdAtWallMs: Long,
    val freeUseStartedElapsedMs: Long?,
    val freeUseDeadlineWallMs: Long?,
    val freeUseDeadlineElapsedMs: Long?,
    val lockStartedWallMs: Long?,
    val lockStartedElapsedMs: Long?,
    val lockExpirationWallMs: Long?,
    val lockExpirationElapsedMs: Long?,
    val bootCountAtStart: Int,
    val scheduleId: Long?,
    val recoveryCodeLength: Int,
    val failureReason: String?
)

@Entity(tableName = "lock_schedules")
data class LockScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val oneTimeDateEpochDay: Long?,
    val weekdaysMask: Int,
    val startHour: Int,
    val startMinute: Int,
    val lockDurationMinutes: Long,
    val allowedPackagesJson: String,
    val phoneAccessEnabled: Boolean,
    val isEnabled: Boolean,
    val createdAtWallMs: Long,
    val nextTriggerWallMs: Long?
)

@Entity(tableName = "session_history")
data class SessionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val mode: String,
    val freeUseDurationMinutes: Long,
    val lockDurationMinutes: Long,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long,
    val outcome: String,
    val allowedAppCount: Int,
    val scheduleId: Long?
)

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val addedAtWallMs: Long
)

object EntityMappers {
    fun encodePackages(packages: List<String>): String {
        val array = JSONArray()
        packages.distinct().forEach { array.put(it) }
        return array.toString()
    }

    fun decodePackages(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i)
                    if (pkg.isNotBlank()) add(pkg)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun ActiveSession.toEntity(): ActiveSessionEntity = ActiveSessionEntity(
        slotId = 1,
        sessionId = sessionId,
        mode = mode.name,
        state = state.name,
        enforcementMode = enforcementMode.name,
        allowedPackagesJson = encodePackages(allowedPackages),
        phoneAccessEnabled = phoneAccessEnabled,
        freeUseDurationMinutes = freeUseDurationMinutes,
        lockDurationMinutes = lockDurationMinutes,
        createdAtWallMs = createdAtWallMs,
        freeUseStartedElapsedMs = freeUseStartedElapsedMs,
        freeUseDeadlineWallMs = freeUseDeadlineWallMs,
        freeUseDeadlineElapsedMs = freeUseDeadlineElapsedMs,
        lockStartedWallMs = lockStartedWallMs,
        lockStartedElapsedMs = lockStartedElapsedMs,
        lockExpirationWallMs = lockExpirationWallMs,
        lockExpirationElapsedMs = lockExpirationElapsedMs,
        bootCountAtStart = bootCountAtStart,
        scheduleId = scheduleId,
        recoveryCodeLength = recoveryCodeLength,
        failureReason = failureReason
    )

    fun ActiveSessionEntity.toDomain(): ActiveSession = ActiveSession(
        sessionId = sessionId,
        mode = runCatching { LockMode.valueOf(mode) }.getOrDefault(LockMode.INSTANT),
        state = runCatching { SessionState.valueOf(state) }.getOrDefault(SessionState.IDLE),
        enforcementMode = runCatching { EnforcementMode.valueOf(enforcementMode) }.getOrDefault(EnforcementMode.NONE),
        allowedPackages = decodePackages(allowedPackagesJson),
        phoneAccessEnabled = phoneAccessEnabled,
        freeUseDurationMinutes = freeUseDurationMinutes,
        lockDurationMinutes = lockDurationMinutes,
        createdAtWallMs = createdAtWallMs,
        freeUseStartedElapsedMs = freeUseStartedElapsedMs,
        freeUseDeadlineWallMs = freeUseDeadlineWallMs,
        freeUseDeadlineElapsedMs = freeUseDeadlineElapsedMs,
        lockStartedWallMs = lockStartedWallMs,
        lockStartedElapsedMs = lockStartedElapsedMs,
        lockExpirationWallMs = lockExpirationWallMs,
        lockExpirationElapsedMs = lockExpirationElapsedMs,
        bootCountAtStart = bootCountAtStart,
        scheduleId = scheduleId,
        recoveryCodeLength = recoveryCodeLength,
        failureReason = failureReason
    )

    fun LockSchedule.toEntity(): LockScheduleEntity = LockScheduleEntity(
        id = id,
        name = name,
        type = type.name,
        oneTimeDateEpochDay = oneTimeDateEpochDay,
        weekdaysMask = weekdaysMask,
        startHour = startHour,
        startMinute = startMinute,
        lockDurationMinutes = lockDurationMinutes,
        allowedPackagesJson = encodePackages(allowedPackages),
        phoneAccessEnabled = phoneAccessEnabled,
        isEnabled = isEnabled,
        createdAtWallMs = createdAtWallMs,
        nextTriggerWallMs = nextTriggerWallMs
    )

    fun LockScheduleEntity.toDomain(): LockSchedule = LockSchedule(
        id = id,
        name = name,
        type = runCatching { ScheduleType.valueOf(type) }.getOrDefault(ScheduleType.ONE_TIME),
        oneTimeDateEpochDay = oneTimeDateEpochDay,
        weekdaysMask = weekdaysMask,
        startHour = startHour,
        startMinute = startMinute,
        lockDurationMinutes = lockDurationMinutes,
        allowedPackages = decodePackages(allowedPackagesJson),
        phoneAccessEnabled = phoneAccessEnabled,
        isEnabled = isEnabled,
        createdAtWallMs = createdAtWallMs,
        nextTriggerWallMs = nextTriggerWallMs
    )

    fun SessionHistoryRecord.toEntity(): SessionHistoryEntity = SessionHistoryEntity(
        id = id,
        sessionId = sessionId,
        mode = mode.name,
        freeUseDurationMinutes = freeUseDurationMinutes,
        lockDurationMinutes = lockDurationMinutes,
        startedAtWallMs = startedAtWallMs,
        endedAtWallMs = endedAtWallMs,
        outcome = outcome.name,
        allowedAppCount = allowedAppCount,
        scheduleId = scheduleId
    )

    fun SessionHistoryEntity.toDomain(): SessionHistoryRecord = SessionHistoryRecord(
        id = id,
        sessionId = sessionId,
        mode = runCatching { LockMode.valueOf(mode) }.getOrDefault(LockMode.INSTANT),
        freeUseDurationMinutes = freeUseDurationMinutes,
        lockDurationMinutes = lockDurationMinutes,
        startedAtWallMs = startedAtWallMs,
        endedAtWallMs = endedAtWallMs,
        outcome = runCatching { SessionOutcome.valueOf(outcome) }.getOrDefault(SessionOutcome.INTERRUPTED),
        allowedAppCount = allowedAppCount,
        scheduleId = scheduleId
    )
}
