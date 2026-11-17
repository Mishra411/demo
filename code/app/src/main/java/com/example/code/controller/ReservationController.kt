/*
 * ReservationController.kt
 * Controller for reservations. A lottery winner sees the days that can
 * still be reserved (US 01.04.01), picks the campsite and dates
 * (US 01.04.02), confirms the campsite rules (US 02.11.01) and gets a
 * confirmation (US 01.05.01, 01.05.02), or declines (US 01.04.03).
 * Entrants see active and past reservations (US 01.06.01, 01.06.02), cancel
 * (US 01.07.01) and get a cancellation confirmation (US 01.07.02). Owners
 * see booked dates (US 02.06.01), active, past and cancelled reservations
 * (US 02.07.01-03) and can reject one (US 02.08.01). Lists can be exported
 * as CSV (US 01.05.02, 01.07.02).
 * Known issues: two winners choosing the same dates at the same moment are
 * only checked on the device.
 * AI use: date choice, decline, cancel, confirmations and CSV written with
 * Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.NotificationRepository
import com.example.code.data.ReservationRepository
import com.example.code.model.AppNotification
import com.example.code.model.Availability
import com.example.code.model.Campsite
import com.example.code.model.DayState
import com.example.code.model.Days
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.WaitingListEntry
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

/**
 * A campsite and the date ranges that are booked on it.
 *
 * @property campsiteName campsite name.
 * @property stays booked stays as (first day, last day), earliest first.
 */
data class BookedDates(
    val campsiteName: String,
    val stays: List<Pair<Long, Long>>,
)

/**
 * Handles reservations for entrants and campsite owners.
 *
 * @param reservations where reservations are read and saved.
 * @param notifications where confirmations are sent; null to send none.
 * @param clock current time in milliseconds; replaceable in tests.
 */
class ReservationController(
    private val reservations: ReservationRepository,
    private val notifications: NotificationRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** All reservations, updated live. */
    val allReservations: Flow<List<Reservation>> = reservations.observeReservations()

    /**
     * Creates the reservation for a winner of [lottery] for the lottery's
     * whole booking period at its main campsite.
     *
     * @param entry the winner's waiting list entry.
     * @return the reservation, or null if the winner had already accepted.
     * @throws IllegalArgumentException if [entry] did not win [lottery].
     */
    suspend fun acceptWin(lottery: Lottery, entry: WaitingListEntry): Reservation? {
        requireWinner(lottery, entry)
        val reservation = Reservation(
            id = reservationId(lottery.id, entry.entrantId),
            lotteryId = lottery.id,
            campsiteId = lottery.campsiteId,
            campsiteName = lottery.campsiteName,
            entrantId = entry.entrantId,
            entrantName = entry.entrantName,
            ownerId = lottery.ownerId,
            startDate = lottery.bookingStart,
            endDate = lottery.bookingEnd,
            createdAt = clock(),
        )
        return if (reservations.create(reservation)) reservation else null
    }

    /**
     * Reserves [campsiteId] from [firstDay] to [lastDay] for a winner of
     * [lottery] (US 01.04.01, 01.04.02) once they confirmed the rules
     * (US 02.11.01), and sends a confirmation (US 01.05.01).
     *
     * @param firstDay first day of the stay, as a day number from [Days].
     * @param lastDay last day of the stay.
     * @param existing current reservations, to check the days are free.
     * @param campsite the chosen campsite, so its availability schedule is respected.
     * @return the reservation, or null if the winner had already reserved or declined.
     * @throws IllegalArgumentException with a message for the entrant if the
     * choice is not allowed.
     */
    suspend fun reserve(
        lottery: Lottery,
        entry: WaitingListEntry,
        campsiteId: String,
        firstDay: Long,
        lastDay: Long,
        rulesAccepted: Boolean,
        existing: List<Reservation>,
        campsite: Campsite? = null,
        entrantName: String = entry.entrantName,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Reservation? {
        requireWinner(lottery, entry)
        if (forLottery(existing, lottery.id, entry.entrantId) != null) return null
        require(rulesAccepted) { "Please confirm you have read the campsite rules and policies." }
        val choice = LotteryController.campsitesOf(lottery).firstOrNull { it.first == campsiteId }
        requireNotNull(choice) { "Choose one of the campsites in this lottery." }
        require(firstDay <= lastDay) { "The stay must end on or after its first day." }
        val states = stayDays(lottery, campsiteId, campsite, existing, timeZone).toMap()
        require((firstDay..lastDay).all { it in states }) { "Choose dates inside the booking period." }
        require((firstDay..lastDay).none { states[it] == DayState.CLOSED }) {
            "The campsite is not available on some of those days."
        }
        require((firstDay..lastDay).all { states[it] == DayState.OPEN }) {
            "Those dates are already booked. Choose other dates."
        }
        val (start, end) = stayMillis(lottery, firstDay, lastDay, timeZone)
        val now = clock()
        val reservation = Reservation(
            id = reservationId(lottery.id, entry.entrantId),
            lotteryId = lottery.id,
            campsiteId = campsiteId,
            campsiteName = choice.second,
            entrantId = entry.entrantId,
            entrantName = entrantName.trim(),
            // In a shared lottery the chosen campsite may belong to another owner.
            ownerId = LotteryController.ownerOf(lottery, campsiteId),
            startDate = start,
            endDate = end,
            createdAt = now,
            rulesAcceptedAt = now,
        )
        if (!reservations.create(reservation)) return null
        notify(
            reservation,
            recipientId = entry.entrantId,
            type = AppNotification.TYPE_RESERVATION_CONFIRMED,
            title = "Reservation confirmed: ${reservation.campsiteName}",
            message = "Your stay is booked for ${dateRange(start, end)}. " +
                "You can download it as CSV from the Reservations tab.",
            at = now,
        )
        return reservation
    }

    /**
     * Records that a winner declined their win (US 01.04.03) and tells the owner.
     *
     * @return false if the winner had already reserved or declined.
     */
    suspend fun decline(lottery: Lottery, entry: WaitingListEntry): Boolean {
        requireWinner(lottery, entry)
        val now = clock()
        val declined = Reservation(
            id = reservationId(lottery.id, entry.entrantId),
            lotteryId = lottery.id,
            campsiteId = lottery.campsiteId,
            campsiteName = lottery.campsiteName,
            entrantId = entry.entrantId,
            entrantName = entry.entrantName,
            ownerId = lottery.ownerId,
            startDate = lottery.bookingStart,
            endDate = lottery.bookingEnd,
            status = Reservation.STATUS_DECLINED,
            createdAt = now,
        )
        if (!reservations.create(declined)) return false
        notify(
            declined,
            recipientId = lottery.ownerId,
            senderId = entry.entrantId,
            type = AppNotification.TYPE_RESERVATION_DECLINED,
            title = "A winner declined ${lottery.campsiteName}",
            message = "${entry.entrantName.ifBlank { "An entrant" }} will not reserve.",
            at = now,
        )
        return true
    }

    /**
     * Cancels the entrant's [reservation] (US 01.07.01), sends them a
     * confirmation (US 01.07.02) and tells the owner.
     *
     * @throws IllegalArgumentException if it is not active or already over.
     */
    suspend fun cancel(reservation: Reservation) {
        val now = clock()
        require(isActive(reservation, now)) { "Only an upcoming or current reservation can be cancelled." }
        reservations.cancel(reservation.id, now)
        val dates = dateRange(reservation.startDate, reservation.endDate)
        notify(
            reservation,
            recipientId = reservation.entrantId,
            type = AppNotification.TYPE_RESERVATION_CANCELLED,
            title = "Cancelled: ${reservation.campsiteName}",
            message = "Your stay for $dates is cancelled. " +
                "You can download the confirmation as CSV from the Reservations tab.",
            at = now,
        )
        notify(
            reservation,
            recipientId = reservation.ownerId,
            senderId = reservation.entrantId,
            type = AppNotification.TYPE_RESERVATION_CANCELLED,
            title = "Guest cancelled: ${reservation.campsiteName}",
            message = "${reservation.entrantName.ifBlank { "A guest" }} cancelled $dates.",
            at = now,
        )
    }

    /**
     * Rejects [reservation] with the owner's [reason] (US 02.08.01) and
     * tells the entrant.
     *
     * @throws IllegalArgumentException if [reason] is blank or the
     * reservation is not active.
     */
    suspend fun reject(reservation: Reservation, reason: String) {
        val text = reason.trim()
        require(text.isNotEmpty()) { "A reason is required" }
        require(reservation.status == Reservation.STATUS_ACTIVE) { "Reservation is not active" }
        reservations.reject(reservation.id, text)
        notify(
            reservation,
            recipientId = reservation.entrantId,
            senderId = reservation.ownerId,
            type = AppNotification.TYPE_RESERVATION_REJECTED,
            title = "Reservation rejected: ${reservation.campsiteName}",
            message = text,
            at = clock(),
        )
    }

    private suspend fun notify(
        reservation: Reservation,
        recipientId: String,
        type: String,
        title: String,
        message: String,
        at: Long,
        senderId: String = "",
    ) {
        if (recipientId.isBlank()) return
        notifications?.sendAll(
            listOf(
                AppNotification(
                    recipientId = recipientId,
                    senderId = senderId,
                    lotteryId = reservation.lotteryId,
                    reservationId = reservation.id,
                    campsiteName = reservation.campsiteName,
                    type = type,
                    title = title,
                    message = message,
                    createdAt = at,
                ),
            ),
        )
    }

    private fun requireWinner(lottery: Lottery, entry: WaitingListEntry) {
        require(lottery.status == Lottery.STATUS_DRAWN) { "Winners have not been drawn yet" }
        require(entry.status == WaitingListEntry.STATUS_WON) { "Only a winner can accept" }
    }

    companion object {
        /** Formats [millis] as a date, e.g. "Nov 14, 2026". */
        fun dateLabel(millis: Long): String = Days.label(Days.dayOf(millis))

        /** Formats a stay, e.g. "Nov 14, 2026 – Nov 16, 2026". */
        fun dateRange(start: Long, end: Long): String = "${dateLabel(start)} – ${dateLabel(end)}"

        /**
         * Returns true if [reservation] stands and its stay has not ended at [now].
         */
        fun isActive(reservation: Reservation, now: Long): Boolean =
            reservation.status == Reservation.STATUS_ACTIVE && reservation.endDate >= now

        /** Returns true if [reservation] stood and its stay is over at [now]. */
        fun isPast(reservation: Reservation, now: Long): Boolean =
            reservation.status == Reservation.STATUS_ACTIVE && reservation.endDate < now

        /** Returns true if [reservation] was cancelled, rejected or declined. */
        fun isCancelled(reservation: Reservation): Boolean =
            reservation.status == Reservation.STATUS_CANCELLED ||
                reservation.status == Reservation.STATUS_REJECTED ||
                reservation.status == Reservation.STATUS_DECLINED

        /** Builds the document ID for [entrantId]'s reservation in [lotteryId]. */
        fun reservationId(lotteryId: String, entrantId: String): String = "${lotteryId}_$entrantId"

        /** Entrant [entrantId]'s active reservations at [now], soonest first (US 01.06.01). */
        fun activeForEntrant(all: List<Reservation>, entrantId: String, now: Long): List<Reservation> =
            all.filter { it.entrantId == entrantId && isActive(it, now) }.sortedBy { it.startDate }

        /** Entrant [entrantId]'s past stays at [now], latest first (US 01.06.02). */
        fun pastForEntrant(all: List<Reservation>, entrantId: String, now: Long): List<Reservation> =
            all.filter { it.entrantId == entrantId && isPast(it, now) }.sortedByDescending { it.endDate }

        /** Entrant [entrantId]'s cancelled, rejected or declined reservations, latest first. */
        fun cancelledForEntrant(all: List<Reservation>, entrantId: String): List<Reservation> =
            all.filter { it.entrantId == entrantId && isCancelled(it) }
                .sortedByDescending { maxOf(it.cancelledAt, it.createdAt) }

        /** Active reservations at [now] for campsites of owner [ownerId] (US 02.07.01). */
        fun activeForOwner(all: List<Reservation>, ownerId: String, now: Long): List<Reservation> =
            all.filter { it.ownerId == ownerId && isActive(it, now) }.sortedBy { it.startDate }

        /** Past stays at owner [ownerId]'s campsites at [now], latest first (US 02.07.02). */
        fun pastForOwner(all: List<Reservation>, ownerId: String, now: Long): List<Reservation> =
            all.filter { it.ownerId == ownerId && isPast(it, now) }.sortedByDescending { it.endDate }

        /**
         * Reservations at owner [ownerId]'s campsites that the entrant
         * cancelled or the owner rejected, latest first (US 02.07.03).
         */
        fun cancelledForOwner(all: List<Reservation>, ownerId: String): List<Reservation> =
            all.filter {
                it.ownerId == ownerId &&
                    (it.status == Reservation.STATUS_CANCELLED || it.status == Reservation.STATUS_REJECTED)
            }.sortedByDescending { maxOf(it.cancelledAt, it.createdAt) }

        /**
         * Booked stays on each of owner [ownerId]'s campsites at [now],
         * grouped by campsite (US 02.06.01).
         */
        fun bookedDates(all: List<Reservation>, ownerId: String, now: Long): List<BookedDates> =
            activeForOwner(all, ownerId, now)
                .groupBy { it.campsiteName }
                .map { (name, list) -> BookedDates(name, list.map { it.startDate to it.endDate }) }
                .sortedBy { it.campsiteName }

        /** Entrant [entrantId]'s reservation from [lotteryId], or null. */
        fun forLottery(all: List<Reservation>, lotteryId: String, entrantId: String): Reservation? =
            all.firstOrNull { it.id == reservationId(lotteryId, entrantId) }

        /**
         * Every day of [lottery]'s booking period on [campsiteId] with its
         * state (US 01.04.01): open, already booked, or closed because the
         * [campsite]'s availability schedule does not include it.
         */
        fun stayDays(
            lottery: Lottery,
            campsiteId: String,
            campsite: Campsite?,
            all: List<Reservation>,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): List<Pair<Long, DayState>> {
            if (lottery.bookingEnd < lottery.bookingStart) return emptyList()
            val first = Days.dayOf(lottery.bookingStart, timeZone)
            val last = Days.dayOf(lottery.bookingEnd, timeZone)
            return (first..last).map { day ->
                day to when {
                    campsite != null && !Availability.isOpen(campsite, day) -> DayState.CLOSED
                    Availability.isBooked(all, campsiteId, day, timeZone) -> DayState.BOOKED
                    else -> DayState.OPEN
                }
            }
        }

        /**
         * Converts chosen days to a stay inside the booking period of
         * [lottery]: from the later of midnight on [firstDay] and the booking
         * start, to the earlier of the end of [lastDay] and the booking end.
         */
        fun stayMillis(
            lottery: Lottery,
            firstDay: Long,
            lastDay: Long,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): Pair<Long, Long> {
            val start = maxOf(Days.startOfDay(firstDay, timeZone), lottery.bookingStart)
            val end = minOf(Days.endOfDay(lastDay, timeZone), lottery.bookingEnd)
            return start to end
        }

        /**
         * Builds a CSV file of [reservations] for download (US 01.05.02,
         * 01.07.02). Every field is quoted so names with commas stay in one column.
         */
        fun toCsv(reservations: List<Reservation>): String {
            fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
            val header = listOf(
                "Reservation ID", "Campsite", "Entrant", "Check-in", "Check-out", "Status", "Reason", "Booked on",
            )
            val rows = reservations.map {
                listOf(
                    it.id,
                    it.campsiteName,
                    it.entrantName,
                    dateLabel(it.startDate),
                    dateLabel(it.endDate),
                    it.status,
                    it.rejectionReason,
                    dateLabel(it.createdAt),
                )
            }
            return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { cell(it) } } + "\n"
        }
    }
}
