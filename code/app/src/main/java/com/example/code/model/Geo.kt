/*
 * Geo.kt
 * Distance maths for the map view WOW feature: how far a campsite is from
 * the entrant and which campsites are inside a chosen radius.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A pin on a map.
 *
 * @property id what the pin stands for, e.g. a campsite ID.
 * @property title name shown when the pin is tapped.
 * @property subtitle second line shown with the title.
 * @property latitude latitude in degrees.
 * @property longitude longitude in degrees.
 * @property highlight true to draw the pin in the accent colour (e.g. an active stay).
 */
data class MapPoint(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val latitude: Double,
    val longitude: Double,
    val highlight: Boolean = false,
)

/** Map distance rules. */
object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in kilometres between two points (haversine formula). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** True if [campsite] has a real map location (0, 0 means it was never set). */
    fun hasLocation(campsite: Campsite): Boolean =
        campsite.latitude in -90.0..90.0 && campsite.longitude in -180.0..180.0 &&
            !(campsite.latitude == 0.0 && campsite.longitude == 0.0)

    /**
     * Campsites within [radiusKm] of ([latitude], [longitude]), nearest
     * first, each with its distance in kilometres.
     */
    fun withinRadius(
        campsites: List<Campsite>,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<Pair<Campsite, Double>> =
        campsites.filter(::hasLocation)
            .map { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= radiusKm }
            .sortedBy { it.second }

    /** One pin per campsite that has a map location. */
    fun campsitePoints(campsites: List<Campsite>): List<MapPoint> =
        campsites.filter(::hasLocation).map {
            MapPoint(it.id, it.name, it.location, it.latitude, it.longitude)
        }

    /**
     * Pins for the reservation map WOW feature: one per reservation whose
     * campsite still exists and has a map location. [active] stays are
     * highlighted; [past] stays are not.
     *
     * @param describe text shown under the campsite name, e.g. the stay dates.
     */
    fun reservationPoints(
        active: List<Reservation>,
        past: List<Reservation>,
        campsites: List<Campsite>,
        describe: (Reservation) -> String,
    ): List<MapPoint> {
        val byId = campsites.filter(::hasLocation).associateBy { it.id }
        fun pins(list: List<Reservation>, highlight: Boolean) = list.mapNotNull { reservation ->
            byId[reservation.campsiteId]?.let {
                MapPoint(reservation.id, reservation.campsiteName, describe(reservation), it.latitude, it.longitude, highlight)
            }
        }
        return pins(active, true) + pins(past, false)
    }

    /** Distance for display, e.g. "850 m" or "12.4 km". */
    fun formatDistance(km: Double): String =
        if (km < 1.0) "${(km * 1000).toInt()} m" else String.format(java.util.Locale.CANADA, "%.1f km", km)
}
