/*
 * EntrantReservationsScreen.kt
 * Entrant's Reservations tab (mockup E09): active (US 01.06.01), past
 * (US 01.06.02) and cancelled reservations. The entrant can cancel an
 * upcoming stay (US 01.07.01), message the owner (US 02.12.01), download
 * each list as CSV (US 01.05.02, 01.07.02) and open the reservation map
 * (WOW feature).
 * AI use: tabs, cancel, CSV and map link written with Claude (Anthropic),
 * Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.controller.ReservationController
import com.example.code.model.Reservation
import com.example.code.ui.common.CountTabs
import com.example.code.ui.common.DownloadCsvButton
import com.example.code.ui.common.ReservationCard
import com.example.code.ui.common.formatDateRange

/**
 * Lists the entrant's reservations in Active, Past and Cancelled tabs.
 *
 * @param reservations active reservations, soonest first.
 * @param past past stays, latest first.
 * @param cancelled cancelled, rejected or declined reservations.
 * @param statusMessage result of the last action, or null.
 * @param onCancel called with a reservation the entrant confirmed cancelling; null hides Cancel.
 * @param onMessage opens the conversation with the owner; null hides the button.
 * @param onOpenMap opens the reservation map; null hides the button.
 */
@Composable
fun EntrantReservationsScreen(
    reservations: List<Reservation>,
    modifier: Modifier = Modifier,
    past: List<Reservation> = emptyList(),
    cancelled: List<Reservation> = emptyList(),
    statusMessage: String? = null,
    onCancel: ((Reservation) -> Unit)? = null,
    onMessage: ((Reservation) -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf<Reservation?>(null) }
    val shown = when (tab) {
        0 -> reservations
        1 -> past
        else -> cancelled
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My reservations", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (onOpenMap != null) {
                TextButton(onClick = onOpenMap) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Map", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        CountTabs(listOf("Active", "Past", "Cancelled"), listOf(0, past.size, cancelled.size), tab) { tab = it }
        DownloadCsvButton(
            csv = ReservationController.toCsv(shown),
            fileName = "symbol-${listOf("active", "past", "cancelled")[tab]}-reservations.csv",
            enabled = shown.isNotEmpty(),
        )
        if (statusMessage != null) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }

        if (shown.isEmpty()) {
            Text(
                when (tab) {
                    0 -> "You have no active reservations."
                    1 -> "You have no past reservations."
                    else -> "You have no cancelled reservations."
                },
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { reservation ->
                ReservationCard(reservation, showEntrant = false, datePrefix = "Stay: ") {
                    if (onMessage != null && reservation.status == Reservation.STATUS_ACTIVE) {
                        TextButton(onClick = { onMessage(reservation) }) { Text("Message owner") }
                    }
                    if (tab == 0 && onCancel != null) {
                        TextButton(onClick = { cancelling = reservation }) { Text("Cancel") }
                    }
                }
            }
        }
    }

    cancelling?.let { reservation ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this reservation?") },
            text = {
                Text(
                    "${reservation.campsiteName}, ${formatDateRange(reservation.startDate, reservation.endDate)}. " +
                        "You will get a confirmation. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancel?.invoke(reservation)
                        cancelling = null
                    },
                ) { Text("Cancel reservation") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep it") } },
        )
    }
}
