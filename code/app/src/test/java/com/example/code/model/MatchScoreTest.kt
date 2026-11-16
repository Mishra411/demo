/*
 * MatchScoreTest.kt
 * Unit tests for the match score WOW feature.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScoreTest {
    private val jasper = Campsite(
        id = "camp-jasper-riverside", name = "Jasper Riverside", location = "Jasper, AB",
        maxOccupancy = 4, rating = 5.0,
    )

    @Test
    fun perfectMatchScores100() {
        assertEquals(100, MatchScore.score(jasper, "Jasper, AB", 4, datesFree = true, lotteryOpen = true))
    }

    @Test
    fun onlyRatingCountsWithoutFilters() {
        assertEquals(100, MatchScore.score(jasper, "", null, null, lotteryOpen = false))
        assertEquals(80, MatchScore.score(jasper.copy(rating = 4.0), "", null, null, lotteryOpen = false))
        assertEquals(50, MatchScore.score(jasper.copy(rating = null), "", null, null, lotteryOpen = false))
    }

    @Test
    fun wholeWordLocationBeatsPartialText() {
        val word = MatchScore.score(jasper, "jasper", null, null, false)
        val partial = MatchScore.score(jasper, "jasp", null, null, false)
        val none = MatchScore.score(jasper, "Banff", null, null, false)
        assertTrue(word > partial && partial > none)
        // (35 x 0.85 + 15) / 50
        assertEquals(90, word)
        assertEquals(30, none)
    }

    @Test
    fun campsiteTooSmallForTheGroupScoresNothingForGuests() {
        // (25 x 0 + 15) / 40
        assertEquals(38, MatchScore.score(jasper, "", 6, null, false))
    }

    @Test
    fun closerFitToTheGroupScoresHigher() {
        val exact = MatchScore.score(jasper, "", 4, null, false)
        val roomy = MatchScore.score(jasper.copy(maxOccupancy = 12), "", 4, null, false)
        assertEquals(100, exact)
        assertTrue(roomy < exact)
    }

    @Test
    fun freeDatesWithAnOpenLotteryScoreHighest() {
        val open = MatchScore.score(jasper, "", null, datesFree = true, lotteryOpen = true)
        val closed = MatchScore.score(jasper, "", null, datesFree = true, lotteryOpen = false)
        val taken = MatchScore.score(jasper, "", null, datesFree = false, lotteryOpen = true)
        assertEquals(100, open)
        // (25 x 0.6 + 15) / 40
        assertEquals(75, closed)
        assertEquals(38, taken)
    }

    @Test
    fun rankPutsBestMatchFirstAndKeepsTies() {
        val banff = jasper.copy(id = "camp-banff-tunnel")
        val elk = jasper.copy(id = "camp-elk-island")
        val ranked = MatchScore.rank(listOf(jasper to 60, banff to 90, elk to 60))
        assertEquals(listOf("camp-banff-tunnel", "camp-jasper-riverside", "camp-elk-island"), ranked.map { it.first.id })
    }
}
