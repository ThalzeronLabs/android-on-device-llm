package com.thalzeronlabs.ondevicellm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thalzeronlabs.ondevicellm.battery.BatteryStatus
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.ConversationSummary
import com.thalzeronlabs.ondevicellm.chat.ConversationTitles
import com.thalzeronlabs.ondevicellm.chat.HistoryWindow
import com.thalzeronlabs.ondevicellm.chat.MarkdownExport
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.ChatEngine
import com.thalzeronlabs.ondevicellm.data.ConversationRepository
import com.thalzeronlabs.ondevicellm.data.EngineState
import com.thalzeronlabs.ondevicellm.data.GenerationOptions
import com.thalzeronlabs.ondevicellm.data.LlamaChatEngine
import com.thalzeronlabs.ondevicellm.data.ModelManager
import com.thalzeronlabs.ondevicellm.data.ModelsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class ChatUiState(
    /** The open conversation, or null for a new one that is saved on its first message. */
    val conversationId: Long? = null,
    val messages: List<UiMessage> = emptyList(),
    val settings: ChatSettings = ChatSettings(),
    val generating: Boolean = false,
    val tokensPerSecond: Double? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val battery: BatteryStatus? = null,
    /** The user turned the battery saver off until the battery recovers. */
    val batterySaverDismissed: Boolean = false,
) {
    val title: String
        get() = conversations.firstOrNull { it.id == conversationId }?.title ?: ConversationTitles.NEW_CHAT

    /** Low battery (or Android's Battery Saver): warn, offer Stop and keep answers short. */
    val batterySaver: Boolean get() = battery?.isLow == true && !batterySaverDismissed

    /** The answer length actually used: capped while the battery saver is on. */
    val effectiveMaxTokens: Int
        get() = if (batterySaver) minOf(settings.maxTokens, SAVER_MAX_TOKENS) else settings.maxTokens

    companion object {
        const val SAVER_MAX_TOKENS = 256
    }
}

class ChatViewModel(
    private val repository: ConversationRepository,
    private val modelManager: ModelManager,
    private val engine: ChatEngine,
    battery: Flow<BatteryStatus> = emptyFlow(),
    private val contextTokens: Int = LlamaChatEngine.CONTEXT_TOKENS,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** The model catalog and the loaded model. */
    val models: StateFlow<ModelsState> = modelManager.state

    private var work: Job? = null

    init {
        repository.observeConversations()
            .onEach { list -> _state.update { it.copy(conversations = list) } }
            .launchIn(viewModelScope)
        battery
            .onEach { status ->
                // Dismissing lasts until the battery is fine again; the next low battery warns again.
                _state.update { it.copy(battery = status, batterySaverDismissed = it.batterySaverDismissed && status.isLow) }
            }
            .launchIn(viewModelScope)
    }

    // region Chat

    fun send(text: String) {
        val prompt = text.trim()
        val current = _state.value
        if (prompt.isEmpty() || current.generating || models.value.engine !is EngineState.Ready) return

        val turns = current.messages + UiMessage(true, prompt)
        _state.update { it.copy(messages = turns + UiMessage(false, ""), generating = true) }
        val settings = current.settings
        val maxTokens = current.effectiveMaxTokens
        work = viewModelScope.launch {
            var conversationId: Long? = null
            val generated = StringBuilder()
            try {
                conversationId = current.conversationId ?: repository.create(settings).also { id ->
                    _state.update { it.copy(conversationId = id) }
                }
                repository.addMessage(conversationId, fromUser = true, text = prompt)
                val history = HistoryWindow.build(settings.systemPrompt, turns, maxTokens, contextTokens)
                engine.reply(history, GenerationOptions(settings.temperature, maxTokens)).collect { piece ->
                    generated.append(piece)
                    appendToAnswer(piece)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendToAnswer("\n[error: ${e.message}]")
            } finally {
                // Stop keeps what was written: store it even though this coroutine is being cancelled.
                // An error note is shown but not stored.
                withContext(NonCancellable) {
                    val answer = generated.toString()
                    val shown = _state.value.messages.last().text
                    val id = conversationId
                    val storedId = if (id != null && answer.isNotBlank()) {
                        runCatching { repository.addMessage(id, fromUser = false, text = answer) }.getOrDefault(0)
                    } else {
                        0
                    }
                    _state.update { s ->
                        val last = s.messages.last()
                        s.copy(
                            messages = if (shown.isBlank()) s.messages.dropLast(1) else s.messages.dropLast(1) + last.copy(id = storedId),
                            generating = false,
                            tokensPerSecond = engine.lastTokensPerSecond,
                        )
                    }
                }
            }
        }
    }

    /** Stops the answer being written; what was already generated stays on screen and is saved. */
    fun stop() {
        if (_state.value.generating) work?.cancel()
    }

    private fun appendToAnswer(piece: String) {
        _state.update { s ->
            val last = s.messages.last()
            s.copy(messages = s.messages.dropLast(1) + last.copy(text = last.text + piece))
        }
    }

    /** The open conversation as a Markdown document. */
    fun exportMarkdown(now: Long = System.currentTimeMillis()): String {
        val s = _state.value
        return MarkdownExport.render(s.title, s.messages, (models.value.engine as? EngineState.Ready)?.name, now)
    }

    // endregion

    // region History

    /** Starts an empty conversation with the default settings. It is saved when the first message is sent. */
    fun newChat() = afterGeneration {
        _state.update {
            it.copy(conversationId = null, messages = emptyList(), settings = ChatSettings(), tokensPerSecond = null)
        }
    }

    /** Opens a saved conversation to read it or carry on. */
    fun openConversation(id: Long) = afterGeneration {
        val conversation = repository.conversation(id) ?: return@afterGeneration
        val messages = repository.messages(id)
        _state.update {
            it.copy(conversationId = id, messages = messages, settings = conversation.settings, tokensPerSecond = null)
        }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun delete(id: Long) {
        if (id == _state.value.conversationId) {
            afterGeneration {
                repository.delete(id)
                _state.update {
                    it.copy(conversationId = null, messages = emptyList(), settings = ChatSettings(), tokensPerSecond = null)
                }
            }
        } else {
            viewModelScope.launch { repository.delete(id) }
        }
    }

    // endregion

    // region Settings

    /** Changes the open conversation's settings; a new conversation keeps them when it is saved. */
    fun updateSettings(settings: ChatSettings) {
        val clean = settings.sanitized()
        _state.update { it.copy(settings = clean) }
        val id = _state.value.conversationId ?: return
        viewModelScope.launch { repository.updateSettings(id, clean) }
    }

    fun resetSettings() = updateSettings(ChatSettings())

    /** Turns the battery saver off until the battery is no longer low. */
    fun dismissBatterySaver() = _state.update { it.copy(batterySaverDismissed = true) }

    // endregion

    // region Models

    /** Switches the model without restarting the app; a running answer is stopped first. */
    fun selectModel(id: String) = afterGeneration { modelManager.select(id) }

    fun downloadModel(id: String) = modelManager.download(id)

    fun pauseDownload(id: String) = modelManager.pause(id)

    fun deleteModel(id: String) = afterGeneration { modelManager.delete(id) }

    fun importModel(input: InputStream, name: String?) = afterGeneration { modelManager.import(input, name) }

    // endregion

    /** Stops a running answer, waits until it is saved, then runs [block]. */
    private fun afterGeneration(block: suspend () -> Unit) {
        viewModelScope.launch {
            work?.cancelAndJoin()
            block()
        }
    }
}
