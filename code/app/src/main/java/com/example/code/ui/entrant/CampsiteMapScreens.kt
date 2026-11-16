/*
 * CampsiteMapScreens.kt
 * Two map WOW features. CampsiteRadiusMap shows campsites within a chosen
 * radius of the device's location. ReservationMapScreen shows the entrant's
 * past and active reservations on a map.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.code.model.Campsite
import com.example.code.model.Geo
import com.example.code.model.MapPoint
import com.example.code.model.Reservation
import com.example.code.ui.common.BackButton
import com.example.code.ui.common.StatusChip
import kotlin.math.roundToInt

/** Draws pins, with an optional circle of a radius in km around a centre. */
typealias MapRenderer = @Composable (
    points: List<MapPoint>,
    center: Pair<Double, Double>?,
    radiusKm: Double?,
    onPointClick: (MapPoint) -> Unit,
) -> Unit

/** Smallest and largest radius the entrant can choose, in kilometres. */
val RADIUS_RANGE_KM = 10f..600f

/**
 * Map view: campsites within [radiusKm] of the entrant's location.
 *
 * @param campsites campsites to consider (already searched and filtered).
 * @param origin the device's location, or null if it is not known yet.
 * @param radiusKm chosen radius in kilometres.
 * @param onRadiusChange called while the entrant moves the radius slider.
 * @param locationMessage why the location is missing, or null.
 * @param onUseMyLocation asks for the device's location.
 * @param onCampsiteClick opens a campsite.
 * @param map draws the map; replaced by a stand-in in tests.
 */
@Composable
fun CampsiteRadiusMap(
    campsites: List<Campsite>,
    origin: Pair<Double, Double>?,
    radiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    locationMessage: String?,
    onUseMyLocation: () -> Unit,
    onCampsiteClick: (Campsite) -> Unit,
    map: MapRenderer,
    modifier: Modifier = Modifier,
) {
    val located = campsites.filter(Geo::hasLocation)
    val nearby: List<Pair<Campsite, Double?>> = if (origin == null) {
        located.map { it to null }
    } else {
        Geo.withinRadius(located, origin.first, origin.second, radiusKm.toDouble())
    }
    val byId = nearby.associate { it.first.id to it.first }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseMyLocation) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Use my location", modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                if (origin == null) locationMessage ?: "Showing all campsites."
                else "Within ${radiusKm.roundToInt()} km of you",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        if (origin != null) {
            Slider(
                value = radiusKm,
                onValueChange = onRadiusChange,
                valueRange = RADIUS_RANGE_KM,
                modifier = Modifier.testTag("radiusSlider"),
            )
        }
        Box(Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium)) {
            map(
                nearby.map { (campsite, km) ->
                    MapPoint(
                        campsite.id,
                        campsite.name,
                        km?.let { "${Geo.formatDistance(it)} away" } ?: campsite.location,
                        campsite.latitude,
                        campsite.longitude,
                    )
                },
                origin,
                if (origin != null) radiusKm.toDouble() else null,
            ) { point -> byId[point.id]?.let(onCampsiteClick) }
        }
        Text(
            "${nearby.size} campsite${if (nearby.size == 1) "" else "s"} on the map",
            style = MaterialTheme.typography.labelLarge,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(nearby, key = { it.first.id }) { (campsite, km) ->
                ListItem(
                    headlineContent = { Text(campsite.name) },
                    supportingContent = { Text(campsite.location) },
                    trailingContent = { if (km != null) Text(Geo.formatDistance(km)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onCampsiteClick(campsite) },
                )
            }
        }
    }
}

/**
 * Reservation map: the entrant's active and past stays as pins.
 *
 * @param points pins from Geo.reservationPoints; active stays are highlighted.
 * @param missing number of reservations whose campsite has no map location.
 * @param onBack called when the entrant goes back.
 * @param map draws the map; replaced by a stand-in in tests.
 */
@Composable
fun ReservationMapScreen(
    points: List<MapPoint>,
    missing: Int,
    onBack: () -> Unit,
    map: MapRenderer,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackButton(onBack)
        Text("Reservation map", style = MaterialTheme.typography.headlineSmall)
        if (points.isEmpty()) {
            Text("You have no reservations to show on the map yet.")
        } else {
            Text(
                "${points.count { it.highlight }} active and ${points.count { !it.highlight }} past stays",
                style = MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.fillMaxWidth().height(320.dp).clip(MaterialTheme.shapes.medium)) {
                map(points, null, null) {}
            }
            LazyColumn(Modifier.weight(1f)) {
                items(points, key = { it.id }) { point ->
                    ListItem(
                        headlineContent = { Text(point.title) },
                        supportingContent = { Text(point.subtitle) },
                        trailingContent = { StatusChip(if (point.highlight) "Active" else "Past") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        if (missing > 0) {
            Text(
                "$missing reservation${if (missing == 1) " is" else "s are"} not shown because the campsite has no map location.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
