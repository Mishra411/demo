/*
 * EntrantNotificationsScreen.kt
 * Displays notifications sent to the current user: lottery wins and losses
 * (US 01.03.01, 01.03.02), owner messages (US 02.10.01), reservation
 * confirmations and cancellations (US 01.05.01, 01.07.02) and direct
 * messages (US 02.12.01). Unread notifications are marked "New".
 * Lottery-win notifications provide a way to continue to reservations.
 * AI use: icons, unread state and reply button written with Claude
 * (Anthropic), Nov 2026; reviewed by Aalok Mishra.
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.code.model.AppNotification
import com.example.code.ui.common.StatusChip
import com.example.code.ui.common.relativeTime

/**
 * Displays notifications belonging to the current user.
 *
 * @param notifications notifications for this user, newest first.
 * @param onOpenReservations called when a lottery winner chooses to continue
 * to the reservation flow.
 * @param onOpenMessage called to reply to a direct message; null hides Reply.
 * @param now current time, for texts like "5 min ago".
 */
@Composable
fun EntrantNotificationsScreen(
    notifications: List<AppNotification>,
    onOpenReservations: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMessage: ((AppNotification) -> Unit)? = null,
    now: Long = System.currentTimeMillis(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (notifications.isEmpty()) {
            Text("You have no notifications.")
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = notifications,
                key = { it.id },
            ) { notification ->
                NotificationCard(notification, onOpenReservations, onOpenMessage, now)
            }
        }
    }
}

/**
 * Card for one notification, with its actions: winners continue to
 * reservations and direct messages can be answered.
 */
@Composable
fun NotificationCard(
    notification: AppNotification,
    onOpenReservations: () -> Unit,
    onOpenMessage: ((AppNotification) -> Unit)?,
    now: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.read) MaterialTheme.colorScheme.surfaceContainerLowest
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                iconFor(notification.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(notification.message)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!notification.read) StatusChip("New")
                    if (notification.createdAt > 0) {
                        Text(
                            relativeTime(notification.createdAt, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (
                    notification.type ==
                    AppNotification.TYPE_LOTTERY_WON
                ) {
                    Button(
                        onClick = onOpenReservations,
                    ) {
                        Text("View reservations")
                    }
                }
                if (notification.type == AppNotification.TYPE_DIRECT_MESSAGE && onOpenMessage != null) {
                    OutlinedButton(onClick = { onOpenMessage(notification) }) { Text("Reply") }
                }
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    AppNotification.TYPE_LOTTERY_WON -> Icons.Filled.EmojiEvents
    AppNotification.TYPE_LOTTERY_LOST -> Icons.Outlined.SentimentDissatisfied
    AppNotification.TYPE_RESERVATION_CONFIRMED -> Icons.Outlined.CheckCircle
    AppNotification.TYPE_RESERVATION_CANCELLED -> Icons.Outlined.EventBusy
    AppNotification.TYPE_RESERVATION_REJECTED, AppNotification.TYPE_RESERVATION_DECLINED -> Icons.Outlined.DoNotDisturbOn
    AppNotification.TYPE_DIRECT_MESSAGE -> Icons.AutoMirrored.Outlined.Message
    else -> Icons.Outlined.Campaign
}
