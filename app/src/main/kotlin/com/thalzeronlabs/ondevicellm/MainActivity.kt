package com.thalzeronlabs.ondevicellm

import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thalzeronlabs.ondevicellm.chat.MarkdownExport
import com.thalzeronlabs.ondevicellm.ui.ChatActions
import com.thalzeronlabs.ondevicellm.ui.ChatScreen
import com.thalzeronlabs.ondevicellm.ui.OnDeviceLlmTheme
import com.thalzeronlabs.ondevicellm.ui.copyToClipboard
import com.thalzeronlabs.ondevicellm.ui.shareMarkdownIntent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OnDeviceLlmApp).container
        // First launch on Wi-Fi: the default model downloads by itself. On mobile data, the user picks first.
        container.models.start(autoDownload = isOnUnmeteredNetwork())
        val factory = viewModelFactory {
            initializer { ChatViewModel(container.conversations, container.models, container.engine, container.battery) }
        }
        setContent {
            OnDeviceLlmTheme {
                val vm: ChatViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val models by vm.models.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) contentResolver.openInputStream(uri)?.let { vm.importModel(it, displayName(uri)) }
                }
                val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
                    if (uri != null) saveText(uri, vm.exportMarkdown())
                }
                ChatScreen(
                    state = state,
                    models = models,
                    actions = ChatActions(
                        onSend = vm::send,
                        onStop = vm::stop,
                        onNewChat = vm::newChat,
                        onOpenConversation = vm::openConversation,
                        onRename = vm::rename,
                        onDelete = vm::delete,
                        onSettings = vm::updateSettings,
                        onResetSettings = vm::resetSettings,
                        onDismissBatterySaver = vm::dismissBatterySaver,
                        onDownloadModel = vm::downloadModel,
                        onPauseDownload = vm::pauseDownload,
                        onSelectModel = vm::selectModel,
                        onDeleteModel = vm::deleteModel,
                        onImportModel = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                        onShare = { startActivity(shareMarkdownIntent(state.title, vm.exportMarkdown())) },
                        onExport = { saver.launch(MarkdownExport.fileName(state.title)) },
                        onCopy = { copyToClipboard(this, it) },
                    ),
                )
            }
        }
    }

    private fun saveText(uri: Uri, text: String) {
        val saved = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
        }.isSuccess
        Toast.makeText(this, if (saved) "Saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.activeNetwork != null && !connectivity.isActiveNetworkMetered
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
