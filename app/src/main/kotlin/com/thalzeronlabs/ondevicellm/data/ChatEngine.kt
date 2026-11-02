package com.thalzeronlabs.ondevicellm.data

import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.llama.LlamaModel
import com.thalzeronlabs.llama.LlamaSession
import com.thalzeronlabs.llama.SessionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private val baseParams: SessionParams = SessionParams(contextSize = CONTEXT_TOKENS),
) : ChatEngine {

    private var model: LlamaModel? = null
    private var session: LlamaSession? = null

    override suspend fun load(file: File) {
        close()
        model = LlamaModel.load(file)
    }

    override fun reply(history: List<ChatMessage>, options: GenerationOptions): Flow<String> = flow {
        emitAll(sessionFor(options.temperature).chat(history, maxTokens = options.maxTokens))
    }.flowOn(Dispatchers.Default)

    /**
     * Temperature is fixed when a llama-kotlin session is created, so a conversation with another temperature
     * gets a fresh session. [LlamaSession.chat] re-sends the whole history anyway, so nothing is lost.
     */
    @Synchronized
    private fun sessionFor(temperature: Float): LlamaSession {
        val loaded = checkNotNull(model) { "Load a model first." }
        session?.let { current ->
            if (currentTemperature == temperature) return current
            current.close()
        }
        return loaded.newSession(baseParams.copy(temperature = temperature)).also {
            session = it
            currentTemperature = temperature
        }
    }

    private var currentTemperature: Float? = null

    override val lastTokensPerSecond: Double?
        get() = session?.lastStats?.tokensPerSecond

    @Synchronized
    override fun close() {
        model?.close() // also closes the session
        model = null
        session = null
        currentTemperature = null
    }

    companion object {
        /** Context window (prompt + history + answer) of every session. */
        const val CONTEXT_TOKENS = 4096
    }
}
