package com.thalzeronlabs.ondevicellm.chat

/** One bubble on screen. [id] is the database id, 0 while it is not stored yet. */
data class UiMessage(val fromUser: Boolean, val text: String, val id: Long = 0)

/** A row of the conversation list. */
data class ConversationSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val lastMessage: String?,
)

/** A stored conversation, without its messages. */
data class Conversation(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val settings: ChatSettings,
)

object ConversationTitles {
    const val NEW_CHAT = "New chat"
    const val MAX_CHARS = 40

    /** A title made from the first thing the user asked: its first line, cut at a word near [MAX_CHARS]. */
    fun fromFirstMessage(text: String): String {
        val line = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.replace(Regex("\\s+"), " ")
            ?: return NEW_CHAT
        if (line.length <= MAX_CHARS) return line
        val cut = line.take(MAX_CHARS)
        val atWord = cut.substringBeforeLast(' ').takeIf { it.length >= MAX_CHARS / 2 } ?: cut
        return atWord.trimEnd(' ', ',', '.', ';', ':') + "…"
    }

    /** What a rename is allowed to store. */
    fun clean(title: String): String = title.trim().replace(Regex("\\s+"), " ").take(80).ifEmpty { NEW_CHAT }
}
