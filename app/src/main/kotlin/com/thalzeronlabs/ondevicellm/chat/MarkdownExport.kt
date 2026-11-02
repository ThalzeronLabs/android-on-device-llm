package com.thalzeronlabs.ondevicellm.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Renders a conversation as a Markdown document, for sharing or saving to a file. */
object MarkdownExport {

    fun render(
        title: String,
        messages: List<UiMessage>,
        modelName: String?,
        exportedAt: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = buildString {
        append("# ").append(title.trim().ifEmpty { ConversationTitles.NEW_CHAT }).append("\n\n")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { this.timeZone = timeZone }
            .format(Date(exportedAt))
        append("_Exported from On-device LLM on ").append(date)
        if (!modelName.isNullOrBlank()) append(" · model: ").append(modelName)
        append(" · generated on the phone, offline._\n")
        messages.filter { it.text.isNotBlank() }.forEach { message ->
            append("\n**").append(if (message.fromUser) "You" else "Assistant").append("**\n\n")
            append(message.text.trim()).append("\n")
        }
    }

    /** A safe file name for [title], e.g. `Two-line poem about the sea.md`. */
    fun fileName(title: String): String {
        val base = title.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").replace(Regex("\\s+"), " ")
            .removeSuffix("…").trim().take(60).trim()
        return (base.ifEmpty { "conversation" }) + ".md"
    }
}
