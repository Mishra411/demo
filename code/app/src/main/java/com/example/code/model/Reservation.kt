/*
 * Reservation.kt
 * Model of a campsite reservation made by a lottery winner
 * (Firestore: reservations/{lotteryId}_{entrantId}). Data only; the rules
 * (is it active, its ID) are in ReservationController.
 * A winner picks the campsite and dates (US 01.04.01, 01.04.02), can decline
 * (US 01.04.03) or cancel later (US 01.07.01).
 * Known issues: none.
 */

package com.example.code.model

/**
 * A reservation of one campsite by one entrant. Times are milliseconds
 * since the epoch.
 *
 * @property id Firestore document ID, "{lotteryId}_{entrantId}", so a winner
 * can only reserve once per lottery.
 * @property lotteryId lottery the entrant won.
 * @property campsiteId campsite reserved.
 * @property campsiteName campsite name, for display.
 * @property entrantId device ID of the entrant.
 * @property entrantName entrant name shown to the owner.
 * @property ownerId device ID of the campsite owner.
 * @property startDate first day of the stay.
 * @property endDate last day of the stay.
 * @property status [STATUS_ACTIVE], [STATUS_REJECTED], [STATUS_CANCELLED] or [STATUS_DECLINED].
 * @property rejectionReason owner's reason, if rejected.
 * @property createdAt time the reservation was made.
 * @property cancelledAt time the entrant cancelled, or 0 (US 01.07.01).
 * @property rulesAcceptedAt time the entrant confirmed the campsite rules, or 0 (US 02.11.01).
 */
data class Reservation(
    val id: String = "",
    val lotteryId: String = "",
    val campsiteId: String = "",
    val campsiteName: String = "",
    val entrantId: String = "",
    val entrantName: String = "",
    val ownerId: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val status: String = STATUS_ACTIVE,
    val rejectionReason: String = "",
    val createdAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val rulesAcceptedAt: Long = 0L,
) {

    companion object {
        /** Reservation stands. */
        const val STATUS_ACTIVE = "ACTIVE"

        /** Owner rejected the reservation (US 02.08.01). */
        const val STATUS_REJECTED = "REJECTED"

        /** Entrant cancelled the reservation (US 01.07.01). */
        const val STATUS_CANCELLED = "CANCELLED"

        /** Winner declined to reserve (US 01.04.03). */
        const val STATUS_DECLINED = "DECLINED"
    }
}
