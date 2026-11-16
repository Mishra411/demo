/*
 * MatchScore.kt
 * Match score WOW feature: ranks campsites by how well they fit the
 * entrant's filters (location, guests, dates) and by rating.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

import kotlin.math.roundToInt

/** Scores campsites against an entrant's filters. */
object MatchScore {
    private const val LOCATION_WEIGHT = 35.0
    private const val GUESTS_WEIGHT = 25.0
    private const val DATES_WEIGHT = 25.0
    private const val RATING_WEIGHT = 15.0

    /**
     * Score from 0 to 100 for [campsite]. Only the filters the entrant set
     * count, plus the campsite's rating.
     *
     * - Location: 100% when the location is exactly [location], 85% when a
     *   whole word matches, 70% when it only contains the text, else 0.
     * - Guests: full marks when the campsite fits the group exactly; a
     *   campsite much larger than the group scores lower, too small scores 0.
     * - Dates: full marks when the dates are free and a lottery is open to
     *   join now, 60% when the dates are free but no lottery is open.
     * - Rating: the average rating out of 5; unrated campsites get half.
     *
     * @param location location filter, or blank.
     * @param guests guest filter, or null.
     * @param datesFree true if the filtered dates are free, false if not, null if no date filter.
     * @param lotteryOpen true if the campsite has a lottery open for registration.
     */
    fun score(
        campsite: Campsite,
        location: String,
        guests: Int?,
        datesFree: Boolean?,
        lotteryOpen: Boolean,
    ): Int {
        var earned = 0.0
        var possible = 0.0

        val wanted = location.trim()
        if (wanted.isNotEmpty()) {
            possible += LOCATION_WEIGHT
            val place = campsite.location.trim()
            val fit = when {
                place.equals(wanted, ignoreCase = true) -> 1.0
                place.split(Regex("[^\\p{L}\\p{N}]+")).any { it.equals(wanted, ignoreCase = true) } -> 0.85
                place.contains(wanted, ignoreCase = true) -> 0.7
                else -> 0.0
            }
            earned += LOCATION_WEIGHT * fit
        }

        if (guests != null) {
            possible += GUESTS_WEIGHT
            val fit = if (campsite.maxOccupancy < guests || campsite.maxOccupancy <= 0) {
                0.0
            } else {
                0.5 + 0.5 * guests.toDouble() / campsite.maxOccupancy
            }
            earned += GUESTS_WEIGHT * fit
        }

        if (datesFree != null) {
            possible += DATES_WEIGHT
            earned += DATES_WEIGHT * if (!datesFree) 0.0 else if (lotteryOpen) 1.0 else 0.6
        }

        possible += RATING_WEIGHT
        val rating = campsite.rating
        earned += RATING_WEIGHT *
            if (rating != null && rating.isFinite() && rating in 1.0..5.0) rating / 5.0 else 0.5

        return (earned / possible * 100).roundToInt().coerceIn(0, 100)
    }

    /** Sorts [scored] campsites best match first; equal scores keep their order. */
    fun rank(scored: List<Pair<Campsite, Int>>): List<Pair<Campsite, Int>> =
        scored.sortedByDescending { it.second }
}
