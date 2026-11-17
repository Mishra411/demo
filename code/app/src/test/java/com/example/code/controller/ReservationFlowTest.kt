/*
 * ReservationFlowTest.kt
 * Unit tests for choosing dates and a campsite after winning (US 01.04.01,
 * 01.04.02), confirming rules (US 02.11.01), declining (US 01.04.03),
 * cancelling (US 01.07.01), the confirmations sent (US 01.05.01, 01.07.02),
 * past and cancelled lists (US 01.06.02, 02.07.02, 02.07.03) and the CSV
 * download (US 01.05.02).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.fakes.FakeNotificationRepository
import com.example.code.fakes.FakeReservationRepository
import com.example.code.fakes.TestData
import com.example.code.model.AppNotification
import com.example.code.model.Campsite
import com.example.code.model.DayState
import com.example.code.model.Days
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.WaitingListEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReservationFlowTest {
    private val mountain = TimeZone.getTimeZone("GMT-06:00")
    private val now = TestData.AFTER_REGISTRATION
    private val drawn = TestData.jasperLottery.copy(status = Lottery.STATUS_DRAWN)
    private val winner = TestData.priya.copy(status = WaitingListEntry.STATUS_WON)
    private val nov14 = Days.of(2026, 10, 14)

    private val repository = FakeReservationRepository()
    private val notifications = FakeNotificationRepository()
    private val controller = ReservationController(repository, notifications) { now }

    private fun saved(): List<Reservation> = repository.reservations.value.values.toList()

    private fun reserve(
        firstDay: Long = nov14,
        lastDay: Long = nov14 + 1,
        lottery: Lottery = drawn,
        campsiteId: String = lottery.campsiteId,
        rulesAccepted: Boolean = true,
        campsite: Campsite? = null,
        entry: WaitingListEntry = winner,
    ): Reservation? = runBlocking {
        controller.reserve(lottery, entry, campsiteId, firstDay, lastDay, rulesAccepted, saved(), campsite, timeZone = mountain)
    }

    @Test
    fun winnerReservesChosenDaysInsideTheBookingPeriod() {
        val reservation = reserve()
        assertNotNull(reservation)
        assertEquals(TestData.BOOKING_START, reservation!!.startDate)
        assertEquals(Days.endOfDay(nov14 + 1, mountain), reservation.endDate)
        assertEquals("Priya Shah", reservation.entrantName)
        assertEquals(now, reservation.rulesAcceptedAt)
        assertEquals(listOf(reservation), saved())
    }

    @Test
    fun lastDayOfTheBookingPeriodEndsWhenThePeriodEnds() {
        val reservation = reserve(nov14 + 2, nov14 + 2)
        assertEquals(TestData.BOOKING_END, reservation!!.endDate)
    }

    @Test
    fun confirmationNotificationIsSentToTheEntrant() {
        reserve()
        val sent = notifications.sent.single()
        assertEquals(AppNotification.TYPE_RESERVATION_CONFIRMED, sent.type)
        assertEquals(winner.entrantId, sent.recipientId)
        assertEquals("Reservation confirmed: Jasper Riverside", sent.title)
        assertEquals(ReservationController.reservationId(drawn.id, winner.entrantId), sent.reservationId)
    }

    @Test
    fun rulesMustBeConfirmed() {
        val error = assertThrows(IllegalArgumentException::class.java) { reserve(rulesAccepted = false) }
        assertEquals("Please confirm you have read the campsite rules and policies.", error.message)
        assertTrue(saved().isEmpty())
    }

    @Test
    fun daysOutsideTheBookingPeriodAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { reserve(nov14 - 1, nov14) }
        assertThrows(IllegalArgumentException::class.java) { reserve(nov14 + 2, nov14 + 3) }
        assertThrows(IllegalArgumentException::class.java) { reserve(nov14 + 1, nov14) }
    }

    @Test
    fun daysAlreadyBookedByAnotherWinnerAreRejected() {
        reserve(nov14, nov14)
        val liam = TestData.liam.copy(status = WaitingListEntry.STATUS_WON)
        val error = assertThrows(IllegalArgumentException::class.java) { reserve(nov14, nov14 + 1, entry = liam) }
        assertEquals("Those dates are already booked. Choose other dates.", error.message)
        // The free days can still be reserved.
        assertNotNull(reserve(nov14 + 1, nov14 + 2, entry = liam))
    }

    @Test
    fun daysClosedByTheAvailabilityScheduleAreRejected() {
        // Nov 14 and 15, 2026 are a weekend; Nov 16 is a Monday.
        val weekendsOnly = Campsite(
            id = drawn.campsiteId, availableFrom = "2026-11-01", availableTo = "2026-11-30",
            availableWeekdays = listOf(1, 7),
        )
        assertNotNull(reserve(nov14, nov14 + 1, campsite = weekendsOnly))
        val liam = TestData.liam.copy(status = WaitingListEntry.STATUS_WON)
        assertThrows(IllegalArgumentException::class.java) {
            reserve(nov14 + 2, nov14 + 2, campsite = weekendsOnly, entry = liam)
        }
    }

    @Test
    fun winnerChoosesAmongTheCampsitesOfAMultiCampsiteLottery() {
        val multi = drawn.copy(
            extraCampsiteIds = listOf("camp-banff-tunnel"),
            extraCampsiteNames = listOf("Banff Tunnel Mountain"),
        )
        val reservation = reserve(lottery = multi, campsiteId = "camp-banff-tunnel")
        assertEquals("camp-banff-tunnel", reservation!!.campsiteId)
        assertEquals("Banff Tunnel Mountain", reservation.campsiteName)
        assertThrows(IllegalArgumentException::class.java) {
            reserve(lottery = multi, campsiteId = "camp-not-in-lottery", entry = TestData.liam.copy(status = WaitingListEntry.STATUS_WON))
        }
    }

    @Test
    fun reservingTwiceKeepsTheFirstReservation() {
        val first = reserve(nov14, nov14)
        assertNull(reserve(nov14 + 1, nov14 + 1))
        assertEquals(listOf(first), saved())
    }

    @Test
    fun entrantNotSelectedCannotReserve() {
        assertThrows(IllegalArgumentException::class.java) {
            reserve(entry = TestData.priya.copy(status = WaitingListEntry.STATUS_NOT_SELECTED))
        }
    }

    @Test
    fun stayDaysShowOpenBookedAndClosedDays() {
        val weekendsOnly = Campsite(
            id = drawn.campsiteId, availableFrom = "2026-11-01", availableTo = "2026-11-30",
            availableWeekdays = listOf(1, 7),
        )
        val booked = TestData.priyaReservation.copy(
            startDate = Days.startOfDay(nov14, mountain), endDate = Days.endOfDay(nov14, mountain),
        )
        val days = ReservationController.stayDays(drawn, drawn.campsiteId, weekendsOnly, listOf(booked), mountain)
        assertEquals(
            listOf(nov14 to DayState.BOOKED, nov14 + 1 to DayState.OPEN, nov14 + 2 to DayState.CLOSED),
            days,
        )
    }

    @Test
    fun winnerDeclinesAndTheOwnerIsTold() = runBlocking {
        assertTrue(controller.decline(drawn, winner))
        val declined = saved().single()
        assertEquals(Reservation.STATUS_DECLINED, declined.status)
        val sent = notifications.sent.single()
        assertEquals(AppNotification.TYPE_RESERVATION_DECLINED, sent.type)
        assertEquals(TestData.OWNER_ID, sent.recipientId)
        assertEquals("Priya Shah will not reserve.", sent.message)
        // A declined win cannot be reserved or declined again.
        assertFalse(controller.decline(drawn, winner))
        assertNull(reserve())
    }

    @Test
    fun declinedWinDoesNotBookAnyDay() = runBlocking {
        controller.decline(drawn, winner)
        val liam = TestData.liam.copy(status = WaitingListEntry.STATUS_WON)
        assertNotNull(reserve(nov14, nov14 + 2, entry = liam))
    }

    @Test
    fun entrantCancelsAndBothSidesAreNotified() = runBlocking {
        val reservation = reserve()!!
        notifications.sent.clear()
        controller.cancel(reservation)
        val cancelled = saved().single()
        assertEquals(Reservation.STATUS_CANCELLED, cancelled.status)
        assertEquals(now, cancelled.cancelledAt)
        assertEquals(
            listOf(winner.entrantId, TestData.OWNER_ID),
            notifications.sent.map { it.recipientId },
        )
        assertTrue(notifications.sent.all { it.type == AppNotification.TYPE_RESERVATION_CANCELLED })
        assertEquals("Cancelled: Jasper Riverside", notifications.sent.first().title)
    }

    @Test
    fun cancelledDaysCanBeReservedByAnotherWinner() = runBlocking {
        controller.cancel(reserve(nov14, nov14 + 2)!!)
        val liam = TestData.liam.copy(status = WaitingListEntry.STATUS_WON)
        assertNotNull(reserve(nov14, nov14 + 2, entry = liam))
    }

    @Test
    fun finishedOrCancelledReservationCannotBeCancelled() {
        val finished = ReservationController(repository, notifications) { TestData.BOOKING_END + 1 }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { finished.cancel(TestData.priyaReservation) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.cancel(TestData.priyaReservation.copy(status = Reservation.STATUS_CANCELLED)) }
        }
    }

    @Test
    fun rejectionTellsTheEntrantTheReason() = runBlocking {
        val reservation = reserve()!!
        notifications.sent.clear()
        controller.reject(reservation, " Site flooded ")
        val sent = notifications.sent.single()
        assertEquals(AppNotification.TYPE_RESERVATION_REJECTED, sent.type)
        assertEquals(winner.entrantId, sent.recipientId)
        assertEquals("Site flooded", sent.message)
    }

    @Test
    fun reservationsAreSplitIntoActivePastAndCancelled() {
        val active = TestData.priyaReservation
        val past = TestData.priyaReservation.copy(id = "past", startDate = 1_000, endDate = 2_000)
        val cancelled = TestData.priyaReservation.copy(id = "cancelled", status = Reservation.STATUS_CANCELLED, cancelledAt = 50)
        val rejected = TestData.priyaReservation.copy(id = "rejected", status = Reservation.STATUS_REJECTED, createdAt = 40)
        val declined = TestData.priyaReservation.copy(id = "declined", status = Reservation.STATUS_DECLINED, createdAt = 30)
        val all = listOf(past, declined, active, rejected, cancelled, TestData.liamReservation)
        val entrant = "device-priya-01"

        assertEquals(listOf(active), ReservationController.activeForEntrant(all, entrant, now))
        assertEquals(listOf(past), ReservationController.pastForEntrant(all, entrant, now))
        assertEquals(
            listOf("cancelled", "rejected", "declined"),
            ReservationController.cancelledForEntrant(all, entrant).map { it.id },
        )
        assertEquals(listOf(past), ReservationController.pastForOwner(all, TestData.OWNER_ID, now))
        // The owner's cancelled list has cancellations and rejections, not declined wins.
        assertEquals(
            listOf("cancelled", "rejected"),
            ReservationController.cancelledForOwner(all, TestData.OWNER_ID).map { it.id },
        )
    }

    @Test
    fun csvHasAHeaderAndOneQuotedRowPerReservation() {
        val tricky = TestData.priyaReservation.copy(entrantName = "Shah, \"Priya\"")
        val lines = ReservationController.toCsv(listOf(tricky, TestData.liamReservation)).trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("\"Reservation ID\",\"Campsite\",\"Entrant\",\"Check-in\",\"Check-out\",\"Status\""))
        assertTrue(lines[1].contains("\"Shah, \"\"Priya\"\"\""))
        assertTrue(lines[2].contains("\"Banff Tunnel Mountain\""))
    }

    @Test
    fun csvOfNothingIsJustTheHeader() {
        assertEquals(1, ReservationController.toCsv(emptyList()).trim().lines().size)
    }

    // US 02.04.08: a stay at another owner's campsite in a shared lottery belongs to that owner.
    @Test
    fun reservationAtAnotherOwnersCampsiteGoesToThatOwner() {
        val sharedLottery = drawn.copy(
            shared = true,
            extraCampsiteIds = listOf("camp-lakeside"),
            extraCampsiteNames = listOf("Lakeside Pines"),
            extraCampsiteOwnerIds = listOf("owner-device-b2c1"),
        )
        val reservation = reserve(lottery = sharedLottery, campsiteId = "camp-lakeside")!!
        assertEquals("owner-device-b2c1", reservation.ownerId)
        assertEquals("Lakeside Pines", reservation.campsiteName)
    }
}
