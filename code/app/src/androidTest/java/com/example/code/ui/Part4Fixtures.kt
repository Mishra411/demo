/*
 * Part4Fixtures.kt
 * Realistic sample data shared by the Part 4 UI (intent) tests.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui

import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.WaitingListEntry
import java.util.TimeZone

/** Fixture data belongs in androidTest; src/test fixtures are a separate source set. */
internal object Part4Fixtures {
    /** Mountain time used by the sample times below. */
    val mountain: TimeZone = TimeZone.getTimeZone("GMT-06:00")

    const val OWNER_ID = "owner-device-7f3a"
    const val PRIYA_ID = "device-priya-01"

    const val BOOKING_START = 1794636000000L // Nov 14, 2026 00:00 (UTC-6)
    const val BOOKING_END = 1794808800000L // Nov 16, 2026 00:00 (UTC-6)

    val jasper = Campsite(
        id = "camp-jasper-riverside",
        ownerId = OWNER_ID,
        name = "Jasper Riverside",
        location = "Jasper, AB",
        description = "Quiet riverside site with a fire pit.",
        maxOccupancy = 4,
        latitude = 52.8737,
        longitude = -118.0814,
        tags = listOf("river", "hiking"),
        rating = 4.5,
        ratingCount = 2,
        price = 65.0,
        currency = "CAD",
        rules = "No fires after 11 pm.",
        cancellationPolicy = "Free cancellation up to 7 days before.",
        availableFrom = "2027-05-01",
        availableTo = "2027-05-31",
        availableWeekdays = listOf(1, 7),
    )

    val banff = Campsite(
        id = "camp-banff-tunnel",
        ownerId = OWNER_ID,
        name = "Banff Tunnel Mountain",
        location = "Banff, AB",
        description = "Mountain views close to town.",
        maxOccupancy = 6,
        latitude = 51.1905,
        longitude = -115.5370,
        tags = listOf("mountain"),
        rules = "Store food in the bear locker.",
        cancellationPolicy = "No refunds within 48 hours.",
    )

    val drawnLottery = Lottery(
        id = "lottery-jasper-nov",
        campsiteId = jasper.id,
        campsiteName = jasper.name,
        ownerId = OWNER_ID,
        registrationStart = 1793545200000L,
        registrationEnd = 1794178800000L,
        bookingStart = BOOKING_START,
        bookingEnd = BOOKING_END,
        winnerCount = 2,
        waitlistCapacity = 4,
        waitingListCount = 4,
        status = Lottery.STATUS_DRAWN,
    )

    val priyaWon = WaitingListEntry(PRIYA_ID, "Priya Shah", 1793545260000L, WaitingListEntry.STATUS_WON)

    val priyaReservation = Reservation(
        id = "lottery-jasper-nov_device-priya-01",
        lotteryId = drawnLottery.id,
        campsiteId = jasper.id,
        campsiteName = jasper.name,
        entrantId = PRIYA_ID,
        entrantName = "Priya Shah",
        ownerId = OWNER_ID,
        startDate = BOOKING_START,
        endDate = BOOKING_END,
        createdAt = 1794232800000L,
    )
}
