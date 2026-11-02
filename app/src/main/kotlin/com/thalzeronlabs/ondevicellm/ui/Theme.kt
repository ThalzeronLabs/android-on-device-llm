package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Thalzeron Labs palette: near-black surfaces with a single bright yellow accent. */
object Brand {
    val Yellow = Color(0xFFFFD600)
    val YellowDim = Color(0xFFB89A00)
    val Black = Color(0xFF121212)
    val Graphite = Color(0xFF2C2C2E)
    val Graphite2 = Color(0xFF1C1C1E)
    val Muted = Color(0xFF98989D)
    val Green = Color(0xFF34C759)
}

/** Every text/background pair used by the UI meets WCAG AA (4.5:1), checked by ThemeContrastTest. */
internal val DarkColors = darkColorScheme(
    primary = Brand.Yellow,
    onPrimary = Brand.Black,
    primaryContainer = Brand.Yellow,
    onPrimaryContainer = Brand.Black,
    secondary = Brand.Muted,
    secondaryContainer = Brand.Graphite,
    onSecondaryContainer = Color(0xFFF2F2F7),
    background = Brand.Black,
    onBackground = Color(0xFFF2F2F7),
    surface = Brand.Black,
    onSurface = Color(0xFFF2F2F7),
    // Must differ from every surfaceContainer*: contentColorFor() checks surfaceVariant first, and an equal color
    // would give the drawer and the sheets the secondary text color (ThemeContrastTest guards this).
    surfaceVariant = Color(0xFF242426),
    onSurfaceVariant = Brand.Muted,
    surfaceContainer = Brand.Graphite2,
    surfaceContainerHigh = Brand.Graphite,
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF3A3A3C),
    error = Color(0xFFFF6961),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    onPrimary = Brand.Yellow,
    primaryContainer = Brand.Yellow,
    onPrimaryContainer = Brand.Black,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Brand.Black,
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFF7F7F9),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF636366),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6),
    outline = Color(0xFFD1D1D6),
    error = Color(0xFFB3261E),
)

@Composable
fun OnDeviceLlmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
