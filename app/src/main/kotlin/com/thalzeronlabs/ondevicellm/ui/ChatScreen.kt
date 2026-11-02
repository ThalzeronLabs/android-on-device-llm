package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.battery.BatteryStatus
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.EngineState
import com.thalzeronlabs.ondevicellm.data.ModelItem
import com.thalzeronlabs.ondevicellm.data.ModelStatus
import com.thalzeronlabs.ondevicellm.data.ModelsState
import kotlinx.coroutines.launch
import java.util.Locale

private val Suggestions = listOf(
    "Explain how you work without internet",
    "Write a two-line poem about the sea",
    "Give me 3 tips to focus better",
)

/** Everything the screen can ask for. Defaults do nothing, which keeps previews and UI tests short. */
class ChatActions(
    val onSend: (String) -> Unit = {},
    val onStop: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onOpenConversation: (Long) -> Unit = {},
    val onRename: (Long, String) -> Unit = { _, _ -> },
    val onDelete: (Long) -> Unit = {},
    val onSettings: (ChatSettings) -> Unit = {},
    val onResetSettings: () -> Unit = {},
    val onDismissBatterySaver: () -> Unit = {},
    val onDownloadModel: (String) -> Unit = {},
    val onPauseDownload: (String) -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
    val onDeleteModel: (String) -> Unit = {},
    val onImportModel: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: ChatUiState, models: ModelsState, actions: ChatActions) {
    var showInfo by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ready = models.engine is EngineState.Ready

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            HistoryDrawer(
                state = state,
                onNewChat = { actions.onNewChat(); scope.launch { drawer.close() } },
                onOpen = { actions.onOpenConversation(it); scope.launch { drawer.close() } },
                onRename = actions.onRename,
                onDelete = actions.onDelete,
            )
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Header(
                    state = state,
                    models = models,
                    onMenu = { scope.launch { drawer.open() } },
                    onModels = { showModels = true },
                    onInfo = { showInfo = true },
                    onSettings = { showSettings = true },
                    actions = actions,
                )
                AnimatedVisibility(
                    visible = state.batterySaver,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    BatteryBanner(state.battery, state.generating, actions.onStop, actions.onDismissBatterySaver)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = ready,
                        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                        label = "screen",
                    ) { isReady ->
                        if (isReady) Conversation(state, actions)
                        else ModelSetup(models, actions)
                    }
                }
            }
        }
    }

    if (showInfo) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) { ModelInfo(state, models) }
    }
    if (showModels) {
        ModalBottomSheet(
            onDismissRequest = { showModels = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { ModelsSheet(models, actions) }
    }
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            SettingsSheet(
                settings = state.settings,
                batterySaver = state.batterySaver,
                onChange = actions.onSettings,
                onReset = actions.onResetSettings,
            )
        }
    }
}

@Composable
private fun Header(
    state: ChatUiState,
    models: ModelsState,
    onMenu: () -> Unit,
    onModels: () -> Unit,
    onInfo: () -> Unit,
    onSettings: () -> Unit,
    actions: ChatActions,
) {
    val engine = models.engine
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(AppIcons.Menu, contentDescription = "Conversations") }
        Orb(size = 30.dp, active = state.generating && state.messages.lastOrNull()?.text.isNullOrEmpty())
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (engine is EngineState.Ready) state.title else "On-device LLM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onModels).testTag("model-chip"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Brand.Green))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (engine) {
                        is EngineState.Ready -> "${engine.name} · offline"
                        is EngineState.Loading -> "Loading ${engine.name}…"
                        else -> "100% offline · runs on this phone"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    AppIcons.ExpandMore,
                    contentDescription = "Choose a model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (engine is EngineState.Ready) {
            Pill(
                text = state.tokensPerSecond?.let { String.format(Locale.US, "%.1f tok/s", it) } ?: "Info",
                onClick = onInfo,
            )
            OverflowMenu(state, onModels, onSettings, actions)
        }
    }
}

@Composable
private fun OverflowMenu(state: ChatUiState, onModels: () -> Unit, onSettings: () -> Unit, actions: ChatActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val hasMessages = state.messages.isNotEmpty()
            DropdownMenuItem(text = { Text("New chat") }, onClick = { open = false; actions.onNewChat() })
            DropdownMenuItem(text = { Text("Chat settings") }, onClick = { open = false; onSettings() })
            DropdownMenuItem(text = { Text("Models") }, onClick = { open = false; onModels() })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Share as Markdown") },
                enabled = hasMessages,
                onClick = { open = false; actions.onShare() },
            )
            DropdownMenuItem(
                text = { Text("Save as .md file") },
                enabled = hasMessages,
                onClick = { open = false; actions.onExport() },
            )
        }
    }
}

/** Shown while the battery is low (or Android's Battery Saver is on). */
@Composable
fun BatteryBanner(battery: BatteryStatus?, generating: Boolean, onStop: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .testTag("battery-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.BatteryAlert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val level = battery?.percent?.takeIf { it >= 0 }?.let { "Battery at $it%" } ?: "Battery Saver is on"
            Text(
                level,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Answers use every CPU core, so they are kept short (${ChatUiState.SAVER_MAX_TOKENS} tokens).",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (generating) {
            TextButton(onClick = onStop) { Text("Stop", fontWeight = FontWeight.Bold) }
        } else {
            TextButton(onClick = onDismiss) { Text("Turn off") }
        }
    }
}

@Composable
private fun ModelSetup(models: ModelsState, actions: ChatActions) {
    val engine = models.engine
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val busy = engine is EngineState.Loading || models.anyDownloading
        Spacer(Modifier.height(12.dp))
        Orb(size = 96.dp, active = busy)
        Spacer(Modifier.height(20.dp))
        Text(
            "Private AI, on your phone",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a model and download it once. After that, every answer is generated on this device. " +
                "Nothing you type ever leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        when (engine) {
            is EngineState.Loading -> Caption("Loading ${engine.name} into memory…")
            is EngineState.Failed -> Text(engine.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            else -> {}
        }
        Spacer(Modifier.height(8.dp))
        ModelList(models, actions)
    }
}

@Composable
private fun Conversation(state: ChatUiState, actions: ChatActions) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.conversationId, state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyState(Modifier.weight(1f), onSuggestion = actions.onSend)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(state.messages) { index, message ->
                    val streaming = state.generating && index == state.messages.lastIndex
                    MessageBubble(message, streaming, onCopy = actions.onCopy, modifier = Modifier.testTag("message-$index"))
                }
            }
        }
        InputBar(
            value = input,
            onValueChange = { input = it },
            generating = state.generating,
            onSend = { actions.onSend(input); input = "" },
            onStop = actions.onStop,
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

/** One message. A long press copies its text (with a haptic tick). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: UiMessage, streaming: Boolean, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val copy = Modifier
        .semantics { contentDescription = if (message.fromUser) "Your message" else "Answer" }
        .combinedClickable(
            enabled = message.text.isNotBlank() && !streaming,
            onClickLabel = null,
            onLongClickLabel = "Copy",
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCopy(message.text)
            },
            onClick = {},
        )
    if (message.fromUser) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .then(copy)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(message.text, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp)) { Orb(size = 26.dp, active = streaming && message.text.isEmpty()) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(copy)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                if (message.text.isEmpty() && streaming) TypingDots()
                else Text(
                    message.text + if (streaming) " ▍" else "",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
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
                .clickable(enabled = enabled, onClickLabel = if (generating) "Stop" else "Send") {
                    if (generating) onStop() else onSend()
                }
                .semantics { contentDescription = if (generating) "Stop" else "Send" },
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
private fun ModelInfo(state: ChatUiState, models: ModelsState) {
    val ready = models.engine as? EngineState.Ready
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
        InfoRow("Model", ready?.name ?: "None")
        InfoRow("Size", ready?.sizeBytes?.let { "${it.humanSize()} on disk" } ?: "-")
        InfoRow("Engine", "llama.cpp on the CPU (ARM dot-product)")
        InfoRow(
            "Speed",
            state.tokensPerSecond?.let { String.format(Locale.US, "%.1f tokens per second", it) } ?: "Ask something first",
        )
        InfoRow(
            "This chat",
            String.format(
                Locale.US,
                "Temperature %.1f · up to %d tokens per answer",
                state.settings.temperature,
                state.effectiveMaxTokens,
            ),
        )
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

internal fun previewModels(ready: Boolean = true) = ModelsState(
    items = listOf(
        ModelItem("smollm2-360m", "SmolLM2 360M Instruct", 386_404_992, ModelStatus.NotDownloaded, "The fastest.", "Apache-2.0"),
        ModelItem(
            "qwen-0.5b", "Qwen2.5 0.5B Instruct", 491_400_032, ModelStatus.Installed, "The default.", "Apache-2.0",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        ),
        ModelItem(
            "qwen-1.5b", "Qwen2.5 1.5B Instruct", 1_117_320_736,
            ModelStatus.Downloading(0.42f, 469_000_000, 1_117_320_736), "The smartest here.", "Apache-2.0",
        ),
    ),
    engine = if (ready) EngineState.Ready("qwen-0.5b", "Qwen2.5 0.5B Instruct", 491_400_032) else EngineState.NoModel,
)

@Preview
@Composable
private fun ChatPreview() {
    OnDeviceLlmTheme(dark = true) {
        ChatScreen(
            state = ChatUiState(
                messages = listOf(
                    UiMessage(true, "What runs this chat?"),
                    UiMessage(false, "A 0.5B-parameter model, quantized to 4 bits, running on your phone's CPU."),
                ),
                tokensPerSecond = 18.4,
                battery = BatteryStatus(14, charging = false),
            ),
            models = previewModels(),
            actions = ChatActions(),
        )
    }
}

@Preview
@Composable
private fun SetupPreviewLight() {
    OnDeviceLlmTheme(dark = false) {
        ChatScreen(state = ChatUiState(), models = previewModels(ready = false), actions = ChatActions())
    }
}
