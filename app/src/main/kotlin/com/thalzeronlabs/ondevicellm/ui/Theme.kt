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
    val Muted = Color(0xFF8E8E93)
    val Green = Color(0xFF34C759)
}

private val Dark = darkColorScheme(
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
    surfaceVariant = Brand.Graphite2,
    onSurfaceVariant = Brand.Muted,
    surfaceContainer = Brand.Graphite2,
    surfaceContainerHigh = Brand.Graphite,
    outline = Color(0xFF3A3A3C),
    error = Color(0xFFFF453A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    onPrimary = Brand.Yellow,
    primaryContainer = Brand.Yellow,
    onPrimaryContainer = Brand.Black,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Brand.Black,
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFF7F7F9),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    outline = Color(0xFFD1D1D6),
)

@Composable
fun OnDeviceLlmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
