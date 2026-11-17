/*
 * ReviewsSection.kt
 * Ratings and reviews on the campsite details screen. An entrant gives 1 to
 * 5 stars with an optional comment (US 01.13.01), once they have stayed (US 01.13.02).
 * Administrators can remove ratings and reviews (US 03.09.01, 03.10.01). Anyone can reply in a
 * review's thread and react to reviews and replies (advanced review system
 * WOW feature).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.entrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.code.controller.ReviewThread
import com.example.code.model.Review
import com.example.code.ui.common.RatingStars
import com.example.code.ui.common.StarRatingInput
import com.example.code.ui.common.formatDate

/**
 * Review form and review threads for one campsite.
 *
 * @param threads reviews with their replies, newest review first.
 * @param myId this user's device ID, to find their review and reactions.
 * @param busy true while a review is being saved.
 * @param message result of the last action, or null.
 * @param onSubmit called with the stars and comment to post.
 * @param onReply called with a review and the reply text.
 * @param onReact called with a review or reply and the reaction key tapped.
 * @param canReview false for the campsite's owner, who can reply and react but not rate,
 * and for entrants who have not stayed yet (US 01.13.02).
 * @param notAllowedMessage shown instead of the review form when [canReview] is false.
 * @param onRemoveRating administrator action that removes a rating (US 03.09.01); null hides it.
 * @param onRemoveReview administrator action that removes a review or reply (US 03.10.01); null hides it.
 */
@Composable
fun ReviewsSection(
    threads: List<ReviewThread>,
    myId: String,
    busy: Boolean,
    message: String?,
    onSubmit: (Int, String) -> Unit,
    onReply: (Review, String) -> Unit,
    onReact: (Review, String) -> Unit,
    modifier: Modifier = Modifier,
    canReview: Boolean = true,
    notAllowedMessage: String? = null,
    onRemoveRating: ((Review) -> Unit)? = null,
    onRemoveReview: ((Review) -> Unit)? = null,
) {
    // Removal waiting for the administrator to confirm: the review and true for rating.
    var removing by remember { mutableStateOf<Pair<Review, Boolean>?>(null) }
    val admin = AdminReviewActions(
        onRemoveRating?.let { { review: Review -> removing = review to true } },
        onRemoveReview?.let { { review: Review -> removing = review to false } },
    )
    val mine = threads.firstOrNull { it.review.authorId == myId }?.review
    var stars by remember(mine?.stars) { mutableIntStateOf(mine?.stars ?: 0) }
    var text by remember(mine?.text) { mutableStateOf(mine?.text.orEmpty()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ratings and reviews", style = MaterialTheme.typography.titleMedium)
            if (canReview) {
                Text(
                    if (mine == null) "Rate this campsite" else "Your rating: ${mine.stars} out of 5. Tap to change it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                StarRatingInput(value = stars, onChange = { stars = it }, enabled = !busy)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Write a review (optional)") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSubmit(stars, text) }, enabled = !busy && stars > 0) {
                    Text(if (mine == null) "Post review" else "Update review")
                }
            } else if (notAllowedMessage != null) {
                Text(notAllowedMessage, style = MaterialTheme.typography.bodySmall)
            }
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall)

            if (threads.isEmpty()) {
                Text(if (canReview) "No reviews yet. Be the first to review this campsite." else "No reviews yet.")
            }
            threads.forEach { thread ->
                HorizontalDivider()
                ReviewItem(thread.review, myId, onReact, onReply, admin)
                thread.replies.forEach { reply ->
                    Column(Modifier.padding(start = 24.dp)) {
                        ReviewItem(reply, myId, onReact, onReply = null, admin = admin)
                    }
                }
            }
        }
    }

    removing?.let { (review, rating) ->
        val who = review.authorName.ifBlank { "this camper" }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(if (rating) "Remove this rating?" else if (review.isReply) "Remove this reply?" else "Remove this review?") },
            text = {
                Text(
                    if (rating) {
                        "The ${review.stars}-star rating by $who is removed and the campsite's average is updated."
                    } else if (review.isReply) {
                        "The reply by $who is removed from the app."
                    } else {
                        "The review text by $who and its replies are removed from the app."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        if (rating) onRemoveRating?.invoke(review) else onRemoveReview?.invoke(review)
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/** Administrator removal actions shown on each review (US 03.09.01, 03.10.01). */
private class AdminReviewActions(
    val removeRating: ((Review) -> Unit)?,
    val removeReview: ((Review) -> Unit)?,
)

/** One review or reply with its reactions and, for a review, a reply box. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItem(
    review: Review,
    myId: String,
    onReact: (Review, String) -> Unit,
    onReply: ((Review, String) -> Unit)?,
    admin: AdminReviewActions? = null,
) {
    var replying by remember(review.id) { mutableStateOf(false) }
    var draft by remember(review.id) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("review-${review.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                review.authorName.ifBlank { "Camper ${review.authorId.take(4)}" } +
                    if (review.authorId == myId) " (you)" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            if (!review.isReply && review.stars in 1..5) RatingStars(review.stars.toDouble(), size = 14.dp)
        }
        if (review.text.isNotBlank()) Text(review.text)
        if (review.createdAt > 0) {
            Text(
                formatDate(review.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Review.REACTIONS.forEach { (key, label) ->
                val users = review.reactions[key].orEmpty()
                FilterChip(
                    selected = myId in users,
                    onClick = { onReact(review, key) },
                    label = { Text(if (users.isEmpty()) label else "$label ${users.size}") },
                    modifier = Modifier.testTag("react-$key-${review.id}"),
                )
            }
            if (onReply != null) {
                TextButton(onClick = { replying = !replying }) { Text("Reply") }
            }
        }
        if (admin != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (admin.removeRating != null && !review.isReply && review.stars in 1..5) {
                    OutlinedButton(
                        onClick = { admin.removeRating.invoke(review) },
                        modifier = Modifier.testTag("removeRating-${review.id}"),
                    ) { Text("Remove rating") }
                }
                if (admin.removeReview != null && (review.isReply || review.text.isNotBlank())) {
                    OutlinedButton(
                        onClick = { admin.removeReview.invoke(review) },
                        modifier = Modifier.testTag("removeReview-${review.id}"),
                    ) { Text(if (review.isReply) "Remove reply" else "Remove review") }
                }
            }
        }
        if (replying && onReply != null) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Your reply") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    onReply(review, draft)
                    draft = ""
                    replying = false
                },
                enabled = draft.isNotBlank(),
            ) { Text("Send reply") }
        }
    }
}
