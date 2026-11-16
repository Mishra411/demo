/*
 * OwnerReservationsScreen.kt
 * Owner's Reservations tab (mockups O03, O06, O08): booked dates for each
 * campsite (US 02.06.01), active reservations (US 02.07.01), past
 * reservations (US 02.07.02), cancelled reservations (US 02.07.03),
 * rejecting a reservation with a reason (US 02.08.01), messaging a guest
 * (US 02.12.01) and the owner's inbox of guest updates.
 * Known issues: none.
 * AI use: the Past, Cancelled and Inbox tabs, messaging and CSV download
 * were written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.controller.BookedDates
import com.example.code.controller.Inbox
import com.example.code.controller.ReservationController
import com.example.code.model.AppNotification
import com.example.code.model.Reservation
import com.example.code.ui.common.CountTabs
import com.example.code.ui.common.DownloadCsvButton
import com.example.code.ui.common.EmptyState
import com.example.code.ui.common.ReservationCard
import com.example.code.ui.common.formatDateRange
import com.example.code.ui.entrant.NotificationCard

/**
 * Shows booked dates and reservations for the owner's campsites.
 *
 * @param bookedDates booked stays grouped by campsite.
 * @param reservations active reservations, soonest first.
 * @param statusMessage result of the last action, or null.
 * @param onReject called with a reservation and the owner's reason.
 * @param past stays that are over, latest first (US 02.07.02).
 * @param cancelled cancelled and rejected reservations (US 02.07.03).
 * @param onMessage opens the conversation with a guest (US 02.12.01); null hides the button.
 * @param inbox guest updates for the owner, newest first; null hides the Inbox tab.
 * @param onOpenInboxMessage opens the conversation a guest's message belongs to.
 * @param onInboxShown called when the owner opens the Inbox tab.
 */
@Composable
fun OwnerReservationsScreen(
    bookedDates: List<BookedDates>,
    reservations: List<Reservation>,
    statusMessage: String?,
    onReject: (Reservation, String) -> Unit,
    modifier: Modifier = Modifier,
    past: List<Reservation> = emptyList(),
    cancelled: List<Reservation> = emptyList(),
    onMessage: ((Reservation) -> Unit)? = null,
    inbox: List<AppNotification>? = null,
    onOpenInboxMessage: ((AppNotification) -> Unit)? = null,
    onInboxShown: (() -> Unit)? = null,
    now: Long = System.currentTimeMillis(),
) {
    var rejecting by remember { mutableStateOf<Reservation?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Active", "Past", "Cancelled") + if (inbox != null) listOf("Inbox") else emptyList()
    val counts = listOf(reservations.size, past.size, cancelled.size) +
        if (inbox != null) listOf(Inbox.unread(inbox)) else emptyList()

    if (tab == 3 && onInboxShown != null) {
        LaunchedEffect(Unit) { onInboxShown() }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reservations", style = MaterialTheme.typography.headlineSmall)
        CountTabs(titles, counts, tab) { tab = it }
        if (statusMessage != null) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    item { Text("Booked dates", style = MaterialTheme.typography.titleMedium) }
                    if (bookedDates.isEmpty()) {
                        item { Text("No dates are booked.") }
                    }
                    items(bookedDates, key = { "booked-${it.campsiteName}" }) { campsite ->
                        Column {
                            Text(campsite.campsiteName, style = MaterialTheme.typography.titleSmall)
                            campsite.stays.forEach { (start, end) -> Text(formatDateRange(start, end)) }
                        }
                    }

                    item { Text("Active reservations", style = MaterialTheme.typography.titleMedium) }
                    if (reservations.isEmpty()) {
                        item { Text("No active reservations.") }
                    }
                    items(reservations, key = { it.id }) { reservation ->
                        ReservationCard(reservation, showEntrant = true) {
                            OutlinedButton(onClick = { rejecting = reservation }) { Text("Reject") }
                            if (onMessage != null) {
                                TextButton(onClick = { onMessage(reservation) }) { Text("Message guest") }
                            }
                        }
                    }
                }

                1 -> {
                    if (past.isEmpty()) {
                        item { EmptyState(Icons.Outlined.EventAvailable, "No past reservations yet.") }
                    } else {
                        item { DownloadCsvButton(ReservationController.toCsv(past), "past-reservations.csv") }
                    }
                    items(past, key = { it.id }) { reservation ->
                        ReservationCard(reservation, showEntrant = true) {
                            if (onMessage != null) {
                                TextButton(onClick = { onMessage(reservation) }) { Text("Message guest") }
                            }
                        }
                    }
                }

                2 -> {
                    if (cancelled.isEmpty()) {
                        item { EmptyState(Icons.Outlined.EventAvailable, "No cancelled reservations.") }
                    } else {
                        item {
                            DownloadCsvButton(ReservationController.toCsv(cancelled), "cancelled-reservations.csv")
                        }
                    }
                    items(cancelled, key = { it.id }) { reservation ->
                        ReservationCard(reservation, showEntrant = true)
                    }
                }

                else -> {
                    val updates = inbox.orEmpty()
                    if (updates.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.Inbox,
                                "No guest updates yet.",
                                message = "Messages from guests, cancellations and declined wins appear here.",
                            )
                        }
                    }
                    items(updates, key = { it.id }) { notification ->
                        NotificationCard(notification, onOpenReservations = {}, onOpenMessage = onOpenInboxMessage, now = now)
                    }
                }
            }
        }
    }

    rejecting?.let { reservation ->
        RejectDialog(
            reservation = reservation,
            onConfirm = { reason ->
                onReject(reservation, reason)
                rejecting = null
            },
            onDismiss = { rejecting = null },
        )
    }
}

/** Dialog where the owner enters the reason for rejecting [reservation]. */
@Composable
private fun RejectDialog(
    reservation: Reservation,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject reservation?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${reservation.campsiteName}, " +
                        formatDateRange(reservation.startDate, reservation.endDate),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) {
                Text("Reject reservation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
