/*
 * ReservationRoutes.kt
 * Connects the reservation screens to ReservationController.
 * Known issues: "now" is read when a screen is shown.
 * AI use: choosing dates, declining, cancelling, messaging and the
 * reservation map were wired in with Claude (Anthropic), Nov 2026;
 * reviewed and tested by Aalok Mishra.
 */

package com.example.code.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.code.controller.ReservationController
import com.example.code.data.NotificationRepository
import com.example.code.model.AppNotification
import com.example.code.model.Campsite
import com.example.code.model.Geo
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.WaitingListEntry
import com.example.code.ui.common.OsmMap
import com.example.code.ui.common.formatDateRange
import com.example.code.ui.entrant.EntrantReservationsScreen
import com.example.code.ui.entrant.ReservationMapScreen
import com.example.code.ui.entrant.ReserveStayDialog
import com.example.code.ui.entrant.WinnerReservationCard
import com.example.code.ui.owner.OwnerReservationsScreen
import kotlinx.coroutines.launch

/**
 * Accept and decline buttons, or the reservation status, shown to a winner
 * on the campsite details lottery card. Accepting opens the dialog where
 * the winner chooses a campsite and dates and confirms the rules
 * (US 01.04.01, 01.04.02, 02.11.01).
 *
 * @param lottery the drawn lottery.
 * @param myEntry this entrant's waiting list entry.
 * @param reservations all reservations.
 * @param controller reservation controller.
 * @param campsites all campsites, for schedules, rules and policies.
 * @param entrantName the winner's profile name, saved on the reservation.
 */
@Composable
fun WinnerReservationContent(
    lottery: Lottery,
    myEntry: WaitingListEntry?,
    reservations: List<Reservation>,
    controller: ReservationController,
    campsites: List<Campsite> = emptyList(),
    entrantName: String = "",
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var declining by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val reservation = myEntry?.let {
        ReservationController.forLottery(reservations, lottery.id, it.entrantId)
    }

    WinnerReservationCard(
        myEntry = myEntry,
        reservation = reservation,
        busy = busy,
        statusMessage = message,
        onAccept = {
            dialogError = null
            choosing = true
        },
        onDecline = { declining = true },
    )

    if (choosing && myEntry != null && reservation == null) {
        ReserveStayDialog(
            lottery = lottery,
            campsites = campsites,
            reservations = reservations,
            busy = busy,
            error = dialogError,
            onConfirm = { campsiteId, firstDay, lastDay ->
                busy = true
                scope.launch {
                    try {
                        val made = controller.reserve(
                            lottery = lottery,
                            entry = myEntry,
                            campsiteId = campsiteId,
                            firstDay = firstDay,
                            lastDay = lastDay,
                            rulesAccepted = true,
                            existing = reservations,
                            campsite = campsites.firstOrNull { it.id == campsiteId },
                            entrantName = entrantName.ifBlank { myEntry.entrantName },
                        )
                        message = if (made != null) {
                            "Reservation confirmed. A confirmation is in your notifications."
                        } else {
                            "You already answered this win."
                        }
                        choosing = false
                    } catch (e: IllegalArgumentException) {
                        dialogError = e.message
                    } catch (e: Exception) {
                        dialogError = "Could not reserve: ${e.message}"
                    }
                    busy = false
                }
            },
            onDismiss = { if (!busy) choosing = false },
        )
    }

    if (declining && myEntry != null) {
        AlertDialog(
            onDismissRequest = { declining = false },
            title = { Text("Decline this win?") },
            text = { Text("You will not be able to reserve ${lottery.campsiteName} from this lottery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        declining = false
                        busy = true
                        scope.launch {
                            message = try {
                                if (controller.decline(lottery, myEntry)) {
                                    "You declined. The owner has been told."
                                } else {
                                    "You already answered this win."
                                }
                            } catch (e: Exception) {
                                "Could not decline: ${e.message}"
                            }
                            busy = false
                        }
                    },
                ) { Text("Decline win") }
            },
            dismissButton = { TextButton(onClick = { declining = false }) { Text("Keep my win") } },
        )
    }
}

/**
 * Entrant's Reservations tab: active, past and cancelled reservations
 * (US 01.06.01, 01.06.02), cancelling (US 01.07.01), messaging the owner and
 * the reservation map.
 *
 * @param reservations all reservations.
 * @param deviceId this entrant's ID.
 * @param controller reservation controller; null makes the tab read-only.
 * @param onMessage opens the conversation about a reservation.
 * @param onOpenMap opens the reservation map.
 */
@Composable
fun EntrantReservationsRoute(
    reservations: List<Reservation>,
    deviceId: String,
    controller: ReservationController? = null,
    onMessage: ((Reservation) -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()

    EntrantReservationsScreen(
        reservations = ReservationController.activeForEntrant(reservations, deviceId, now),
        past = ReservationController.pastForEntrant(reservations, deviceId, now),
        cancelled = ReservationController.cancelledForEntrant(reservations, deviceId),
        statusMessage = message,
        onCancel = controller?.let {
            { reservation ->
                scope.launch {
                    message = try {
                        it.cancel(reservation)
                        "Reservation for ${reservation.campsiteName} cancelled. A confirmation is in your notifications."
                    } catch (e: Exception) {
                        "Could not cancel: ${e.message}"
                    }
                }
            }
        },
        onMessage = onMessage,
        onOpenMap = onOpenMap,
    )
}

/**
 * Reservation map WOW feature: the entrant's active and past stays as pins.
 *
 * @param reservations all reservations.
 * @param campsites all campsites, for their map locations.
 * @param deviceId this entrant's ID.
 * @param onBack called when the entrant goes back.
 */
@Composable
fun ReservationMapRoute(
    reservations: List<Reservation>,
    campsites: List<Campsite>,
    deviceId: String,
    onBack: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val active = ReservationController.activeForEntrant(reservations, deviceId, now)
    val past = ReservationController.pastForEntrant(reservations, deviceId, now)
    val points = Geo.reservationPoints(active, past, campsites) { formatDateRange(it.startDate, it.endDate) }
    ReservationMapScreen(
        points = points,
        missing = active.size + past.size - points.size,
        onBack = onBack,
        map = { pins, center, radiusKm, onPointClick -> OsmMap(pins, center = center, radiusKm = radiusKm, onPointClick = onPointClick) },
    )
}

/**
 * Owner's Reservations tab.
 *
 * @param reservations all reservations.
 * @param deviceId this owner's ID.
 * @param controller reservation controller.
 * @param inbox guest updates for this owner, newest first; null hides the Inbox tab.
 * @param onMessage opens the conversation with a guest; null hides the button.
 * @param notifications used to mark the inbox read once it has been shown.
 */
@Composable
fun OwnerReservationsRoute(
    reservations: List<Reservation>,
    deviceId: String,
    controller: ReservationController,
    inbox: List<AppNotification>? = null,
    onMessage: ((Reservation) -> Unit)? = null,
    notifications: NotificationRepository? = null,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var inboxShown by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    if (inboxShown && inbox != null && notifications != null) MarkRead(inbox, notifications)

    OwnerReservationsScreen(
        bookedDates = ReservationController.bookedDates(reservations, deviceId, now),
        reservations = ReservationController.activeForOwner(reservations, deviceId, now),
        past = ReservationController.pastForOwner(reservations, deviceId, now),
        cancelled = ReservationController.cancelledForOwner(reservations, deviceId),
        statusMessage = message,
        onReject = { reservation, reason ->
            scope.launch {
                message = try {
                    controller.reject(reservation, reason)
                    "Reservation for ${reservation.campsiteName} rejected."
                } catch (e: Exception) {
                    "Could not reject: ${e.message}"
                }
            }
        },
        onMessage = onMessage,
        inbox = inbox,
        onInboxShown = { inboxShown = true },
        onOpenInboxMessage = onMessage?.let { open ->
            { notification ->
                reservations.firstOrNull { it.id == notification.reservationId }?.let(open)
            }
        },
    )
}
