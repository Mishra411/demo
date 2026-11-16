/*
 * Components.kt
 * Small reusable UI pieces shared by every mode: empty states, status
 * chips, star ratings, info rows, the start-up loading screen and short
 * time texts.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.code.ui.theme.StarGold
import java.util.Locale

/**
 * Friendly message shown when a list is empty.
 *
 * @param icon picture above the message.
 * @param title main message.
 * @param message optional hint below the title.
 * @param actionLabel optional button text.
 * @param onAction called when the button is tapped.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Small rounded label such as "Active" or "Open". Always has text, so it never relies on colour alone. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Shows [rating] (1 to 5, halves allowed) as stars. */
@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val label = "Rated ${String.format(Locale.CANADA, "%.1f", rating)} out of 5"
    Row(modifier = modifier.semantics { contentDescription = label }) {
        for (i in 1..5) {
            val icon = when {
                rating >= i - 0.25 -> Icons.Filled.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(icon, contentDescription = null, tint = StarGold, modifier = Modifier.size(size))
        }
    }
}

/**
 * Five tappable stars for choosing a rating.
 *
 * @param value current choice, 0 for none.
 * @param onChange called with 1 to 5.
 */
@Composable
fun StarRatingInput(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                if (i <= value) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$i star${if (i == 1) "" else "s"}",
                tint = StarGold,
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .clickable(enabled = enabled) { onChange(i) }
                    .padding(6.dp),
            )
        }
    }
}

/** Icon, bold label and value, e.g. a campsite's location. */
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value.ifBlank { "Not provided" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Back" button with an arrow, used at the top of detail screens. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onBack, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Back", modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Shown while the device profile loads, or with Retry when it failed
 * (for example with no internet), so the user is never stuck.
 *
 * @param error loading error, or null while loading.
 * @param onRetry called when the user taps Retry.
 */
@Composable
fun LoadingScreen(error: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Cabin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Symbol Camp", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading user profile...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                if (error.contains("offline", ignoreCase = true)) {
                    "You seem to be offline. Check your internet connection and try again."
                } else {
                    error
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * Short time left until [target], e.g. "2d 4h", "3h 12m" or "45s".
 * Returns "now" once [target] has passed.
 */
fun countdownText(target: Long, now: Long): String {
    val seconds = (target - now) / 1000
    if (seconds <= 0) return "now"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

/** How long ago [time] was, e.g. "just now", "5 min ago", "3 h ago", or the date. */
fun relativeTime(time: Long, now: Long): String {
    val minutes = (now - time) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} d ago"
        else -> formatDate(time)
    }
}

/** Up to two initials of [name], or "?" when it is blank. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
