package com.thalzeronlabs.ondevicellm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `offers four models, smallest first, with the 0_5B Qwen as default`() {
        assertEquals(4, ModelCatalog.all.size)
        assertEquals(ModelCatalog.all.sortedBy { it.sizeBytes }, ModelCatalog.all)
        assertEquals(ModelCatalog.QWEN_2_5_0_5B, ModelCatalog.default)
        assertEquals(ModelCatalog.QWEN_2_5_0_5B, ModelSpec.QWEN_2_5_0_5B_Q4_K_M)
    }

    @Test
    fun `every model is pinned to an official Hugging Face file`() {
        ModelCatalog.all.forEach { m ->
            val repo = m.pageUrl.removePrefix("https://huggingface.co/")
            assertTrue(m.id, m.url.startsWith("https://huggingface.co/$repo/resolve/main/"))
            assertTrue(m.id, repo.substringBefore('/') in setOf("Qwen", "HuggingFaceTB"))
            assertTrue(m.id, m.url.endsWith(".gguf"))
            assertTrue(m.id, m.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(m.id, m.sizeBytes in 300_000_000L..1_200_000_000L)
        }
    }

    @Test
    fun `every model shows its size, license and description`() {
        ModelCatalog.all.forEach { m ->
            assertEquals(m.id, "Apache-2.0", m.license)
            assertTrue(m.id, m.licenseUrl.startsWith("https://huggingface.co/"))
            assertTrue(m.id, m.description.isNotBlank())
            assertEquals(m.id, m.url.substringAfterLast('/'), m.fileName)
        }
    }

    @Test
    fun `ids and hashes are unique and looked up by id`() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.sha256 }.toSet().size)
        ModelCatalog.all.forEach { assertEquals(it, ModelCatalog.byId(it.id)) }
        assertEquals(null, ModelCatalog.byId("nope"))
    }

    @Test
    fun `the published hashes are the ones checked by hand`() {
        val expected = mapOf(
            "smollm2-360m-instruct-q8_0" to "48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201",
            "qwen2.5-0.5b-instruct-q4_k_m" to "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            "smollm2-1.7b-instruct-q4_k_m" to "decd2598bc2c8ed08c19adc3c8fdd461ee19ed5708679d1c54ef54a5a30d4f33",
            "qwen2.5-1.5b-instruct-q4_k_m" to "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        )
        assertEquals(expected, ModelCatalog.all.associate { it.id to it.sha256 })
    }
}
