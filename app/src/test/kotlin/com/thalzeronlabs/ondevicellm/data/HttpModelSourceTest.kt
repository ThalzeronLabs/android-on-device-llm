package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HttpModelSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val noNetwork = HttpTransport { _, _ -> error("no network in this test") }

    private fun source() = HttpModelSource(tmp.root, transport = noNetwork)

    private fun gguf(extra: String = "rest of the model") = ("GGUF" + extra).byteInputStream()

    @Test
    fun `importing a GGUF installs it under its own name`() = runTest {
        val source = source()

        source.import(gguf(), "Llama-3.2-1B-Instruct-Q4_K_M.gguf")

        assertTrue(source.isReady())
        assertEquals("Llama-3.2-1B-Instruct-Q4_K_M", source.displayName)
    }

    @Test
    fun `a file that is not GGUF is rejected and leaves no trace`() = runTest {
        val source = source()
        try {
            source.import("<html>not a model</html>".byteInputStream(), "page.html")
            fail("expected the import to be rejected")
        } catch (e: IOException) {
            assertEquals("That file is not a GGUF model.", e.message)
        }
        assertFalse(source.isReady())
        assertEquals(listOf<String>(), tmp.root.list()!!.toList())
    }

    @Test
    fun `importing does not destroy a half-finished download`() = runTest {
        File(tmp.root, "model.gguf.part").writeBytes(ByteArray(1234))
        val source = source()

        source.import(gguf(), "mine.gguf")

        assertEquals(1234L, source.partialBytes())
    }

    @Test
    fun `without an import the default model name is shown`() {
        assertEquals(ModelSpec.QWEN_2_5_0_5B_Q4_K_M.displayName, source().displayName)
        assertEquals(491_400_032L, source().approximateBytes)
    }
}
