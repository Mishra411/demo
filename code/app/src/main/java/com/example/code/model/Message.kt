/*
 * Message.kt
 * Model of a message about one reservation, between the campsite owner and
 * the entrant who reserved (Firestore: messages/{id}). Used by US 02.12.01,
 * US 03.12.01 and the messaging system WOW feature. Data only.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

/**
 * One message in a reservation's conversation.
 *
 * @property id Firestore document ID.
 * @property reservationId reservation the conversation is about.
 * @property campsiteName campsite name, for display.
 * @property senderId device ID of the sender.
 * @property senderName sender's display name.
 * @property recipientId device ID of the recipient.
 * @property fromOwner true if the campsite owner sent it.
 * @property text message body.
 * @property createdAt time sent, in milliseconds since the epoch.
 */
data class Message(
    val id: String = "",
    val reservationId: String = "",
    val campsiteName: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val fromOwner: Boolean = false,
    val text: String = "",
    val createdAt: Long = 0L,
)
