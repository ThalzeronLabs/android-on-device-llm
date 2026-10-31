package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondaryContainer = Color(0xFF2A2F3A),
    onSecondaryContainer = Color(0xFFDDE3EE),
    background = Color(0xFF0E1117),
    surface = Color(0xFF0E1117),
    surfaceVariant = Color(0xFF1B2029),
    onSurfaceVariant = Color(0xFFB8C2D1),
)

private val Light = lightColorScheme(
    primary = Color(0xFF006874),
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondaryContainer = Color(0xFFE3E8F0),
)

@Composable
fun OnDeviceLlmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
