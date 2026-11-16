/*
 * FeaturedCarousel.kt
 * Featured carousel WOW feature: a swipeable row of campsites picked for
 * the entrant from their preferred tags, ratings and open lotteries.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.code.model.Campsite
import com.example.code.model.FeaturedCampsite
import com.example.code.ui.common.RemoteImage
import com.example.code.ui.common.StatusChip

/**
 * Swipeable carousel of featured campsites.
 *
 * @param featured campsites from Featured.pick, best first.
 * @param onCampsiteClick called with the campsite the entrant tapped.
 */
@Composable
fun FeaturedCarousel(
    featured: List<FeaturedCampsite>,
    onCampsiteClick: (Campsite) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (featured.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { featured.size })
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Featured for you", style = MaterialTheme.typography.titleMedium)
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = 48.dp),
            pageSpacing = 10.dp,
            modifier = Modifier.testTag("featuredCarousel"),
        ) { page ->
            val item = featured[page]
            Card(onClick = { onCampsiteClick(item.campsite) }, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Box(Modifier.fillMaxSize()) {
                    RemoteImage(item.campsite.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                        ),
                    )
                    StatusChip(
                        text = item.reason,
                        icon = Icons.Filled.AutoAwesome,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(
                            "Featured: ${item.campsite.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.campsite.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Text(
            "${pagerState.currentPage + 1} of ${featured.size} · swipe for more",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
