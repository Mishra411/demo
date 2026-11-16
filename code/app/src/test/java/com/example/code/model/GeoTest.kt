/*
 * GeoTest.kt
 * Unit tests for distances, the radius map view and reservation map pins.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import com.example.code.fakes.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    // Edmonton
    private val homeLat = 53.5461
    private val homeLon = -113.4938

    private val jasper = Campsite(
        id = "camp-jasper-riverside", name = "Jasper Riverside", location = "Jasper, AB",
        latitude = 52.8737, longitude = -118.0814,
    )
    private val elkIsland = Campsite(
        id = "camp-elk-island", name = "Elk Island Astotin", location = "Elk Island, AB",
        latitude = 53.6836, longitude = -112.8650,
    )
    private val unplaced = Campsite(id = "camp-unplaced", name = "No Location")

    @Test
    fun distanceFromEdmontonToJasperIsAbout313Km() {
        val km = Geo.distanceKm(homeLat, homeLon, jasper.latitude, jasper.longitude)
        assertEquals(313.0, km, 5.0)
    }

    @Test
    fun distanceToTheSamePointIsZero() {
        assertEquals(0.0, Geo.distanceKm(homeLat, homeLon, homeLat, homeLon), 1e-9)
    }

    @Test
    fun zeroZeroIsNotARealLocation() {
        assertTrue(Geo.hasLocation(jasper))
        assertFalse(Geo.hasLocation(unplaced))
        assertFalse(Geo.hasLocation(jasper.copy(latitude = 123.0)))
    }

    @Test
    fun radiusKeepsNearbyCampsitesNearestFirst() {
        val all = listOf(jasper, unplaced, elkIsland)
        assertEquals(
            listOf("camp-elk-island"),
            Geo.withinRadius(all, homeLat, homeLon, 100.0).map { it.first.id },
        )
        assertEquals(
            listOf("camp-elk-island", "camp-jasper-riverside"),
            Geo.withinRadius(all, homeLat, homeLon, 400.0).map { it.first.id },
        )
    }

    @Test
    fun campsitePinsSkipCampsitesWithoutALocation() {
        val points = Geo.campsitePoints(listOf(jasper, unplaced))
        assertEquals(listOf(MapPoint("camp-jasper-riverside", "Jasper Riverside", "Jasper, AB", 52.8737, -118.0814)), points)
    }

    @Test
    fun reservationPinsHighlightActiveStays() {
        val past = TestData.priyaReservation.copy(id = "past-stay")
        val points = Geo.reservationPoints(
            active = listOf(TestData.priyaReservation),
            past = listOf(past, TestData.liamReservation),
            campsites = listOf(jasper),
        ) { "Nov 14 – Nov 16" }
        // Liam's campsite has no location, so it has no pin.
        assertEquals(listOf(TestData.priyaReservation.id, "past-stay"), points.map { it.id })
        assertEquals(listOf(true, false), points.map { it.highlight })
        assertEquals("Nov 14 – Nov 16", points.first().subtitle)
    }

    @Test
    fun formatsDistances() {
        assertEquals("850 m", Geo.formatDistance(0.85))
        assertEquals("12.4 km", Geo.formatDistance(12.44))
    }
}
