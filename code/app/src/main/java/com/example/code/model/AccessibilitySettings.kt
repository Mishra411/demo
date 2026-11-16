/*
 * AccessibilitySettings.kt
 * Accessibility mode WOW feature: the user's choices for colour-blind
 * colours and for the size of text, buttons and other components. Data and
 * its limits only; the theme applies them.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

/**
 * Accessibility choices for this device.
 *
 * @property colorBlind true to use colours that do not rely on red and green.
 * @property textScale text size multiplier, from [MIN_SCALE] to [MAX_SCALE].
 * @property uiScale multiplier for buttons and other components, in the same range.
 */
data class AccessibilitySettings(
    val colorBlind: Boolean = false,
    val textScale: Float = 1f,
    val uiScale: Float = 1f,
) {
    /** These settings with both scales forced inside the allowed range. */
    fun normalized(): AccessibilitySettings = copy(
        textScale = clampScale(textScale),
        uiScale = clampScale(uiScale),
    )

    companion object {
        /** Smallest allowed scale. */
        const val MIN_SCALE = 0.85f

        /** Largest allowed scale. */
        const val MAX_SCALE = 1.5f

        /** Scale choices offered on the settings screen, with their labels. */
        val SCALE_CHOICES: List<Pair<String, Float>> = listOf(
            "Small" to 0.85f,
            "Default" to 1f,
            "Large" to 1.2f,
            "Extra large" to 1.4f,
        )

        /** Forces [scale] inside the allowed range; bad values become 1. */
        fun clampScale(scale: Float): Float =
            if (scale.isNaN()) 1f else scale.coerceIn(MIN_SCALE, MAX_SCALE)
    }
}
