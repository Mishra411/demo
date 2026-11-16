/*
 * InboxTest.kt
 * Unit tests for splitting one device's notifications between its
 * entrant Notifications tab and its owner inbox.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.fakes.TestData
import com.example.code.model.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxTest {
    private val reservation = TestData.priyaReservation
    private val owner = TestData.OWNER_ID
    private val guest = reservation.entrantId

    private fun note(id: String, type: String, to: String, from: String = "", read: Boolean = false) =
        AppNotification(id = id, recipientId = to, senderId = from, reservationId = reservation.id, type = type, read = read)

    private val won = note("won", AppNotification.TYPE_LOTTERY_WON, guest, owner)
    private val declined = note("declined", AppNotification.TYPE_RESERVATION_DECLINED, owner, guest)
    private val guestCancelled = note("guest-cancelled", AppNotification.TYPE_RESERVATION_CANCELLED, owner, guest)
    private val myCancellation = note("my-cancellation", AppNotification.TYPE_RESERVATION_CANCELLED, guest)
    private val fromGuest = note("from-guest", AppNotification.TYPE_DIRECT_MESSAGE, owner, guest, read = true)
    private val fromOwner = note("from-owner", AppNotification.TYPE_DIRECT_MESSAGE, guest, owner)
    private val all = listOf(won, declined, guestCancelled, myCancellation, fromGuest, fromOwner)

    @Test
    fun ownerInboxHasGuestUpdatesOnly() {
        assertEquals(
            listOf("declined", "guest-cancelled", "from-guest"),
            Inbox.forOwner(all, listOf(reservation)).map { it.id },
        )
    }

    @Test
    fun entrantTabHasEverythingElse() {
        assertEquals(
            listOf("won", "my-cancellation", "from-owner"),
            Inbox.forEntrant(all, listOf(reservation)).map { it.id },
        )
    }

    @Test
    fun messageAboutAnUnknownReservationStaysWithTheEntrant() {
        assertEquals(listOf(fromGuest), Inbox.forEntrant(listOf(fromGuest), emptyList()))
    }

    @Test
    fun unreadCountsOnlyUnreadNotifications() {
        assertEquals(2, Inbox.unread(Inbox.forOwner(all, listOf(reservation))))
        assertEquals(0, Inbox.unread(emptyList()))
    }
}
