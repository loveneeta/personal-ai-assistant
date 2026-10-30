package com.personalai.assistant.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.data.local.AppDatabase
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.playback.AudioPlayer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Batch7FeaturesTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var ragEngine: LocalRagChatEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database, context)
        ragEngine = LocalRagChatEngine(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRagEngine_TaskQueryAndCitationsGrounded() = runTest {
        val taskId = repository.insertTask(
            TaskEntity(
                title = "Send finalized quotation to client",
                description = "Follow up on pricing review",
                status = "PENDING",
                assignedPerson = "Loveneet"
            )
        )
        assertTrue(taskId > 0)

        val response = ragEngine.answerQuery("What are my pending tasks?")
        assertFalse(response.isUser)
        assertTrue("Response must mention quotation task", response.content.contains("Send finalized quotation"))
        assertEquals(1, response.citations.size)
        val citation = response.citations[0]
        assertEquals("TASK", citation.type)
        assertEquals(taskId, citation.referenceId)
        assertEquals("Send finalized quotation to client", citation.title)
    }

    @Test
    fun testRagEngine_TranscriptSearchWithCitations() = runTest {
        val recId = repository.insertRecording(
            RecordingEntity(
                title = "Quarterly Strategy Sync",
                filePath = "/dummy/rec1.wav",
                startTimestamp = 10000L,
                durationMs = 30000L,
                status = "COMPLETED"
            )
        )
        repository.insertTranscripts(
            listOf(
                TranscriptEntity(
                    recordingId = recId,
                    segmentIndex = 0,
                    startOffsetMs = 2500L,
                    endOffsetMs = 6000L,
                    text = "We agreed that the subscription pricing model should launch in November."
                )
            )
        )

        // Query with stop words - engine must isolate substantive search term "subscription" or "pricing"
        val response = ragEngine.answerQuery("What did we say about subscription pricing?")
        assertTrue("Must ground answer in transcript text", response.content.contains("subscription pricing model"))
        assertEquals(1, response.citations.size)
        val citation = response.citations[0]
        assertEquals("RECORDING", citation.type)
        assertEquals(recId, citation.referenceId)
        assertEquals(2500L, citation.offsetMs)
    }

    @Test
    fun testRagEngine_NoMatchesReturnsHonestMessageWithoutHallucination() = runTest {
        val response = ragEngine.answerQuery("Where did we hide the secret golden key?")
        assertTrue(response.content.contains("couldn't find any information"))
        assertTrue("Zero citations on empty match", response.citations.isEmpty())
    }

    @Test
    fun testDailySummary_PreventsDuplicateNotesOnMultipleRuns() = runTest {
        val today = Date()
        val report = DailySummarySynthesizer.synthesize(
            date = today,
            todayRecordings = emptyList(),
            todayMeetings = emptyList(),
            allTasks = listOf(TaskEntity(title = "Test Task", status = "PENDING")),
            todayNotes = emptyList()
        )

        // First run: insert note
        val noteTitle = report.title
        val allNotes1 = repository.getAllNotes()
        val existing1 = allNotes1.firstOrNull { it.title == noteTitle }
        if (existing1 != null) {
            repository.updateNote(existing1.copy(content = report.markdownContent))
        } else {
            repository.insertNote(NoteEntity(title = noteTitle, content = report.markdownContent, isPinned = true))
        }

        val countAfterFirstRun = repository.getAllNotes().filter { it.title == noteTitle }.size
        assertEquals(1, countAfterFirstRun)

        // Second run on the same day: must update rather than insert duplicate
        val allNotes2 = repository.getAllNotes()
        val existing2 = allNotes2.firstOrNull { it.title == noteTitle }
        assertNotNull("Existing note must be found", existing2)
        repository.updateNote(existing2!!.copy(content = report.markdownContent + "\nUpdated content"))

        val countAfterSecondRun = repository.getAllNotes().filter { it.title == noteTitle }.size
        assertEquals("Must still have exactly 1 note for today", 1, countAfterSecondRun)

        val updatedNote = repository.getAllNotes().first { it.title == noteTitle }
        assertTrue(updatedNote.content.contains("Updated content"))
    }

    @Test
    fun testAudioPlayer_SafeStateOnMissingOrInvalidFile() {
        val player = AudioPlayer()
        assertFalse(player.isPlaying.value)
        assertEquals(0, player.currentPositionMs.value)

        // Attempt to play a non-existent file
        player.play("/path/that/does/not/exist.wav")
        assertFalse("Player must not report playing on missing file", player.isPlaying.value)
        assertEquals(null, player.currentPlayingPath.value)

        // Attempt to play a dummy corrupt file (< 44 bytes)
        val tempCorrupt = File(context.cacheDir, "corrupt_test.wav").apply {
            writeBytes(ByteArray(20))
        }
        player.play(tempCorrupt.absolutePath)
        assertFalse("Player must not report playing on corrupt file", player.isPlaying.value)

        player.release()
        tempCorrupt.delete()
    }
}
