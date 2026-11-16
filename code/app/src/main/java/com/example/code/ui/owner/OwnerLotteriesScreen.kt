/*
 * OwnerLotteriesScreen.kt
 * Owner's Lotteries tab: lists the owner's lotteries, opens each lottery's
 * waiting list (US 02.09.01), updates an open lottery (US 02.04.06) and
 * opens the notifications the owner has sent (US 02.10.02).
 * Known issues: none.
 * AI use: the Edit and Sent notifications actions and the status chips were
 * written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.controller.LotteryController
import com.example.code.model.Lottery
import com.example.code.ui.common.EmptyState
import com.example.code.ui.common.StatusChip
import com.example.code.ui.common.formatDateRange
import com.example.code.ui.common.formatDateTime

/**
 * Lists the owner's lotteries.
 *
 * @param lotteries lotteries created by this owner.
 * @param onOpenWaitingList called with the lottery whose waiting list to show.
 * @param onEdit called with an open lottery to update (US 02.04.06); null hides Edit.
 * @param onOpenSent opens the notifications the owner sent (US 02.10.02); null hides the button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerLotteriesScreen(
    lotteries: List<Lottery>,
    onOpenWaitingList: (Lottery) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: ((Lottery) -> Unit)? = null,
    onOpenSent: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My lotteries", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (onOpenSent != null) {
                TextButton(onClick = onOpenSent) { Text("Sent notifications") }
            }
        }

        if (lotteries.isEmpty()) {
            Text("You have no lotteries yet.")
            EmptyState(
                Icons.Outlined.ConfirmationNumber,
                "Create your first lottery",
                message = "Open one of your campsites in the Campsites tab and tap Create lottery.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lotteries, key = { it.id }) { lottery ->
                val drawn = lottery.status == Lottery.STATUS_DRAWN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lottery.campsiteName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (drawn) {
                                StatusChip(
                                    "Winners drawn",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                StatusChip(
                                    "Open",
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        if (lottery.extraCampsiteNames.isNotEmpty()) {
                            Text(
                                "Also for: ${LotteryController.campsitesOf(lottery).drop(1).joinToString(", ") { it.second }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("Registration closes ${formatDateTime(lottery.registrationEnd)}")
                        Text(
                            "Stay: ${formatDateRange(lottery.bookingStart, lottery.bookingEnd)} · " +
                                "${lottery.winnerCount} winner${if (lottery.winnerCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onOpenWaitingList(lottery) }) {
                                Text("Waiting list (${lottery.waitingListCount})")
                            }
                            if (onEdit != null && !drawn) {
                                TextButton(onClick = { onEdit(lottery) }) { Text("Edit lottery") }
                            }
                        }
                    }
                }
            }
        }
    }
}
