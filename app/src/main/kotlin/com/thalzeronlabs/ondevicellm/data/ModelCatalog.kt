package com.thalzeronlabs.ondevicellm.data

/**
 * The models the app can download: small, instruction-tuned, Apache-2.0, published without a login wall by the
 * model authors themselves.
 *
 * Every entry is pinned to the exact file size and SHA-256 that Hugging Face publishes for it (the LFS object id,
 * also sent as `X-Linked-ETag` by the download URL). A file that does not match is deleted, never loaded.
 * Models whose official files are gated behind a license click-through (Llama 3.2, Gemma 3) are left out on
 * purpose: the app could not download them without an account. Bring them with "Import a .gguf file" instead.
 */
object ModelCatalog {

    private const val APACHE_2 = "Apache-2.0"

    val SMOLLM2_360M = ModelSpec(
        id = "smollm2-360m-instruct-q8_0",
        displayName = "SmolLM2 360M Instruct",
        description = "The fastest. Good for short answers and quick tests on older phones. 8-bit (Q8_0).",
        url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
        sizeBytes = 386_404_992L,
        sha256 = "48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201",
        license = APACHE_2,
        licenseUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct",
        pageUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF",
    )

    val QWEN_2_5_0_5B = ModelSpec(
        id = "qwen2.5-0.5b-instruct-q4_k_m",
        displayName = "Qwen2.5 0.5B Instruct",
        description = "The default: fast on any phone, multilingual, a good first try. 4-bit (Q4_K_M).",
        url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sizeBytes = 491_400_032L,
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        license = APACHE_2,
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/main/LICENSE",
        pageUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
    )

    val SMOLLM2_1_7B = ModelSpec(
        id = "smollm2-1.7b-instruct-q4_k_m",
        displayName = "SmolLM2 1.7B Instruct",
        description = "Better reasoning in English, slower. Best on phones with 6 GB of RAM or more. 4-bit (Q4_K_M).",
        url = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
        sizeBytes = 1_055_609_536L,
        sha256 = "decd2598bc2c8ed08c19adc3c8fdd461ee19ed5708679d1c54ef54a5a30d4f33",
        license = APACHE_2,
        licenseUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct",
        pageUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
    )

    val QWEN_2_5_1_5B = ModelSpec(
        id = "qwen2.5-1.5b-instruct-q4_k_m",
        displayName = "Qwen2.5 1.5B Instruct",
        description = "The smartest here, multilingual. Best on phones with 6 GB of RAM or more. 4-bit (Q4_K_M).",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 1_117_320_736L,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        license = APACHE_2,
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/LICENSE",
        pageUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",
    )

    /** Smallest first. */
    val all: List<ModelSpec> = listOf(SMOLLM2_360M, QWEN_2_5_0_5B, SMOLLM2_1_7B, QWEN_2_5_1_5B)

    /** Downloaded by itself on the first launch on Wi-Fi. */
    val default: ModelSpec = QWEN_2_5_0_5B

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
