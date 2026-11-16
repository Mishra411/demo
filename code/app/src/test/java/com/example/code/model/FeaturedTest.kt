/*
 * FeaturedTest.kt
 * Unit tests for choosing campsites for the featured carousel.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedTest {
    private val jasper = Campsite(id = "jasper", name = "Jasper Riverside", tags = listOf("River", "hiking"), rating = 4.0)
    private val banff = Campsite(id = "banff", name = "Banff Tunnel Mountain", tags = listOf("mountain"), rating = 5.0)
    private val elk = Campsite(id = "elk", name = "Elk Island Astotin", tags = listOf("lake"))
    private val plain = Campsite(id = "plain", name = "Plain Field")
    private val all = listOf(jasper, banff, elk, plain)

    @Test
    fun preferredTagsComeFirstWithAReason() {
        val picks = Featured.pick(all, setOf("river", "Hiking"), emptySet())
        assertEquals("jasper", picks.first().campsite.id)
        assertEquals("Matches: River, hiking", picks.first().reason)
    }

    @Test
    fun withoutPreferencesTopRatedAndOpenLotteriesAreFeatured() {
        val picks = Featured.pick(all, emptySet(), setOf("elk"))
        assertEquals(listOf("banff", "jasper", "elk"), picks.map { it.campsite.id })
        assertEquals(listOf("Top rated", "Top rated", "Lottery open now"), picks.map { it.reason })
    }

    @Test
    fun campsitesWithNothingToRecommendAreLeftOut() {
        assertTrue(Featured.pick(listOf(plain), setOf("river"), emptySet()).isEmpty())
    }

    @Test
    fun limitCapsTheCarousel() {
        assertEquals(2, Featured.pick(all, emptySet(), setOf("elk"), limit = 2).size)
    }

    @Test
    fun tagsAreListedMostCommonFirst() {
        val more = all + Campsite(id = "x", tags = listOf("Lake", " river "))
        assertEquals(listOf("lake", "river", "hiking", "mountain"), Featured.allTags(more))
    }
}
