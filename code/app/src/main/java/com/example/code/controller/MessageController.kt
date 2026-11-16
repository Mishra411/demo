/*
 * MessageController.kt
 * Messages between a campsite owner and the entrant who reserved their
 * campsite (US 02.12.01 and the messaging system WOW feature). The
 * recipient also gets an in-app notification. Admins read every message in
 * the logs (US 03.12.01).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.MessageRepository
import com.example.code.data.NotificationRepository
import com.example.code.model.AppNotification
import com.example.code.model.Message
import com.example.code.model.Reservation
import kotlinx.coroutines.flow.Flow

/**
 * Sends and lists messages about reservations.
 *
 * @param messages where messages are saved.
 * @param notifications where message alerts are sent.
 * @param clock current time in milliseconds; replaceable in tests.
 */
class MessageController(
    private val messages: MessageRepository,
    private val notifications: NotificationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Messages about [reservation], oldest first. */
    fun conversation(reservation: Reservation): Flow<List<Message>> =
        messages.conversation(reservation.id)

    /** Every message, newest first (US 03.12.01). */
    fun all(): Flow<List<Message>> = messages.observeAll()

    /**
     * Sends [text] from [senderId] about [reservation] to the other party.
     *
     * @return the saved message.
     * @throws IllegalArgumentException if the text is blank or too long, or the
     * sender is not the owner or the entrant of the reservation.
     */
    suspend fun send(reservation: Reservation, senderId: String, senderName: String, text: String): Message {
        val body = text.trim()
        require(body.isNotEmpty()) { "Message cannot be empty" }
        require(body.length <= MAX_LENGTH) { "Messages can be at most $MAX_LENGTH characters." }
        val recipient = recipientFor(reservation, senderId)
        val fromOwner = senderId == reservation.ownerId
        val now = clock()
        val message = Message(
            reservationId = reservation.id,
            campsiteName = reservation.campsiteName,
            senderId = senderId,
            senderName = senderName.trim().ifBlank { if (fromOwner) "Campsite owner" else "Guest" },
            recipientId = recipient,
            fromOwner = fromOwner,
            text = body,
            createdAt = now,
        )
        val id = messages.send(message)
        notifications.sendAll(
            listOf(
                AppNotification(
                    recipientId = recipient,
                    senderId = senderId,
                    lotteryId = reservation.lotteryId,
                    reservationId = reservation.id,
                    campsiteName = reservation.campsiteName,
                    type = AppNotification.TYPE_DIRECT_MESSAGE,
                    title = "${message.senderName} · ${reservation.campsiteName}",
                    message = body,
                    createdAt = now,
                ),
            ),
        )
        return message.copy(id = id)
    }

    companion object {
        /** Longest message allowed. */
        const val MAX_LENGTH = 1000

        /**
         * The other party of [reservation] for [senderId].
         *
         * @throws IllegalArgumentException if [senderId] is not part of it.
         */
        fun recipientFor(reservation: Reservation, senderId: String): String = when (senderId) {
            reservation.ownerId -> reservation.entrantId
            reservation.entrantId -> reservation.ownerId
            else -> throw IllegalArgumentException("Only the owner and the guest can message about this reservation.")
        }
    }
}
