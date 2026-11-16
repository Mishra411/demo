/*
 * NotificationRoutes.kt
 * Connects entrant notification data to the Notifications UI and marks
 * notifications as read once they have been shown.
 */

package com.example.code.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.code.controller.Inbox
import com.example.code.data.NotificationRepository
import com.example.code.model.AppNotification
import com.example.code.ui.entrant.EntrantNotificationsScreen
import kotlinx.coroutines.delay

/** How long a notification stays marked "New" after it is shown. */
private const val READ_DELAY_MS = 2_500L

/**
 * Displays the entrant's [notifications].
 *
 * @param notifications this entrant's notifications, newest first.
 * @param repository notification data source, used to mark them read.
 * @param onOpenReservations opens the entrant reservation screen.
 * @param onOpenMessage opens the conversation a direct message belongs to.
 */
@Composable
fun EntrantNotificationsRoute(
    notifications: List<AppNotification>,
    repository: NotificationRepository,
    onOpenReservations: () -> Unit,
    onOpenMessage: ((AppNotification) -> Unit)? = null,
) {
    MarkRead(notifications, repository)
    EntrantNotificationsScreen(
        notifications = notifications,
        onOpenReservations = onOpenReservations,
        onOpenMessage = onOpenMessage,
    )
}

/** Marks the unread [notifications] as read shortly after they are shown. */
@Composable
fun MarkRead(notifications: List<AppNotification>, repository: NotificationRepository) {
    val unread = notifications.filter { !it.read && it.id.isNotBlank() }.map { it.id }
    LaunchedEffect(unread) {
        if (unread.isNotEmpty() && Inbox.unread(notifications) > 0) {
            delay(READ_DELAY_MS)
            try {
                repository.markRead(unread)
            } catch (_: Exception) {
                // Still shown as new next time; nothing is lost.
            }
        }
    }
}
