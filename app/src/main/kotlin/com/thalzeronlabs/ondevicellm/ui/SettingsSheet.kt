package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import java.util.Locale
import kotlin.math.roundToInt

/** System prompt, temperature and answer length of the open conversation. */
@Composable
fun SettingsSheet(
    settings: ChatSettings,
    batterySaver: Boolean,
    onChange: (ChatSettings) -> Unit,
    onReset: () -> Unit,
) {
    // The text field edits a local copy (saved trimmed on every change), so spaces can still be typed at the end.
    var prompt by remember { mutableStateOf(settings.systemPrompt) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Chat settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { prompt = ChatSettings.DEFAULT_SYSTEM_PROMPT; onReset() },
                enabled = !settings.isDefault,
            ) { Text("Reset") }
        }
        Text(
            "Saved with this conversation only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it.take(ChatSettings.MAX_SYSTEM_PROMPT_CHARS)
                onChange(settings.copy(systemPrompt = prompt))
            },
            label = { Text("System prompt") },
            supportingText = { Text("How the assistant should behave, e.g. \"Answer in Portuguese\".") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("system-prompt"),
        )
        Spacer(Modifier.height(20.dp))

        SliderRow(
            label = "Temperature",
            value = String.format(Locale.US, "%.1f", settings.temperature),
            hint = "Lower is focused and repeatable, higher is more varied.",
        )
        Slider(
            value = settings.temperature,
            onValueChange = { onChange(settings.copy(temperature = (it * 10).roundToInt() / 10f)) },
            valueRange = ChatSettings.TEMPERATURE_RANGE,
            steps = 14,
            modifier = Modifier.testTag("temperature"),
        )
        Spacer(Modifier.height(12.dp))

        SliderRow(
            label = "Maximum answer length",
            value = "${settings.maxTokens} tokens",
            hint = if (batterySaver) "Capped at ${ChatUiState.SAVER_MAX_TOKENS} while the battery is low."
            else "About ${settings.maxTokens * 3 / 4} words. Longer answers take longer.",
        )
        Slider(
            value = settings.maxTokens.toFloat(),
            onValueChange = { onChange(settings.copy(maxTokens = (it / 64).roundToInt() * 64)) },
            valueRange = ChatSettings.MAX_TOKENS_RANGE.first.toFloat()..ChatSettings.MAX_TOKENS_RANGE.last.toFloat(),
            modifier = Modifier.testTag("max-tokens"),
        )
    }
}

@Composable
private fun SliderRow(label: String, value: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
    Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
