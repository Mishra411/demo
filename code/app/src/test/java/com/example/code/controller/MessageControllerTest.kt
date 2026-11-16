/*
 * MessageControllerTest.kt
 * Unit tests for messages between a campsite owner and the entrant who
 * reserved their campsite (US 02.12.01 and the messaging system).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.messageFromMap
import com.example.code.data.messageToMap
import com.example.code.fakes.FakeMessageRepository
import com.example.code.fakes.FakeNotificationRepository
import com.example.code.fakes.TestData
import com.example.code.model.AppNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageControllerTest {
    private val reservation = TestData.priyaReservation
    private val messages = FakeMessageRepository()
    private val notifications = FakeNotificationRepository()
    private var now = 5_000L
    private val controller = MessageController(messages, notifications) { now }

    @Test
    fun ownerMessagesTheGuest() = runBlocking {
        val message = controller.send(reservation, TestData.OWNER_ID, "Jasper Camps", " Gate code is 4521. ")
        assertEquals("Gate code is 4521.", message.text)
        assertTrue(message.fromOwner)
        assertEquals(reservation.entrantId, message.recipientId)
        assertEquals("message-1", message.id)

        val sent = notifications.sent.single()
        assertEquals(AppNotification.TYPE_DIRECT_MESSAGE, sent.type)
        assertEquals(reservation.entrantId, sent.recipientId)
        assertEquals(reservation.id, sent.reservationId)
        assertEquals("Jasper Camps · Jasper Riverside", sent.title)
        assertEquals("Gate code is 4521.", sent.message)
    }

    @Test
    fun guestRepliesToTheOwner() = runBlocking {
        val message = controller.send(reservation, reservation.entrantId, "Priya Shah", "Thanks!")
        assertFalse(message.fromOwner)
        assertEquals(TestData.OWNER_ID, message.recipientId)
        assertEquals(TestData.OWNER_ID, notifications.sent.single().recipientId)
    }

    @Test
    fun senderWithoutANameGetsARoleName() = runBlocking {
        assertEquals("Campsite owner", controller.send(reservation, TestData.OWNER_ID, " ", "Hello").senderName)
        assertEquals("Guest", controller.send(reservation, reservation.entrantId, "", "Hi").senderName)
    }

    @Test
    fun emptyOrTooLongMessagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.send(reservation, TestData.OWNER_ID, "Jasper Camps", "   ") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                controller.send(reservation, TestData.OWNER_ID, "Jasper Camps", "x".repeat(MessageController.MAX_LENGTH + 1))
            }
        }
        assertTrue(messages.sent.value.isEmpty())
        assertTrue(notifications.sent.isEmpty())
    }

    @Test
    fun strangersCannotMessageAboutSomeoneElsesReservation() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.send(reservation, "device-liam-02", "Liam Chen", "Hello") }
        }
    }

    @Test
    fun conversationHasOnlyThisReservationsMessagesOldestFirst() = runBlocking {
        controller.send(reservation, TestData.OWNER_ID, "Jasper Camps", "First")
        now = 6_000L
        controller.send(TestData.liamReservation, TestData.OWNER_ID, "Jasper Camps", "Other stay")
        now = 7_000L
        controller.send(reservation, reservation.entrantId, "Priya Shah", "Second")

        assertEquals(listOf("First", "Second"), controller.conversation(reservation).first().map { it.text })
        assertEquals(3, controller.all().first().size)
    }

    @Test
    fun messageSurvivesSavingAndLoading() = runBlocking {
        val message = controller.send(reservation, TestData.OWNER_ID, "Jasper Camps", "Gate code is 4521.")
        assertEquals(message, messageFromMap(message.id, messageToMap(message)))
    }
}
