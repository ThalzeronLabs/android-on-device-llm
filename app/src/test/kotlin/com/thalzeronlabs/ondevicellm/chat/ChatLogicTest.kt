package com.thalzeronlabs.ondevicellm.chat

import com.thalzeronlabs.llama.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ChatSettingsTest {

    @Test
    fun `defaults are sensible`() {
        val s = ChatSettings()
        assertTrue(s.systemPrompt.contains("without internet"))
        assertEquals(0.7f, s.temperature)
        assertEquals(512, s.maxTokens)
        assertTrue(s.isDefault)
    }

    @Test
    fun `sanitized keeps values in range`() {
        assertEquals(ChatSettings("x", 0f, 64), ChatSettings(" x ", -1f, 0).sanitized())
        assertEquals(ChatSettings("", 1.5f, 2048), ChatSettings("", 7f, 1_000_000).sanitized())
        assertEquals(ChatSettings.DEFAULT_TEMPERATURE, ChatSettings(temperature = Float.NaN).sanitized().temperature)
        assertEquals(ChatSettings.MAX_SYSTEM_PROMPT_CHARS, ChatSettings("a".repeat(5_000)).sanitized().systemPrompt.length)
        assertEquals(ChatSettings(), ChatSettings().sanitized())
    }
}

class ConversationTitlesTest {

    @Test
    fun `a short first message is the title as is`() {
        assertEquals("Hello there", ConversationTitles.fromFirstMessage("  Hello   there \n"))
    }

    @Test
    fun `a long message is cut at a word`() {
        val title = ConversationTitles.fromFirstMessage("Explain how a quantized language model runs on a phone CPU")
        assertEquals("Explain how a quantized language model…", title)
        assertTrue(title.length <= ConversationTitles.MAX_CHARS + 1)
    }

    @Test
    fun `only the first non-empty line counts`() {
        assertEquals("Fix this code", ConversationTitles.fromFirstMessage("\n\nFix this code\nfun main() {}"))
        assertEquals(ConversationTitles.NEW_CHAT, ConversationTitles.fromFirstMessage("   "))
    }
}

class HistoryWindowTest {

    private fun turns(vararg texts: String) = texts.mapIndexed { i, t -> UiMessage(fromUser = i % 2 == 0, text = t) }

    @Test
    fun `a short conversation is sent whole after the system prompt`() {
        val history = HistoryWindow.build("SYS", turns("hi", "hello", "how are you?"), maxTokens = 512, contextTokens = 4096)
        assertEquals(
            listOf(ChatMessage.system("SYS"), ChatMessage.user("hi"), ChatMessage.assistant("hello"), ChatMessage.user("how are you?")),
            history,
        )
    }

    @Test
    fun `a long conversation keeps the newest turns that fit and starts with the user`() {
        val long = "x".repeat(3_000)
        val history = HistoryWindow.build("SYS", turns(long, long, long, long, "last question"), maxTokens = 512, contextTokens = 4096)
        // Budget: (4096 - 512 - 256) * 3 = 9984 chars minus the prompt: three 3000-char turns do not fit with the question.
        assertEquals(ChatMessage.system("SYS"), history.first())
        assertEquals(ChatMessage.user("last question"), history.last())
        assertEquals(ChatMessage.Role.USER, history[1].role)
        assertTrue(history.sumOf { it.content.length } <= (4096 - 512 - HistoryWindow.MARGIN_TOKENS) * HistoryWindow.CHARS_PER_TOKEN)
        assertTrue(history.size < 6)
    }

    @Test
    fun `a single huge message keeps its end`() {
        val huge = "a".repeat(50_000) + "THE QUESTION"
        val history = HistoryWindow.build("", turns(huge), maxTokens = 512, contextTokens = 4096)
        assertEquals(1, history.size)
        assertTrue(history.single().content.endsWith("THE QUESTION"))
        assertTrue(history.single().content.length < huge.length)
    }

    @Test
    fun `a bigger answer leaves less room for history`() {
        val many = turns(*Array(41) { "message number $it ".repeat(20) })
        val short = HistoryWindow.build("", many, maxTokens = 64, contextTokens = 4096)
        val long = HistoryWindow.build("", many, maxTokens = 2048, contextTokens = 4096)
        assertTrue(long.size < short.size)
    }
}

class MarkdownExportTest {

    @Test
    fun `renders title, date, model and every message`() {
        val md = MarkdownExport.render(
            title = "Sea poem",
            messages = listOf(UiMessage(true, "Write a poem"), UiMessage(false, "  Waves fold,\nsalt and gold.  "), UiMessage(false, "")),
            modelName = "Qwen2.5 0.5B Instruct",
            exportedAt = 0,
            timeZone = TimeZone.getTimeZone("UTC"),
        )
        assertEquals(
            """
            # Sea poem

            _Exported from On-device LLM on 1970-01-01 00:00 · model: Qwen2.5 0.5B Instruct · generated on the phone, offline._

            **You**

            Write a poem

            **Assistant**

            Waves fold,
            salt and gold.

            """.trimIndent(),
            md,
        )
    }

    @Test
    fun `file names are safe`() {
        assertEquals("What is 23 (a test).md", MarkdownExport.fileName("What is 2/3? (a test)"))
        assertEquals("a b.md", MarkdownExport.fileName("  a:  b*  "))
        assertEquals("conversation.md", MarkdownExport.fileName("///"))
        assertEquals("Long title cut at the dots.md", MarkdownExport.fileName("Long title cut at the dots…"))
    }
}
