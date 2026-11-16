/*
 * AdminLogsScreen.kt
 * Admin Logs tab: review the log of every notification (US 03.08.01) and of
 * every message between campsite owners and entrants who reserved their
 * campsite (US 03.12.01). Both logs can be searched.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.code.model.AppNotification
import com.example.code.model.Message
import com.example.code.ui.common.CountTabs
import com.example.code.ui.common.EmptyState
import com.example.code.ui.common.formatDateTime
import com.example.code.ui.owner.notificationTypeLabel

/**
 * One line of an admin log.
 *
 * @property key unique key in the list.
 * @property title what happened.
 * @property body text of the notification or message.
 * @property meta who sent it to whom, and when.
 * @property time time in milliseconds, for sorting.
 */
data class LogEntry(val key: String, val title: String, val body: String, val meta: String, val time: Long)

/** Short form of a device ID for logs. */
private fun who(id: String): String = id.take(8).ifBlank { "system" }

/** Log lines for [notifications], newest first (US 03.08.01). */
fun notificationLog(notifications: List<AppNotification>, timeText: (Long) -> String): List<LogEntry> =
    notifications.sortedByDescending { it.createdAt }.mapIndexed { index, n ->
        LogEntry(
            key = "n-${n.id.ifBlank { index.toString() }}",
            title = "${notificationTypeLabel(n.type)} · ${n.title}",
            body = n.message,
            meta = "From ${who(n.senderId)} to ${who(n.recipientId)} · ${timeText(n.createdAt)}",
            time = n.createdAt,
        )
    }

/** Log lines for [messages], newest first (US 03.12.01). */
fun messageLog(messages: List<Message>, timeText: (Long) -> String): List<LogEntry> =
    messages.sortedByDescending { it.createdAt }.mapIndexed { index, m ->
        LogEntry(
            key = "m-${m.id.ifBlank { index.toString() }}",
            title = "${if (m.fromOwner) "Owner to guest" else "Guest to owner"} · ${m.campsiteName}",
            body = m.text,
            meta = "From ${m.senderName.ifBlank { who(m.senderId) }} (${who(m.senderId)}) to ${who(m.recipientId)} · " +
                timeText(m.createdAt),
            time = m.createdAt,
        )
    }

/** Keeps the [entries] that contain every word of [query], ignoring case. */
fun searchLog(entries: List<LogEntry>, query: String): List<LogEntry> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return entries
    return entries.filter { entry ->
        val text = "${entry.title} ${entry.body} ${entry.meta}".lowercase()
        words.all { it in text }
    }
}

/**
 * Shows the notification log and the message log.
 *
 * @param notifications every notification sent in the app.
 * @param messages every message between owners and guests.
 * @param error loading error, or null.
 */
@Composable
fun AdminLogsScreen(
    notifications: List<AppNotification>,
    messages: List<Message>,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val all = if (tab == 0) notificationLog(notifications, ::formatDateTime) else messageLog(messages, ::formatDateTime)
    val shown = searchLog(all, query)

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Logs", style = MaterialTheme.typography.headlineSmall)
        CountTabs(listOf("Notifications", "Messages"), listOf(notifications.size, messages.size), tab) { tab = it }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search logs") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("logSearch"),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (shown.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Outlined.ListAlt,
                if (all.isEmpty()) "Nothing has been logged yet." else "No log entries match your search.",
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.key }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                        Text(entry.body)
                        Text(
                            entry.meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
