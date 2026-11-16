/*
 * WinnerReservationCard.kt
 * Shown on the campsite details lottery card after the draw. A winner
 * reserves (US 01.04.01, 01.04.02) or declines (US 01.04.03), then sees the
 * reservation confirmed, rejected, cancelled or declined. ReserveStayDialog
 * lets the winner pick the campsite and free days on a calendar and confirm
 * the campsite rules (US 02.11.01).
 * AI use: stay dialog and decline flow written with Claude (Anthropic),
 * Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.code.controller.LotteryController
import com.example.code.controller.ReservationController
import com.example.code.model.Availability
import com.example.code.model.Campsite
import com.example.code.model.DayState
import com.example.code.model.Days
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.WaitingListEntry
import com.example.code.ui.common.StayCalendar
import com.example.code.ui.common.formatDateRange
import java.util.TimeZone

/**
 * Accept and decline buttons, or the reservation's status, for a lottery winner.
 *
 * @param myEntry this entrant's waiting list entry, or null if not joined.
 * @param reservation the entrant's reservation from this lottery, or null.
 * @param busy true while the reservation is being saved.
 * @param statusMessage result of the last action, or null.
 * @param onAccept called when the winner taps "Accept reservation".
 * @param onDecline called when the winner taps "Decline"; null hides the button.
 */
@Composable
fun WinnerReservationCard(
    myEntry: WaitingListEntry?,
    reservation: Reservation?,
    busy: Boolean,
    statusMessage: String?,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    onDecline: (() -> Unit)? = null,
) {
    if (myEntry?.status != WaitingListEntry.STATUS_WON) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            reservation == null -> {
                Button(onClick = onAccept, enabled = !busy) {
                    Text("Accept reservation")
                }
                if (onDecline != null) {
                    OutlinedButton(onClick = onDecline, enabled = !busy) {
                        Text("Decline")
                    }
                }
            }

            reservation.status == Reservation.STATUS_REJECTED ->
                Text("The owner rejected this reservation: ${reservation.rejectionReason}")

            reservation.status == Reservation.STATUS_DECLINED ->
                Text("You declined this win.")

            reservation.status == Reservation.STATUS_CANCELLED ->
                Text("You cancelled this reservation.")

            else ->
                Text("Reserved: ${formatDateRange(reservation.startDate, reservation.endDate)}")
        }

        if (statusMessage != null) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Full-screen dialog where a winner picks a campsite (US 01.04.02) and free
 * days (US 01.04.01) and confirms the rules (US 02.11.01).
 *
 * @param lottery the drawn lottery.
 * @param campsites all campsites, for each choice's schedule and rules.
 * @param reservations all reservations, to mark booked days.
 * @param busy true while saving.
 * @param error last error, or null.
 * @param onConfirm called with the campsite ID and the first and last day numbers.
 * @param onDismiss called when the dialog is closed.
 */
@Composable
fun ReserveStayDialog(
    lottery: Lottery,
    campsites: List<Campsite>,
    reservations: List<Reservation>,
    busy: Boolean,
    error: String?,
    onConfirm: (String, Long, Long) -> Unit,
    onDismiss: () -> Unit,
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    val choices = LotteryController.campsitesOf(lottery)
    var campsiteId by rememberSaveable { mutableStateOf(choices.first().first) }
    var first by rememberSaveable { mutableStateOf<Long?>(null) }
    var last by rememberSaveable { mutableStateOf<Long?>(null) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val campsite = campsites.firstOrNull { it.id == campsiteId }
    val states = remember(campsiteId, campsite, reservations) {
        ReservationController.stayDays(lottery, campsiteId, campsite, reservations, timeZone).toMap()
    }
    val open = states.count { it.value == DayState.OPEN }
    val initialDay = states.entries.firstOrNull { it.value == DayState.OPEN }?.key
        ?: states.keys.minOrNull() ?: Days.dayOf(lottery.bookingStart, timeZone)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose your stay", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Text(
                    "Booking period: ${formatDateRange(lottery.bookingStart, lottery.bookingEnd)}. " +
                        "$open of ${states.size} days can still be reserved.",
                )
                if (choices.size > 1) {
                    Text("Campsite", style = MaterialTheme.typography.titleMedium)
                    choices.forEach { (id, name) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = id == campsiteId,
                                onClick = {
                                    campsiteId = id
                                    first = null
                                    last = null
                                },
                                role = Role.RadioButton,
                            ).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = id == campsiteId, onClick = null)
                            Text(name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Text("Tap the first day, then the last day of your stay.", style = MaterialTheme.typography.bodySmall)
                StayCalendar(
                    stateOf = { states[it] ?: DayState.CLOSED },
                    initialDay = initialDay,
                    selectedFirst = first,
                    selectedLast = last,
                    onDayClick = { day ->
                        val (newFirst, newLast) = Availability.selectDay(first, last, day)
                        // A stay cannot run across a booked or closed day.
                        if (newFirst != null && newLast != null &&
                            !(newFirst..newLast).all { states[it] == DayState.OPEN }
                        ) {
                            first = day
                            last = null
                        } else {
                            first = newFirst
                            last = newLast
                        }
                    },
                )
                first?.let { start ->
                    val end = last ?: start
                    val days = end - start + 1
                    Text(
                        "${Days.label(start)} – ${Days.label(end)} · $days day${if (days == 1L) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("stayChoice"),
                    )
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Campsite rules", style = MaterialTheme.typography.titleSmall)
                        Text(campsite?.rules?.ifBlank { null } ?: "No rules provided")
                        Text("Cancellation policy", style = MaterialTheme.typography.titleSmall)
                        Text(campsite?.cancellationPolicy?.ifBlank { null } ?: "No cancellation policy provided")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = agreed,
                        onValueChange = { agreed = it },
                        role = Role.Checkbox,
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Text(
                        "I have read and agree to the campsite rules and policies",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { first?.let { onConfirm(campsiteId, it, last ?: it) } },
                    enabled = !busy && agreed && first != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Reserving…" else "Confirm reservation")
                }
            }
        }
    }
}
