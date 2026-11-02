package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Where the model files live and how they get there. Faked in unit tests. */
interface ModelStore {
    /** The models that can be downloaded. */
    val catalog: List<ModelSpec>

    /** Name of the user's imported model, or null when there is none. */
    val importedName: String?

    /** The model the user picked last, remembered across launches. */
    var selectedId: String?

    fun file(id: String): File

    fun isInstalled(id: String): Boolean = file(id).let { it.isFile && it.length() > 0 }

    /** Bytes of an unfinished download that the next [download] will continue from. */
    fun partialBytes(id: String): Long

    /** Downloads (or resumes downloading) a catalog model, then verifies its SHA-256. */
    fun download(id: String): Flow<DownloadProgress>

    /** Deletes the model file and any unfinished download of it. */
    fun delete(id: String)

    /** Copies a user-picked GGUF file into app storage, replacing the previous import. Returns its id. */
    suspend fun import(input: InputStream, name: String?): String

    companion object {
        const val IMPORTED_ID = "imported"
    }
}

/**
 * Catalog models are downloaded from Hugging Face by [ModelDownloader] into `files/models/<published file name>`;
 * the user can also bring their own `.gguf`, kept as `files/models/imported.gguf`.
 */
class FileModelStore(
    private val filesDir: File,
    override val catalog: List<ModelSpec> = ModelCatalog.all,
    private val transport: HttpTransport = UrlConnectionTransport(),
) : ModelStore {

    private val dir = File(filesDir, "models")
    private val importedLabel = File(dir, "imported.label")
    private val selectedFile = File(dir, "selected")
    private val downloaders = mutableMapOf<String, ModelDownloader>()

    init {
        dir.mkdirs()
        migrateFromV010()
    }

    override val importedName: String?
        get() = if (!isInstalled(ModelStore.IMPORTED_ID)) null
        else importedLabel.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: "Imported model"

    override var selectedId: String?
        get() = selectedFile.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
        set(value) {
            if (value == null) selectedFile.delete() else selectedFile.writeText(value)
        }

    override fun file(id: String): File =
        if (id == ModelStore.IMPORTED_ID) File(dir, "imported.gguf") else File(dir, spec(id).fileName)

    override fun partialBytes(id: String): Long =
        if (id == ModelStore.IMPORTED_ID) 0 else downloader(id).partialBytes()

    override fun download(id: String): Flow<DownloadProgress> = downloader(id).download()

    override fun delete(id: String) {
        file(id).delete()
        if (id == ModelStore.IMPORTED_ID) importedLabel.delete() else downloader(id).partial.delete()
        if (selectedId == id) selectedId = null
    }

    override suspend fun import(input: InputStream, name: String?): String = withContext(Dispatchers.IO) {
        val target = file(ModelStore.IMPORTED_ID)
        // A separate temp file, so a rejected file never replaces a working import.
        val temp = File(dir, "imported.gguf.tmp")
        try {
            input.use { source -> temp.outputStream().use { source.copyTo(it, 1 shl 16) } }
            // InputStream.readNBytes() only exists from API 33; minSdk is 26.
            val magic = ByteArray(4)
            val read = temp.inputStream().use { it.read(magic) }
            if (read != 4 || !magic.contentEquals("GGUF".toByteArray())) {
                throw IOException("That file is not a GGUF model.")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not move the model into place")
            val label = name?.removeSuffix(".gguf")?.trim().orEmpty()
            if (label.isEmpty()) importedLabel.delete() else importedLabel.writeText(label)
            ModelStore.IMPORTED_ID
        } finally {
            temp.delete()
        }
    }

    private fun spec(id: String): ModelSpec =
        catalog.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown model $id")

    private fun downloader(id: String): ModelDownloader = synchronized(downloaders) {
        downloaders.getOrPut(id) { spec(id).let { ModelDownloader(it, File(dir, it.fileName), transport) } }
    }

    /**
     * v0.1.0 kept a single model at `files/model.gguf`: the default Qwen download, or an import when
     * `files/model.label` existed. Move it (and an unfinished download) to the new layout so nothing is
     * downloaded twice.
     */
    private fun migrateFromV010() {
        val legacy = File(filesDir, "model.gguf")
        val legacyLabel = File(filesDir, "model.label")
        val legacyPartial = File(filesDir, "model.gguf.part")
        val qwen = catalog.firstOrNull { it.id == ModelCatalog.QWEN_2_5_0_5B.id }
        if (legacy.isFile) {
            if (legacyLabel.isFile) {
                legacy.renameTo(file(ModelStore.IMPORTED_ID))
                legacyLabel.renameTo(importedLabel)
                selectedId = ModelStore.IMPORTED_ID
            } else if (qwen != null) {
                legacy.renameTo(File(dir, qwen.fileName))
                selectedId = qwen.id
            }
        }
        if (legacyPartial.isFile && qwen != null) legacyPartial.renameTo(File(dir, qwen.fileName + ".part"))
        legacyLabel.delete()
    }
}
