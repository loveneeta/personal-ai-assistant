package com.personalai.assistant.backup

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
import com.personalai.assistant.retention.AudioRetentionPolicy
import com.personalai.assistant.retention.RetentionManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_AesGcmEncryptionDecryptionRoundTrip() {
        val testData = "{\"message\":\"Zero cost offline encrypted personal backup\"}".toByteArray(StandardCharsets.UTF_8)
        val passphrase = "SecurePassword123!"

        // 1. Encrypt
        val random = SecureRandom()
        val salt = ByteArray(16).apply { random.nextBytes(this) }
        val iv = ByteArray(12).apply { random.nextBytes(this) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(testData)

        // 2. Decrypt with correct passphrase
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decryptedBytes = decryptCipher.doFinal(cipherText)

        assertEquals(String(testData, StandardCharsets.UTF_8), String(decryptedBytes, StandardCharsets.UTF_8))

        // 3. Decrypt with wrong passphrase must fail
        val wrongSpec = PBEKeySpec("WrongPassphrase".toCharArray(), salt, 65536, 256)
        val wrongKey = SecretKeySpec(factory.generateSecret(wrongSpec).encoded, "AES")
        val wrongCipher = Cipher.getInstance("AES/GCM/NoPadding")
        wrongCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, iv))

        var failed = false
        try {
            wrongCipher.doFinal(cipherText)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Expected decryption with wrong passphrase to throw an exception", failed)
    }

    @Test
    fun test2_FullBackupAndRestoreRoundTrip_preservesEntitiesAndRelationships() = runTest {
        // 1. Populate full relational data
        val rec1Id = repository.insertRecording(
            RecordingEntity(
                title = "Q4 Financial Review",
                filePath = "/test/rec1.wav",
                startTimestamp = 1730000000000L,
                endTimestamp = 1730000600000L,
                durationMs = 600000L,
                status = "COMPLETED",
                fileSizeBytes = 1920044L,
                processingStatus = "COMPLETED"
            )
        )
        val rec2Id = repository.insertRecording(
            RecordingEntity(
                title = "Hinglish Client Discussion",
                filePath = "/test/rec2.wav",
                startTimestamp = 1730010000000L,
                endTimestamp = 1730010300000L,
                durationMs = 300000L,
                status = "COMPLETED",
                fileSizeBytes = 960044L,
                processingStatus = "COMPLETED"
            )
        )

        val t1 = TranscriptEntity(
            recordingId = rec1Id,
            segmentIndex = 0,
            startOffsetMs = 0L,
            endOffsetMs = 3000L,
            text = "Welcome to the financial review.",
            language = "en",
            confidence = 0.99f,
            reviewStatus = "VERIFIED"
        )
        val t2 = TranscriptEntity(
            recordingId = rec2Id,
            segmentIndex = 0,
            startOffsetMs = 0L,
            endOffsetMs = 4500L,
            text = "Kal customer ki meeting hai, please quotation ready kar dena.",
            language = "hi-en",
            confidence = 0.98f,
            reviewStatus = "VERIFIED"
        )
        repository.insertTranscripts(listOf(t1, t2))

        val meetId = repository.insertMeeting(
            MeetingEntity(
                title = "Financial Review Sync",
                startTimestamp = 1730000000000L,
                endTimestamp = 1730000600000L,
                summary = "Approved budget allocations.",
                sourceRecordingId = rec1Id
            )
        )

        val taskId = repository.insertTask(
            TaskEntity(
                title = "Email quotation to client",
                description = "Send before 5 PM",
                sourceMeetingId = meetId,
                sourceRecordingId = rec2Id,
                dueDate = 1730040000000L,
                assignedPerson = "Sales Lead",
                status = "PENDING"
            )
        )

        val noteId = repository.insertNote(
            NoteEntity(
                title = "Client Pricing Notes",
                content = "Agreed to 10% discount on early sign-off.",
                linkedRecordingId = rec2Id,
                linkedMeetingId = meetId,
                isPinned = true
            )
        )

        // 2. Create encrypted backup
        val passphrase = "MasterPassphrase2026!"
        val backupResult = BackupManager.createEncryptedBackup(context, repository, passphrase)
        assertTrue(backupResult.success)
        val backupFile = backupResult.file
        assertNotNull(backupFile)
        assertTrue(backupFile!!.exists())

        // 3. Clear database to simulate restore onto clean install or new device
        repository.clearAllData()
        assertTrue(repository.getAllRecordings().isEmpty())
        assertTrue(repository.getAllTranscripts().isEmpty())
        assertTrue(repository.getAllMeetings().isEmpty())
        assertTrue(repository.getAllTasks().isEmpty())
        assertTrue(repository.getAllNotes().isEmpty())

        // 4. Restore from encrypted backup
        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupFile, passphrase)
        assertTrue(restoreResult.success)
        assertTrue(restoreResult.message.contains("Restored 2 recordings"))

        // 5. Verify data integrity & reconstructed relationships
        val restoredRecs = repository.getAllRecordings()
        assertEquals(2, restoredRecs.size)
        val restoredRec1 = restoredRecs.find { it.title == "Q4 Financial Review" }!!
        val restoredRec2 = restoredRecs.find { it.title == "Hinglish Client Discussion" }!!

        assertEquals(600000L, restoredRec1.durationMs)
        assertEquals("COMPLETED", restoredRec1.processingStatus)

        // Transcripts
        val rec1Transcripts = repository.getTranscripts(restoredRec1.id)
        assertEquals(1, rec1Transcripts.size)
        assertEquals("Welcome to the financial review.", rec1Transcripts[0].text)
        assertEquals("en", rec1Transcripts[0].language)

        val rec2Transcripts = repository.getTranscripts(restoredRec2.id)
        assertEquals(1, rec2Transcripts.size)
        assertEquals("Kal customer ki meeting hai, please quotation ready kar dena.", rec2Transcripts[0].text)
        assertEquals("hi-en", rec2Transcripts[0].language)

        // Meetings with reconnected sourceRecordingId
        val restoredMeetings = repository.getAllMeetings()
        assertEquals(1, restoredMeetings.size)
        val restoredMeeting = restoredMeetings[0]
        assertEquals("Financial Review Sync", restoredMeeting.title)
        assertEquals(restoredRec1.id, restoredMeeting.sourceRecordingId)

        // Tasks with reconnected sourceMeetingId and sourceRecordingId
        val restoredTasks = repository.getAllTasks()
        assertEquals(1, restoredTasks.size)
        val restoredTask = restoredTasks[0]
        assertEquals("Email quotation to client", restoredTask.title)
        assertEquals(restoredMeeting.id, restoredTask.sourceMeetingId)
        assertEquals(restoredRec2.id, restoredTask.sourceRecordingId)

        // Notes with reconnected linkedRecordingId and linkedMeetingId
        val restoredNotes = repository.getAllNotes()
        assertEquals(1, restoredNotes.size)
        val restoredNote = restoredNotes[0]
        assertEquals("Client Pricing Notes", restoredNote.title)
        assertEquals(restoredRec2.id, restoredNote.linkedRecordingId)
        assertEquals(restoredMeeting.id, restoredNote.linkedMeetingId)
        assertTrue(restoredNote.isPinned)
    }

    @Test
    fun test3_WrongPassphrase_authenticationFailsGracefully() = runTest {
        repository.insertRecording(
            RecordingEntity(title = "Secret Session", filePath = "/test.wav", startTimestamp = 1000L)
        )
        val backupResult = BackupManager.createEncryptedBackup(context, repository, "CorrectPass123")
        assertTrue(backupResult.success)

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupResult.file!!, "WrongPassphrase!")
        assertFalse("Restore with wrong passphrase must fail", restoreResult.success)
        assertTrue(restoreResult.message.contains("Authentication failed"))
    }

    @Test
    fun test4_TamperedCiphertext_authenticationFailsGracefully() = runTest {
        repository.insertRecording(
            RecordingEntity(title = "Tamper Test", filePath = "/test.wav", startTimestamp = 1000L)
        )
        val backupResult = BackupManager.createEncryptedBackup(context, repository, "CorrectPass123")
        assertTrue(backupResult.success)
        val backupFile = backupResult.file!!

        // Flip 1 byte in the ciphertext portion
        val bytes = backupFile.readBytes()
        bytes[bytes.lastIndex - 5] = (bytes[bytes.lastIndex - 5].toInt() xor 0xFF).toByte()
        backupFile.writeBytes(bytes)

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupFile, "CorrectPass123")
        assertFalse("Tampered ciphertext must fail authentication", restoreResult.success)
        assertTrue(restoreResult.message.contains("Authentication failed") || restoreResult.message.contains("failed"))
    }

    @Test
    fun test5_TruncatedFile_rejectedImmediately() = runTest {
        val truncatedFile = tempFolder.newFile("truncated.enc")
        truncatedFile.writeBytes(ByteArray(20)) // Less than minimum 44 bytes

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, truncatedFile, "AnyPassword123")
        assertFalse(restoreResult.success)
        assertTrue(restoreResult.message.contains("too small") || restoreResult.message.contains("corrupted"))
    }

    @Test
    fun test6_RetentionRules_preservesActiveRecordingsAndPendingTranscriptions() = runTest {
        val testAudioFile = tempFolder.newFile("active_rec.wav").apply { writeBytes(ByteArray(1000)) }

        // 1. Actively recording session -> Must NEVER be deleted
        val activeRecId = repository.insertRecording(
            RecordingEntity(
                title = "Live Recording",
                filePath = testAudioFile.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10, // 10 days old
                status = "RECORDING",
                processingStatus = "PENDING"
            )
        )

        // 2. Pending transcription session -> Must NEVER be deleted
        val pendingAudio = tempFolder.newFile("pending_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val pendingRecId = repository.insertRecording(
            RecordingEntity(
                title = "Pending Transcription",
                filePath = pendingAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = "COMPLETED",
                processingStatus = "PENDING"
            )
        )

        // 3. Actively processing transcription -> Must NEVER be deleted
        val processingAudio = tempFolder.newFile("processing_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val processingRecId = repository.insertRecording(
            RecordingEntity(
                title = "Processing Transcription",
                filePath = processingAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = "COMPLETED",
                processingStatus = "PROCESSING"
            )
        )

        // 4. Completed session with transcript -> Eligible for retention cleanup after 7 days
        val completedAudio = tempFolder.newFile("completed_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val completedRecId = repository.insertRecording(
            RecordingEntity(
                title = "Old Completed Session",
                filePath = completedAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = "COMPLETED",
                processingStatus = "COMPLETED"
            )
        )

        // Set 7-day retention policy
        RetentionManager.setPolicy(context, AudioRetentionPolicy.DAYS_7)
        val cleaned = RetentionManager.executeRetentionCleanup(context, repository)

        assertEquals("Only the completed 10-day old audio should be cleaned", 1, cleaned)

        // Active, Pending, and Processing files must still exist on disk!
        assertTrue(testAudioFile.exists())
        assertTrue(pendingAudio.exists())
        assertTrue(processingAudio.exists())

        // Completed audio file pruned from disk, but DB record retained with empty filePath
        assertFalse(completedAudio.exists())
        val completedRecAfter = repository.getRecording(completedRecId)
        assertNotNull(completedRecAfter)
        assertEquals("", completedRecAfter?.filePath)
    }

    @Test
    fun test7_SearchSanitization_emptyAndSpecialCharactersHandledGracefully() = runTest {
        val recId = repository.insertRecording(
            RecordingEntity(title = "Budget Meeting", filePath = "", startTimestamp = 1000L)
        )
        repository.insertTranscripts(
            listOf(TranscriptEntity(recordingId = recId, segmentIndex = 0, startOffsetMs = 0L, endOffsetMs = 1000L, text = "Project cost is zero rupees."))
        )

        // Empty queries return emptyList immediately
        assertTrue(repository.searchRecordings("").isEmpty())
        assertTrue(repository.searchRecordings("   ").isEmpty())
        assertTrue(repository.searchTranscripts("").isEmpty())
        assertTrue(repository.searchMeetings("").isEmpty())
        assertTrue(repository.searchTasks("").isEmpty())
        assertTrue(repository.searchNotes("").isEmpty())

        // Special characters (quotes, stars, parentheses) do not crash FTS
        val specialResults = repository.searchTranscripts("\"zero* (rupees)\"")
        assertEquals(1, specialResults.size)
        assertTrue(specialResults[0].text.contains("zero rupees"))
    }
}
