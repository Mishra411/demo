/*
 * CampsiteDetailsScreen.kt
 * Full campsite details (US 01.01.05, 02.01.04): photo, every stored field,
 * the campsite's location on a map (geolocation feature) and a calendar of
 * its availability (availability schedule feature), with slots for the
 * lottery card (US 01.01.06) and reviews (US 01.13.01).
 * AI use: map, availability calendar and visual design written with Claude
 * (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */
package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.code.model.Availability
import com.example.code.model.Campsite
import com.example.code.model.DayState
import com.example.code.model.Days
import com.example.code.model.Geo
import com.example.code.model.Lottery
import com.example.code.model.MapPoint
import com.example.code.model.Reservation
import com.example.code.ui.common.BackButton
import com.example.code.ui.common.InfoRow
import com.example.code.ui.common.RatingStars
import com.example.code.ui.common.RemoteImage
import com.example.code.ui.common.StayCalendar
import java.util.Locale

/**
 * Resolves the selected ID from live records, so edits and removal update an open screen.
 *
 * @param lotteries all lotteries, for the availability calendar.
 * @param reservations all reservations, for the availability calendar.
 * @param map draws the campsite's pin on a map; null hides the map.
 * @param reviewsContent ratings and reviews shown under the details.
 */
@Composable
fun CampsiteDetailsDestination(
    campsiteId: String?,
    campsites: List<Campsite>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    lotteryContent: @Composable () -> Unit = {},
    lotteries: List<Lottery> = emptyList(),
    reservations: List<Reservation> = emptyList(),
    map: (@Composable (MapPoint) -> Unit)? = null,
    reviewsContent: @Composable () -> Unit = {},
) {
    val campsite = campsites.firstOrNull { it.id == campsiteId }
    when {
        errorMessage != null -> DetailsStatus(onBack) {
            Text("Unable to load campsite details. Please try again.")
            Button(onClick = onRetry) { Text("Retry") }
        }
        isLoading -> DetailsStatus(onBack) {
            CircularProgressIndicator()
            Text("Loading campsite details…")
        }
        campsite == null -> DetailsStatus(onBack) {
            Text("Campsite not found. It may have been removed.")
        }
        else -> CampsiteDetailsScreen(campsite, onBack, lotteryContent, lotteries, reservations, map, reviewsContent)
    }
}

@Composable
private fun DetailsStatus(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackButton(onBack)
        content()
    }
}

/** Full stored information, shared by entrant, owner and admin detail views. */
@Composable
fun CampsiteDetailsScreen(
    campsite: Campsite,
    onBack: () -> Unit,
    lotteryContent: @Composable () -> Unit = {},
    lotteries: List<Lottery> = emptyList(),
    reservations: List<Reservation> = emptyList(),
    map: (@Composable (MapPoint) -> Unit)? = null,
    reviewsContent: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackButton(onBack)
        Text(campsite.name, style = MaterialTheme.typography.headlineSmall)
        val rating = campsite.rating
        if (rating != null && rating.isFinite() && rating in 1.0..5.0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RatingStars(rating, size = 20.dp)
                if (campsite.ratingCount > 0) {
                    Text(
                        "${campsite.ratingCount} rating${if (campsite.ratingCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        RemoteImage(
            ref = campsite.imageUrl,
            contentDescription = if (campsite.imageUrl.isNullOrBlank())
                "No promotional image available" else "Promotional image for ${campsite.name}",
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
        )
        lotteryContent()
        DetailsCard {
            InfoRow(Icons.Outlined.Place, "Location", campsite.location)
            InfoRow(Icons.Outlined.Description, "Description / amenities", campsite.description)
            InfoRow(Icons.Outlined.Group, "Maximum occupancy", campsite.maxOccupancy.toString())
            InfoRow(Icons.Outlined.AttachMoney, "Price", campsitePriceText(campsite.price, campsite.currency))
            InfoRow(Icons.Outlined.StarOutline, "Rating", campsiteRatingText(campsite.rating))
            InfoRow(Icons.Outlined.MyLocation, "Map location", "${campsite.latitude}, ${campsite.longitude}")
            InfoRow(Icons.Outlined.LocalOffer, "Tags", campsite.tags.joinToString(", ").ifBlank { "No tags" })
            InfoRow(Icons.Outlined.Gavel, "Rules", campsite.rules.ifBlank { "No rules provided" })
            InfoRow(
                Icons.Outlined.EventBusy,
                "Cancellation policy",
                campsite.cancellationPolicy.ifBlank { "No cancellation policy provided" },
            )
        }
        if (map != null && Geo.hasLocation(campsite)) {
            DetailsCard {
                Text("On the map", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.small)) {
                    map(MapPoint(campsite.id, campsite.name, campsite.location, campsite.latitude, campsite.longitude))
                }
            }
        }
        AvailabilityCard(campsite, lotteries, reservations)
        reviewsContent()
    }
}

/**
 * Calendar of the days [campsite] is available, booked or not offered.
 * Hidden when there is neither a schedule nor a lottery to show.
 */
@Composable
fun AvailabilityCard(campsite: Campsite, lotteries: List<Lottery>, reservations: List<Reservation>) {
    val mine = lotteries.filter { it.campsiteId == campsite.id || campsite.id in it.extraCampsiteIds }
    if (!Availability.hasSchedule(campsite) && mine.isEmpty()) return
    val today = Days.dayOf(System.currentTimeMillis())
    val starts = listOfNotNull(Days.parse(campsite.availableFrom).takeIf { Availability.hasSchedule(campsite) }) +
        mine.filter { it.bookingStart > 0 }.map { Days.dayOf(it.bookingStart) }
    val initial = starts.filter { it >= today }.minOrNull() ?: starts.maxOrNull() ?: today
    DetailsCard {
        Text("Availability", style = MaterialTheme.typography.titleMedium)
        Text(
            if (Availability.hasSchedule(campsite)) Availability.describe(campsite)
            else "Available during its lottery's booking period.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StayCalendar(
            stateOf = { day -> Availability.dayState(campsite, mine, reservations, day) },
            initialDay = initial,
        )
    }
}

@Composable
private fun DetailsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** Do not invent a currency or a price for older campsite records. */
internal fun campsitePriceText(price: Double?, currency: String?): String {
    if (price == null || !price.isFinite() || price < 0) return "Price not provided"
    val amount = String.format(Locale.getDefault(), "%.2f", price)
    return listOfNotNull(currency?.trim()?.takeIf { it.isNotEmpty() }, amount).joinToString(" ")
}
