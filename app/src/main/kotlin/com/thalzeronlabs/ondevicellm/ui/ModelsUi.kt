package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.thalzeronlabs.ondevicellm.data.EngineState
import com.thalzeronlabs.ondevicellm.data.ModelItem
import com.thalzeronlabs.ondevicellm.data.ModelStatus
import com.thalzeronlabs.ondevicellm.data.ModelsState

/** The model picker as a bottom sheet, reachable from the header at any time. */
@Composable
fun ModelsSheet(models: ModelsState, actions: ChatActions) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
    ) {
        Text("Models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "All Apache-2.0, downloaded from their authors' Hugging Face pages and checked against their SHA-256. " +
                "Switching takes a few seconds and keeps your conversation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ModelList(models, actions)
    }
}

/** Every catalog model (and the imported one), each with its own download and actions. */
@Composable
fun ModelList(models: ModelsState, actions: ChatActions) {
    var confirmDelete by remember { mutableStateOf<ModelItem?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        models.items.forEach { item ->
            ModelCard(
                item = item,
                active = models.activeId == item.id,
                loading = (models.engine as? EngineState.Loading)?.id == item.id,
                actions = actions,
                onDelete = { confirmDelete = item },
            )
        }
        SecondaryButton("Import a .gguf file", actions.onImportModel)
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This frees ${item.sizeBytes.humanSize()}. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = { actions.onDeleteModel(item.id); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ModelCard(item: ModelItem, active: Boolean, loading: Boolean, actions: ChatActions, onDelete: () -> Unit) {
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(16.dp)
            .testTag("model-${item.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                Text(
                    if (loading) "Loading…" else "In use",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption(item.sizeBytes.humanSize(), textAlign = TextAlign.Start)
            if (item.license.isNotEmpty()) {
                Caption(" · ", textAlign = TextAlign.Start)
                LinkText(item.license) { if (item.licenseUrl.isNotEmpty()) uri.openUri(item.licenseUrl) }
            }
            if (item.pageUrl.isNotEmpty()) {
                Caption(" · ", textAlign = TextAlign.Start)
                LinkText("Hugging Face") { uri.openUri(item.pageUrl) }
            }
        }
        if (item.description.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        ModelActions(item, active, actions, onDelete)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
    )
}

@Composable
private fun ModelActions(item: ModelItem, active: Boolean, actions: ChatActions, onDelete: () -> Unit) {
    val half = Modifier.height(44.dp)
    when (val status = item.status) {
        ModelStatus.NotDownloaded -> PrimaryButton("Download", { actions.onDownloadModel(item.id) }, half.fillMaxWidth())
        is ModelStatus.Downloading -> {
            Progress(status.progress)
            Caption(
                (if (status.resumed) "Resuming · " else "") +
                    "${status.bytes.mb()} of ${status.total.mb()} MB · ${(status.progress * 100).toInt()}%",
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Pause", { actions.onPauseDownload(item.id) }, half.fillMaxWidth())
        }
        is ModelStatus.Paused -> {
            Progress(status.bytes.toFloat() / status.total.coerceAtLeast(1))
            Caption("Paused at ${status.bytes.mb()} of ${status.total.mb()} MB", textAlign = TextAlign.Start)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Resume", { actions.onDownloadModel(item.id) }, half.weight(1f))
                SecondaryButton("Delete", onDelete, half.weight(1f))
            }
        }
        is ModelStatus.Verifying -> {
            Progress(status.progress)
            Caption("Checking the file's SHA-256…", textAlign = TextAlign.Start)
        }
        ModelStatus.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!active) PrimaryButton("Use", { actions.onSelectModel(item.id) }, half.weight(1f))
            SecondaryButton("Delete", onDelete, half.weight(1f))
        }
        is ModelStatus.Failed -> {
            Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Try again", { actions.onDownloadModel(item.id) }, half.weight(1f))
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
