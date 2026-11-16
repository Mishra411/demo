/*
 * AccessibilitySettingsTest.kt
 * Unit tests for the accessibility mode choices.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsTest {
    @Test
    fun defaultsChangeNothing() {
        val settings = AccessibilitySettings()
        assertFalse(settings.colorBlind)
        assertEquals(1f, settings.textScale)
        assertEquals(1f, settings.uiScale)
        assertEquals(settings, settings.normalized())
    }

    @Test
    fun scalesAreKeptInsideTheAllowedRange() {
        val settings = AccessibilitySettings(colorBlind = true, textScale = 9f, uiScale = 0.1f).normalized()
        assertTrue(settings.colorBlind)
        assertEquals(AccessibilitySettings.MAX_SCALE, settings.textScale)
        assertEquals(AccessibilitySettings.MIN_SCALE, settings.uiScale)
    }

    @Test
    fun brokenSavedValueFallsBackToDefault() {
        assertEquals(1f, AccessibilitySettings.clampScale(Float.NaN))
    }

    @Test
    fun everyOfferedChoiceIsAllowed() {
        AccessibilitySettings.SCALE_CHOICES.forEach { (_, scale) ->
            assertEquals(scale, AccessibilitySettings.clampScale(scale))
        }
        assertTrue(AccessibilitySettings.SCALE_CHOICES.any { it.second == 1f })
    }
}
