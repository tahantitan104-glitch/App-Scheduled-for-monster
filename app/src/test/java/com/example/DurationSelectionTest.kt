package com.example

import com.example.domain.model.DurationSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationSelectionTest {

    @Test
    fun `selecting hours then minutes yields exact 3h 40m without resetting`() {
        val initial = DurationSelection(hours = 0, minutes = 0)
        val afterHours = initial.withHours(3)
        assertEquals("03 : 00", afterHours.formattedDisplay())
        assertEquals(0, afterHours.normalizedMinutes)

        val afterMinutes = afterHours.withMinutes(40)
        assertEquals("03 : 40", afterMinutes.formattedDisplay())
        assertEquals(3, afterMinutes.normalizedHours)
        assertEquals(40, afterMinutes.normalizedMinutes)
        assertEquals(220L, afterMinutes.totalMinutes)
    }

    @Test
    fun `selecting minutes then hours in reverse order yields exact 3h 40m`() {
        val initial = DurationSelection(hours = 0, minutes = 0)
        val afterMinutes = initial.withMinutes(40)
        assertEquals("00 : 40", afterMinutes.formattedDisplay())

        val afterHours = afterMinutes.withHours(3)
        assertEquals("03 : 40", afterHours.formattedDisplay())
        assertEquals(220L, afterHours.totalMinutes)
    }

    @Test
    fun `changing hours preserves arbitrary single-minute values like 17 and 57`() {
        val d17 = DurationSelection(hours = 1, minutes = 17).withHours(5)
        assertEquals(5, d17.normalizedHours)
        assertEquals(17, d17.normalizedMinutes)

        val d57 = DurationSelection(hours = 0, minutes = 57).withHours(2)
        assertEquals(2, d57.normalizedHours)
        assertEquals(57, d57.normalizedMinutes)
    }

    @Test
    fun `zero duration is invalid and positive durations are valid`() {
        assertFalse(DurationSelection(0, 0).isValid)
        assertTrue(DurationSelection(0, 1).isValid)
        assertTrue(DurationSelection.fromTotalMinutes(220L).totalMinutes == 220L)
    }
}
