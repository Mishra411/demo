/*
 * Availability.kt
 * Rules for a campsite's availability schedule: the dates and weekdays the
 * owner opened, which days are already booked, and the state of each day
 * shown on the availability calendar. Data stays in Campsite; these are the
 * rules, kept free of Android so they can be unit tested.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

import java.util.TimeZone

/** What a calendar day means for someone who wants to stay. */
enum class DayState {
    /** The campsite is available and nobody has booked the day. */
    OPEN,

    /** The campsite is available but the day is already reserved. */
    BOOKED,

    /** The owner has not made the day available. */
    CLOSED,
}

/** Availability rules for campsites. */
object Availability {

    /** True if the owner set a schedule on [campsite]. */
    fun hasSchedule(campsite: Campsite): Boolean =
        Days.parse(campsite.availableFrom) != null && Days.parse(campsite.availableTo) != null

    /**
     * True if [campsite]'s schedule includes [day]. A campsite with no
     * schedule is never closed by this rule.
     */
    fun isOpen(campsite: Campsite, day: Long): Boolean {
        val from = Days.parse(campsite.availableFrom) ?: return true
        val to = Days.parse(campsite.availableTo) ?: return true
        return day in from..to &&
            (campsite.availableWeekdays.isEmpty() || Days.weekday(day) in campsite.availableWeekdays)
    }

    /** True if [campsite] has a schedule and it includes every day from [first] to [last]. */
    fun covers(campsite: Campsite, first: Long, last: Long): Boolean =
        hasSchedule(campsite) && first <= last && (first..last).all { isOpen(campsite, it) }

    /**
     * Checks schedule fields typed by the owner.
     *
     * @return the cleaned (from, to) pair; both blank means no schedule.
     * @throws IllegalArgumentException with a message for the owner.
     */
    fun validate(from: String, to: String, weekdays: List<Int>): Pair<String, String> {
        val a = from.trim()
        val b = to.trim()
        if (a.isEmpty() && b.isEmpty()) {
            require(weekdays.isEmpty()) { "Enter the available dates before choosing weekdays." }
            return "" to ""
        }
        require(a.isNotEmpty() && b.isNotEmpty()) { "Enter both availability dates, or leave both empty." }
        val first = requireNotNull(Days.parse(a)) { "Use YYYY-MM-DD for the first available date." }
        val last = requireNotNull(Days.parse(b)) { "Use YYYY-MM-DD for the last available date." }
        require(first <= last) { "The last available date must be on or after the first." }
        require(weekdays.all { it in 1..7 }) { "Weekdays must be Sunday to Saturday." }
        require(weekdays.isEmpty() || (first..last).any { Days.weekday(it) in weekdays }) {
            "None of the chosen weekdays fall inside those dates."
        }
        return a to b
    }

    /** One-line description of [campsite]'s schedule for display. */
    fun describe(campsite: Campsite): String {
        val from = Days.parse(campsite.availableFrom)
        val to = Days.parse(campsite.availableTo)
        if (from == null || to == null) return "Availability not set"
        val days = if (campsite.availableWeekdays.isEmpty()) {
            "every day"
        } else {
            campsite.availableWeekdays.sorted().joinToString(", ") { Days.weekdayName(it) }
        }
        return "${Days.label(from)} – ${Days.label(to)} · $days"
    }

    /** True if an active reservation on [campsiteId] includes [day]. */
    fun isBooked(
        reservations: List<Reservation>,
        campsiteId: String,
        day: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean = reservations.any {
        it.campsiteId == campsiteId &&
            it.status == Reservation.STATUS_ACTIVE &&
            day in Days.dayOf(it.startDate, timeZone)..Days.dayOf(it.endDate, timeZone)
    }

    /**
     * State of [day] on [campsite]'s availability calendar. Without a
     * schedule, the booking periods of the campsite's [lotteries] count as
     * available.
     */
    fun dayState(
        campsite: Campsite,
        lotteries: List<Lottery>,
        reservations: List<Reservation>,
        day: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): DayState {
        val offered = if (hasSchedule(campsite)) {
            isOpen(campsite, day)
        } else {
            lotteries.any {
                (it.campsiteId == campsite.id || campsite.id in it.extraCampsiteIds) &&
                    it.bookingEnd >= it.bookingStart && it.bookingStart > 0 &&
                    day in Days.dayOf(it.bookingStart, timeZone)..Days.dayOf(it.bookingEnd, timeZone)
            }
        }
        return when {
            !offered -> DayState.CLOSED
            isBooked(reservations, campsite.id, day, timeZone) -> DayState.BOOKED
            else -> DayState.OPEN
        }
    }

    /**
     * Updates a tapped date range: the first tap starts a stay, a later day
     * ends it, and any other tap starts again.
     *
     * @return the new (first, last) selection.
     */
    fun selectDay(first: Long?, last: Long?, tapped: Long): Pair<Long?, Long?> =
        if (first != null && last == null && tapped >= first) first to tapped else tapped to null
}
