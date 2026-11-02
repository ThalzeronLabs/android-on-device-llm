package com.thalzeronlabs.ondevicellm.data

import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.Conversation
import com.thalzeronlabs.ondevicellm.chat.ConversationSummary
import com.thalzeronlabs.ondevicellm.chat.ConversationTitles
import com.thalzeronlabs.ondevicellm.chat.UiMessage
import com.thalzeronlabs.ondevicellm.data.db.ChatDao
import com.thalzeronlabs.ondevicellm.data.db.ConversationEntity
import com.thalzeronlabs.ondevicellm.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The saved conversations. Everything stays in a local database on the phone. */
interface ConversationRepository {
    /** Every conversation, most recently active first. */
    fun observeConversations(): Flow<List<ConversationSummary>>

    suspend fun conversation(id: Long): Conversation?

    suspend fun messages(conversationId: Long): List<UiMessage>

    /** Starts a conversation titled "New chat"; the first user message gives it a real title. */
    suspend fun create(settings: ChatSettings = ChatSettings()): Long

    /** Appends a message and returns its id. The first user message of an untitled conversation names it. */
    suspend fun addMessage(conversationId: Long, fromUser: Boolean, text: String): Long

    suspend fun updateMessage(messageId: Long, text: String)

    suspend fun rename(conversationId: Long, title: String)

    suspend fun updateSettings(conversationId: Long, settings: ChatSettings)

    /** Deletes the conversation and all of its messages. */
    suspend fun delete(conversationId: Long)
}

class RoomConversationRepository(
    private val dao: ChatDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ConversationRepository {

    override fun observeConversations(): Flow<List<ConversationSummary>> = dao.observeSummaries().map { rows ->
        rows.map { ConversationSummary(it.id, it.title, it.updatedAt, it.messageCount, it.lastMessage) }
    }

    override suspend fun conversation(id: Long): Conversation? = dao.conversation(id)?.let {
        Conversation(it.id, it.title, it.createdAt, it.updatedAt, ChatSettings(it.systemPrompt, it.temperature, it.maxTokens))
    }

    override suspend fun messages(conversationId: Long): List<UiMessage> =
        dao.messages(conversationId).map { UiMessage(it.fromUser, it.text, it.id) }

    override suspend fun create(settings: ChatSettings): Long {
        val now = clock()
        val s = settings.sanitized()
        return dao.insert(
            ConversationEntity(
                title = ConversationTitles.NEW_CHAT,
                createdAt = now,
                updatedAt = now,
                systemPrompt = s.systemPrompt,
                temperature = s.temperature,
                maxTokens = s.maxTokens,
            )
        )
    }

    override suspend fun addMessage(conversationId: Long, fromUser: Boolean, text: String): Long {
        val conversation = checkNotNull(dao.conversation(conversationId)) { "No conversation $conversationId" }
        val newTitle = if (fromUser && conversation.title == ConversationTitles.NEW_CHAT) {
            ConversationTitles.fromFirstMessage(text)
        } else {
            null
        }
        return dao.addMessage(MessageEntity(conversationId = conversationId, fromUser = fromUser, text = text, createdAt = clock()), newTitle)
    }

    override suspend fun updateMessage(messageId: Long, text: String) = dao.updateMessageText(messageId, text)

    override suspend fun rename(conversationId: Long, title: String) =
        dao.rename(conversationId, ConversationTitles.clean(title))

    override suspend fun updateSettings(conversationId: Long, settings: ChatSettings) {
        val s = settings.sanitized()
        dao.updateSettings(conversationId, s.systemPrompt, s.temperature, s.maxTokens)
    }

    override suspend fun delete(conversationId: Long) = dao.delete(conversationId)
}
