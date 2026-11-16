/*
 * Color.kt
 * Brand colours (forest green, campfire amber, lake blue) and the
 * colour-blind palette used by accessibility mode, which avoids telling
 * things apart by red and green.
 * AI use: palettes chosen with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */
package com.example.code.ui.theme

import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF2F6B4F)
val ForestLight = Color(0xFF9AD5B3)
val ForestContainer = Color(0xFFCDEBDA)
val ForestDeep = Color(0xFF0E3B27)
val Campfire = Color(0xFFB85C1E)
val CampfireLight = Color(0xFFFFB68A)
val CampfireContainer = Color(0xFFFFDCC6)
val Lake = Color(0xFF2F6492)
val LakeLight = Color(0xFF9CCAFF)
val LakeContainer = Color(0xFFD2E4FF)
val Sand = Color(0xFFFAF7F0)
val SandVariant = Color(0xFFEDE7DA)
val Bark = Color(0xFF1D1B16)
val Night = Color(0xFF111512)
val NightVariant = Color(0xFF2A322D)

// Colour-blind safe palette (after Okabe and Ito): blue, orange and vermillion.
val SafeBlue = Color(0xFF005A8C)
val SafeBlueLight = Color(0xFF8FCBFF)
val SafeBlueContainer = Color(0xFFCFE6FF)
val SafeOrange = Color(0xFF8A5A00)
val SafeOrangeLight = Color(0xFFFFC14D)
val SafeOrangeContainer = Color(0xFFFFE0A3)
val SafeVermillion = Color(0xFFB34700)
val SafeVermillionLight = Color(0xFFFFB38A)

/** Star colour for ratings. */
val StarGold = Color(0xFFE69F00)
