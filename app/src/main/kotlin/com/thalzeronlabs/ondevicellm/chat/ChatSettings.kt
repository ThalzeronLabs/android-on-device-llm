package com.thalzeronlabs.ondevicellm.chat

/** Per-conversation settings, stored with each conversation. */
data class ChatSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    /** 0 always picks the most likely word; higher is more varied. */
    val temperature: Float = DEFAULT_TEMPERATURE,
    /** Upper bound on the length of one answer, in tokens (roughly ¾ of a word each). */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    /** The same settings with every value inside the range the engine and the context window can take. */
    fun sanitized(): ChatSettings = copy(
        systemPrompt = systemPrompt.trim().take(MAX_SYSTEM_PROMPT_CHARS),
        temperature = if (temperature.isNaN()) DEFAULT_TEMPERATURE
        else temperature.coerceIn(TEMPERATURE_RANGE.start, TEMPERATURE_RANGE.endInclusive),
        maxTokens = maxTokens.coerceIn(MAX_TOKENS_RANGE),
    )

    val isDefault: Boolean get() = this == ChatSettings()

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant running entirely on the user's phone, without internet. Be concise."
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 512
        val TEMPERATURE_RANGE = 0f..1.5f
        val MAX_TOKENS_RANGE = 64..2048
        const val MAX_SYSTEM_PROMPT_CHARS = 2_000
    }
}
