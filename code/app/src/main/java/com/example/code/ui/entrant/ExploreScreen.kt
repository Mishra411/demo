/*
 * ExploreScreen.kt
 * Searches live campsite records and applies validated criteria from the filter dialog.
 * With filters on, results are ranked by match score (WOW feature). The screen can also
 * show a featured carousel and a map view of the results (WOW features).
 * Known issues: dates depend on published lottery periods, availability schedules and
 * reservations supplied by navigation.
 * AI use: match score ranking, carousel, map toggle and visual design written with
 * Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */
package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.controller.CampsiteFilterController
import com.example.code.controller.LotteryController
import com.example.code.controller.WaitingListController
import com.example.code.model.Campsite
import com.example.code.model.CampsiteFilters
import com.example.code.model.FeaturedCampsite
import com.example.code.model.Lottery
import com.example.code.model.MatchScore
import com.example.code.model.Reservation
import com.example.code.model.filterCampsites
import com.example.code.model.searchCampsites
import com.example.code.ui.common.EmptyState
import kotlinx.coroutines.delay

/**
 * Browses the latest records using search and the applied filter state.
 * Filter controls supply [filters]; date availability is resolved by their lottery lookup.
 *
 * @param featured campsites for the featured carousel; empty hides it.
 * @param mapContent draws the results on a map; null hides the List / Map switch.
 */
@Composable
fun ExploreScreen(
    campsites: List<Campsite>,
    onCampsiteClick: (Campsite) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    filters: CampsiteFilters = CampsiteFilters(),
    onClearFilters: (() -> Unit)? = null,
    lotteries: List<Lottery> = emptyList(),
    reservations: List<Reservation> = emptyList(),
    availabilityReady: Boolean = true,
    featured: List<FeaturedCampsite> = emptyList(),
    mapContent: (@Composable (List<Campsite>) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var guests by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var editingFilters by rememberSaveable { mutableStateOf(false) }
    val filterController = remember { CampsiteFilterController() }
    val selection = filterController.parse(location, guests, start, end)
    val filtersActive = filters.isActive || selection.isActive
    val clearFilters = {
        location = ""; guests = ""; start = ""; end = ""; query = ""
        onClearFilters?.invoke()
        editingFilters = false
    }
    val filtered = filterController.apply(
        filterCampsites(searchCampsites(campsites, query), filters), selection, lotteries, reservations,
    )
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    // Match score: with filters on, rank the results by how well they fit.
    val scores: Map<String, Int> = if (selection.isActive) {
        filtered.associate { campsite ->
            val lottery = WaitingListController.lotteryForCampsite(lotteries, campsite.id)
            campsite.id to MatchScore.score(
                campsite = campsite,
                location = location,
                guests = selection.guests,
                datesFree = if (selection.stay != null) true else null,
                lotteryOpen = lottery != null && LotteryController.isRegistrationOpen(lottery, now),
            )
        }
    } else {
        emptyMap()
    }
    val results = if (scores.isEmpty()) filtered else filtered.sortedByDescending { scores[it.id] ?: 0 }
    var view by rememberSaveable { mutableIntStateOf(0) }
    if (editingFilters) {
        CampsiteFilterDialog(
            initial = listOf(location, guests, start, end),
            onApply = { nextLocation, nextGuests, nextStart, nextEnd ->
                filterController.parse(nextLocation, nextGuests, nextStart, nextEnd)
                location = nextLocation; guests = nextGuests; start = nextStart; end = nextEnd
                editingFilters = false
            },
            onClear = clearFilters,
            onDismiss = { editingFilters = false },
        )
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, filters, location, guests, start, end) { listState.scrollToItem(0) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search campsites") },
            placeholder = { Text("Name, location or area") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("Clear search") }
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { editingFilters = true }) { Text("Filters") }
            if (selection.isActive) {
                TextButton(onClick = clearFilters) { Text("Clear filters") }
            } else if (filters.isActive && onClearFilters != null) {
                TextButton(onClick = onClearFilters) { Text("Clear filters") }
            }
        }
        if (selection.isActive) {
            Text(
                listOfNotNull(
                    location.trim().takeIf { it.isNotEmpty() }?.let { "Location: $it" },
                    selection.guests?.let { "Guests: $it" },
                    selection.stay?.let { "$start to $end" },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (filters.isActive) {
            Text(
                buildList {
                    if (filters.location.isNotBlank()) add("Location: ${filters.location.trim()}")
                    filters.guestCount?.let { add("Guests: $it") }
                    if (filters.availableCampsiteIds != null) add("Dates applied")
                }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (mapContent != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("List", "Map").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = view == index,
                        onClick = { view = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }
        }
        when {
            isLoading -> {
                CircularProgressIndicator()
                Text("Loading campsites…")
            }
            errorMessage != null -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Unable to load campsites. Please try again.",
                actionLabel = "Retry",
                onAction = onRetry,
            )
            selection.stay != null && !availabilityReady -> {
                CircularProgressIndicator()
                Text("Loading availability…")
            }
            mapContent != null && view == 1 -> mapContent(results)
            results.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = if (filtersActive) "No campsites match your current filters."
                else if (query.isBlank()) "No campsites available yet."
                else "No results found. Try another name or location.",
            )
            else -> {
                Text(
                    "${results.size} campsite${if (results.size == 1) "" else "s"} found",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (scores.isNotEmpty()) {
                    Text(
                        "Sorted by best match for your filters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (featured.isNotEmpty() && query.isBlank() && !filtersActive) {
                        item(key = "featured") { FeaturedCarousel(featured, onCampsiteClick) }
                    }
                    items(items = results, key = { it.id }) { campsite ->
                        CampsiteCard(
                            campsite = campsite,
                            onClick = { onCampsiteClick(campsite) },
                            lottery = WaitingListController.lotteryForCampsite(lotteries, campsite.id),
                            now = now,
                            matchScore = scores[campsite.id],
                        )
                    }
                }
            }
        }
    }
}
