package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileModelStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val noNetwork = HttpTransport { _, _ -> error("no network in this test") }

    private fun store() = FileModelStore(tmp.root, transport = noNetwork)

    private fun gguf(extra: String = "rest of the model") = ("GGUF" + extra).byteInputStream()

    @Test
    fun `each catalog model has its own file named after the published one`() {
        val store = store()
        assertEquals(File(tmp.root, "models/qwen2.5-0.5b-instruct-q4_k_m.gguf"), store.file(ModelCatalog.QWEN_2_5_0_5B.id))
        assertEquals(File(tmp.root, "models/smollm2-360m-instruct-q8_0.gguf"), store.file(ModelCatalog.SMOLLM2_360M.id))
        assertFalse(store.isInstalled(ModelCatalog.QWEN_2_5_0_5B.id))
    }

    @Test
    fun `importing a GGUF installs it under its own name`() = runTest {
        val store = store()
        val id = store.import(gguf(), "Llama-3.2-1B-Instruct-Q4_K_M.gguf")
        assertEquals(ModelStore.IMPORTED_ID, id)
        assertTrue(store.isInstalled(id))
        assertEquals("Llama-3.2-1B-Instruct-Q4_K_M", store.importedName)
    }

    @Test
    fun `a file that is not GGUF is rejected and leaves no trace`() = runTest {
        val store = store()
        try {
            store.import("<html>not a model</html>".byteInputStream(), "page.html")
            fail("expected the import to be rejected")
        } catch (e: IOException) {
            assertEquals("That file is not a GGUF model.", e.message)
        }
        assertNull(store.importedName)
        assertEquals(listOf<String>(), File(tmp.root, "models").list()!!.toList())
    }

    @Test
    fun `the chosen model is remembered across launches`() {
        store().selectedId = ModelCatalog.SMOLLM2_1_7B.id
        assertEquals(ModelCatalog.SMOLLM2_1_7B.id, store().selectedId)
    }

    @Test
    fun `delete removes the file, an unfinished download and the remembered choice`() {
        val store = store()
        val id = ModelCatalog.QWEN_2_5_1_5B.id
        store.file(id).writeText("GGUF")
        File(store.file(id).path + ".part").writeText("half")
        store.selectedId = id

        store.delete(id)

        assertFalse(store.isInstalled(id))
        assertEquals(0L, store.partialBytes(id))
        assertNull(store.selectedId)
    }

    @Test
    fun `v0_1_0 default model is moved, not downloaded again`() {
        File(tmp.root, "model.gguf").writeText("GGUF qwen")
        val store = store()
        assertTrue(store.isInstalled(ModelCatalog.QWEN_2_5_0_5B.id))
        assertEquals(ModelCatalog.QWEN_2_5_0_5B.id, store.selectedId)
        assertFalse(File(tmp.root, "model.gguf").exists())
    }

    @Test
    fun `v0_1_0 imported model keeps its name`() {
        File(tmp.root, "model.gguf").writeText("GGUF phi")
        File(tmp.root, "model.label").writeText("Phi-3-mini")
        val store = store()
        assertEquals("Phi-3-mini", store.importedName)
        assertEquals(ModelStore.IMPORTED_ID, store.selectedId)
        assertFalse(store.isInstalled(ModelCatalog.QWEN_2_5_0_5B.id))
    }

    @Test
    fun `v0_1_0 unfinished download resumes in the new place`() {
        File(tmp.root, "model.gguf.part").writeBytes(ByteArray(1234))
        assertEquals(1234L, store().partialBytes(ModelCatalog.QWEN_2_5_0_5B.id))
    }
}
