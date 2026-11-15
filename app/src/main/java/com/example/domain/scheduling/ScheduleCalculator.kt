package com.example.domain.scheduling

import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleConflict
import com.example.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleCalculator {

    fun computeNextTriggerWallMs(
        schedule: LockSchedule,
        nowWallMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!schedule.isEnabled || schedule.lockDurationMinutes <= 0L) return null
        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowWallMs), zoneId)
        val startTime = LocalTime.of(
            schedule.startHour.coerceIn(0, 23),
            schedule.startMinute.coerceIn(0, 59)
        )

        return when (schedule.type) {
            ScheduleType.ONE_TIME -> {
                val epochDay = schedule.oneTimeDateEpochDay ?: return null
                val date = LocalDate.ofEpochDay(epochDay)
                val targetZdt = ZonedDateTime.of(date, startTime, zoneId)
                val millis = targetZdt.toInstant().toEpochMilli()
                if (millis > nowWallMs) millis else null
            }

            ScheduleType.DAILY -> {
                var candidate = ZonedDateTime.of(nowZdt.toLocalDate(), startTime, zoneId)
                if (candidate.toInstant().toEpochMilli() <= nowWallMs) {
                    candidate = candidate.plusDays(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            ScheduleType.CUSTOM_WEEKLY -> {
                val days = schedule.selectedDays()
                if (days.isEmpty()) return null
                for (offset in 0L..7L) {
                    val date = nowZdt.toLocalDate().plusDays(offset)
                    if (days.contains(date.dayOfWeek)) {
                        val candidate = ZonedDateTime.of(date, startTime, zoneId)
                        val millis = candidate.toInstant().toEpochMilli()
                        if (millis > nowWallMs) {
                            return millis
                        }
                    }
                }
                null
            }
        }
    }

    /**
     * Checks if a one-time schedule or recurring schedule had a trigger window that started recently
     * and would still be active right now (e.g., if device was off before trigger and turned on during window).
     * Returns the trigger start wall time if still within its lock window, or null if already expired.
     */
    fun findInProgressWindowStartMs(
        schedule: LockSchedule,
        nowWallMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!schedule.isEnabled || schedule.lockDurationMinutes <= 0L) return null
        val durationMs = schedule.lockDurationMinutes * 60_000L
        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowWallMs), zoneId)
        val startTime = LocalTime.of(schedule.startHour.coerceIn(0, 23), schedule.startMinute.coerceIn(0, 59))

        return when (schedule.type) {
            ScheduleType.ONE_TIME -> {
                val epochDay = schedule.oneTimeDateEpochDay ?: return null
                val startMs = ZonedDateTime.of(LocalDate.ofEpochDay(epochDay), startTime, zoneId)
                    .toInstant().toEpochMilli()
                if (nowWallMs in startMs until (startMs + durationMs)) startMs else null
            }
            ScheduleType.DAILY -> {
                for (offset in listOf(0L, -1L)) {
                    val date = nowZdt.toLocalDate().plusDays(offset)
                    val startMs = ZonedDateTime.of(date, startTime, zoneId).toInstant().toEpochMilli()
                    if (nowWallMs in startMs until (startMs + durationMs)) return startMs
                }
                null
            }
            ScheduleType.CUSTOM_WEEKLY -> {
                val days = schedule.selectedDays()
                if (days.isEmpty()) return null
                for (offset in listOf(0L, -1L)) {
                    val date = nowZdt.toLocalDate().plusDays(offset)
                    if (days.contains(date.dayOfWeek)) {
                        val startMs = ZonedDateTime.of(date, startTime, zoneId).toInstant().toEpochMilli()
                        if (nowWallMs in startMs until (startMs + durationMs)) return startMs
                    }
                }
                null
            }
        }
    }

    fun detectConflict(
        candidate: LockSchedule,
        existingSchedules: List<LockSchedule>,
        nowWallMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScheduleConflict? {
        if (!candidate.isEnabled) return null
        val candidateStartMin = candidate.startHour * 60 + candidate.startMinute
        val candidateEndMin = candidateStartMin + candidate.lockDurationMinutes.toInt()

        for (existing in existingSchedules) {
            if (existing.id == candidate.id || !existing.isEnabled) continue

            if (candidate.type == ScheduleType.ONE_TIME && existing.type == ScheduleType.ONE_TIME) {
                if (candidate.oneTimeDateEpochDay == existing.oneTimeDateEpochDay) {
                    if (windowsOverlap(
                            candidateStartMin,
                            candidateEndMin,
                            existing.startHour * 60 + existing.startMinute,
                            existing.startHour * 60 + existing.startMinute + existing.lockDurationMinutes.toInt()
                        )
                    ) {
                        return ScheduleConflict(
                            existing.name,
                            "Overlaps with '${existing.name}' on the same date."
                        )
                    }
                }
                continue
            }

            // Check next 7 days overlap in local timezone for recurring vs any
            val candidateNext = computeNextTriggerWallMs(candidate, nowWallMs, zoneId)
            val existingNext = computeNextTriggerWallMs(existing, nowWallMs, zoneId)
            if (candidateNext != null && existingNext != null) {
                val candDays = effectiveDaysForCheck(candidate, zoneId)
                val existDays = effectiveDaysForCheck(existing, zoneId)
                val sharedDays = candDays.intersect(existDays)
                if (sharedDays.isNotEmpty()) {
                    val existStartMin = existing.startHour * 60 + existing.startMinute
                    val existEndMin = existStartMin + existing.lockDurationMinutes.toInt()
                    if (windowsOverlap(candidateStartMin, candidateEndMin, existStartMin, existEndMin)) {
                        return ScheduleConflict(
                            existing.name,
                            "Time window overlaps with '${existing.name}'."
                        )
                    }
                }
            }
        }
        return null
    }

    private fun effectiveDaysForCheck(schedule: LockSchedule, zoneId: ZoneId): Set<DayOfWeek> {
        return when (schedule.type) {
            ScheduleType.DAILY -> DayOfWeek.entries.toSet()
            ScheduleType.CUSTOM_WEEKLY -> schedule.selectedDays()
            ScheduleType.ONE_TIME -> {
                val epochDay = schedule.oneTimeDateEpochDay ?: return emptySet()
                setOf(LocalDate.ofEpochDay(epochDay).dayOfWeek)
            }
        }
    }

    private fun windowsOverlap(s1: Int, e1: Int, s2: Int, e2: Int): Boolean {
        // Normalize potential midnight wrap by checking both same-day and +1440m shift
        fun overlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
            aStart < bEnd && bStart < aEnd

        return overlap(s1, e1, s2, e2) ||
            overlap(s1 + 1440, e1 + 1440, s2, e2) ||
            overlap(s1, e1, s2 + 1440, e2 + 1440)
    }

    fun formatTriggerTimestamp(wallMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(wallMs), zoneId)
        return "%04d-%02d-%02d %02d:%02d".format(
            dt.year,
            dt.monthValue,
            dt.dayOfMonth,
            dt.hour,
            dt.minute
        )
    }
}
