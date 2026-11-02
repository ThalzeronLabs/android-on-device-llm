package com.thalzeronlabs.ondevicellm

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.ondevicellm.battery.BatteryStatus
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.EngineState
import com.thalzeronlabs.ondevicellm.data.GenerationOptions
import com.thalzeronlabs.ondevicellm.data.ModelManager
import com.thalzeronlabs.ondevicellm.fakes.FakeEngine
import com.thalzeronlabs.ondevicellm.fakes.FakeModelStore
import com.thalzeronlabs.ondevicellm.fakes.managerScope
import com.thalzeronlabs.ondevicellm.fakes.InMemoryConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private lateinit var store: FakeModelStore
    private lateinit var manager: ModelManager
    private val repo = InMemoryConversationRepository()

    private fun TestScope.viewModel(
        engine: FakeEngine = FakeEngine(),
        battery: Flow<BatteryStatus> = emptyFlow(),
        installed: Boolean = true,
    ): ChatViewModel {
        store = FakeModelStore(tmp.newFolder())
        if (installed) store.install("small")
        manager = ModelManager(store, engine, managerScope())
        manager.start(autoDownload = false)
        return ChatViewModel(repo, manager, engine, battery).also { advanceUntilIdle() }
    }

    private suspend fun ReceiveTurbine<ChatUiState>.awaitUntil(predicate: (ChatUiState) -> Boolean): ChatUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `send streams the answer and saves the conversation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            vm.send("  Write a two-line poem about the sea  ")
            assertTrue(awaitUntil { it.generating }.messages.last().text.isEmpty())
            val done = awaitUntil { !it.generating && it.conversations.isNotEmpty() && it.messages.last().id != 0L }
            assertEquals(listOf(true to "Write a two-line poem about the sea", false to "Hello!"), done.messages.map { it.fromUser to it.text })
            assertEquals(12.5, done.tokensPerSecond!!, 1e-9)
            assertEquals("Write a two-line poem about the sea", done.title)
            cancelAndIgnoreRemainingEvents()
        }
        val id = vm.state.value.conversationId!!
        assertEquals(listOf("Write a two-line poem about the sea", "Hello!"), repo.messages(id).map { it.text })
    }

    @Test
    fun `the conversation settings reach the engine`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.updateSettings(ChatSettings(systemPrompt = "Answer in Portuguese.", temperature = 0.2f, maxTokens = 128))
        vm.send("Hi")
        advanceUntilIdle()

        assertEquals(listOf(ChatMessage.system("Answer in Portuguese."), ChatMessage.user("Hi")), engine.histories.single())
        assertEquals(GenerationOptions(temperature = 0.2f, maxTokens = 128), engine.options.single())
        // A new conversation keeps the settings it was given before its first message.
        assertEquals(0.2f, repo.conversation(vm.state.value.conversationId!!)!!.settings.temperature)
    }

    @Test
    fun `an empty system prompt sends no system message`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.updateSettings(ChatSettings(systemPrompt = "   "))
        vm.send("Hi"); advanceUntilIdle()
        assertEquals(listOf(ChatMessage.user("Hi")), engine.histories.single())
    }

    @Test
    fun `history carries earlier turns`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.updateSettings(ChatSettings(systemPrompt = "SYS"))
        vm.send("one"); advanceUntilIdle()
        vm.send("two"); advanceUntilIdle()
        assertEquals(
            listOf(ChatMessage.system("SYS"), ChatMessage.user("one"), ChatMessage.assistant("Hello!"), ChatMessage.user("two")),
            engine.histories.last(),
        )
    }

    @Test
    fun `stop keeps the partial answer, saves it and unlocks the input`() = runTest(dispatcher) {
        val vm = viewModel(FakeEngine(answer = { flow { emit("Partial"); awaitCancellation() } }))
        vm.state.test {
            vm.send("go")
            awaitUntil { it.generating && it.messages.last().text == "Partial" }
            vm.stop()
            val stopped = awaitUntil { !it.generating }
            assertEquals("Partial", stopped.messages.last().text)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertEquals(listOf("go", "Partial"), repo.messages(vm.state.value.conversationId!!).map { it.text })
    }

    @Test
    fun `an engine error is shown but not saved`() = runTest(dispatcher) {
        val vm = viewModel(FakeEngine(answer = { flow { emit("Half"); error("context full") } }))
        vm.send("go"); advanceUntilIdle()
        assertEquals("Half\n[error: context full]", vm.state.value.messages.last().text)
        assertEquals(listOf("go", "Half"), repo.messages(vm.state.value.conversationId!!).map { it.text })
    }

    @Test
    fun `blank input, sending while generating and sending without a model are ignored`() = runTest(dispatcher) {
        val engine = FakeEngine(answer = { flow { emit("thinking"); awaitCancellation() } })
        val vm = viewModel(engine)
        vm.send("   ")
        vm.send("first"); advanceUntilIdle()
        vm.send("second"); advanceUntilIdle()
        assertEquals(1, engine.histories.size)
        vm.stop(); advanceUntilIdle()

        val noModel = viewModel(FakeEngine(), installed = false)
        noModel.send("hello"); advanceUntilIdle()
        assertTrue(noModel.state.value.messages.isEmpty())
    }

    @Test
    fun `new chat starts empty with default settings and saves nothing until the first message`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateSettings(ChatSettings(temperature = 1.2f))
        vm.send("hi"); advanceUntilIdle()

        vm.newChat(); advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.conversationId)
        assertTrue(s.messages.isEmpty())
        assertEquals(ChatSettings(), s.settings)
        assertEquals(1, s.conversations.size)
    }

    @Test
    fun `opening a saved conversation restores its messages and settings and continues it`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = viewModel(engine)
        vm.updateSettings(ChatSettings(systemPrompt = "Pirate", temperature = 1.0f))
        vm.send("Ahoy"); advanceUntilIdle()
        val first = vm.state.value.conversationId!!
        vm.newChat(); advanceUntilIdle()
        vm.send("Other"); advanceUntilIdle()

        vm.state.test {
            vm.openConversation(first)
            val opened = awaitUntil { it.conversationId == first }
            assertEquals(listOf("Ahoy", "Hello!"), opened.messages.map { it.text })
            assertEquals("Pirate", opened.settings.systemPrompt)
            cancelAndIgnoreRemainingEvents()
        }

        vm.send("Again"); advanceUntilIdle()
        assertEquals(
            listOf(ChatMessage.system("Pirate"), ChatMessage.user("Ahoy"), ChatMessage.assistant("Hello!"), ChatMessage.user("Again")),
            engine.histories.last(),
        )
        assertEquals(4, repo.messages(first).size)
    }

    @Test
    fun `the conversation list is most recent first and follows renames`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.send("First question"); advanceUntilIdle()
        val first = vm.state.value.conversationId!!
        vm.newChat(); advanceUntilIdle()
        vm.send("Second question"); advanceUntilIdle()

        vm.state.test {
            assertEquals(listOf("Second question", "First question"), awaitItem().conversations.map { it.title })
            vm.rename(first, "  Renamed   chat ")
            assertEquals("Renamed chat", awaitUntil { s -> s.conversations.any { it.id == first && it.title != "First question" } }
                .conversations.first { it.id == first }.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting the open conversation starts a new one, deleting another keeps it open`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.send("keep me"); advanceUntilIdle()
        val kept = vm.state.value.conversationId!!
        vm.newChat(); advanceUntilIdle()
        vm.send("delete me"); advanceUntilIdle()
        val open = vm.state.value.conversationId!!

        vm.openConversation(kept); advanceUntilIdle()
        vm.delete(open); advanceUntilIdle()
        assertEquals(kept, vm.state.value.conversationId)
        assertEquals(listOf(kept), vm.state.value.conversations.map { it.id })

        vm.delete(kept); advanceUntilIdle()
        assertNull(vm.state.value.conversationId)
        assertTrue(vm.state.value.messages.isEmpty())
        assertTrue(vm.state.value.conversations.isEmpty())
    }

    @Test
    fun `settings are clamped and saved with an open conversation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.send("hi"); advanceUntilIdle()
        vm.updateSettings(ChatSettings(systemPrompt = "  Be brief.  ", temperature = 9f, maxTokens = 1))
        advanceUntilIdle()
        val expected = ChatSettings("Be brief.", 1.5f, 64)
        assertEquals(expected, vm.state.value.settings)
        assertEquals(expected, repo.conversation(vm.state.value.conversationId!!)!!.settings)

        vm.resetSettings(); advanceUntilIdle()
        assertEquals(ChatSettings(), repo.conversation(vm.state.value.conversationId!!)!!.settings)
    }

    @Test
    fun `low battery warns, caps the answer length and can be turned off until it recovers`() = runTest(dispatcher) {
        val battery = MutableSharedFlow<BatteryStatus>()
        val engine = FakeEngine()
        val vm = viewModel(engine, battery)
        vm.updateSettings(ChatSettings(maxTokens = 1024))

        vm.state.test {
            battery.emit(BatteryStatus(15, charging = false))
            val low = awaitUntil { it.battery != null }
            assertTrue(low.batterySaver)
            assertEquals(ChatUiState.SAVER_MAX_TOKENS, low.effectiveMaxTokens)

            vm.dismissBatterySaver()
            assertFalse(awaitUntil { it.batterySaverDismissed }.batterySaver)

            battery.emit(BatteryStatus(60, charging = true))
            awaitUntil { it.battery?.percent == 60 }
            battery.emit(BatteryStatus(10, charging = false))
            assertTrue("warns again on the next low battery", awaitUntil { it.battery?.percent == 10 }.batterySaver)
            cancelAndIgnoreRemainingEvents()
        }

        vm.send("long story please"); advanceUntilIdle()
        assertEquals(ChatUiState.SAVER_MAX_TOKENS, engine.options.single().maxTokens)
    }

    @Test
    fun `charging or a short answer setting is not capped`() = runTest(dispatcher) {
        val charging = ChatUiState(settings = ChatSettings(maxTokens = 1024), battery = BatteryStatus(5, charging = true))
        assertFalse(charging.batterySaver)
        assertEquals(1024, charging.effectiveMaxTokens)
        val short = ChatUiState(settings = ChatSettings(maxTokens = 128), battery = BatteryStatus(5, charging = false))
        assertEquals(128, short.effectiveMaxTokens)
        val powerSave = ChatUiState(battery = BatteryStatus(90, charging = false, powerSaveMode = true))
        assertTrue(powerSave.batterySaver)
    }

    @Test
    fun `switching the model stops the answer and loads the new model without a restart`() = runTest(dispatcher) {
        val engine = FakeEngine(answer = { flow { emit("Partial"); awaitCancellation() } })
        val vm = viewModel(engine)
        store.install("big")
        manager.download("big"); advanceUntilIdle() // already on disk: reported as installed
        vm.send("go"); advanceUntilIdle()
        assertTrue(vm.state.value.generating)

        vm.selectModel("big"); advanceUntilIdle()

        assertFalse(vm.state.value.generating)
        assertEquals("Partial", vm.state.value.messages.last().text)
        assertEquals(EngineState.Ready("big", "Model big", 4), vm.models.value.engine)
        assertEquals(store.file("big"), engine.loaded.last())
    }

    @Test
    fun `export renders the open conversation as Markdown`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.send("What is GGUF?"); advanceUntilIdle()
        val md = vm.exportMarkdown(now = 0)
        assertTrue(md.startsWith("# What is GGUF?\n"))
        assertTrue(md.contains("model: Model small"))
        assertTrue(md.contains("**You**\n\nWhat is GGUF?\n"))
        assertTrue(md.contains("**Assistant**\n\nHello!\n"))
    }

    @Test
    fun `messages keep their database ids`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.send("hi"); advanceUntilIdle()
        val ids = vm.state.value.messages.map(UiMessage::id)
        assertEquals(repo.messages(vm.state.value.conversationId!!).map(UiMessage::id).last(), ids.last())
    }
}
