package com.example

import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.domain.scheduling.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `daily schedule selects next day when start time already passed today`() {
        val now = ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val schedule = LockSchedule(
            id = 1L,
            name = "Afternoon Focus",
            type = ScheduleType.DAILY,
            startHour = 9,
            startMinute = 30,
            lockDurationMinutes = 60L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isEnabled = true,
            createdAtWallMs = now
        )

        val next = ScheduleCalculator.computeNextTriggerWallMs(schedule, now, zone)
        val expected = ZonedDateTime.of(2026, 11, 16, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }

    @Test
    fun `expired one-time schedule returns null next trigger`() {
        val now = ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val schedule = LockSchedule(
            id = 2L,
            name = "Past One Time",
            type = ScheduleType.ONE_TIME,
            oneTimeDateEpochDay = LocalDate.of(2026, 11, 14).toEpochDay(),
            startHour = 10,
            startMinute = 0,
            lockDurationMinutes = 30L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isEnabled = true,
            createdAtWallMs = now
        )

        assertNull(ScheduleCalculator.computeNextTriggerWallMs(schedule, now, zone))
    }

    @Test
    fun `overlapping daily schedules are detected as conflict`() {
        val now = ZonedDateTime.of(2026, 11, 15, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val existing = LockSchedule(
            id = 1L,
            name = "Morning Block",
            type = ScheduleType.DAILY,
            startHour = 9,
            startMinute = 0,
            lockDurationMinutes = 60L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isEnabled = true,
            createdAtWallMs = now
        )
        val candidate = existing.copy(
            id = 0L,
            name = "Overlapping Block",
            startHour = 9,
            startMinute = 30,
            lockDurationMinutes = 45L
        )

        val conflict = ScheduleCalculator.detectConflict(candidate, listOf(existing), now, zone)
        assertNotNull(conflict)
        assertEquals("Morning Block", conflict?.conflictingScheduleName)
    }

    @Test
    fun `weekly schedule skips non-matching weekdays`() {
        // 2026-11-15 is Sunday
        val now = ZonedDateTime.of(2026, 11, 15, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val schedule = LockSchedule(
            id = 3L,
            name = "Wednesday Focus",
            type = ScheduleType.CUSTOM_WEEKLY,
            weekdaysMask = LockSchedule.maskFromDays(setOf(DayOfWeek.WEDNESDAY)),
            startHour = 10,
            startMinute = 0,
            lockDurationMinutes = 60L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isEnabled = true,
            createdAtWallMs = now
        )

        val next = ScheduleCalculator.computeNextTriggerWallMs(schedule, now, zone)
        val expected = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }
}
