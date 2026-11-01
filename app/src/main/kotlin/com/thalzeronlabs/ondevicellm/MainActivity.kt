package com.thalzeronlabs.ondevicellm

import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.thalzeronlabs.ondevicellm.data.HttpModelSource
import com.thalzeronlabs.ondevicellm.data.LlamaChatEngine
import com.thalzeronlabs.ondevicellm.ui.ChatScreen
import com.thalzeronlabs.ondevicellm.ui.OnDeviceLlmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    engine = LlamaChatEngine(),
                    source = HttpModelSource(applicationContext.filesDir),
                    // First launch on Wi-Fi: the model downloads by itself. On mobile data, ask first (~491 MB).
                    autoDownload = isOnUnmeteredNetwork(),
                )
            }
        }
        setContent {
            OnDeviceLlmTheme {
                val vm: ChatViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) contentResolver.openInputStream(uri)?.let { vm.import(it, displayName(uri)) }
                }
                ChatScreen(
                    state = state,
                    onDownload = vm::download,
                    onPauseDownload = vm::pauseDownload,
                    onPickFile = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                    onSend = vm::send,
                    onStop = vm::stop,
                    onClear = vm::clear,
                )
            }
        }
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
