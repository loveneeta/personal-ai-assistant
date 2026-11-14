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
import com.personalai.assistant.data.local.entity.RecordingStatus
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TaskStatus
import com.personalai.assistant.data.local.entity.TranscriptEntity
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
            status = RecordingStatus.COMPLETED,
            fileSizeBytes = 1920044L,
            mimeType = "audio/wav"
        )
        val id = recordingDao.insert(recording)
        assertTrue(id > 0)

        val retrieved = recordingDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Project Planning Sync", retrieved?.title)
        assertEquals(60000L, retrieved?.durationMs)
        assertEquals(RecordingStatus.COMPLETED, retrieved?.status)
    }

    @Test
    fun recordingDao_renameAndFinalize() = runTest {
        val recording = RecordingEntity(
            title = "Old Title",
            filePath = "/path/test.wav",
            startTimestamp = 1000L,
            status = RecordingStatus.RECORDING
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
            status = RecordingStatus.COMPLETED,
            updatedAt = 3000L
        )
        updated = recordingDao.getById(id)
        assertEquals(RecordingStatus.COMPLETED, updated?.status)
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
                status = RecordingStatus.COMPLETED
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
                status = RecordingStatus.COMPLETED
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
                status = RecordingStatus.COMPLETED
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

        // Meeting, Task, and Note MUST still exist even after recording is deleted
        val meetingAfter = meetingDao.getById(meetingId)
        assertNotNull(meetingAfter)

        val taskAfter = taskDao.getById(taskId)
        assertNotNull(taskAfter)

        val noteAfter = noteDao.getById(noteId)
        assertNotNull(noteAfter)
    }

    @Test
    fun taskDao_toggleTaskCompletedState() = runTest {
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Review PR",
                status = TaskStatus.PENDING
            )
        )

        var task = taskDao.getById(taskId)
        assertEquals(TaskStatus.PENDING, task?.status)

        taskDao.updateStatus(taskId, TaskStatus.COMPLETED)
        task = taskDao.getById(taskId)
        assertEquals(TaskStatus.COMPLETED, task?.status)
    }

    @Test
    fun database_cleanInstallVerifiesIntegrityWithoutDestructiveFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "clean_test.db"
        context.deleteDatabase(dbName)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM room_master_table")
        assertTrue(cursor.moveToFirst())
        val identityHash = cursor.getString(cursor.getColumnIndexOrThrow("identity_hash"))
        assertEquals("fb80d5bdf4e5d90dccb6d2b81f723f17", identityHash)
        cursor.close()
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun database_upgradeFromV1ToV2PreservesDataAndIntegrity() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "upgrade_test.db"
        context.deleteDatabase(dbName)

        // 1. Create a version 1 database directly with SQLite
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recordings` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `filePath` TEXT NOT NULL,
                            `startTimestamp` INTEGER NOT NULL,
                            `endTimestamp` INTEGER,
                            `durationMs` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `fileSizeBytes` INTEGER NOT NULL,
                            `mimeType` TEXT NOT NULL,
                            `processingStatus` TEXT NOT NULL,
                            `errorDetails` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_startTimestamp` ON `recordings` (`startTimestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_status` ON `recordings` (`status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_processingStatus` ON `recordings` (`processingStatus`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `meetings` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `startTimestamp` INTEGER NOT NULL,
                            `endTimestamp` INTEGER,
                            `summary` TEXT,
                            `sourceRecordingId` INTEGER,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            FOREIGN KEY(`sourceRecordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_meetings_sourceRecordingId` ON `meetings` (`sourceRecordingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_meetings_startTimestamp` ON `meetings` (`startTimestamp`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `tasks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT,
                            `sourceMeetingId` INTEGER,
                            `sourceRecordingId` INTEGER,
                            `dueDate` INTEGER,
                            `assignedPerson` TEXT,
                            `status` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            FOREIGN KEY(`sourceMeetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                            FOREIGN KEY(`sourceRecordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_sourceMeetingId` ON `tasks` (`sourceMeetingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_sourceRecordingId` ON `tasks` (`sourceRecordingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueDate` ON `tasks` (`dueDate`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transcripts` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `recordingId` INTEGER NOT NULL,
                            `segmentIndex` INTEGER NOT NULL,
                            `startOffsetMs` INTEGER NOT NULL,
                            `endOffsetMs` INTEGER NOT NULL,
                            `text` TEXT NOT NULL,
                            `language` TEXT NOT NULL,
                            `confidence` REAL,
                            `reviewStatus` TEXT NOT NULL,
                            FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_recordingId` ON `transcripts` (`recordingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_startOffsetMs` ON `transcripts` (`startOffsetMs`)")

                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `transcripts_fts` USING FTS4(`text` TEXT, `language` TEXT, content=`transcripts`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notes` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `linkedRecordingId` INTEGER,
                            `linkedMeetingId` INTEGER,
                            `isPinned` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            FOREIGN KEY(`linkedRecordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                            FOREIGN KEY(`linkedMeetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_linkedRecordingId` ON `notes` (`linkedRecordingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_linkedMeetingId` ON `notes` (`linkedMeetingId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isPinned` ON `notes` (`isPinned`)")

                    // Insert room master table for v1
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1_hash')")

                    db.execSQL("INSERT INTO recordings (id, title, filePath, startTimestamp, durationMs, status, fileSizeBytes, mimeType, processingStatus, createdAt, updatedAt) VALUES (1, 'V1 Rec', '/path', 1000, 5000, 'COMPLETED', 1024, 'audio/wav', 'COMPLETED', 1000, 1000)")
                    db.execSQL("INSERT INTO tasks (id, title, status, createdAt) VALUES (1, 'V1 Task', 'PENDING', 1000)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val sqliteDb = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(helperConfig).writableDatabase
        sqliteDb.close()

        // 2. Open with AppDatabase (version 2) with MIGRATION_1_2 without destructive fallback
        val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        val task = upgradedDb.taskDao().getById(1)
        assertNotNull(task)
        assertEquals("V1 Task", task?.title)
        assertEquals(TaskStatus.PENDING, task?.status)

        val rec = upgradedDb.recordingDao().getById(1)
        assertNotNull(rec)
        assertEquals("V1 Rec", rec?.title)

        // Wake events table must exist and be writable
        val wakeCursor = upgradedDb.openHelper.readableDatabase.query("SELECT count(*) FROM wake_events")
        assertTrue(wakeCursor.moveToFirst())
        assertEquals(0, wakeCursor.getInt(0))
        wakeCursor.close()

        upgradedDb.close()
        context.deleteDatabase(dbName)
    }
}
