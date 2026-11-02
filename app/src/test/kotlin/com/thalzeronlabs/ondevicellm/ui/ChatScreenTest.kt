package com.thalzeronlabs.ondevicellm.ui

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.battery.BatteryStatus
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.ConversationSummary
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule val compose = createComposeRule()

    private val conversation = ChatUiState(
        messages = listOf(UiMessage(true, "What runs this chat?"), UiMessage(false, "llama.cpp, on your phone.")),
    )

    private fun show(state: ChatUiState, actions: ChatActions, dark: Boolean = true) = compose.setContent {
        OnDeviceLlmTheme(dark = dark) { ChatScreen(state, previewModels(), actions) }
    }

    @Test
    fun `a long press on a message copies its text`() {
        val copied = mutableListOf<String>()
        show(conversation, ChatActions(onCopy = { copied += it }))

        compose.onNodeWithText("llama.cpp, on your phone.").performTouchInput { longClick() }
        compose.onNodeWithText("What runs this chat?").performTouchInput { longClick() }

        assertEquals(listOf("llama.cpp, on your phone.", "What runs this chat?"), copied)
    }

    @Test
    fun `a message being written cannot be copied yet`() {
        val copied = mutableListOf<String>()
        val streaming = conversation.copy(messages = conversation.messages + UiMessage(false, "Half an ans"), generating = true)
        show(streaming, ChatActions(onCopy = { copied += it }))
        compose.onNodeWithText("Half an ans ▍").performTouchInput { longClick() }
        assertTrue(copied.isEmpty())
    }

    @Test
    fun `low battery shows a banner with Stop while an answer is being written`() {
        var stopped = 0
        val state = conversation.copy(generating = true, battery = BatteryStatus(12, charging = false))
        show(state, ChatActions(onStop = { stopped++ }), dark = false)

        compose.onNodeWithTag("battery-banner").assertIsDisplayed()
        compose.onNodeWithText("Battery at 12%").assertIsDisplayed()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun `the battery banner can be turned off and is hidden when the battery is fine`() {
        var dismissed = 0
        show(conversation.copy(battery = BatteryStatus(12, charging = false)), ChatActions(onDismissBatterySaver = { dismissed++ }))
        compose.onNodeWithText("Turn off").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `no banner on a healthy battery`() {
        show(conversation.copy(battery = BatteryStatus(80, charging = false)), ChatActions())
        compose.onNodeWithTag("battery-banner").assertDoesNotExist()
    }

    @Test
    fun `the overflow menu shares and saves the conversation`() {
        var shared = 0
        var exported = 0
        show(conversation, ChatActions(onShare = { shared++ }, onExport = { exported++ }))
        compose.onNode(hasContentDescription("More")).performClick()
        compose.onNodeWithText("Share as Markdown").performClick()
        compose.onNode(hasContentDescription("More")).performClick()
        compose.onNodeWithText("Save as .md file").performClick()
        assertEquals(1, shared)
        assertEquals(1, exported)
    }

    @Test
    fun `the model picker lists size, license and Hugging Face link and starts a download`() {
        val downloads = mutableListOf<String>()
        compose.setContent {
            OnDeviceLlmTheme { ModelsSheet(previewModels(), ChatActions(onDownloadModel = { downloads += it })) }
        }
        // The sheet scrolls: rows below the fold exist but are not on screen.
        compose.onNodeWithText("SmolLM2 360M Instruct").assertIsDisplayed()
        compose.onNodeWithText("386 MB").assertIsDisplayed()
        compose.onAllNodesWithText("Apache-2.0").assertCountEquals(3)
        compose.onAllNodesWithText("Hugging Face").assertCountEquals(1)
        compose.onNodeWithText("In use").assertExists()
        compose.onNodeWithText("1.1 GB").assertExists()
        compose.onNodeWithText("469 of 1117 MB · 42%").assertExists()
        compose.onNodeWithText("Download").performClick()
        assertEquals(listOf("smollm2-360m"), downloads)
    }

    @Test
    fun `chat settings show the current values and can be reset`() {
        var reset = 0
        compose.setContent {
            OnDeviceLlmTheme {
                SettingsSheet(ChatSettings("Answer in Portuguese.", 0.3f, 256), batterySaver = false, onChange = {}, onReset = { reset++ })
            }
        }
        compose.onNodeWithText("Answer in Portuguese.").assertIsDisplayed()
        compose.onNodeWithText("0.3").assertIsDisplayed()
        compose.onNodeWithText("256 tokens").assertIsDisplayed()
        compose.onNodeWithText("Reset").performClick()
        assertEquals(1, reset)
    }

    @Test
    fun `history lists conversations and renames one`() {
        val renamed = mutableListOf<Pair<Long, String>>()
        val state = ChatUiState(
            conversations = listOf(
                ConversationSummary(7, "Sea poem", 0, 2, "Waves"),
            ),
        )
        compose.setContent {
            OnDeviceLlmTheme { HistoryDrawer(state, onNewChat = {}, onOpen = {}, onRename = { id, t -> renamed += id to t }, onDelete = {}) }
        }
        compose.onNodeWithText("Sea poem").assertIsDisplayed()
        compose.onNode(hasContentDescription("Options for Sea poem")).performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithTag("rename-field").performTextReplacement("Ocean poem")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(7L to "Ocean poem"), renamed)
    }
}

@RunWith(AndroidJUnit4::class)
class PlatformTest {

    @Test
    fun `copy puts the message on the clipboard`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        copyToClipboard(context, "Copied text")
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("Copied text", clip.getItemAt(0).text.toString())
    }

    @Test
    fun `share sends the Markdown as plain text with the title`() {
        val chooser = shareMarkdownIntent("Sea poem", "# Sea poem\n")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("Sea poem", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("# Sea poem\n", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
