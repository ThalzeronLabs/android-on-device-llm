package com.thalzeronlabs.ondevicellm.data

import app.cash.turbine.test
import com.thalzeronlabs.ondevicellm.fakes.FakeEngine
import com.thalzeronlabs.ondevicellm.fakes.FakeModelStore
import com.thalzeronlabs.ondevicellm.fakes.managerScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var store: FakeModelStore
    private val engine = FakeEngine()

    private fun TestScope.manager(setup: FakeModelStore.() -> Unit = {}): ModelManager {
        store = FakeModelStore(tmp.root).apply(setup)
        return ModelManager(store, engine, managerScope())
    }

    private fun ModelManager.status(id: String) = state.value.item(id)!!.status

    @Test
    fun `lists the catalog with what is on disk`() = runTest {
        val m = manager { install("small"); partial["big"] = 1_000_000 }
        assertEquals(ModelStatus.Installed, m.status("small"))
        assertEquals(ModelStatus.Paused(1_000_000, 10_000_000), m.status("big"))
        assertEquals(EngineState.NoModel, m.state.value.engine)
    }

    @Test
    fun `start loads the model picked last time`() = runTest {
        val m = manager { install("small"); install("big"); selectedId = "big" }
        m.start(autoDownload = false)
        advanceUntilIdle()
        assertEquals(EngineState.Ready("big", "Model big", 4), m.state.value.engine)
        assertEquals(store.file("big"), engine.loaded.single())
    }

    @Test
    fun `start with nothing installed downloads the default only when allowed`() = runTest {
        val m = manager()
        m.start(autoDownload = false)
        advanceUntilIdle()
        assertEquals(EngineState.NoModel, m.state.value.engine)
        assertTrue(engine.loaded.isEmpty())
    }

    @Test
    fun `a download goes through progress and verification, then the first model is loaded`() = runTest {
        val m = manager()
        m.state.test {
            awaitItem()
            m.download("small")
            val seen = mutableListOf<ModelStatus>()
            do {
                val item = awaitItem()
                seen += item.item("small")!!.status
            } while (item.engine !is EngineState.Ready)
            assertTrue(ModelStatus.Downloading(0.5f, 2_500_000, 5_000_000) in seen)
            assertTrue(ModelStatus.Verifying(1f) in seen)
            assertTrue(ModelStatus.Installed in seen)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("small", store.selectedId)
    }

    @Test
    fun `a second download does not replace the model in use`() = runTest {
        val m = manager { install("small") }
        m.start(autoDownload = false)
        advanceUntilIdle()
        m.download("big")
        advanceUntilIdle()
        assertEquals(ModelStatus.Installed, m.status("big"))
        assertEquals("small", m.state.value.activeId)
    }

    @Test
    fun `pause keeps the bytes and starting another download pauses the first`() = runTest {
        val m = manager {
            downloads["small"] = flow {
                emit(DownloadProgress.Transferring(1_000_000, 5_000_000, resumedFrom = 0))
                awaitCancellation()
            }
        }
        m.download("small")
        advanceUntilIdle()
        assertEquals(ModelStatus.Downloading(0.2f, 1_000_000, 5_000_000), m.status("small"))

        store.partial["small"] = 1_000_000
        m.download("big")
        advanceUntilIdle()

        assertEquals(ModelStatus.Paused(1_000_000, 5_000_000), m.status("small"))
        assertEquals(ModelStatus.Installed, m.status("big"))
    }

    @Test
    fun `a failed download shows the error and can be retried`() = runTest {
        var calls = 0
        val m = manager {
            downloads["small"] = flow {
                if (calls++ == 0) throw java.io.IOException("offline")
                emit(DownloadProgress.Verifying(5_000_000, 5_000_000))
                install("small")
            }
        }
        m.download("small"); advanceUntilIdle()
        assertEquals(ModelStatus.Failed("Download failed: offline"), m.status("small"))

        m.download("small"); advanceUntilIdle()
        assertEquals(EngineState.Ready("small", "Model small", 4), m.state.value.engine)
    }

    @Test
    fun `select switches models without a restart`() = runTest {
        val m = manager { install("small"); install("big") }
        m.start(autoDownload = false); advanceUntilIdle()
        m.select("big"); advanceUntilIdle()
        assertEquals("big", m.state.value.activeId)
        assertEquals(listOf(store.file("small"), store.file("big")), engine.loaded)
        assertEquals("big", store.selectedId)
    }

    @Test
    fun `a model that fails to load shows the error`() = runTest {
        engine.failLoad = true
        val m = manager { install("small") }
        m.start(autoDownload = false); advanceUntilIdle()
        assertEquals(EngineState.Failed("small", "Could not load Model small: bad file"), m.state.value.engine)
    }

    @Test
    fun `deleting the active model unloads it`() = runTest {
        val m = manager { install("small") }
        m.start(autoDownload = false); advanceUntilIdle()
        m.delete("small")
        assertEquals(EngineState.NoModel, m.state.value.engine)
        assertEquals(ModelStatus.NotDownloaded, m.status("small"))
        assertEquals(1, engine.closed)
        assertEquals(listOf("small"), store.deleted)
    }

    @Test
    fun `an imported model is listed under its own name and used right away`() = runTest {
        val m = manager()
        m.import("GGUF data".byteInputStream(), "Phi-3-mini")
        advanceUntilIdle()
        val imported = m.state.value.item(ModelStore.IMPORTED_ID)!!
        assertEquals("Phi-3-mini", imported.name)
        assertTrue(imported.imported)
        assertEquals(EngineState.Ready(ModelStore.IMPORTED_ID, "Phi-3-mini", 9), m.state.value.engine)
    }

    @Test
    fun `a file that is not GGUF is rejected`() = runTest {
        val m = manager()
        m.import("<html>".byteInputStream(), "page.html")
        advanceUntilIdle()
        assertEquals(EngineState.Failed(ModelStore.IMPORTED_ID, "That file is not a GGUF model."), m.state.value.engine)
        assertEquals(null, m.state.value.item(ModelStore.IMPORTED_ID))
    }
}
