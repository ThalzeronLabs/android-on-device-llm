package com.thalzeronlabs.ondevicellm.data

import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.llama.LlamaModel
import com.thalzeronlabs.llama.LlamaSession
import com.thalzeronlabs.llama.SessionParams
import kotlinx.coroutines.flow.Flow
import java.io.File

/** What the screen needs from a language model. Faked in unit tests. */
interface ChatEngine {
    suspend fun load(file: File)
    fun reply(history: List<ChatMessage>): Flow<String>
    /** Speed of the last answer, or null before the first one. */
    val lastTokensPerSecond: Double?
    fun close()
}

/** The real engine: llama.cpp running on the phone's CPU through llama-kotlin. */
class LlamaChatEngine(
    private val params: SessionParams = SessionParams(contextSize = 4096, temperature = 0.7f),
) : ChatEngine {

    private var model: LlamaModel? = null
    private var session: LlamaSession? = null

    override suspend fun load(file: File) {
        close()
        val loaded = LlamaModel.load(file)
        model = loaded
        session = loaded.newSession(params)
    }

    override fun reply(history: List<ChatMessage>): Flow<String> {
        val current = checkNotNull(session) { "Load a model first." }
        return current.chat(history, maxTokens = 1024)
    }

    override val lastTokensPerSecond: Double?
        get() = session?.lastStats?.tokensPerSecond

    override fun close() {
        model?.close() // also closes the session
        model = null
        session = null
    }
}
