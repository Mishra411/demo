/*
 * ReviewControllerTest.kt
 * Unit tests for rating a campsite (US 01.13.01) and the advanced review
 * system: reply threads and reactions. Also reviewing only after a stay
 * (US 01.13.02) and admin removal of ratings and reviews (US 03.09.01, 03.10.01).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.reviewFromMap
import com.example.code.data.reviewToMap
import com.example.code.fakes.FakeReviewRepository
import com.example.code.fakes.TestData
import com.example.code.model.Reservation
import com.example.code.model.Review
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewControllerTest {
    private val campsiteId = "camp-jasper-riverside"
    private val repository = FakeReviewRepository()
    private var now = 1_000L
    private val controller = ReviewController(repository) { now }

    private fun saved(): List<Review> = repository.reviews.value.values.toList()

    @Test
    fun entrantRatesACampsiteWithAComment() = runBlocking {
        val review = controller.submit(campsiteId, "device-priya-01", " Priya Shah ", 5, " Loved the river. ")
        assertEquals(5, review.stars)
        assertEquals("Priya Shah", review.authorName)
        assertEquals("Loved the river.", review.text)
        assertFalse(review.isReply)
        assertEquals(listOf(review), saved())
    }

    @Test
    fun ratingAgainReplacesTheEarlierRating() = runBlocking {
        controller.submit(campsiteId, "device-priya-01", "Priya Shah", 5, "")
        controller.submit(campsiteId, "device-priya-01", "Priya Shah", 3, "Noisy at night.")
        assertEquals(1, saved().size)
        assertEquals(3, saved().single().stars)
    }

    @Test
    fun starsMustBeOneToFive() {
        listOf(0, 6, -1).forEach { stars ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { controller.submit(campsiteId, "device-priya-01", "Priya Shah", stars, "") }
            }
        }
        assertTrue(saved().isEmpty())
    }

    @Test
    fun reviewTextHasAMaximumLength() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                controller.submit(campsiteId, "device-priya-01", "Priya", 4, "x".repeat(ReviewController.MAX_LENGTH + 1))
            }
        }
    }

    @Test
    fun replyJoinsTheReviewsThread() = runBlocking {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "Great site.")
        now = 2_000L
        val reply = controller.reply(review, "owner-device-7f3a", "Jasper Camps", " Thanks for staying! ")
        assertTrue(reply.isReply)
        assertEquals(review.id, reply.parentId)
        assertEquals("Thanks for staying!", reply.text)
        // Replying to a reply stays in the same thread.
        now = 3_000L
        val second = controller.reply(reply, "device-priya-01", "Priya Shah", "We will be back.")
        assertEquals(review.id, second.parentId)

        val thread = ReviewController.threads(saved()).single()
        assertEquals(review.id, thread.review.id)
        assertEquals(listOf("Thanks for staying!", "We will be back."), thread.replies.map { it.text })
    }

    @Test
    fun emptyReplyIsRejected() = runBlocking<Unit> {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.reply(review, "owner-device-7f3a", "Jasper Camps", "   ") }
        }
    }

    @Test
    fun reactionIsAddedThenRemovedOnASecondTap() = runBlocking {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "Great site.")
        assertTrue(controller.react(review, "helpful", "device-liam-02"))
        assertEquals(listOf("device-liam-02"), saved().single().reactions["helpful"])
        assertFalse(controller.react(saved().single(), "helpful", "device-liam-02"))
        assertTrue(saved().single().reactions.isEmpty())
    }

    @Test
    fun unknownReactionIsRejected() = runBlocking<Unit> {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.react(review, "angry", "device-liam-02") }
        }
    }

    @Test
    fun reactionsFromDifferentPeopleAreCountedTogether() {
        val (one, _) = ReviewController.toggled(emptyMap(), "love", "device-liam-02")
        val (two, reacted) = ReviewController.toggled(one, "love", "device-sofia-03")
        assertTrue(reacted)
        assertEquals(listOf("device-liam-02", "device-sofia-03"), two["love"])
    }

    @Test
    fun averageRatingFollowsNewAndChangedRatings() {
        // First rating of an unrated campsite.
        assertEquals(4.0 to 1, ReviewController.updatedAverage(null, 0, null, 4))
        // A second rater.
        assertEquals(4.5 to 2, ReviewController.updatedAverage(4.0, 1, null, 5))
        // The second rater changes 5 to 2: the count stays.
        assertEquals(3.0 to 2, ReviewController.updatedAverage(4.5, 2, 5, 2))
    }

    @Test
    fun threadsAreNewestFirst() {
        val older = Review(id = "a", campsiteId = campsiteId, authorId = "a", stars = 4, createdAt = 10)
        val newer = Review(id = "b", campsiteId = campsiteId, authorId = "b", stars = 5, createdAt = 20)
        assertEquals(listOf("b", "a"), ReviewController.threads(listOf(older, newer)).map { it.review.id })
    }

    @Test
    fun reviewSurvivesSavingAndLoading() {
        val review = Review(
            id = "device-priya-01", campsiteId = campsiteId, authorId = "device-priya-01", authorName = "Priya Shah",
            stars = 4, text = "Great site.", reactions = mapOf("helpful" to listOf("device-liam-02")), createdAt = 1_000,
        )
        assertEquals(review, reviewFromMap(review.id, campsiteId, reviewToMap(review)))
    }

    // US 01.13.02
    @Test
    fun onlyEntrantsWithAFinishedStayCanReview() {
        val stay = TestData.priyaReservation
        val during = stay.startDate + 1
        val after = stay.endDate + 1
        assertFalse(ReviewController.canReview(listOf(stay), stay.campsiteId, stay.entrantId, during))
        assertTrue(ReviewController.canReview(listOf(stay), stay.campsiteId, stay.entrantId, after))
        // Not another campsite, not another entrant, not a cancelled stay.
        assertFalse(ReviewController.canReview(listOf(stay), "camp-other", stay.entrantId, after))
        assertFalse(ReviewController.canReview(listOf(stay), stay.campsiteId, "device-liam-02", after))
        val cancelled = stay.copy(status = Reservation.STATUS_CANCELLED)
        assertFalse(ReviewController.canReview(listOf(cancelled), stay.campsiteId, stay.entrantId, after))
    }

    @Test
    fun reviewWithoutAStayIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.submit(campsiteId, "device-priya-01", "Priya Shah", 5, "Great", stayed = false) }
        }
        assertEquals(ReviewController.NEEDS_STAY, error.message)
        assertTrue(saved().isEmpty())
    }

    // US 03.09.01
    @Test
    fun adminRemovesARatingAndTheAverageIsUpdated() = runBlocking {
        val priya = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 5, "Loved it.")
        controller.submit(campsiteId, "device-liam-02", "Liam Chen", 2, "")
        repository.averages.value = mapOf(campsiteId to (3.5 to 2))

        controller.removeRating(priya)

        assertEquals(2.0 to 1, repository.averages.value[campsiteId])
        // The comment stays without its stars.
        val kept = saved().first { it.authorId == "device-priya-01" }
        assertEquals(0, kept.stars)
        assertEquals("Loved it.", kept.text)
    }

    @Test
    fun removingARatingWithNoCommentRemovesTheWholeReview() = runBlocking {
        val liam = controller.submit(campsiteId, "device-liam-02", "Liam Chen", 2, "")
        repository.averages.value = mapOf(campsiteId to (2.0 to 1))
        controller.removeRating(liam)
        assertTrue(saved().isEmpty())
        assertEquals(null to 0, repository.averages.value[campsiteId])
    }

    @Test
    fun removedAverageHandlesTheLastRating() {
        assertEquals(4.0 to 2, ReviewController.removedAverage(3.0, 3, 1))
        assertEquals(null to 0, ReviewController.removedAverage(5.0, 1, 5))
        assertEquals(null to 0, ReviewController.removedAverage(null, 0, 3))
    }

    @Test
    fun replyHasNoRatingToRemove() = runBlocking<Unit> {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "Nice")
        val reply = controller.reply(review, "owner-device-7f3a", "Jasper Camps", "Thanks!")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { controller.removeRating(reply) } }
    }

    // US 03.10.01
    @Test
    fun adminRemovesAReviewAndItsRepliesButKeepsTheRating() = runBlocking {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 1, "Rude words here.")
        controller.reply(review, "owner-device-7f3a", "Jasper Camps", "Sorry to hear that.")

        controller.removeReview(review)

        val left = saved().single()
        assertEquals("", left.text)
        assertEquals(1, left.stars)
        assertTrue(ReviewController.threads(saved()).single().replies.isEmpty())
    }

    @Test
    fun adminRemovesOneReply() = runBlocking {
        val review = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "Nice")
        val reply = controller.reply(review, "device-liam-02", "Liam Chen", "Spam link")
        controller.removeReview(reply)
        assertEquals(listOf(review.id), saved().map { it.id })
    }

    @Test
    fun reviewWithoutTextHasNothingToRemove() = runBlocking<Unit> {
        val rating = controller.submit(campsiteId, "device-priya-01", "Priya Shah", 4, "")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { controller.removeReview(rating) } }
    }
}
