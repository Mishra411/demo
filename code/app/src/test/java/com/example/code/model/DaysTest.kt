/*
 * DaysTest.kt
 * Unit tests for the day-number calendar maths used by the availability
 * calendar and reservations.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class DaysTest {
    private val mountain = TimeZone.getTimeZone("GMT-06:00")

    @Test
    fun dayZeroIsTheFirstOfJanuary1970AThursday() {
        assertEquals(0L, Days.of(1970, 0, 1))
        assertEquals(5, Days.weekday(0))
        assertEquals("Thu", Days.weekdayName(Days.weekday(0)))
    }

    @Test
    fun firstOfMay2027IsASaturday() {
        val day = Days.of(2027, 4, 1)
        assertEquals(7, Days.weekday(day))
        assertEquals(1, Days.weekday(day + 1))
    }

    @Test
    fun formatsAndParsesIsoDates() {
        val day = Days.of(2027, 4, 9)
        assertEquals("2027-05-09", Days.format(day))
        assertEquals(day, Days.parse("2027-05-09"))
        assertEquals(day, Days.parse(" 2027-05-09 "))
    }

    @Test
    fun rejectsDatesThatDoNotExist() {
        assertNull(Days.parse("2027-02-30"))
        assertNull(Days.parse("2027-13-01"))
        assertNull(Days.parse("May 9"))
        assertNull(Days.parse(""))
    }

    @Test
    fun leapDayExistsOnlyInLeapYears() {
        assertEquals(29, Days.daysInMonth(2028, 1))
        assertEquals(28, Days.daysInMonth(2027, 1))
        assertNull(Days.parse("2027-02-29"))
    }

    @Test
    fun labelsADayForPeople() {
        assertEquals("Nov 14, 2026", Days.label(Days.of(2026, 10, 14)))
        assertEquals("May 2027", Days.monthLabel(2027, 4))
    }

    @Test
    fun dayOfUsesTheGivenTimeZone() {
        // Nov 14, 2026 00:00 at UTC-6 is 06:00 UTC.
        val midnight = 1794636000000L
        assertEquals(Days.of(2026, 10, 14), Days.dayOf(midnight, mountain))
        assertEquals(Days.of(2026, 10, 13), Days.dayOf(midnight - 1, mountain))
    }

    @Test
    fun startAndEndOfDayBracketTheDay() {
        val day = Days.of(2026, 10, 14)
        assertEquals(1794636000000L, Days.startOfDay(day, mountain))
        assertEquals(Days.startOfDay(day + 1, mountain) - 1, Days.endOfDay(day, mountain))
    }

    @Test
    fun monthGridStartsOnSundayWithBlanks() {
        // May 2027 starts on a Saturday: six blanks, then 31 days.
        val grid = Days.monthGrid(2027, 4)
        assertEquals(37, grid.size)
        assertEquals(List<Long?>(6) { null }, grid.take(6))
        assertEquals(Days.of(2027, 4, 1), grid[6])
        assertEquals(Days.of(2027, 4, 31), grid.last())
    }
}
