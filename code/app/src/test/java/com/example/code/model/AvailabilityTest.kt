/*
 * AvailabilityTest.kt
 * Unit tests for the campsite availability schedule and calendar day states.
 * Uses the project scenario: weekends in May 2027.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import com.example.code.fakes.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AvailabilityTest {
    private val mountain = TimeZone.getTimeZone("GMT-06:00")

    /** Open on Saturdays and Sundays in May 2027. */
    private val weekendsInMay = Campsite(
        id = "camp-jasper-riverside",
        name = "Jasper Riverside",
        availableFrom = "2027-05-01",
        availableTo = "2027-05-31",
        availableWeekdays = listOf(1, 7),
    )
    private val saturday = Days.of(2027, 4, 1)

    @Test
    fun weekendScheduleIsOpenOnlyOnWeekendsInsideTheDates() {
        assertTrue(Availability.isOpen(weekendsInMay, saturday))
        assertTrue(Availability.isOpen(weekendsInMay, saturday + 1))
        assertFalse(Availability.isOpen(weekendsInMay, saturday + 2))
        // Saturday June 5 is after the last available date.
        assertFalse(Availability.isOpen(weekendsInMay, Days.of(2027, 5, 5)))
    }

    @Test
    fun noWeekdaysMeansEveryDayInsideTheDates() {
        val everyDay = weekendsInMay.copy(availableWeekdays = emptyList())
        assertTrue(Availability.isOpen(everyDay, saturday + 2))
        assertFalse(Availability.isOpen(everyDay, saturday - 1))
    }

    @Test
    fun campsiteWithoutAScheduleIsNeverClosedByIt() {
        val none = Campsite(id = "camp-banff-tunnel")
        assertFalse(Availability.hasSchedule(none))
        assertTrue(Availability.isOpen(none, saturday + 2))
        assertFalse(Availability.covers(none, saturday, saturday + 1))
    }

    @Test
    fun coversNeedsEveryDayOpen() {
        assertTrue(Availability.covers(weekendsInMay, saturday, saturday + 1))
        assertFalse(Availability.covers(weekendsInMay, saturday, saturday + 2))
        assertFalse(Availability.covers(weekendsInMay, saturday + 1, saturday))
    }

    @Test
    fun validateAcceptsEmptyOrCompleteSchedules() {
        assertEquals("" to "", Availability.validate("", " ", emptyList()))
        assertEquals("2027-05-01" to "2027-05-31", Availability.validate(" 2027-05-01", "2027-05-31 ", listOf(1, 7)))
    }

    @Test
    fun validateRejectsBadSchedules() {
        fun rejected(from: String, to: String, weekdays: List<Int> = emptyList()) {
            assertThrows(IllegalArgumentException::class.java) { Availability.validate(from, to, weekdays) }
        }
        rejected("2027-05-01", "")
        rejected("", "", listOf(7))
        rejected("May 1", "2027-05-31")
        rejected("2027-05-31", "2027-05-01")
        rejected("2027-05-01", "2027-05-31", listOf(8))
        // Monday to Wednesday contains no Saturday.
        rejected("2027-05-03", "2027-05-05", listOf(7))
    }

    @Test
    fun describesTheSchedule() {
        assertEquals("May 1, 2027 – May 31, 2027 · Sun, Sat", Availability.describe(weekendsInMay))
        assertEquals("Availability not set", Availability.describe(Campsite()))
    }

    @Test
    fun activeReservationBooksItsDays() {
        // Priya stays Nov 14-16, 2026.
        val reservations = listOf(TestData.priyaReservation)
        val campsite = Campsite(id = "camp-jasper-riverside", availableFrom = "2026-11-01", availableTo = "2026-11-30")
        val nov14 = Days.of(2026, 10, 14)
        assertEquals(DayState.BOOKED, Availability.dayState(campsite, emptyList(), reservations, nov14, mountain))
        assertEquals(DayState.BOOKED, Availability.dayState(campsite, emptyList(), reservations, nov14 + 2, mountain))
        assertEquals(DayState.OPEN, Availability.dayState(campsite, emptyList(), reservations, nov14 + 3, mountain))
        assertEquals(DayState.CLOSED, Availability.dayState(campsite, emptyList(), reservations, nov14 + 30, mountain))
    }

    @Test
    fun cancelledReservationFreesItsDays() {
        val cancelled = TestData.priyaReservation.copy(status = Reservation.STATUS_CANCELLED)
        assertFalse(
            Availability.isBooked(listOf(cancelled), "camp-jasper-riverside", Days.of(2026, 10, 14), mountain),
        )
    }

    @Test
    fun withoutAScheduleTheLotteryBookingPeriodIsOffered() {
        val campsite = Campsite(id = "camp-jasper-riverside")
        val lotteries = listOf(TestData.jasperLottery)
        val nov14 = Days.of(2026, 10, 14)
        assertEquals(DayState.OPEN, Availability.dayState(campsite, lotteries, emptyList(), nov14, mountain))
        assertEquals(DayState.CLOSED, Availability.dayState(campsite, lotteries, emptyList(), nov14 - 1, mountain))
        assertEquals(DayState.CLOSED, Availability.dayState(campsite, emptyList(), emptyList(), nov14, mountain))
    }

    @Test
    fun tappingDaysSelectsAStayRange() {
        assertEquals(10L to null, Availability.selectDay(null, null, 10))
        assertEquals(10L to 12L, Availability.selectDay(10, null, 12))
        // Tapping before the first day, or after a finished range, starts again.
        assertEquals(8L to null, Availability.selectDay(10, null, 8))
        assertEquals(20L to null, Availability.selectDay(10, 12, 20))
    }
}
