package com.thalzeronlabs.ondevicellm.data

import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.llama.LlamaModel
import com.thalzeronlabs.llama.LlamaSession
import com.thalzeronlabs.llama.SessionParams
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Sampling settings for one answer. */
data class GenerationOptions(val temperature: Float, val maxTokens: Int)

/** What the app needs from a language model. Faked in unit tests. */
interface ChatEngine {
    /** Loads [file], replacing (and freeing) the model loaded before. */
    suspend fun load(file: File)
    fun reply(history: List<ChatMessage>, options: GenerationOptions): Flow<String>
    /** Speed of the last answer, or null before the first one. */
    val lastTokensPerSecond: Double?
    fun close()
}

/** The real engine: llama.cpp running on the phone's CPU through llama-kotlin. */
class LlamaChatEngine(
    private val params: SessionParams = SessionParams(contextSize = CONTEXT_TOKENS),
) : ChatEngine {

    private var model: LlamaModel? = null
    private var session: LlamaSession? = null

    override suspend fun load(file: File) {
        close()
        val loaded = LlamaModel.load(file)
        model = loaded
        session = loaded.newSession(params)
    }

    /**
     * One session per model: the temperature is passed per answer, so a conversation with its own temperature
     * keeps the session and its KV cache (only the new part of the conversation is processed).
     */
    override fun reply(history: List<ChatMessage>, options: GenerationOptions): Flow<String> {
        val current = checkNotNull(session) { "Load a model first." }
        return current.chat(history, maxTokens = options.maxTokens, temperature = options.temperature)
    }

    override val lastTokensPerSecond: Double?
        get() = session?.lastStats?.tokensPerSecond

    override fun close() {
        model?.close() // also closes the session
        model = null
        session = null
    }

    companion object {
        /** Context window (prompt + history + answer) of every session. */
        const val CONTEXT_TOKENS = 4096
    }
}
