/*
 * Featured.kt
 * Featured carousel WOW feature: picks the campsites shown in the carousel
 * from the entrant's preferred tags, ratings and open lotteries.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

/**
 * A campsite chosen for the carousel.
 *
 * @property campsite the campsite.
 * @property reason why it was chosen, e.g. "Matches: lake, hiking".
 */
data class FeaturedCampsite(val campsite: Campsite, val reason: String)

/** Chooses featured campsites. */
object Featured {
    /**
     * Up to [limit] campsites for the carousel, best first. Each matching
     * preferred tag adds 3 points, an open lottery adds 2, and the rating
     * (0 to 5) is added on top. Campsites with nothing to recommend them
     * (no tag match, no rating, no open lottery) are left out.
     *
     * @param preferredTags tags the entrant chose on their profile.
     * @param openLotteryCampsiteIds campsites whose lottery is open now.
     */
    fun pick(
        campsites: List<Campsite>,
        preferredTags: Set<String>,
        openLotteryCampsiteIds: Set<String>,
        limit: Int = 5,
    ): List<FeaturedCampsite> {
        val wanted = preferredTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return campsites.mapNotNull { campsite ->
            val matches = campsite.tags.filter { it.trim().lowercase() in wanted }
            val open = campsite.id in openLotteryCampsiteIds
            val rating = campsite.rating?.takeIf { it.isFinite() && it in 1.0..5.0 }
            if (matches.isEmpty() && !open && rating == null) return@mapNotNull null
            val points = matches.size * 3.0 + (if (open) 2.0 else 0.0) + (rating ?: 0.0)
            val reason = when {
                matches.isNotEmpty() -> "Matches: ${matches.joinToString(", ")}"
                open -> "Lottery open now"
                else -> "Top rated"
            }
            Triple(campsite, points, reason)
        }.sortedByDescending { it.second }
            .take(limit)
            .map { FeaturedCampsite(it.first, it.third) }
    }

    /** Every tag used by [campsites], lower-cased, most common first. */
    fun allTags(campsites: List<Campsite>): List<String> =
        campsites.flatMap { it.tags }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}
