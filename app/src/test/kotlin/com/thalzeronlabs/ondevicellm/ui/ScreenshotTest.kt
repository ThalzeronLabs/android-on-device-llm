package com.thalzeronlabs.ondevicellm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.thalzeronlabs.ondevicellm.ChatUiState
import com.thalzeronlabs.ondevicellm.battery.BatteryStatus
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.ConversationSummary
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.EngineState
import com.thalzeronlabs.ondevicellm.data.ModelCatalog
import com.thalzeronlabs.ondevicellm.data.ModelItem
import com.thalzeronlabs.ondevicellm.data.ModelSpec
import com.thalzeronlabs.ondevicellm.data.ModelStatus
import com.thalzeronlabs.ondevicellm.data.ModelsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The README screenshots, rendered on the JVM (Robolectric native graphics + Roborazzi).
 *
 * `./gradlew :app:recordRoborazziDebug` rewrites the PNGs in `docs/screenshots/`;
 * CI runs `verifyRoborazziDebug`, which fails when the UI no longer matches them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // A frozen clock, so the animated orb is drawn at the same angle on every run.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OnDeviceLlmTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png", roborazziOptions = options)
    }

    private val conversation = listOf(
        UiMessage(true, "Write a two-line poem about the sea"),
        UiMessage(false, "Salt wind folds the evening light,\nthe tide keeps time through the night."),
        UiMessage(true, "Now explain how you answer without internet"),
        UiMessage(
            false,
            "I'm a small language model stored on this phone. Your message is turned into tokens, and the phone's " +
                "CPU predicts the answer one token at a time. Nothing is sent to a server.",
        ),
    )

    private val chat = ChatUiState(
        conversationId = 1,
        messages = conversation,
        conversations = listOf(ConversationSummary(1, "Write a two-line poem about the sea", NOW, 4, null)),
    )

    @Test fun chatDark() = capture("chat-dark", dark = true) { ChatScreen(chat, readyModels, ChatActions()) }

    @Test fun chatLight() = capture("chat-light", dark = false) { ChatScreen(chat, readyModels, ChatActions()) }

    @Test fun batterySaver() = capture("battery-saver-light", dark = false) {
        val streaming = chat.copy(
            messages = conversation.take(3) + UiMessage(false, "I'm a small language model stored on this"),
            generating = true,
            battery = BatteryStatus(14, charging = false),
        )
        ChatScreen(streaming, readyModels, ChatActions())
    }

    @Test fun modelsDark() = capture("models-dark", dark = true) { ChatScreen(ChatUiState(), setupModels, ChatActions()) }

    @Test fun modelsLight() = capture("models-light", dark = false) { ChatScreen(ChatUiState(), setupModels, ChatActions()) }

    @Test fun historyDark() = capture("history-dark", dark = true) { History() }

    @Test fun historyLight() = capture("history-light", dark = false) { History() }

    @Test fun settingsDark() = capture("settings-dark", dark = true) { Settings() }

    @Test fun settingsLight() = capture("settings-light", dark = false) { Settings() }

    @Composable
    private fun History() = Box {
        HistoryDrawer(
            state = ChatUiState(
                conversationId = 2,
                conversations = listOf(
                    ConversationSummary(1, "Write a two-line poem about the sea", NOW - 4 * MINUTE, 4, null),
                    ConversationSummary(2, "Plan a 3-day trip to Lisbon", NOW - 2 * HOUR, 6, null),
                    ConversationSummary(3, "Explain Kotlin coroutines simply", NOW - 26 * HOUR, 8, null),
                    ConversationSummary(4, "Reply to my landlord politely", NOW - 3 * DAY, 2, null),
                ),
            ),
            onNewChat = {},
            onOpen = {},
            onRename = { _, _ -> },
            onDelete = {},
            now = NOW,
        )
    }

    @Composable
    private fun Settings() = Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        SettingsSheet(
            settings = ChatSettings(systemPrompt = "Answer in Portuguese, in short sentences.", temperature = 0.4f, maxTokens = 384),
            batterySaver = false,
            onChange = {},
            onReset = {},
        )
    }

    private companion object {
        val options = RoborazziOptions(
            // Allows for anti-aliasing differences between machines; a real UI change is far bigger.
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )

        const val NOW = 1_793_620_800_000L // a fixed instant, so "4 minutes ago" never changes
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        fun ModelSpec.item(status: ModelStatus) = ModelItem(
            id = id,
            name = displayName,
            sizeBytes = sizeBytes,
            status = status,
            description = description,
            license = license,
            licenseUrl = licenseUrl,
            pageUrl = pageUrl,
        )

        private val catalog = with(ModelCatalog) {
            listOf(
                SMOLLM2_360M.item(ModelStatus.NotDownloaded),
                QWEN_2_5_0_5B.item(ModelStatus.Installed),
                SMOLLM2_1_7B.item(ModelStatus.Downloading(0.42f, (SMOLLM2_1_7B.sizeBytes * 0.42).toLong(), SMOLLM2_1_7B.sizeBytes)),
                QWEN_2_5_1_5B.item(ModelStatus.Paused(QWEN_2_5_1_5B.sizeBytes / 4, QWEN_2_5_1_5B.sizeBytes)),
            )
        }

        val readyModels = ModelsState(
            items = catalog,
            engine = EngineState.Ready(ModelCatalog.QWEN_2_5_0_5B.id, ModelCatalog.QWEN_2_5_0_5B.displayName, ModelCatalog.QWEN_2_5_0_5B.sizeBytes),
        )

        val setupModels = ModelsState(
            items = catalog.map { if (it.status == ModelStatus.Installed) it.copy(status = ModelStatus.NotDownloaded) else it },
        )
    }
}
