package com.personalai.assistant.ui.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.data.local.AppDatabase
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testSendMessage_persistsToDatabaseAndSurvivesViewModelRecreation() = runTest {
        val vm1 = ChatViewModel(repository, context)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        vm1.sendMessage("What are my notes from yesterday?")
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        var savedInDb = repository.getRecentChatContext(10)
        var attempts = 0
        while (savedInDb.isEmpty() && attempts < 20) {
            Thread.sleep(50)
            savedInDb = repository.getRecentChatContext(10)
            attempts++
        }

        assertTrue("Expected messages to be persisted in database", savedInDb.isNotEmpty())
        assertTrue("Expected user message to be recorded", savedInDb.any { it.isUser && it.content == "What are my notes from yesterday?" })

        // Recreate ViewModel to simulate closing and reopening the app
        val vm2 = ChatViewModel(repository, context)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        var restoredMessages = vm2.messages.value
        attempts = 0
        while (restoredMessages.none { it.content == "What are my notes from yesterday?" } && attempts < 20) {
            Thread.sleep(50)
            advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            restoredMessages = vm2.messages.value
            attempts++
        }

        assertTrue("Restored messages should not be empty", restoredMessages.isNotEmpty())
        assertTrue("Restored messages should contain the user prompt", restoredMessages.any { it.content == "What are my notes from yesterday?" })
    }

    @Test
    fun testClearHistory_removesMessagesFromDatabase() = runTest {
        val vm = ChatViewModel(repository, context)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        vm.sendMessage("Temporary message")
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        var saved = repository.getRecentChatContext(10)
        var attempts = 0
        while (saved.isEmpty() && attempts < 20) {
            Thread.sleep(50)
            saved = repository.getRecentChatContext(10)
            attempts++
        }

        assertTrue(saved.isNotEmpty())

        vm.clearHistory()
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        attempts = 0
        while (saved.isNotEmpty() && attempts < 20) {
            Thread.sleep(50)
            saved = repository.getRecentChatContext(10)
            attempts++
        }

        assertEquals(0, saved.size)
    }
}
