package com.thalzeronlabs.ondevicellm.fakes

import com.thalzeronlabs.llama.ChatMessage
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.Conversation
import com.thalzeronlabs.ondevicellm.chat.ConversationSummary
import com.thalzeronlabs.ondevicellm.chat.ConversationTitles
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.ChatEngine
import com.thalzeronlabs.ondevicellm.data.ConversationRepository
import com.thalzeronlabs.ondevicellm.data.DownloadProgress
import com.thalzeronlabs.ondevicellm.data.GenerationOptions
import com.thalzeronlabs.ondevicellm.data.ModelSpec
import com.thalzeronlabs.ondevicellm.data.ModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream

class FakeEngine(var answer: () -> Flow<String> = { flowOf("Hel", "lo", "!") }) : ChatEngine {
    val histories = mutableListOf<List<ChatMessage>>()
    val options = mutableListOf<GenerationOptions>()
    val loaded = mutableListOf<File>()
    var failLoad = false
    var closed = 0
    override suspend fun load(file: File) {
        if (failLoad) error("bad file")
        loaded += file
    }
    override fun reply(history: List<ChatMessage>, options: GenerationOptions): Flow<String> {
        histories += history
        this.options += options
        return answer()
    }
    override val lastTokensPerSecond: Double? = 12.5
    override fun close() {
        closed++
    }
}

fun spec(id: String, size: Long = 5_000_000) = ModelSpec(
    displayName = "Model $id",
    url = "https://example.test/$id.gguf",
    sizeBytes = size,
    sha256 = "0".repeat(64),
    id = id,
    license = "Apache-2.0",
)

/** Model files in a temp folder; downloads are scripted per model. */
class FakeModelStore(
    private val dir: File,
    override val catalog: List<ModelSpec> = listOf(spec("small"), spec("big", 10_000_000)),
) : ModelStore {
    val partial = mutableMapOf<String, Long>()
    val downloads = mutableMapOf<String, Flow<DownloadProgress>>()
    val deleted = mutableListOf<String>()
    override var importedName: String? = null
    override var selectedId: String? = null

    override fun file(id: String) = File(dir, "$id.gguf")
    override fun partialBytes(id: String) = partial[id] ?: 0

    fun install(id: String) = file(id).writeText("GGUF")

    override fun download(id: String): Flow<DownloadProgress> = downloads[id] ?: flow {
        val total = catalog.first { it.id == id }.sizeBytes
        emit(DownloadProgress.Transferring(total / 2, total, resumedFrom = partialBytes(id)))
        emit(DownloadProgress.Transferring(total, total, resumedFrom = partialBytes(id)))
        emit(DownloadProgress.Verifying(total, total))
        install(id)
    }

    override fun delete(id: String) {
        deleted += id
        file(id).delete()
        partial.remove(id)
        if (id == ModelStore.IMPORTED_ID) importedName = null
        if (selectedId == id) selectedId = null
    }

    override suspend fun import(input: InputStream, name: String?): String {
        val bytes = input.readBytes()
        if (!String(bytes).startsWith("GGUF")) throw java.io.IOException("That file is not a GGUF model.")
        file(ModelStore.IMPORTED_ID).writeBytes(bytes)
        importedName = name ?: "Imported model"
        return ModelStore.IMPORTED_ID
    }
}

/** The repository contract, in memory. */
class InMemoryConversationRepository(private val clock: () -> Long = { 1_000L }) : ConversationRepository {
    private data class Row(val conversation: Conversation, val messages: List<UiMessage>)
    private val rows = MutableStateFlow<Map<Long, Row>>(emptyMap())
    private var nextId = 1L
    private var nextMessageId = 1L

    override fun observeConversations(): Flow<List<ConversationSummary>> = rows.map { map ->
        map.values.sortedWith(compareByDescending<Row> { it.conversation.updatedAt }.thenByDescending { it.conversation.id })
            .map {
                ConversationSummary(
                    it.conversation.id, it.conversation.title, it.conversation.updatedAt,
                    it.messages.size, it.messages.lastOrNull()?.text,
                )
            }
    }

    override suspend fun conversation(id: Long) = rows.value[id]?.conversation
    override suspend fun messages(conversationId: Long) = rows.value[conversationId]?.messages.orEmpty()

    override suspend fun create(settings: ChatSettings): Long {
        val id = nextId++
        val now = clock()
        rows.value += id to Row(Conversation(id, ConversationTitles.NEW_CHAT, now, now, settings.sanitized()), emptyList())
        return id
    }

    override suspend fun addMessage(conversationId: Long, fromUser: Boolean, text: String): Long {
        val row = checkNotNull(rows.value[conversationId])
        val id = nextMessageId++
        val title = if (fromUser && row.conversation.title == ConversationTitles.NEW_CHAT) {
            ConversationTitles.fromFirstMessage(text)
        } else {
            row.conversation.title
        }
        rows.value += conversationId to Row(
            row.conversation.copy(title = title, updatedAt = clock()),
            row.messages + UiMessage(fromUser, text, id),
        )
        return id
    }

    override suspend fun updateMessage(messageId: Long, text: String) {
        rows.value = rows.value.mapValues { (_, row) ->
            row.copy(messages = row.messages.map { if (it.id == messageId) it.copy(text = text) else it })
        }
    }

    override suspend fun rename(conversationId: Long, title: String) {
        val row = rows.value[conversationId] ?: return
        rows.value += conversationId to row.copy(conversation = row.conversation.copy(title = ConversationTitles.clean(title)))
    }

    override suspend fun updateSettings(conversationId: Long, settings: ChatSettings) {
        val row = rows.value[conversationId] ?: return
        rows.value += conversationId to row.copy(conversation = row.conversation.copy(settings = settings.sanitized()))
    }

    override suspend fun delete(conversationId: Long) {
        rows.value -= conversationId
    }
}

/**
 * An app-wide scope on the test scheduler. Not `backgroundScope`: `advanceUntilIdle()` skips background work,
 * and not the test scope itself, which would wait forever for a paused download.
 */
fun kotlinx.coroutines.test.TestScope.managerScope(): kotlinx.coroutines.CoroutineScope =
    kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.test.StandardTestDispatcher(testScheduler) + kotlinx.coroutines.SupervisorJob()
    )
