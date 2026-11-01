package com.thalzeronlabs.ondevicellm

import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.ondevicellm.data.ChatEngine
import com.thalzeronlabs.ondevicellm.data.DownloadProgress
import com.thalzeronlabs.ondevicellm.data.ModelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeEngine(var answer: () -> Flow<String> = { flowOf("Hel", "lo", "!") }) : ChatEngine {
        val histories = mutableListOf<List<ChatMessage>>()
        var loaded: File? = null
        var failLoad = false
        override suspend fun load(file: File) {
            if (failLoad) error("bad file")
            loaded = file
        }
        override fun reply(history: List<ChatMessage>): Flow<String> {
            histories += history
            return answer()
        }
        override val lastTokensPerSecond: Double? = 12.5
        override fun close() {}
    }

    private inner class FakeSource(
        ready: Boolean,
        var partial: Long = 0,
        private val downloadFlow: Flow<DownloadProgress>? = null,
    ) : ModelSource {
        override val modelFile: File = tmp.newFile("model.gguf").apply { if (ready) writeText("GGUF") else delete() }
        override var displayName = "Tiny"
        override val approximateBytes = 5_000_000L
        val seen = mutableListOf<ModelState>()
        override fun partialBytes() = partial
        override fun download(): Flow<DownloadProgress> = downloadFlow ?: flow {
            emit(DownloadProgress.Transferring(2_500_000, 5_000_000, resumedFrom = partial))
            emit(DownloadProgress.Transferring(5_000_000, 5_000_000, resumedFrom = partial))
            emit(DownloadProgress.Verifying(5_000_000, 5_000_000))
            modelFile.writeText("GGUF")
        }
        override suspend fun import(input: InputStream, name: String?) {
            modelFile.writeBytes(input.readBytes())
            displayName = name ?: "Imported"
        }
    }

    @Test
    fun `starts in Missing when there is no model on disk`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false))
        advanceUntilIdle()
        assertEquals(ModelState.Missing, vm.state.value.model)
        assertEquals(5L, vm.state.value.modelSizeMb)
    }

    @Test
    fun `loads automatically when the model is already downloaded`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ChatViewModel(engine, FakeSource(ready = true))
        advanceUntilIdle()
        assertEquals(ModelState.Ready, vm.state.value.model)
        assertTrue(engine.loaded!!.name == "model.gguf")
    }

    @Test
    fun `download then load ends Ready`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false))
        vm.download()
        advanceUntilIdle()
        assertEquals(ModelState.Ready, vm.state.value.model)
    }

    @Test
    fun `a model that fails to load shows the error`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine().apply { failLoad = true }, FakeSource(ready = true))
        advanceUntilIdle()
        assertTrue(vm.state.value.model is ModelState.Failed)
    }

    @Test
    fun `send streams the answer into the last bubble`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ChatViewModel(engine, FakeSource(ready = true), systemPrompt = "SYS")
        advanceUntilIdle()

        vm.send("  Hi  ")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf(UiMessage(true, "Hi"), UiMessage(false, "Hello!")), s.messages)
        assertFalse(s.generating)
        assertEquals(12.5, s.tokensPerSecond!!, 1e-9)
        assertEquals(listOf(ChatMessage.system("SYS"), ChatMessage.user("Hi")), engine.histories.single())
    }

    @Test
    fun `history carries earlier turns`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ChatViewModel(engine, FakeSource(ready = true), systemPrompt = "SYS")
        advanceUntilIdle()
        vm.send("one"); advanceUntilIdle()
        vm.send("two"); advanceUntilIdle()
        assertEquals(
            listOf(ChatMessage.system("SYS"), ChatMessage.user("one"), ChatMessage.assistant("Hello!"), ChatMessage.user("two")),
            engine.histories.last(),
        )
    }

    @Test
    fun `blank input and sending while generating are ignored`() = runTest(dispatcher) {
        val engine = FakeEngine(answer = { flow { emit("thinking"); awaitCancellation() } })
        val vm = ChatViewModel(engine, FakeSource(ready = true))
        advanceUntilIdle()
        vm.send("   ")
        vm.send("first")
        advanceUntilIdle()
        vm.send("second")
        advanceUntilIdle()
        assertEquals(1, engine.histories.size)
        vm.stop()
        advanceUntilIdle()
    }

    @Test
    fun `stop keeps the partial answer and unlocks the input`() = runTest(dispatcher) {
        val engine = FakeEngine(answer = { flow { emit("Partial"); awaitCancellation() } })
        val vm = ChatViewModel(engine, FakeSource(ready = true))
        advanceUntilIdle()
        vm.send("go")
        advanceUntilIdle()
        assertTrue(vm.state.value.generating)

        vm.stop()
        advanceUntilIdle()

        assertFalse(vm.state.value.generating)
        assertEquals("Partial", vm.state.value.messages.last().text)
    }

    @Test
    fun `clear empties the conversation`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = true))
        advanceUntilIdle()
        vm.send("hi"); advanceUntilIdle()
        vm.clear()
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `download progress goes through Downloading and Verifying`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false))
        val states = mutableListOf<ModelState>()
        val watcher = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { states += it.model } }
        vm.download()
        advanceUntilIdle()
        watcher.cancel()
        assertTrue(states.contains(ModelState.Downloading(0.5f, 2_500_000, 5_000_000)))
        assertTrue(states.contains(ModelState.Verifying(1f)))
        assertEquals(ModelState.Ready, states.last())
    }

    @Test
    fun `an unfinished download shows as Paused on start`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false, partial = 1_000_000))
        advanceUntilIdle()
        assertEquals(ModelState.Paused(1_000_000, 5_000_000), vm.state.value.model)
    }

    @Test
    fun `pause stops the download and keeps its progress`() = runTest(dispatcher) {
        val source = FakeSource(
            ready = false,
            downloadFlow = flow {
                emit(DownloadProgress.Transferring(1_000_000, 5_000_000, resumedFrom = 0))
                awaitCancellation()
            },
        )
        val vm = ChatViewModel(FakeEngine(), source)
        vm.download()
        advanceUntilIdle()
        assertEquals(ModelState.Downloading(0.2f, 1_000_000, 5_000_000), vm.state.value.model)

        source.partial = 1_000_000
        vm.pauseDownload()
        advanceUntilIdle()

        assertEquals(ModelState.Paused(1_000_000, 5_000_000), vm.state.value.model)
    }

    @Test
    fun `auto download starts by itself and ends Ready`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false), autoDownload = true)
        advanceUntilIdle()
        assertEquals(ModelState.Ready, vm.state.value.model)
    }

    @Test
    fun `a failed download can be retried`() = runTest(dispatcher) {
        var calls = 0
        val source = FakeSource(ready = false, downloadFlow = flow {
            if (calls++ == 0) throw java.io.IOException("offline")
            emit(DownloadProgress.Verifying(5_000_000, 5_000_000))
        })
        val vm = ChatViewModel(FakeEngine(), source)
        vm.download(); advanceUntilIdle()
        assertEquals(ModelState.Failed("Download failed: offline"), vm.state.value.model)

        source.modelFile.writeText("GGUF")
        vm.download(); advanceUntilIdle()
        assertEquals(ModelState.Ready, vm.state.value.model)
    }

    @Test
    fun `an imported model shows its own name`() = runTest(dispatcher) {
        val vm = ChatViewModel(FakeEngine(), FakeSource(ready = false))
        vm.import("GGUF".byteInputStream(), "Phi-3-mini")
        advanceUntilIdle()
        assertEquals(ModelState.Ready, vm.state.value.model)
        assertEquals("Phi-3-mini", vm.state.value.modelName)
    }
}
