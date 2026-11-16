/*
 * LotterySection.kt
 * Lottery part of the entrant's campsite details screen (mockup E05):
 * shows the campsite's lottery and its periods (US 01.01.06) and lets the
 * entrant join or leave its waiting list (US 01.02.01, 01.02.02). After the
 * automatic draw (US 02.05.01) it shows whether the entrant won.
 * Known issues: none.
 * AI use: status chip with countdown and visual design written with Claude
 * (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.controller.LotteryController
import com.example.code.model.Lottery
import com.example.code.model.WaitingListEntry
import com.example.code.ui.common.StatusChip
import com.example.code.ui.common.countdownText
import com.example.code.ui.common.formatDateRange
import com.example.code.ui.common.formatDateTime

/**
 * Shows [lottery] and the entrant's place on its waiting list.
 *
 * @param lottery the campsite's lottery, or null if it has none.
 * @param myEntry this entrant's waiting list entry, or null if not joined.
 * @param now current time in milliseconds.
 * @param busy true while a join or leave request is running.
 * @param statusMessage result of the last action, or null.
 * @param onJoin called when the entrant taps "Join Waiting List".
 * @param onLeave called when the entrant taps "Leave Waiting List".
 * @param drawnContent extra content shown after the draw (e.g. accepting a win);
 * receives [myEntry].
 */
@Composable
fun LotterySection(
    lottery: Lottery?,
    myEntry: WaitingListEntry?,
    now: Long,
    busy: Boolean,
    statusMessage: String?,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    drawnContent: @Composable (WaitingListEntry?) -> Unit = {},
) {
    val won = lottery?.status == Lottery.STATUS_DRAWN && myEntry?.status == WaitingListEntry.STATUS_WON
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (won) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (won) Icons.Filled.EmojiEvents else Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text("Lottery", style = MaterialTheme.typography.titleMedium)
            }

            if (lottery == null) {
                Text("This campsite has no lottery yet.")
                return@Column
            }

            LotteryStatusChip(lottery, now)
            Text(
                "Registration: ${formatDateTime(lottery.registrationStart)} – " +
                    formatDateTime(lottery.registrationEnd),
            )
            Text("Stay: ${formatDateRange(lottery.bookingStart, lottery.bookingEnd)}")
            if (lottery.extraCampsiteNames.isNotEmpty()) {
                Text(
                    "Campsites in this lottery: " +
                        LotteryController.campsitesOf(lottery).joinToString(", ") { it.second },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Winners drawn: ${lottery.winnerCount}")
            Text(waitingListText(lottery))

            when {
                lottery.status == Lottery.STATUS_DRAWN -> {
                    Text(drawnText(myEntry), style = MaterialTheme.typography.titleMedium)
                    drawnContent(myEntry)
                }

                now < lottery.registrationStart ->
                    Text("Registration opens ${formatDateTime(lottery.registrationStart)}.")

                now >= lottery.registrationEnd ->
                    Text("Registration is closed. Winners are being drawn.")

                myEntry != null -> {
                    Text("You are on the waiting list.")
                    OutlinedButton(onClick = onLeave, enabled = !busy) {
                        Text("Leave Waiting List")
                    }
                }

                !LotteryController.hasRoom(lottery) -> Text("The waiting list is full.")

                else -> Button(onClick = onJoin, enabled = !busy) {
                    Text("Join Waiting List")
                }
            }

            if (statusMessage != null) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Chip showing whether registration is upcoming, open (with a countdown) or closed. */
@Composable
private fun LotteryStatusChip(lottery: Lottery, now: Long) {
    when {
        lottery.status == Lottery.STATUS_DRAWN -> StatusChip("Drawn", icon = Icons.Filled.EmojiEvents)
        now < lottery.registrationStart -> StatusChip(
            "Opens in ${countdownText(lottery.registrationStart, now)}",
            icon = Icons.Outlined.Timer,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        now < lottery.registrationEnd -> StatusChip(
            "Open · closes in ${countdownText(lottery.registrationEnd, now)}",
            icon = Icons.Outlined.Timer,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        )
        else -> StatusChip("Closed", icon = Icons.Outlined.Timer)
    }
}

/** Text showing how many entrants have joined, and the cap if there is one. */
private fun waitingListText(lottery: Lottery): String =
    if (lottery.waitlistCapacity == null) {
        "Waiting list: ${lottery.waitingListCount} joined"
    } else {
        "Waiting list: ${lottery.waitingListCount} of ${lottery.waitlistCapacity} spots taken"
    }

/** Result text shown to the entrant once winners are drawn. */
private fun drawnText(myEntry: WaitingListEntry?): String = when (myEntry?.status) {
    WaitingListEntry.STATUS_WON -> "You won this lottery!"
    WaitingListEntry.STATUS_NOT_SELECTED -> "You were not selected this time."
    else -> "Winners have been drawn."
}
