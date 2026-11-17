/*
 * ReviewRepository.kt
 * Saves and loads campsite reviews, replies and reactions
 * (Firestore: campsites/{id}/reviews/{id}) and keeps each campsite's
 * average rating up to date. US 01.13.01 and the advanced review system
 * WOW feature.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.data

import com.example.code.controller.ReviewController
import com.example.code.model.Review
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Access to campsite reviews. */
interface ReviewRepository {
    /** Reviews and replies of [campsiteId], oldest first. */
    fun observe(campsiteId: String): Flow<List<Review>>

    /**
     * Saves a top-level [review] under its author's ID, replacing their
     * earlier review, and updates the campsite's average rating.
     */
    suspend fun submit(review: Review)

    /** Saves [reply] in a review's thread and returns its new ID. */
    suspend fun reply(reply: Review): String

    /**
     * Adds [userId]'s reaction [key] to review [reviewId], or removes it if
     * it was already there.
     *
     * @return true if the user now reacts with [key].
     */
    suspend fun toggleReaction(campsiteId: String, reviewId: String, key: String, userId: String): Boolean

    /**
     * Removes the star rating of review [reviewId] and takes it out of the
     * campsite's average (US 03.09.01). The comment stays.
     * AI use: written with Claude (Anthropic), Nov 2026.
     */
    suspend fun removeRating(campsiteId: String, reviewId: String) {
        throw UnsupportedOperationException("Removing ratings is not supported here.")
    }

    /**
     * Removes the text of review [reviewId] and its replies, or a single
     * reply (US 03.10.01). A star rating stays until it is removed too.
     */
    suspend fun removeReview(campsiteId: String, reviewId: String) {
        throw UnsupportedOperationException("Removing reviews is not supported here.")
    }
}

/** [ReviewRepository] backed by Cloud Firestore. */
class FirestoreReviewRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ReviewRepository {

    private fun reviews(campsiteId: String) =
        db.collection("campsites").document(campsiteId).collection(REVIEWS)

    override fun observe(campsiteId: String): Flow<List<Review>> = callbackFlow {
        val registration = reviews(campsiteId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty()
                    .map { reviewFromMap(it.id, campsiteId, it.data.orEmpty()) }
                    .sortedBy { it.createdAt },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun submit(review: Review) {
        val campsiteRef = db.collection("campsites").document(review.campsiteId)
        val reviewRef = reviews(review.campsiteId).document(review.authorId)
        db.runTransaction { transaction ->
            val campsite = transaction.get(campsiteRef)
            require(campsite.exists()) { "This campsite no longer exists." }
            val previous = transaction.get(reviewRef).getLong("stars")?.toInt()?.takeIf { it in 1..5 }
            val average = (campsite.get("rating") as? Number)?.toDouble()
            val count = (campsite.get("ratingCount") as? Number)?.toInt() ?: if (average != null) 1 else 0
            val (newAverage, newCount) = ReviewController.updatedAverage(average, count, previous, review.stars)
            // Merge so reactions on an edited review are kept.
            transaction.set(reviewRef, reviewToMap(review) - "reactions", SetOptions.merge())
            transaction.update(campsiteRef, mapOf("rating" to newAverage, "ratingCount" to newCount))
        }.awaitResult()
    }

    override suspend fun reply(reply: Review): String {
        val ref = reviews(reply.campsiteId).document()
        ref.set(reviewToMap(reply)).awaitResult()
        return ref.id
    }

    override suspend fun toggleReaction(
        campsiteId: String,
        reviewId: String,
        key: String,
        userId: String,
    ): Boolean {
        val ref = reviews(campsiteId).document(reviewId)
        return db.runTransaction { transaction ->
            val current = reviewFromMap(reviewId, campsiteId, transaction.get(ref).data.orEmpty())
            val (updated, reacted) = ReviewController.toggled(current.reactions, key, userId)
            transaction.update(ref, "reactions", updated)
            reacted
        }.awaitResult()
    }

    override suspend fun removeRating(campsiteId: String, reviewId: String) {
        val campsiteRef = db.collection("campsites").document(campsiteId)
        val reviewRef = reviews(campsiteId).document(reviewId)
        db.runTransaction { transaction ->
            val review = transaction.get(reviewRef)
            val campsite = transaction.get(campsiteRef)
            val stars = review.getLong("stars")?.toInt() ?: 0
            if (!review.exists() || stars !in 1..5) return@runTransaction
            val average = (campsite.get("rating") as? Number)?.toDouble()
            val count = (campsite.get("ratingCount") as? Number)?.toInt() ?: if (average != null) 1 else 0
            val (newAverage, newCount) = ReviewController.removedAverage(average, count, stars)
            // A rating with no comment leaves nothing to show, so it goes entirely.
            if (review.getString("text").isNullOrBlank()) {
                transaction.delete(reviewRef)
            } else {
                transaction.update(reviewRef, "stars", 0)
            }
            if (campsite.exists()) {
                transaction.update(campsiteRef, mapOf("rating" to newAverage, "ratingCount" to newCount))
            }
        }.awaitResult()
    }

    override suspend fun removeReview(campsiteId: String, reviewId: String) {
        val ref = reviews(campsiteId).document(reviewId)
        val snapshot = ref.get().awaitResult()
        if (!snapshot.exists()) return
        val review = reviewFromMap(reviewId, campsiteId, snapshot.data.orEmpty())
        val batch = db.batch()
        if (review.isReply) {
            batch.delete(ref)
        } else {
            reviews(campsiteId).whereEqualTo("parentId", reviewId).get().awaitResult()
                .documents.forEach { batch.delete(it.reference) }
            // Keep a star rating that has not been removed; it has its own story.
            if (review.stars in 1..5) batch.update(ref, "text", "") else batch.delete(ref)
        }
        batch.commit().awaitResult()
    }

    companion object {
        /** Name of each campsite's reviews subcollection. */
        const val REVIEWS = "reviews"
    }
}

/** Converts [review] to the fields stored in Firestore. */
fun reviewToMap(review: Review): Map<String, Any> = mapOf(
    "authorId" to review.authorId,
    "authorName" to review.authorName,
    "stars" to review.stars,
    "text" to review.text,
    "parentId" to review.parentId,
    "reactions" to review.reactions,
    "createdAt" to review.createdAt,
)

/** Builds a [Review] of [campsiteId] from Firestore document [data]. */
fun reviewFromMap(id: String, campsiteId: String, data: Map<String, Any?>): Review = Review(
    id = id,
    campsiteId = campsiteId,
    authorId = data["authorId"] as? String ?: "",
    authorName = data["authorName"] as? String ?: "",
    stars = (data["stars"] as? Number)?.toInt() ?: 0,
    text = data["text"] as? String ?: "",
    parentId = data["parentId"] as? String ?: "",
    reactions = (data["reactions"] as? Map<*, *>).orEmpty()
        .mapNotNull { (key, value) ->
            val users = (value as? List<*>)?.filterIsInstance<String>().orEmpty()
            (key as? String)?.takeIf { users.isNotEmpty() }?.let { it to users }
        }.toMap(),
    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
)
