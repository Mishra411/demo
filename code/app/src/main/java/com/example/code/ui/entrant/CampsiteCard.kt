/*
 * CampsiteCard.kt
 * Displays summary information for a campsite in the entrant Explore list
 * (US 01.01.04): photo, name, location, maximum occupancy and rating, plus
 * a badge when its lottery is open, its match score and its distance.
 * AI use: visual design and badges written with Claude (Anthropic), Nov 2026;
 * reviewed by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.code.controller.LotteryController
import com.example.code.model.Campsite
import com.example.code.model.Geo
import com.example.code.model.Lottery
import com.example.code.ui.common.RatingStars
import com.example.code.ui.common.RemoteImage
import com.example.code.ui.common.StatusChip
import com.example.code.ui.common.countdownText

/**
 * Displays the summary information required for a campsite listing.
 *
 * @param campsite campsite to display.
 * @param onClick called when the entrant selects the campsite.
 * @param lottery the campsite's lottery, used for the "Lottery open" badge.
 * @param now current time, for the badge's countdown.
 * @param matchScore match score from 0 to 100 to show, or null for none.
 * @param distanceKm distance from the entrant to show, or null for none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampsiteCard(
    campsite: Campsite,
    lottery: Lottery? = null,
    now: Long = System.currentTimeMillis(),
    matchScore: Int? = null,
    distanceKm: Double? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(
                ref = campsite.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = campsite.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconLine(Icons.Outlined.Place) {
                    Text(campsite.location, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconLine(Icons.Outlined.Group) {
                    Text(text = "Maximum occupancy: ${campsite.maxOccupancy}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val rating = campsite.rating
                    if (rating != null && rating.isFinite() && rating in 1.0..5.0) RatingStars(rating, size = 14.dp)
                    Text(
                        text = campsiteRatingText(campsite.rating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (matchScore != null) {
                        StatusChip(
                            text = "$matchScore% match",
                            icon = Icons.Outlined.ThumbUp,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (distanceKm != null) {
                        StatusChip(
                            text = "${Geo.formatDistance(distanceKm)} away",
                            icon = Icons.Outlined.NearMe,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (lottery != null && LotteryController.isRegistrationOpen(lottery, now)) {
                        StatusChip(
                            text = "Lottery open · ${countdownText(lottery.registrationEnd, now)} left",
                            icon = Icons.Filled.ConfirmationNumber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        content()
    }
}
