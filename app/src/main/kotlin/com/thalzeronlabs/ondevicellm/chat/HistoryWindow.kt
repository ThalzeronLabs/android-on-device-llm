package com.thalzeronlabs.ondevicellm.chat

import com.thalzeronlabs.llama.ChatMessage

/**
 * Keeps a long conversation inside the model's context window.
 *
 * Only the most recent turns that fit are sent: the context holds the system prompt, the history and the answer,
 * so the history gets what is left after the answer's [ChatSettings.maxTokens]. Tokens are estimated from
 * characters on the safe side ([CHARS_PER_TOKEN]); the full conversation stays on screen and in the database.
 */
object HistoryWindow {

    /** Conservative: English averages about 4 characters per token, other languages fewer. */
    const val CHARS_PER_TOKEN = 3

    /** Room left for the chat template's own tokens and the estimate's error. */
    const val MARGIN_TOKENS = 256

    fun build(systemPrompt: String, turns: List<UiMessage>, maxTokens: Int, contextTokens: Int): List<ChatMessage> {
        val budgetChars = (contextTokens - maxTokens - MARGIN_TOKENS).coerceAtLeast(256) * CHARS_PER_TOKEN
        val system = systemPrompt.trim()
        var left = budgetChars - system.length
        val kept = ArrayDeque<UiMessage>()
        for (turn in turns.asReversed()) {
            if (turn.text.length > left && kept.isNotEmpty()) break
            kept.addFirst(turn)
            left -= turn.text.length
        }
        // The newest message alone may be longer than the budget: keep its end, where the question usually is.
        if (left < 0 && kept.size == 1) {
            val only = kept.removeFirst()
            kept.addFirst(only.copy(text = only.text.takeLast((budgetChars - system.length).coerceAtLeast(256))))
        }
        // Chat templates expect the history to start with the user.
        while (kept.isNotEmpty() && !kept.first().fromUser) kept.removeFirst()
        val history = kept.map { if (it.fromUser) ChatMessage.user(it.text) else ChatMessage.assistant(it.text) }
        return if (system.isEmpty()) history else listOf(ChatMessage.system(system)) + history
    }
}
