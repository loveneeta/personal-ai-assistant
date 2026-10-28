package com.personalai.assistant.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.data.local.dao.MeetingDao
import com.personalai.assistant.data.local.dao.NoteDao
import com.personalai.assistant.data.local.dao.RecordingDao
import com.personalai.assistant.data.local.dao.TaskDao
import com.personalai.assistant.data.local.dao.TranscriptDao
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var recordingDao: RecordingDao
    private lateinit var transcriptDao: TranscriptDao
    private lateinit var meetingDao: MeetingDao
    private lateinit var taskDao: TaskDao
    private lateinit var noteDao: NoteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recordingDao = database.recordingDao()
        transcriptDao = database.transcriptDao()
        meetingDao = database.meetingDao()
        taskDao = database.taskDao()
        noteDao = database.noteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordingDao_insertAndQueryById() = runTest {
        val recording = RecordingEntity(
            title = "Project Planning Sync",
            filePath = "/data/user/0/com.personalai.assistant/files/recordings/test.wav",
            startTimestamp = 1000L,
            durationMs = 60000L,
            status = "COMPLETED",
            fileSizeBytes = 1920044L,
            mimeType = "audio/wav"
        )
        val id = recordingDao.insert(recording)
        assertTrue(id > 0)

        val retrieved = recordingDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Project Planning Sync", retrieved?.title)
        assertEquals(60000L, retrieved?.durationMs)
        assertEquals("COMPLETED", retrieved?.status)
    }

    @Test
    fun recordingDao_renameAndFinalize() = runTest {
        val recording = RecordingEntity(
            title = "Old Title",
            filePath = "/path/test.wav",
            startTimestamp = 1000L,
            status = "RECORDING"
        )
        val id = recordingDao.insert(recording)

        recordingDao.rename(id, "New Title", 2000L)
        var updated = recordingDao.getById(id)
        assertEquals("New Title", updated?.title)

        recordingDao.finalizeRecording(
            id = id,
            durationMs = 120000L,
            fileSizeBytes = 3840044L,
            endTimestamp = 3000L,
            status = "COMPLETED",
            updatedAt = 3000L
        )
        updated = recordingDao.getById(id)
        assertEquals("COMPLETED", updated?.status)
        assertEquals(120000L, updated?.durationMs)
        assertEquals(3840044L, updated?.fileSizeBytes)
    }

    @Test
    fun transcriptDao_cascadeDeleteWhenRecordingDeleted() = runTest {
        val recId = recordingDao.insert(
            RecordingEntity(
                title = "Meeting with Cascading Transcripts",
                filePath = "/path/rec.wav",
                startTimestamp = 1000L,
                status = "COMPLETED"
            )
        )

        val t1 = TranscriptEntity(
            recordingId = recId,
            segmentIndex = 0,
            startOffsetMs = 0L,
            endOffsetMs = 5000L,
            text = "Welcome everyone to today's review.",
            language = "en"
        )
        val t2 = TranscriptEntity(
            recordingId = recId,
            segmentIndex = 1,
            startOffsetMs = 5000L,
            endOffsetMs = 10000L,
            text = "We will finalize the architecture decisions.",
            language = "en"
        )
        transcriptDao.insertAll(listOf(t1, t2))

        val transcriptsBefore = transcriptDao.getByRecordingId(recId)
        assertEquals(2, transcriptsBefore.size)

        // Delete recording -> transcripts MUST be cascade deleted
        recordingDao.deleteById(recId)

        val transcriptsAfter = transcriptDao.getByRecordingId(recId)
        assertTrue(transcriptsAfter.isEmpty())
    }

    @Test
    fun transcriptDao_searchTranscriptsLike() = runTest {
        val recId = recordingDao.insert(
            RecordingEntity(
                title = "Hindi English Mix Session",
                filePath = "/path/rec.wav",
                startTimestamp = 1000L,
                status = "COMPLETED"
            )
        )

        val t1 = TranscriptEntity(
            recordingId = recId,
            segmentIndex = 0,
            startOffsetMs = 0L,
            endOffsetMs = 4000L,
            text = "Aaj ka meeting budget aur planning ke bare mein hai.",
            language = "hi"
        )
        val t2 = TranscriptEntity(
            recordingId = recId,
            segmentIndex = 1,
            startOffsetMs = 4000L,
            endOffsetMs = 8000L,
            text = "The budget is zero rupees ongoing cost.",
            language = "en"
        )
        transcriptDao.insertAll(listOf(t1, t2))

        val results = transcriptDao.searchTranscriptsLike("zero rupees")
        assertEquals(1, results.size)
        assertEquals(t2.text, results[0].text)

        val hindiResults = transcriptDao.searchTranscriptsLike("planning")
        assertEquals(1, hindiResults.size)
        assertEquals(t1.text, hindiResults[0].text)
    }

    @Test
    fun meetingsTasksNotes_preserveRecordsWhenRecordingDeleted() = runTest {
        val recId = recordingDao.insert(
            RecordingEntity(
                title = "Recorded Session",
                filePath = "/path/rec.wav",
                startTimestamp = 1000L,
                status = "COMPLETED"
            )
        )

        // Create linked meeting
        val meetingId = meetingDao.insert(
            MeetingEntity(
                title = "Strategic Roadmap Meeting",
                startTimestamp = 1000L,
                sourceRecordingId = recId
            )
        )

        // Create linked task
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Deploy On-Device Models",
                sourceMeetingId = meetingId,
                sourceRecordingId = recId
            )
        )

        // Create linked note
        val noteId = noteDao.insert(
            NoteEntity(
                title = "Architecture Decision Record",
                content = "Offline first, zero external API costs.",
                linkedRecordingId = recId,
                linkedMeetingId = meetingId
            )
        )

        // Delete recording
        recordingDao.deleteById(recId)

        // Meeting, Task, and Note MUST still exist (SET_NULL on sourceRecordingId)
        val meetingAfter = meetingDao.getById(meetingId)
        assertNotNull(meetingAfter)
        assertNull(meetingAfter?.sourceRecordingId)

        val taskAfter = taskDao.getById(taskId)
        assertNotNull(taskAfter)
        assertNull(taskAfter?.sourceRecordingId)

        val noteAfter = noteDao.getById(noteId)
        assertNotNull(noteAfter)
        assertNull(noteAfter?.linkedRecordingId)
    }
}
