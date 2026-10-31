package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.ModelState
import com.thalzeronlabs.ondevicellm.UiMessage
import java.util.Locale

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("On-device LLM")
                        Text(
                            "100% offline · llama.cpp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    state.tokensPerSecond?.let {
                        AssistChip(onClick = {}, label = { Text(String.format(Locale.US, "%.1f tok/s", it)) })
                    }
                    if (state.messages.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (val model = state.model) {
                ModelState.Ready -> Conversation(state, onSend, onStop)
                else -> ModelSetup(model, state.modelName, state.modelSizeMb, onDownload, onPickFile)
            }
        }
    }
}

@Composable
private fun ModelSetup(
    model: ModelState,
    name: String,
    sizeMb: Long,
    onDownload: () -> Unit,
    onPickFile: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A language model on your phone", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Download a small model once ($name, about $sizeMb MB). After that, every answer is " +
                        "generated on this device — nothing you type leaves the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (model) {
                    is ModelState.Downloading -> {
                        LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Downloading… ${(model.progress * 100).toInt()}%")
                    }
                    ModelState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text("Loading the model into memory…")
                    }
                    else -> {
                        if (model is ModelState.Failed) {
                            Text(model.message, color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download the model") }
                        OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                            Text("I already have a .gguf file")
                        }
                    }
                }
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
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Ask anything. Try turning on airplane mode first.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.messages) { index, message ->
                    val streaming = state.generating && index == state.messages.lastIndex
                    Bubble(message, streaming)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 5,
            )
            if (state.generating) {
                FilledTonalButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(
                    onClick = { onSend(input); input = "" },
                    enabled = input.isNotBlank(),
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun Bubble(message: UiMessage, streaming: Boolean) {
    val mine = message.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.text.ifEmpty { "…" })
                AnimatedVisibility(streaming) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChatPreview() {
    OnDeviceLlmTheme(dark = true) {
        ChatScreen(
            state = ChatUiState(
                model = ModelState.Ready,
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
