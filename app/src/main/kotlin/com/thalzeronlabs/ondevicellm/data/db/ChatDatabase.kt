package com.thalzeronlabs.ondevicellm.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long,
)

/** Result row of [ChatDao.observeSummaries]. */
data class SummaryRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    @ColumnInfo(name = "messageCount") val messageCount: Int,
    @ColumnInfo(name = "lastMessage") val lastMessage: String?,
)

@Dao
interface ChatDao {

    @Query(
        """
        SELECT c.id, c.title, c.updatedAt,
            (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount,
            (SELECT m.text FROM messages m WHERE m.conversationId = c.id ORDER BY m.id DESC LIMIT 1) AS lastMessage
        FROM conversations c
        ORDER BY c.updatedAt DESC, c.id DESC
        """
    )
    fun observeSummaries(): Flow<List<SummaryRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id")
    suspend fun messages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun messagesCount(): Int

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET text = :text WHERE id = :id")
    suspend fun updateMessageText(id: Long, text: String)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE conversations SET systemPrompt = :systemPrompt, temperature = :temperature, maxTokens = :maxTokens WHERE id = :id")
    suspend fun updateSettings(id: Long, systemPrompt: String, temperature: Float, maxTokens: Int)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    /** Adds a message and moves its conversation to the top of the list, atomically. */
    @Transaction
    suspend fun addMessage(message: MessageEntity, newTitle: String?): Long {
        val id = insert(message)
        touch(message.conversationId, message.createdAt)
        if (newTitle != null) rename(message.conversationId, newTitle)
        return id
    }
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, "conversations.db").build()
    }
}
