/*
 * Theme.kt
 * Material 3 theme for the app: brand colours in light and dark mode,
 * rounded shapes and the type scale. It also applies accessibility mode
 * (WOW feature): a colour-blind palette and larger or smaller text, buttons
 * and other components.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */
package com.example.code.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.code.model.AccessibilitySettings

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ForestContainer,
    onPrimaryContainer = ForestDeep,
    secondary = Campfire,
    onSecondary = Color.White,
    secondaryContainer = CampfireContainer,
    onSecondaryContainer = Color(0xFF3A1A00),
    tertiary = Lake,
    onTertiary = Color.White,
    tertiaryContainer = LakeContainer,
    onTertiaryContainer = Color(0xFF001D35),
    background = Sand,
    onBackground = Bark,
    surface = Sand,
    onSurface = Bark,
    surfaceVariant = SandVariant,
    onSurfaceVariant = Color(0xFF4A473F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF1EDE4),
    surfaceContainerHigh = Color(0xFFEBE6DC),
    surfaceContainerHighest = Color(0xFFE5E0D6),
    outline = Color(0xFF7B776D),
    outlineVariant = Color(0xFFCCC6B9),
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF155235),
    onPrimaryContainer = ForestContainer,
    secondary = CampfireLight,
    onSecondary = Color(0xFF552100),
    secondaryContainer = Color(0xFF793100),
    onSecondaryContainer = CampfireContainer,
    tertiary = LakeLight,
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF0E4A7A),
    onTertiaryContainer = LakeContainer,
    background = Night,
    onBackground = Color(0xFFE3E3DC),
    surface = Night,
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = NightVariant,
    onSurfaceVariant = Color(0xFFC2C9C0),
    surfaceContainerLowest = Color(0xFF0C100D),
    surfaceContainerLow = Color(0xFF161C18),
    surfaceContainer = Color(0xFF1A201C),
    surfaceContainerHigh = Color(0xFF242B26),
    surfaceContainerHighest = Color(0xFF2F3631),
    outline = Color(0xFF8C938B),
    outlineVariant = Color(0xFF424943),
)

/** Light colours for colour-blind mode: blue and orange instead of green and red. */
private val ColorBlindLight = LightColors.copy(
    primary = SafeBlue,
    primaryContainer = SafeBlueContainer,
    onPrimaryContainer = Color(0xFF001D33),
    secondary = SafeOrange,
    secondaryContainer = SafeOrangeContainer,
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF3A4A5C),
    tertiaryContainer = Color(0xFFDCE3EC),
    onTertiaryContainer = Color(0xFF111C28),
    error = SafeVermillion,
    errorContainer = Color(0xFFFFDBCC),
    onErrorContainer = Color(0xFF3A1200),
)

/** Dark colours for colour-blind mode. */
private val ColorBlindDark = DarkColors.copy(
    primary = SafeBlueLight,
    onPrimary = Color(0xFF00304F),
    primaryContainer = Color(0xFF00476F),
    onPrimaryContainer = SafeBlueContainer,
    secondary = SafeOrangeLight,
    onSecondary = Color(0xFF462B00),
    secondaryContainer = Color(0xFF654000),
    onSecondaryContainer = SafeOrangeContainer,
    error = SafeVermillionLight,
    onError = Color(0xFF5A2100),
    errorContainer = Color(0xFF7A3000),
    onErrorContainer = Color(0xFFFFDBCC),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** True while colour-blind mode is on, so screens can add patterns or icons to colour. */
val LocalColorBlind = staticCompositionLocalOf { false }

/** Returns the colour scheme for these choices. */
fun colorSchemeFor(darkTheme: Boolean, colorBlind: Boolean): ColorScheme = when {
    colorBlind && darkTheme -> ColorBlindDark
    colorBlind -> ColorBlindLight
    darkTheme -> DarkColors
    else -> LightColors
}

/**
 * App theme.
 *
 * @param settings accessibility choices: colour-blind colours, text size and component size.
 * @param darkTheme true to use the dark colours; follows the system by default.
 */
@Composable
fun CodeTheme(
    settings: AccessibilitySettings = AccessibilitySettings(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val safe = settings.normalized()
    val system = LocalDensity.current
    // A bigger density makes every dp-sized component bigger. Text is sized
    // by density x fontScale, so dividing by uiScale keeps the two choices
    // independent: uiScale changes components, textScale changes text.
    val density = Density(
        density = system.density * safe.uiScale,
        fontScale = system.fontScale * safe.textScale / safe.uiScale,
    )
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalColorBlind provides safe.colorBlind,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(darkTheme, safe.colorBlind),
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
