/*
 * ConversationScreen.kt
 * Chat between a campsite owner and the entrant who reserved their
 * campsite (US 02.12.01 and the messaging system WOW feature). Either side
 * can write; messages appear live.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.code.model.Message
import com.example.code.model.Reservation

/**
 * Conversation about [reservation].
 *
 * @param messages messages, oldest first.
 * @param myId this user's device ID; their messages are on the right and labelled "You".
 * @param error last send error, or null.
 * @param onSend called with the text to send.
 * @param onBack called when the user goes back.
 */
@Composable
fun ConversationScreen(
    reservation: Reservation,
    messages: List<Message>,
    myId: String,
    error: String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    val other = if (myId == reservation.ownerId) {
        reservation.entrantName.ifBlank { "Guest" }
    } else {
        "Owner of ${reservation.campsiteName}"
    }

    Column(modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackButton(onBack)
        Text(other, style = MaterialTheme.typography.titleLarge)
        Text(
            "${reservation.campsiteName} · ${formatDateRange(reservation.startDate, reservation.endDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (messages.isEmpty()) {
            Text("No messages yet. Say hello or ask about the stay.", modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id.ifBlank { "${it.createdAt}-${it.text}" } }) { message ->
                    val mine = message.senderId == myId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 300.dp)
                                .background(
                                    if (mine) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            val color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Text(
                                if (mine) "You" else message.senderName.ifBlank { other },
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                            Text(message.text, color = color)
                            Text(formatDateTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Write a message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            FilledIconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}
