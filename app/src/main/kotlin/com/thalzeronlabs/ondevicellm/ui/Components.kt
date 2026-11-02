package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** The breathing gradient orb used as the AI's face. Spins and pulses while it is working. */
@Composable
fun Orb(size: Dp, active: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (active) 1_400 else 9_000, easing = LinearEasing)),
        label = "angle",
    )
    val pulse by transition.animateFloat(
        0.92f, 1.06f,
        infiniteRepeatable(tween(if (active) 500 else 2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(Modifier.size(size).scale(pulse).rotate(angle)) {
        drawCircle(
            Brush.sweepGradient(listOf(Brand.Yellow, Color(0xFFFF9F0A), Brand.YellowDim, Brand.Yellow)),
            radius = this.size.minDimension / 2,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(this.size.width * 0.35f, this.size.height * 0.35f),
                radius = this.size.minDimension * 0.5f,
            ),
            radius = this.size.minDimension / 2,
        )
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().height(52.dp)) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().height(52.dp)) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(text) }
}

@Composable
fun Progress(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

fun Long.mb(): Long = this / 1_000_000

/** `1117320736` → `1.1 GB`, `491400032` → `491 MB`. */
fun Long.humanSize(): String =
    if (this >= 1_000_000_000) String.format(Locale.US, "%.1f GB", this / 1e9) else "${this / 1_000_000} MB"

/** A few Material icons (Apache-2.0), drawn from their SVG paths to avoid the large icons artifact. */
object AppIcons {
    val Menu = icon("Menu", "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z")
    val MoreVert = icon(
        "MoreVert",
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 " +
            "2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    )
    val Add = icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val BatteryAlert = icon(
        "BatteryAlert",
        "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 " +
            "1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4zM13,18h-2v-2h2v2zM13,14h-2V9h2v5z",
    )
    val ExpandMore = icon("ExpandMore", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
