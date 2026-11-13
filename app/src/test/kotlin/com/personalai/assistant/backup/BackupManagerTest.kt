package com.personalai.assistant.backup

import com.personalai.assistant.data.local.entity.RecordingStatus
import com.personalai.assistant.data.local.entity.ProcessingStatus
import com.personalai.assistant.data.local.entity.TaskStatus

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
                status = RecordingStatus.COMPLETED,
                fileSizeBytes = 1920044L,
                processingStatus = ProcessingStatus.COMPLETED
            )
        )
        val rec2Id = repository.insertRecording(
            RecordingEntity(
                title = "Hinglish Client Discussion",
                filePath = "/test/rec2.wav",
                startTimestamp = 1730010000000L,
                endTimestamp = 1730010300000L,
                durationMs = 300000L,
                status = RecordingStatus.COMPLETED,
                fileSizeBytes = 960044L,
                processingStatus = ProcessingStatus.COMPLETED
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
                status = TaskStatus.PENDING
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
        assertEquals(ProcessingStatus.COMPLETED, restoredRec1.processingStatus)

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
                status = RecordingStatus.RECORDING,
                processingStatus = ProcessingStatus.PENDING
            )
        )

        // 2. Pending transcription session -> Must NEVER be deleted
        val pendingAudio = tempFolder.newFile("pending_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val pendingRecId = repository.insertRecording(
            RecordingEntity(
                title = "Pending Transcription",
                filePath = pendingAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.PENDING
            )
        )

        // 3. Actively processing transcription -> Must NEVER be deleted
        val processingAudio = tempFolder.newFile("processing_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val processingRecId = repository.insertRecording(
            RecordingEntity(
                title = "Processing Transcription",
                filePath = processingAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.PROCESSING
            )
        )

        // 4. Completed session with transcript -> Eligible for retention cleanup after 7 days
        val completedAudio = tempFolder.newFile("completed_rec.wav").apply { writeBytes(ByteArray(1000)) }
        val completedRecId = repository.insertRecording(
            RecordingEntity(
                title = "Old Completed Session",
                filePath = completedAudio.absolutePath,
                startTimestamp = System.currentTimeMillis() - 86400000L * 10,
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.COMPLETED
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

    @Test
    fun test8_BackupWithMultipleAudioRecordings_packagesAudioAndRemapsPaths() = runTest {
        // Create 2 physical audio files
        val audioFile1 = tempFolder.newFile("sample_voice_1.wav").apply {
            writeBytes("RIFFdummywavecontentforrecording1".toByteArray())
        }
        val audioFile2 = tempFolder.newFile("sample_voice_2.wav").apply {
            writeBytes("RIFFdummywavecontentforrecording2_longer_sample".toByteArray())
        }

        val rec1Id = repository.insertRecording(
            RecordingEntity(
                title = "Meeting Audio 1",
                filePath = audioFile1.absolutePath,
                startTimestamp = 1730000000000L,
                fileSizeBytes = audioFile1.length(),
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.COMPLETED
            )
        )
        val rec2Id = repository.insertRecording(
            RecordingEntity(
                title = "Meeting Audio 2",
                filePath = audioFile2.absolutePath,
                startTimestamp = 1730005000000L,
                fileSizeBytes = audioFile2.length(),
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.COMPLETED
            )
        )

        val passphrase = "SecurePassword123!"
        val backupResult = BackupManager.createEncryptedBackup(context, repository, passphrase)
        assertTrue(backupResult.success)
        val backupFile = backupResult.file!!
        assertTrue(backupFile.exists())
        assertTrue("Backup should mention audio files included", backupResult.message.contains("2 audio files"))

        // Simulate new device: clear database and delete original temp files
        repository.clearAllData()
        audioFile1.delete()
        audioFile2.delete()
        assertTrue(repository.getAllRecordings().isEmpty())

        // Restore onto new device
        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupFile, passphrase)
        assertTrue(restoreResult.success)

        val restoredRecs = repository.getAllRecordings()
        assertEquals(2, restoredRecs.size)

        for (rec in restoredRecs) {
            assertTrue("File path must be populated", rec.filePath.isNotBlank())
            val fileOnDisk = File(rec.filePath)
            assertTrue("Restored audio file must physically exist at remapped path: ${rec.filePath}", fileOnDisk.exists())
            assertTrue("Audio file must have content", fileOnDisk.length() > 0)
        }
    }

    @Test
    fun test9_BackupWithoutAudio_succeedsGracefully() = runTest {
        repository.insertRecording(
            RecordingEntity(
                title = "Text-only Record",
                filePath = "",
                startTimestamp = 1000L,
                status = RecordingStatus.COMPLETED,
                processingStatus = ProcessingStatus.COMPLETED
            )
        )

        val backupResult = BackupManager.createEncryptedBackup(context, repository, "SecurePassword123!")
        assertTrue(backupResult.success)

        repository.clearAllData()
        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupResult.file!!, "SecurePassword123!")
        assertTrue(restoreResult.success)

        val restored = repository.getAllRecordings()
        assertEquals(1, restored.size)
        assertEquals("Text-only Record", restored[0].title)
        assertEquals("", restored[0].filePath)
    }

    @Test
    fun test10_CrossDeviceRestore_remapsToDestinationStorage() = runTest {
        val fakeOldDeviceAudio = tempFolder.newFile("device_a_audio.wav").apply {
            writeBytes("Device A raw audio data".toByteArray())
        }
        repository.insertRecording(
            RecordingEntity(
                title = "Device A Session",
                filePath = fakeOldDeviceAudio.absolutePath,
                startTimestamp = 2000L,
                status = RecordingStatus.COMPLETED
            )
        )

        val backupResult = BackupManager.createEncryptedBackup(context, repository, "SecurePassword123!")
        assertTrue(backupResult.success)

        // Clear original
        repository.clearAllData()

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupResult.file!!, "SecurePassword123!")
        assertTrue(restoreResult.success)

        val restored = repository.getAllRecordings().first()
        assertFalse("Restored path should not equal old temp path", restored.filePath == fakeOldDeviceAudio.absolutePath)
        assertTrue("Restored path should be within context filesDir", restored.filePath.startsWith(context.filesDir.absolutePath))
        assertTrue(File(restored.filePath).exists())
    }

    @Test
    fun test11_ModifiedArchive_checksumMismatchRejected() = runTest {
        val audio = tempFolder.newFile("check_audio.wav").apply { writeBytes("valid audio".toByteArray()) }
        repository.insertRecording(RecordingEntity(title = "Tamper DB", filePath = audio.absolutePath, startTimestamp = 3000L))

        val backupResult = BackupManager.createEncryptedBackup(context, repository, "SecurePassword123!")
        val backupFile = backupResult.file!!

        // Tamper with ciphertext by altering bytes inside
        val bytes = backupFile.readBytes()
        bytes[bytes.lastIndex - 10] = (bytes[bytes.lastIndex - 10].toInt() xor 0x55).toByte()
        backupFile.writeBytes(bytes)

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, backupFile, "SecurePassword123!")
        assertFalse("Tampered backup must be rejected", restoreResult.success)
    }

    @Test
    fun test12_MergeVsReplaceStrategy() = runTest {
        // Step 1: Initial state has Item A
        repository.insertTask(TaskEntity(title = "Item A", status = TaskStatus.PENDING))
        val backupResult = BackupManager.createEncryptedBackup(context, repository, "SecurePassword123!")
        val backupFile = backupResult.file!!

        // Step 2: Database receives Item B while backup exists
        repository.insertTask(TaskEntity(title = "Item B", status = TaskStatus.PENDING))
        assertEquals(2, repository.getAllTasks().size)

        // Step 3: Restore with MERGE strategy
        val mergeResult = BackupManager.restoreEncryptedBackup(
            context, repository, backupFile, "SecurePassword123!",
            strategy = RestoreStrategy.MERGE
        )
        assertTrue(mergeResult.success)
        val tasksAfterMerge = repository.getAllTasks()
        // Item B preserved, Item A merged
        assertTrue(tasksAfterMerge.any { it.title == "Item B" })
        assertTrue(tasksAfterMerge.any { it.title == "Item A" })

        // Step 4: Restore with REPLACE strategy
        val replaceResult = BackupManager.restoreEncryptedBackup(
            context, repository, backupFile, "SecurePassword123!",
            strategy = RestoreStrategy.REPLACE
        )
        assertTrue(replaceResult.success)
        val tasksAfterReplace = repository.getAllTasks()
        // Only Item A from backup should exist, Item B replaced
        assertEquals(1, tasksAfterReplace.size)
        assertEquals("Item A", tasksAfterReplace[0].title)
    }

    @Test
    fun test13_MaliciousZipSlip_detectedAndRejected() = runTest {
        val zipBaos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("../evil_slip.txt"))
            zos.write("malicious payload".toByteArray())
            zos.closeEntry()
        }

        val plainZip = zipBaos.toByteArray()
        val random = SecureRandom()
        val salt = ByteArray(16).apply { random.nextBytes(this) }
        val iv = ByteArray(12).apply { random.nextBytes(this) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec("SecurePassword123!".toCharArray(), salt, 65536, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val aad = java.nio.ByteBuffer.allocate(4 + 4 + 16)
            .put(BackupManager.MAGIC_HEADER)
            .putInt(BackupManager.BACKUP_VERSION)
            .put(salt)
            .array()
        cipher.updateAAD(aad)
        val cipherText = cipher.doFinal(plainZip)

        val evilBackupFile = tempFolder.newFile("evil_backup.enc").apply {
            outputStream().use { fos ->
                fos.write(BackupManager.MAGIC_HEADER)
                fos.write(java.nio.ByteBuffer.allocate(4).putInt(BackupManager.BACKUP_VERSION).array())
                fos.write(salt)
                fos.write(iv)
                fos.write(cipherText)
            }
        }

        val restoreResult = BackupManager.restoreEncryptedBackup(context, repository, evilBackupFile, "SecurePassword123!")
        assertFalse("Malicious Zip Slip archive must be rejected", restoreResult.success)
        assertTrue(restoreResult.message.contains("path traversal") || restoreResult.message.contains("failed"))
    }

    @Test
    fun test14_OpenJarvisApiKey_keystoreEncryptionAndDisconnect() {
        val testKey = "nvapi-prod-super-secret-key-98765"

        // 1. Store API key
        com.personalai.assistant.ai.OpenJarvisManager.setApiKey(context, testKey)
        com.personalai.assistant.ai.OpenJarvisManager.setEnabled(context, true)

        // 2. Verify SharedPreferences NEVER holds plaintext API key
        val prefs = context.getSharedPreferences("openjarvis_prefs", Context.MODE_PRIVATE)
        val rawStoredKey = prefs.getString("openjarvis_api_key", null)
        assertTrue("Plaintext API key must not exist in SharedPreferences", rawStoredKey.isNullOrEmpty())

        // 3. Verify retrieval from secure vault
        val retrievedKey = com.personalai.assistant.ai.OpenJarvisManager.getApiKey(context)
        assertEquals("Retrieved API key must match stored key", testKey, retrievedKey)
        assertTrue(com.personalai.assistant.ai.OpenJarvisManager.isEnabled(context))

        // 4. Verify explicit disconnect clears key and disables integration
        com.personalai.assistant.ai.OpenJarvisManager.disconnect(context)
        assertFalse(com.personalai.assistant.ai.OpenJarvisManager.isEnabled(context))
        assertEquals("", com.personalai.assistant.ai.OpenJarvisManager.getApiKey(context))
    }

    @Test
    fun test15_PassphraseValidation_enforcesMinimumLength() = runTest {
        val shortResult = BackupManager.createEncryptedBackup(context, repository, "short")
        assertFalse(shortResult.success)
        assertTrue(shortResult.message.contains("at least 8 characters"))
    }
}
