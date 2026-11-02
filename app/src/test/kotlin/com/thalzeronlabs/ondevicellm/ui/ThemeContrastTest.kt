package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text stays readable in both themes: every pair the UI draws meets WCAG AA (4.5:1) for body text. */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun ColorScheme.textPairs() = mapOf(
        "onBackground/background" to (onBackground to background),
        "onSurface/surface" to (onSurface to surface),
        "onSurface/surfaceContainer" to (onSurface to surfaceContainer),
        "onSurface/surfaceContainerHigh" to (onSurface to surfaceContainerHigh),
        "onSurfaceVariant/background" to (onSurfaceVariant to background),
        "onSurfaceVariant/surfaceContainer" to (onSurfaceVariant to surfaceContainer),
        "onSurfaceVariant/surfaceContainerHigh" to (onSurfaceVariant to surfaceContainerHigh),
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "primary/surfaceContainer (text buttons)" to (primary to surfaceContainer),
        "primary/secondaryContainer (banner buttons)" to (primary to secondaryContainer),
        "error/background" to (error to background),
        "error/surfaceContainerHigh" to (error to surfaceContainerHigh),
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failures = scheme.textPairs().mapNotNull { (pair, colors) ->
            val ratio = contrast(colors.first, colors.second)
            if (ratio < 4.5) "$pair = %.2f".format(ratio) else null
        }
        assertTrue("$name theme below 4.5:1: $failures", failures.isEmpty())
    }

    /** Text on the drawer, sheets and cards must get the primary text color, not the secondary one. */
    private fun checkContentColors(name: String, scheme: ColorScheme) = with(scheme) {
        listOf(background, surface, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest).forEach { container ->
            val content = contentColorFor(container)
            assertNotEquals("$name: secondary text color on $container", onSurfaceVariant, content)
            assertTrue("$name: weak text on $container", contrast(content, container) >= 7.0)
        }
    }

    @Test fun `containers get the primary text color in both themes`() {
        checkContentColors("dark", DarkColors)
        checkContentColors("light", LightColors)
    }

    @Test fun `dark theme is readable`() = check("dark", DarkColors)

    @Test fun `light theme is readable`() = check("light", LightColors)
}
