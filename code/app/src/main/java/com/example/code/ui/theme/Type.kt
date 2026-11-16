/*
 * Type.kt
 * Type scale: bold, friendly headings and readable body text.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */
package com.example.code.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Typography used by [CodeTheme]. */
val Typography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.25.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
