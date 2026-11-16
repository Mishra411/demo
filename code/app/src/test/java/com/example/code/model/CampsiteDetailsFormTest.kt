/*
 * CampsiteDetailsFormTest.kt
 * Unit tests for the optional campsite form details: price per night,
 * rules, cancellation policy and the availability schedule.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CampsiteDetailsFormTest {
    private val base = Campsite(name = "Jasper Riverside", location = "Jasper, AB", maxOccupancy = 4)

    @Test
    fun scenarioWeekendsInMayAt65DollarsPerNight() {
        val campsite = campsiteDetailsFromForm(
            base, "65", " No fires after 11 pm. ", "Free cancellation up to 7 days before.",
            "2027-05-01", "2027-05-31", listOf(7, 1, 7),
        )
        assertEquals(65.0, campsite.price!!, 0.0)
        assertEquals("CAD", campsite.currency)
        assertEquals("No fires after 11 pm.", campsite.rules)
        assertEquals("Free cancellation up to 7 days before.", campsite.cancellationPolicy)
        assertEquals("2027-05-01", campsite.availableFrom)
        assertEquals("2027-05-31", campsite.availableTo)
        assertEquals(listOf(1, 7), campsite.availableWeekdays)
    }

    @Test
    fun everythingIsOptional() {
        val campsite = campsiteDetailsFromForm(base, "", "", "", "", "", emptyList())
        assertNull(campsite.price)
        assertNull(campsite.currency)
        assertEquals("", campsite.availableFrom)
        assertEquals(emptyList<Int>(), campsite.availableWeekdays)
    }

    @Test
    fun priceMayHaveADollarSignAndCents() {
        assertEquals(64.5, campsiteDetailsFromForm(base, " $64.50 ", "", "", "", "", emptyList()).price!!, 0.0)
    }

    @Test
    fun badPriceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            campsiteDetailsFromForm(base, "sixty", "", "", "", "", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            campsiteDetailsFromForm(base, "-5", "", "", "", "", emptyList())
        }
    }

    @Test
    fun badScheduleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            campsiteDetailsFromForm(base, "65", "", "", "2027-05-31", "2027-05-01", emptyList())
        }
    }
}
