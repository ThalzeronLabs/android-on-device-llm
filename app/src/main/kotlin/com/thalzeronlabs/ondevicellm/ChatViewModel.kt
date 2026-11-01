package com.thalzeronlabs.ondevicellm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.ondevicellm.data.ChatEngine
import com.thalzeronlabs.ondevicellm.data.DownloadProgress
import com.thalzeronlabs.ondevicellm.data.ModelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val progress: Float, val bytes: Long = 0, val total: Long = 0, val resumed: Boolean = false) :
        ModelState
    /** An unfinished download is on disk; [download] continues it. */
    data class Paused(val bytes: Long, val total: Long) : ModelState
    /** The file is downloaded and its SHA-256 is being checked. */
    data class Verifying(val progress: Float) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

data class UiMessage(val fromUser: Boolean, val text: String)

data class ChatUiState(
    val model: ModelState = ModelState.Missing,
    val modelName: String = "",
    val modelSizeMb: Long = 0,
    val messages: List<UiMessage> = emptyList(),
    val generating: Boolean = false,
    val tokensPerSecond: Double? = null,
)

class ChatViewModel(
    private val engine: ChatEngine,
    private val source: ModelSource,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    /** Start (or resume) the model download right away when it is missing, e.g. when on Wi-Fi. */
    autoDownload: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(modelName = source.displayName, modelSizeMb = source.approximateBytes / 1_000_000)
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var work: Job? = null

    init {
        when {
            source.isReady() -> loadModel()
            autoDownload -> download()
            source.partialBytes() > 0 -> setModel(ModelState.Paused(source.partialBytes(), source.approximateBytes))
        }
    }

    /** Downloads the default model, continuing an unfinished download if there is one. */
    fun download() {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            try {
                source.download().collect { p ->
                    setModel(
                        when (p) {
                            is DownloadProgress.Transferring ->
                                ModelState.Downloading(p.fraction, p.bytes, p.total, resumed = p.resumedFrom > 0)
                            is DownloadProgress.Verifying -> ModelState.Verifying(p.fraction)
                        }
                    )
                }
                loadModelNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setModel(ModelState.Failed("Download failed: ${e.message}"))
            }
        }
    }

    /** Stops the download; the bytes already received are kept for [download] to continue from. */
    fun pauseDownload() {
        if (_state.value.model !is ModelState.Downloading) return
        work?.cancel()
        setModel(ModelState.Paused(source.partialBytes(), source.approximateBytes))
    }

    fun import(input: InputStream, name: String? = null) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            try {
                setModel(ModelState.Loading)
                source.import(input, name)
                loadModelNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setModel(ModelState.Failed(e.message ?: "Could not import the file."))
            }
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.generating || _state.value.model != ModelState.Ready) return

        _state.update {
            it.copy(
                messages = it.messages + UiMessage(true, prompt) + UiMessage(false, ""),
                generating = true,
            )
        }
        val history = buildHistory()
        work = viewModelScope.launch {
            try {
                engine.reply(history).collect { piece -> appendToAnswer(piece) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendToAnswer("\n[error: ${e.message}]")
            } finally {
                _state.update { it.copy(generating = false, tokensPerSecond = engine.lastTokensPerSecond) }
            }
        }
    }

    /** Stops the answer being written; what was already generated stays on screen. */
    fun stop() {
        if (_state.value.generating) work?.cancel()
    }

    fun clear() {
        stop()
        _state.update { it.copy(messages = emptyList(), tokensPerSecond = null) }
    }

    private fun loadModel() {
        work = viewModelScope.launch { loadModelNow() }
    }

    private suspend fun loadModelNow() {
        setModel(ModelState.Loading)
        try {
            engine.load(source.modelFile)
            _state.update {
                it.copy(
                    model = ModelState.Ready,
                    modelName = source.displayName,
                    modelSizeMb = source.modelFile.length() / 1_000_000,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setModel(ModelState.Failed("Could not load the model: ${e.message}"))
        }
    }

    private fun buildHistory(): List<ChatMessage> {
        val turns = _state.value.messages.dropLast(1) // the empty answer placeholder
        return listOf(ChatMessage.system(systemPrompt)) + turns.map {
            if (it.fromUser) ChatMessage.user(it.text) else ChatMessage.assistant(it.text)
        }
    }

    private fun appendToAnswer(piece: String) {
        _state.update { s ->
            val last = s.messages.last()
            s.copy(messages = s.messages.dropLast(1) + last.copy(text = last.text + piece))
        }
    }

    private fun setModel(model: ModelState) = _state.update { it.copy(model = model) }

    override fun onCleared() {
        engine.close()
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant running entirely on the user's phone, without internet. Be concise."
    }
}
