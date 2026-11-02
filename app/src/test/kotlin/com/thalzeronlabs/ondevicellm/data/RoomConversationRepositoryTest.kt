package com.thalzeronlabs.ondevicellm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.thalzeronlabs.ondevicellm.chat.ChatSettings
import com.thalzeronlabs.ondevicellm.chat.ConversationTitles
import com.thalzeronlabs.ondevicellm.data.db.ChatDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real Room database (in memory) behind the repository. */
@RunWith(AndroidJUnit4::class)
class RoomConversationRepositoryTest {

    private lateinit var db: ChatDatabase
    private lateinit var repo: RoomConversationRepository
    private var now = 1_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomConversationRepository(db.chatDao()) { now }
    }

    @After fun tearDown() = db.close()

    @Test
    fun `a new conversation is untitled until its first user message`() = runTest {
        val id = repo.create()
        assertEquals(ConversationTitles.NEW_CHAT, repo.conversation(id)!!.title)

        repo.addMessage(id, fromUser = true, text = "How do phones run language models offline?")
        repo.addMessage(id, fromUser = false, text = "With quantized weights.")
        repo.addMessage(id, fromUser = true, text = "Tell me more")

        assertEquals("How do phones run language models…", repo.conversation(id)!!.title)
    }

    @Test
    fun `messages come back in order with their ids`() = runTest {
        val id = repo.create()
        val a = repo.addMessage(id, true, "one")
        val b = repo.addMessage(id, false, "two")
        val messages = repo.messages(id)
        assertEquals(listOf("one", "two"), messages.map { it.text })
        assertEquals(listOf(true, false), messages.map { it.fromUser })
        assertEquals(listOf(a, b), messages.map { it.id })

        repo.updateMessage(b, "two, edited")
        assertEquals("two, edited", repo.messages(id).last().text)
    }

    @Test
    fun `the list is most recently active first, with counts and the last message`() = runTest {
        val old = repo.create()
        now = 2_000
        val recent = repo.create()
        repo.conversations().test {
            assertEquals(listOf(recent, old), awaitItem().map { it.id })

            now = 3_000
            repo.addMessage(old, true, "I'm back")
            val list = awaitItem()
            assertEquals(listOf(old, recent), list.map { it.id })
            val summary = list.first()
            assertEquals(1, summary.messageCount)
            assertEquals("I'm back", summary.lastMessage)
            assertEquals(3_000L, summary.updatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rename cleans the title and an empty name falls back`() = runTest {
        val id = repo.create()
        repo.rename(id, "   Trip   to   Lisbon ")
        assertEquals("Trip to Lisbon", repo.conversation(id)!!.title)
        repo.rename(id, "   ")
        assertEquals(ConversationTitles.NEW_CHAT, repo.conversation(id)!!.title)
    }

    @Test
    fun `a renamed conversation keeps its name after the next message`() = runTest {
        val id = repo.create()
        repo.rename(id, "Mine")
        repo.addMessage(id, true, "first message")
        assertEquals("Mine", repo.conversation(id)!!.title)
    }

    @Test
    fun `settings are stored per conversation, clamped`() = runTest {
        val a = repo.create(ChatSettings("Be a pirate.", 1.1f, 256))
        val b = repo.create()
        repo.updateSettings(b, ChatSettings("  Short.  ", -3f, 99_999))

        assertEquals(ChatSettings("Be a pirate.", 1.1f, 256), repo.conversation(a)!!.settings)
        assertEquals(ChatSettings("Short.", 0f, 2048), repo.conversation(b)!!.settings)
    }

    @Test
    fun `delete removes the conversation and its messages`() = runTest {
        val id = repo.create()
        repo.addMessage(id, true, "bye")
        val other = repo.create()
        repo.addMessage(other, true, "stay")

        repo.delete(id)

        assertNull(repo.conversation(id))
        assertTrue(repo.messages(id).isEmpty())
        assertEquals(listOf("stay"), repo.messages(other).map { it.text })
        assertEquals(1, db.chatDao().messagesCount())
    }

    private fun RoomConversationRepository.conversations() = observeConversations()
}
