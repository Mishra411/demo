/*
 * SentNotificationsScreen.kt
 * History of the notifications a campsite owner has sent (US 02.10.02):
 * messages to waiting lists, lottery results and reservation updates. One
 * message sent to many entrants is shown once with the number of recipients.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.model.AppNotification
import com.example.code.ui.common.BackButton
import com.example.code.ui.common.EmptyState
import com.example.code.ui.common.formatDateTime

/**
 * One message an owner sent, possibly to many entrants.
 *
 * @property sample one of the notifications, for its title, text and time.
 * @property recipients number of entrants it was sent to.
 */
data class SentGroup(val sample: AppNotification, val recipients: Int)

/**
 * Groups [sent] so one message to many entrants appears once, newest first.
 * Notifications are the same message when lottery, type, title, text and
 * time all match.
 */
fun groupSent(sent: List<AppNotification>): List<SentGroup> =
    sent.groupBy { listOf(it.lotteryId, it.type, it.title, it.message, it.createdAt) }
        .map { (_, group) -> SentGroup(group.first(), group.map { it.recipientId }.distinct().size) }
        .sortedByDescending { it.sample.createdAt }

/** Short name of a notification type for owners and administrators. */
fun notificationTypeLabel(type: String): String = when (type) {
    AppNotification.TYPE_WAITLIST_MESSAGE -> "Waiting list message"
    AppNotification.TYPE_LOTTERY_WON -> "Lottery result: selected"
    AppNotification.TYPE_LOTTERY_LOST -> "Lottery result: not selected"
    AppNotification.TYPE_RESERVATION_CONFIRMED -> "Reservation confirmation"
    AppNotification.TYPE_RESERVATION_CANCELLED -> "Cancellation"
    AppNotification.TYPE_RESERVATION_REJECTED -> "Reservation rejected"
    AppNotification.TYPE_RESERVATION_DECLINED -> "Win declined"
    AppNotification.TYPE_DIRECT_MESSAGE -> "Direct message"
    else -> "Notification"
}

/**
 * Lists the notifications the owner sent.
 *
 * @param sent every notification sent by this owner.
 * @param onBack called when the owner goes back.
 */
@Composable
fun SentNotificationsScreen(sent: List<AppNotification>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val groups = groupSent(sent)
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text("Sent notifications", style = MaterialTheme.typography.headlineSmall)
        if (groups.isEmpty()) {
            EmptyState(
                Icons.Outlined.Campaign,
                "You have not sent any notifications yet.",
                message = "Messages to a waiting list and lottery results appear here.",
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { "${it.sample.id}-${it.sample.createdAt}" }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.sample.title, style = MaterialTheme.typography.titleMedium)
                        Text(group.sample.message)
                        Text(
                            "${notificationTypeLabel(group.sample.type)} · " +
                                "${group.recipients} recipient${if (group.recipients == 1) "" else "s"} · " +
                                formatDateTime(group.sample.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
