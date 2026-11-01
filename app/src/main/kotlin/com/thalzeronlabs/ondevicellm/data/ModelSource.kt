package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Where the model file lives and how it gets there. */
interface ModelSource {
    val modelFile: File

    /** Name of the model on disk, or of the one that will be downloaded. */
    val displayName: String

    /** Size of the default download. */
    val approximateBytes: Long

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() > 0

    /** Bytes of an unfinished download that the next [download] will continue from. */
    fun partialBytes(): Long = 0

    /** Downloads (or resumes downloading) the default model, then verifies it. */
    fun download(): Flow<DownloadProgress>

    /** Copies a user-picked GGUF file into app storage, replacing the current model. */
    suspend fun import(input: InputStream, name: String? = null)
}

/**
 * The default model comes from Hugging Face through [ModelDownloader]; the user can also bring their own `.gguf`.
 * Whatever is installed lives at `model.gguf` in app-private storage.
 */
class HttpModelSource(
    filesDir: File,
    private val spec: ModelSpec = ModelSpec.QWEN_2_5_0_5B_Q4_K_M,
    transport: HttpTransport = UrlConnectionTransport(),
) : ModelSource {

    override val modelFile = File(filesDir, "model.gguf")

    /** Holds the name of an imported model; absent when the default model is installed. */
    private val labelFile = File(filesDir, "model.label")

    private val downloader = ModelDownloader(spec, modelFile, transport)

    override val displayName: String
        get() = labelFile.takeIf { isReady() && it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: spec.displayName

    override val approximateBytes: Long = spec.sizeBytes

    override fun partialBytes(): Long = downloader.partialBytes()

    override fun download(): Flow<DownloadProgress> {
        labelFile.delete()
        return downloader.download()
    }

    override suspend fun import(input: InputStream, name: String?): Unit = withContext(Dispatchers.IO) {
        // A separate temp file, so importing never clobbers a half-finished download.
        val temp = File(modelFile.path + ".import")
        try {
            input.use { source -> temp.outputStream().use { source.copyTo(it, 1 shl 16) } }
            // InputStream.readNBytes() only exists from API 33; minSdk is 26.
            val magic = ByteArray(4)
            val read = temp.inputStream().use { it.read(magic) }
            if (read != 4 || !magic.contentEquals("GGUF".toByteArray())) {
                throw IOException("That file is not a GGUF model.")
            }
            if (modelFile.exists()) modelFile.delete()
            if (!temp.renameTo(modelFile)) throw IOException("Could not move the model into place")
            val label = name?.removeSuffix(".gguf")?.trim().orEmpty()
            if (label.isEmpty()) labelFile.delete() else labelFile.writeText(label)
        } finally {
            temp.delete()
        }
    }
}
