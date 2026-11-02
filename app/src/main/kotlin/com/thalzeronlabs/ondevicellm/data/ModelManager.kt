package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

/** Where one model of the catalog stands on this phone. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Downloading(val progress: Float, val bytes: Long, val total: Long, val resumed: Boolean = false) :
        ModelStatus
    /** An unfinished download is on disk; [ModelManager.download] continues it. */
    data class Paused(val bytes: Long, val total: Long) : ModelStatus
    /** The file is downloaded and its SHA-256 is being checked. */
    data class Verifying(val progress: Float) : ModelStatus
    data object Installed : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** A row of the model picker. */
data class ModelItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val status: ModelStatus,
    val description: String = "",
    val license: String = "",
    val licenseUrl: String = "",
    val pageUrl: String = "",
    val imported: Boolean = false,
)

/** The model loaded in the engine, the one the chat talks to. */
sealed interface EngineState {
    data object NoModel : EngineState
    data class Loading(val id: String, val name: String) : EngineState
    data class Ready(val id: String, val name: String, val sizeBytes: Long) : EngineState
    data class Failed(val id: String, val message: String) : EngineState
}

data class ModelsState(
    val items: List<ModelItem> = emptyList(),
    val engine: EngineState = EngineState.NoModel,
) {
    val activeId: String? get() = (engine as? EngineState.Ready)?.id ?: (engine as? EngineState.Loading)?.id
    val anyDownloading: Boolean get() = items.any { it.status is ModelStatus.Downloading || it.status is ModelStatus.Verifying }
    fun item(id: String): ModelItem? = items.firstOrNull { it.id == id }
}

/**
 * Owns the engine and the model files: downloads (one at a time, resumable, verified), deletes, imports, and
 * switches the model the chat uses without restarting the app.
 *
 * Lives as long as the app ([scope] is app-wide), so a download keeps going when the screen is rotated.
 */
class ModelManager(
    private val store: ModelStore,
    private val engine: ChatEngine,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ModelsState(items = store.catalog.map { it.toItem(statusOnDisk(it.id)) }))
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadingId: String? = null
    private var loadJob: Job? = null
    private var started = false

    init {
        refreshImported()
    }

    /**
     * Loads the model picked last time (or any installed one). With nothing installed and [autoDownload] (on
     * Wi-Fi), starts downloading the default model. Safe to call more than once.
     */
    fun start(autoDownload: Boolean) {
        if (started) return
        started = true
        val installed = _state.value.items.filter { it.status == ModelStatus.Installed }.map { it.id }
        val pick = store.selectedId?.takeIf { it in installed } ?: installed.firstOrNull()
        when {
            pick != null -> select(pick)
            autoDownload && store.catalog.any { it.id == ModelCatalog.default.id } -> download(ModelCatalog.default.id)
        }
    }

    /** Downloads [id], continuing an unfinished download. Pauses any other download first. */
    fun download(id: String) {
        if (downloadingId == id && downloadJob?.isActive == true) return
        downloadingId?.let { pause(it) }
        downloadingId = id
        downloadJob = scope.launch {
            try {
                store.download(id).collect { p ->
                    setStatus(
                        id,
                        when (p) {
                            is DownloadProgress.Transferring ->
                                ModelStatus.Downloading(p.fraction, p.bytes, p.total, resumed = p.resumedFrom > 0)
                            is DownloadProgress.Verifying -> ModelStatus.Verifying(p.fraction)
                        },
                    )
                }
                setStatus(id, ModelStatus.Installed)
                // The first model on the phone is used right away; later ones wait for the user to pick them.
                if (_state.value.engine !is EngineState.Ready && _state.value.engine !is EngineState.Loading) select(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(id, ModelStatus.Failed("Download failed: ${e.message}"))
            } finally {
                if (downloadingId == id) downloadingId = null
            }
        }
    }

    /** Stops the download; the bytes already received are kept for [download] to continue from. */
    fun pause(id: String) {
        if (downloadingId != id) return
        downloadJob?.cancel()
        downloadJob = null
        downloadingId = null
        setStatus(id, statusOnDisk(id))
    }

    /** Loads [id] into the engine, replacing the current model. Generation must be stopped first. */
    fun select(id: String) {
        val item = _state.value.item(id) ?: return
        if (item.status != ModelStatus.Installed) return
        if (_state.value.activeId == id && _state.value.engine is EngineState.Ready) return
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(engine = EngineState.Loading(id, item.name)) }
            try {
                engine.load(store.file(id))
                store.selectedId = id
                _state.update { it.copy(engine = EngineState.Ready(id, item.name, store.file(id).length())) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(engine = EngineState.Failed(id, "Could not load ${item.name}: ${e.message}")) }
            }
        }
    }

    /** Deletes a downloaded (or partly downloaded) model. Deleting the active model unloads it. */
    fun delete(id: String) {
        pause(id)
        if (_state.value.activeId == id) {
            loadJob?.cancel()
            engine.close()
            _state.update { it.copy(engine = EngineState.NoModel) }
        }
        store.delete(id)
        if (id == ModelStore.IMPORTED_ID) refreshImported() else setStatus(id, statusOnDisk(id))
    }

    /** Copies a user-picked `.gguf` into the app and switches to it. */
    fun import(input: InputStream, name: String?) {
        scope.launch {
            try {
                val id = store.import(input, name)
                refreshImported()
                // Re-load even if the imported slot was already active: its file has changed.
                if (_state.value.activeId == id) _state.update { it.copy(engine = EngineState.NoModel) }
                select(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(engine = EngineState.Failed(ModelStore.IMPORTED_ID, e.message ?: "Could not import the file."))
                }
            }
        }
    }

    private fun statusOnDisk(id: String): ModelStatus = when {
        store.isInstalled(id) -> ModelStatus.Installed
        store.partialBytes(id) > 0 ->
            ModelStatus.Paused(store.partialBytes(id), store.catalog.firstOrNull { it.id == id }?.sizeBytes ?: 0)
        else -> ModelStatus.NotDownloaded
    }

    private fun setStatus(id: String, status: ModelStatus) = _state.update { s ->
        s.copy(items = s.items.map { if (it.id == id) it.copy(status = status) else it })
    }

    private fun refreshImported() = _state.update { s ->
        val catalogItems = s.items.filterNot { it.imported }
        val name = store.importedName
        val imported = name?.let {
            ModelItem(
                id = ModelStore.IMPORTED_ID,
                name = it,
                sizeBytes = store.file(ModelStore.IMPORTED_ID).length(),
                status = ModelStatus.Installed,
                description = "Your own .gguf file",
                imported = true,
            )
        }
        s.copy(items = catalogItems + listOfNotNull(imported))
    }

    private fun ModelSpec.toItem(status: ModelStatus) = ModelItem(
        id = id,
        name = displayName,
        sizeBytes = sizeBytes,
        status = status,
        description = description,
        license = license,
        licenseUrl = licenseUrl,
        pageUrl = pageUrl,
    )
}
