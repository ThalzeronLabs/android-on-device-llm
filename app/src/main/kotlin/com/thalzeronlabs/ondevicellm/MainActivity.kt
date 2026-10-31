package com.thalzeronlabs.ondevicellm

import android.os.Bundle
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
            initializer { ChatViewModel(LlamaChatEngine(), HttpModelSource(applicationContext.filesDir)) }
        }
        setContent {
            OnDeviceLlmTheme {
                val vm: ChatViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { contentResolver.openInputStream(it) }?.let(vm::import)
                }
                ChatScreen(
                    state = state,
                    onDownload = vm::download,
                    onPickFile = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                    onSend = vm::send,
                    onStop = vm::stop,
                    onClear = vm::clear,
                )
            }
        }
    }
}
