/*
 * ReviewController.kt
 * Campsite reviews: an entrant rates a campsite from 1 to 5 stars with an
 * optional comment (US 01.13.01); anyone can reply in a review's thread and
 * react to a review or reply (advanced review system WOW feature). Also
 * keeps the average rating correct when a rating is added or changed.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.ReviewRepository
import com.example.code.model.Review
import kotlinx.coroutines.flow.Flow

/**
 * A top-level review with the replies in its thread.
 *
 * @property review the review.
 * @property replies replies to it, oldest first.
 */
data class ReviewThread(val review: Review, val replies: List<Review>)

/**
 * Saves reviews, replies and reactions.
 *
 * @param reviews where reviews are saved.
 * @param clock current time in milliseconds; replaceable in tests.
 */
class ReviewController(
    private val reviews: ReviewRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Reviews and replies of [campsiteId], updated live. */
    fun reviews(campsiteId: String): Flow<List<Review>> = reviews.observe(campsiteId)

    /**
     * Saves [authorId]'s rating and comment for [campsiteId], replacing
     * their earlier one.
     *
     * @throws IllegalArgumentException unless [stars] is 1 to 5 and the text fits.
     */
    suspend fun submit(campsiteId: String, authorId: String, authorName: String, stars: Int, text: String): Review {
        require(campsiteId.isNotBlank() && authorId.isNotBlank()) { "Campsite and reviewer are required." }
        validateStars(stars)
        val review = Review(
            id = authorId,
            campsiteId = campsiteId,
            authorId = authorId,
            authorName = authorName.trim(),
            stars = stars,
            text = cleanText(text, allowBlank = true),
            createdAt = clock(),
        )
        reviews.submit(review)
        return review
    }

    /**
     * Adds a reply from [authorId] to the thread of [parent].
     *
     * @throws IllegalArgumentException if the text is blank or too long.
     */
    suspend fun reply(parent: Review, authorId: String, authorName: String, text: String): Review {
        require(authorId.isNotBlank()) { "A reviewer is required." }
        val reply = Review(
            campsiteId = parent.campsiteId,
            authorId = authorId,
            authorName = authorName.trim(),
            text = cleanText(text, allowBlank = false),
            // Replies to a reply stay in the same thread.
            parentId = parent.parentId.ifBlank { parent.id },
            createdAt = clock(),
        )
        return reply.copy(id = reviews.reply(reply))
    }

    /**
     * Adds or removes [userId]'s reaction [key] on [review].
     *
     * @return true if the user now reacts with [key].
     */
    suspend fun react(review: Review, key: String, userId: String): Boolean {
        require(userId.isNotBlank()) { "A user is required." }
        require(Review.REACTIONS.any { it.first == key }) { "Unknown reaction." }
        return reviews.toggleReaction(review.campsiteId, review.id, key, userId)
    }

    companion object {
        /** Longest review or reply allowed. */
        const val MAX_LENGTH = 600

        /** @throws IllegalArgumentException unless [stars] is 1 to 5. */
        fun validateStars(stars: Int) {
            require(stars in 1..5) { "Choose 1 to 5 stars." }
        }

        private fun cleanText(text: String, allowBlank: Boolean): String {
            val body = text.trim()
            require(allowBlank || body.isNotEmpty()) { "Write something first." }
            require(body.length <= MAX_LENGTH) { "Reviews can be at most $MAX_LENGTH characters." }
            return body
        }

        /**
         * New (average, count) after a rater gives [stars], replacing their
         * [previous] rating if they had one.
         */
        fun updatedAverage(average: Double?, count: Int, previous: Int?, stars: Int): Pair<Double, Int> {
            val total = (average ?: 0.0) * count
            return if (previous != null && count > 0) {
                (total - previous + stars) / count to count
            } else {
                (total + stars) / (count + 1) to count + 1
            }
        }

        /**
         * [reactions] after [userId] taps reaction [key]: added if missing,
         * removed if present.
         *
         * @return the new reactions and whether the user now reacts with [key].
         */
        fun toggled(
            reactions: Map<String, List<String>>,
            key: String,
            userId: String,
        ): Pair<Map<String, List<String>>, Boolean> {
            val users = reactions[key].orEmpty()
            val reacted = userId !in users
            val updated = if (reacted) users + userId else users - userId
            val result = if (updated.isEmpty()) reactions - key else reactions + (key to updated)
            return result to reacted
        }

        /** Groups [all] into threads: newest review first, each with its replies oldest first. */
        fun threads(all: List<Review>): List<ReviewThread> {
            val replies = all.filter { it.isReply }.groupBy { it.parentId }
            return all.filter { !it.isReply }
                .sortedByDescending { it.createdAt }
                .map { ReviewThread(it, replies[it.id].orEmpty().sortedBy { reply -> reply.createdAt }) }
        }
    }
}
