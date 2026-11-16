/*
 * ReservationCards.kt
 * Reservation card and tab bar shared by the entrant and owner Reservations
 * tabs (US 01.06.01-02, 02.07.01-03), plus the CSV download button
 * (US 01.05.02, 01.07.02).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.code.model.Reservation

/** Tab row with a count on each tab, e.g. "Past (2)". */
@Composable
fun CountTabs(titles: List<String>, counts: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val tabs = @Composable {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        if (counts[index] > 0) "$title (${counts[index]})" else title,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
    // Four or more tabs scroll sideways so labels never wrap on narrow phones.
    if (titles.size > 3) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp,
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface, tabs = tabs)
    }
}

/**
 * Card for one reservation.
 *
 * @param showEntrant true on owner screens to show who reserved.
 * @param datePrefix text before the dates, e.g. "Stay: ".
 * @param actions buttons shown at the bottom of the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReservationCard(
    reservation: Reservation,
    showEntrant: Boolean,
    modifier: Modifier = Modifier,
    datePrefix: String = "",
    actions: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reservation.campsiteName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ReservationStatusChip(reservation)
            }
            if (showEntrant) {
                Text(reservation.entrantName.ifBlank { "Entrant ${reservation.entrantId.take(6)}" })
            }
            Text(datePrefix + formatDateRange(reservation.startDate, reservation.endDate))
            if (reservation.status == Reservation.STATUS_REJECTED && reservation.rejectionReason.isNotBlank()) {
                Text("Reason: ${reservation.rejectionReason}", style = MaterialTheme.typography.bodySmall)
            }
            if (reservation.status == Reservation.STATUS_CANCELLED && reservation.cancelledAt > 0) {
                Text("Cancelled on ${formatDate(reservation.cancelledAt)}", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
        }
    }
}

/** Chip naming a reservation's status in words, so it never relies on colour alone. */
@Composable
fun ReservationStatusChip(reservation: Reservation, now: Long = System.currentTimeMillis()) {
    val scheme = MaterialTheme.colorScheme
    when (reservation.status) {
        Reservation.STATUS_ACTIVE ->
            if (reservation.endDate < now) StatusChip("Completed", container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
            else StatusChip("Active", container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
        Reservation.STATUS_CANCELLED -> StatusChip("Cancelled", container = scheme.errorContainer, content = scheme.onErrorContainer)
        Reservation.STATUS_REJECTED -> StatusChip("Rejected", container = scheme.errorContainer, content = scheme.onErrorContainer)
        Reservation.STATUS_DECLINED -> StatusChip("Declined", container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
        else -> StatusChip(reservation.status)
    }
}

/**
 * "Download CSV" button that lets the user save [csv] as a file
 * (US 01.05.02, 01.07.02).
 *
 * @param fileName suggested file name.
 */
@Composable
fun DownloadCsvButton(csv: String, fileName: String, enabled: Boolean = true) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        message = when {
            uri == null -> null
            writeText(context, uri, csv) -> "Saved $fileName"
            else -> "Could not save the file."
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { launcher.launch(fileName) }, enabled = enabled) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Download CSV", modifier = Modifier.padding(start = 6.dp))
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
} catch (e: Exception) {
    false
}
