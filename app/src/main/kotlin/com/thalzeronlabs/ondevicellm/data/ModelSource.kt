package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Where the model file lives and how it gets there. */
interface ModelSource {
    val modelFile: File
    val displayName: String
    val approximateBytes: Long
    fun isReady(): Boolean = modelFile.isFile && modelFile.length() > 0

    /** Downloads the model, emitting progress from 0f to 1f. */
    fun download(): Flow<Float>

    /** Copies a user-picked GGUF file into app storage. */
    suspend fun import(input: InputStream)
}

/**
 * Downloads a small chat model from Hugging Face into app-private storage.
 * The download goes to a `.part` file first, so a cancelled or failed download never looks like a valid model.
 */
class HttpModelSource(
    filesDir: File,
    private val url: String = QWEN_0_5B_URL,
    override val displayName: String = "Qwen2.5 0.5B Instruct (Q4_K_M)",
    override val approximateBytes: Long = 491_400_032L,
) : ModelSource {

    override val modelFile = File(filesDir, "model.gguf")

    override fun download(): Flow<Float> = flow {
        val part = File(modelFile.path + ".part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: approximateBytes
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var copied = 0L
                    var lastEmitted = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = (copied * 100 / total).toInt()
                        if (percent != lastEmitted) {
                            lastEmitted = percent
                            emit((copied.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            }
            if (!part.renameTo(modelFile)) throw IOException("Could not move the downloaded model into place")
            emit(1f)
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun import(input: InputStream) {
        val part = File(modelFile.path + ".part")
        input.use { source -> part.outputStream().use { source.copyTo(it, 1 shl 16) } }
        val magic = part.inputStream().use { it.readNBytes(4) }
        if (!magic.contentEquals("GGUF".toByteArray())) {
            part.delete()
            throw IOException("That file is not a GGUF model.")
        }
        if (!part.renameTo(modelFile)) throw IOException("Could not move the model into place")
    }

    companion object {
        const val QWEN_0_5B_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
