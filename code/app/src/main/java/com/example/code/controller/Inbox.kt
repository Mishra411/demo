/*
 * Inbox.kt
 * Splits one device's notifications between its two roles. The same device
 * can be an entrant and a campsite owner, so notifications about the
 * owner's campsites go to the owner inbox and the rest to the entrant's
 * Notifications tab.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.model.AppNotification
import com.example.code.model.Reservation

/** Rules for which inbox a notification belongs to. */
object Inbox {

    /**
     * True if [notification] was sent to its recipient as a campsite owner:
     * a winner declined, a guest cancelled, or a guest wrote about a
     * reservation in [reservations] at the recipient's campsite.
     */
    fun isForOwner(notification: AppNotification, reservations: List<Reservation>): Boolean {
        val fromSomeoneElse = notification.senderId.isNotBlank() && notification.senderId != notification.recipientId
        return when (notification.type) {
            AppNotification.TYPE_RESERVATION_DECLINED -> true
            AppNotification.TYPE_RESERVATION_CANCELLED -> fromSomeoneElse
            AppNotification.TYPE_DIRECT_MESSAGE ->
                fromSomeoneElse && reservations.any {
                    it.id == notification.reservationId && it.ownerId == notification.recipientId
                }
            else -> false
        }
    }

    /** Notifications for the owner inbox, in the given order. */
    fun forOwner(all: List<AppNotification>, reservations: List<Reservation>): List<AppNotification> =
        all.filter { isForOwner(it, reservations) }

    /** Notifications for the entrant's Notifications tab, in the given order. */
    fun forEntrant(all: List<AppNotification>, reservations: List<Reservation>): List<AppNotification> =
        all.filterNot { isForOwner(it, reservations) }

    /** Number of unread notifications in [notifications]. */
    fun unread(notifications: List<AppNotification>): Int = notifications.count { !it.read }
}
