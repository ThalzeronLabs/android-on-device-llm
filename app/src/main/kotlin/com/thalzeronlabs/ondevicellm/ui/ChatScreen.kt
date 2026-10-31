package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.ModelState
import com.thalzeronlabs.ondevicellm.UiMessage
import java.util.Locale

private val Suggestions = listOf(
    "Explain how you work without internet",
    "Write a two-line poem about the sea",
    "Give me 3 tips to focus better",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onDownload: () -> Unit,
    onPickFile: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(state, onInfo = { showInfo = true }, onClear = onClear)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = state.model == ModelState.Ready,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                    label = "screen",
                ) { ready ->
                    if (ready) Conversation(state, onSend, onStop)
                    else ModelSetup(state.model, state.modelName, state.modelSizeMb, onDownload, onPickFile)
                }
            }
        }
    }

    if (showInfo) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ModelInfo(state)
        }
    }
}

@Composable
private fun Header(state: ChatUiState, onInfo: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Orb(size = 34.dp, active = state.generating)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("On-device LLM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Brand.Green))
                Spacer(Modifier.width(6.dp))
                Text(
                    "100% offline · runs on this phone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.model == ModelState.Ready) {
            Pill(
                text = state.tokensPerSecond?.let { String.format(Locale.US, "%.1f tok/s", it) } ?: "Model info",
                onClick = onInfo,
            )
            if (state.messages.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Pill(text = "New", onClick = onClear, filled = false)
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, filled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
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
        )
    }
}

/** The breathing gradient orb used as the AI's face. Spins and pulses while it is generating. */
@Composable
private fun Orb(size: Dp, active: Boolean = false) {
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
private fun ModelSetup(model: ModelState, name: String, sizeMb: Long, onDownload: () -> Unit, onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Orb(size = 120.dp, active = model is ModelState.Downloading || model == ModelState.Loading)
        Spacer(Modifier.height(28.dp))
        Text(
            "Private AI, on your phone",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Download a small model once ($name, about $sizeMb MB). After that, every answer is generated " +
                "on this device. Nothing you type ever leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        when (model) {
            is ModelState.Downloading -> {
                LinearProgressIndicator(
                    progress = { model.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(model.progress * sizeMb).toInt()} of $sizeMb MB",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModelState.Loading -> Text(
                "Loading the model into memory…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                if (model is ModelState.Failed) {
                    Text(model.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Download the model", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("I already have a .gguf file") }
            }
        }
    }
}

@Composable
private fun Conversation(state: ChatUiState, onSend: (String) -> Unit, onStop: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyState(Modifier.weight(1f), onSuggestion = onSend)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(state.messages) { index, message ->
                    val streaming = state.generating && index == state.messages.lastIndex
                    if (message.fromUser) UserBubble(message.text) else AssistantBubble(message.text, streaming)
                }
            }
        }
        InputBar(
            value = input,
            onValueChange = { input = it },
            generating = state.generating,
            onSend = { onSend(input); input = "" },
            onStop = onStop,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onSuggestion: (String) -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Orb(size = 96.dp)
        Spacer(Modifier.height(24.dp))
        Text("Ask anything", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Turn on airplane mode first: it still answers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Suggestions.forEach { suggestion ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantBubble(text: String, streaming: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp)) { Orb(size = 26.dp, active = streaming) }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            if (text.isEmpty() && streaming) TypingDots()
            else Text(text + if (streaming) " ▍" else "", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 5,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        val enabled = generating || value.isNotBlank()
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled) { if (generating) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) {
            val iconColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(16.dp)) {
                if (generating) {
                    drawRoundRect(iconColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
                } else {
                    val w = size.width
                    val h = size.height
                    val arrow = Path().apply {
                        moveTo(w / 2, 0f); lineTo(w, h * 0.5f); lineTo(w * 0.62f, h * 0.5f)
                        lineTo(w * 0.62f, h); lineTo(w * 0.38f, h); lineTo(w * 0.38f, h * 0.5f)
                        lineTo(0f, h * 0.5f); close()
                    }
                    drawPath(arrow, iconColor)
                }
            }
        }
    }
}

@Composable
private fun ModelInfo(state: ChatUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Orb(size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("About this AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Everything below happens on your phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        InfoRow("Model", state.modelName)
        InfoRow("Size", "${state.modelSizeMb} MB, 4-bit quantized")
        InfoRow("Engine", "llama.cpp on the CPU (ARM dot-product)")
        InfoRow("Speed", state.tokensPerSecond?.let { String.format(Locale.US, "%.1f tokens per second", it) } ?: "Ask something first")
        InfoRow("Privacy", "No server, no account, no network calls while chatting")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Preview
@Composable
private fun ChatPreview() {
    OnDeviceLlmTheme(dark = true) {
        ChatScreen(
            state = ChatUiState(
                model = ModelState.Ready,
                modelName = "Qwen2.5 0.5B Instruct",
                modelSizeMb = 491,
                messages = listOf(
                    UiMessage(true, "What runs this chat?"),
                    UiMessage(false, "A 0.5B-parameter model, quantized to 4 bits, running on your phone's CPU."),
                ),
                tokensPerSecond = 18.4,
            ),
            onDownload = {}, onPickFile = {}, onSend = {}, onStop = {}, onClear = {},
        )
    }
}
