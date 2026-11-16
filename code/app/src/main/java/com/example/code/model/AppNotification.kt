/*
 * AppNotification.kt
 * Model of an in-app notification stored in Firestore (notifications/{id})
 * and shown to the recipient in the Notifications tab.
 * Known issues: notifications are in-app only; there are no push notifications.
 */

package com.example.code.model

/**
 * A notification sent to one entrant.
 *
 * Named AppNotification to avoid a clash with android.app.Notification.
 *
 * @property id Firestore document ID.
 * @property recipientId device ID of the entrant who receives it.
 * @property senderId device ID of the sender, or empty if the system sent it.
 * @property lotteryId lottery the notification is about.
 * @property campsiteName campsite name, for display.
 * @property type one of the TYPE constants, e.g. [TYPE_LOTTERY_WON].
 * @property title short heading.
 * @property message body text.
 * @property createdAt time sent, in milliseconds since the epoch.
 * @property reservationId reservation the notification is about, if any.
 * @property read true once the recipient has seen it.
 */
data class AppNotification(
    val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val lotteryId: String = "",
    val campsiteName: String = "",
    val type: String = TYPE_WAITLIST_MESSAGE,
    val title: String = "",
    val message: String = "",
    val createdAt: Long = 0L,
    val reservationId: String = "",
    val read: Boolean = false,
) {
    companion object {
        /** Message from a campsite owner to their waiting list (US 02.10.01). */
        const val TYPE_WAITLIST_MESSAGE = "WAITLIST_MESSAGE"

        /** Entrant was drawn as a lottery winner (US 01.03.01). */
        const val TYPE_LOTTERY_WON = "LOTTERY_WON"

        /** Entrant was not drawn (US 01.03.02). */
        const val TYPE_LOTTERY_LOST = "LOTTERY_LOST"

        /** Reservation confirmed (US 01.05.01, 01.05.02). */
        const val TYPE_RESERVATION_CONFIRMED = "RESERVATION_CONFIRMED"

        /** Reservation cancelled by the entrant (US 01.07.02). */
        const val TYPE_RESERVATION_CANCELLED = "RESERVATION_CANCELLED"

        /** Reservation rejected by the owner (US 02.08.01). */
        const val TYPE_RESERVATION_REJECTED = "RESERVATION_REJECTED"

        /** A winner declined; sent to the owner (US 01.04.03). */
        const val TYPE_RESERVATION_DECLINED = "RESERVATION_DECLINED"

        /** Message between an owner and a guest about a reservation (US 02.12.01). */
        const val TYPE_DIRECT_MESSAGE = "DIRECT_MESSAGE"
    }
}
