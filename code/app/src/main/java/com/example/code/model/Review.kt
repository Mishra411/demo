/*
 * Review.kt
 * Model of a campsite review (Firestore: campsites/{id}/reviews/{id}). A
 * top-level review carries the entrant's star rating (US 01.13.01); a reply
 * points at its parent review, and anyone can react to either (advanced
 * review system WOW feature). Data only.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

/**
 * A review of a campsite, or a reply in a review's thread.
 *
 * @property id Firestore document ID. A top-level review uses its author's
 * device ID, so each entrant has one rating per campsite.
 * @property campsiteId campsite reviewed.
 * @property authorId device ID of the writer.
 * @property authorName writer's display name.
 * @property stars rating from 1 to 5; 0 for a reply.
 * @property text what the writer said; may be blank for a rating only.
 * @property parentId ID of the review this replies to, or blank for a top-level review.
 * @property reactions device IDs of the users who reacted, by reaction key
 * (see [REACTIONS]).
 * @property createdAt time written, in milliseconds since the epoch.
 */
data class Review(
    val id: String = "",
    val campsiteId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val stars: Int = 0,
    val text: String = "",
    val parentId: String = "",
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAt: Long = 0L,
) {
    /** True if this is a reply in another review's thread. */
    val isReply: Boolean get() = parentId.isNotBlank()

    companion object {
        /** Reaction keys and the label shown for each. */
        val REACTIONS: List<Pair<String, String>> = listOf(
            "helpful" to "Helpful",
            "love" to "Love",
            "funny" to "Funny",
        )
    }
}
