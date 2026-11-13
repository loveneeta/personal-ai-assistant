package com.personalai.assistant.backup

import android.content.Context
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.ProcessingStatus
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.RecordingStatus
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TaskStatus
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupResult(
    val success: Boolean,
    val message: String,
    val file: File? = null
)

enum class RestoreStrategy {
    MERGE,
    REPLACE
}

/**
 * Hardened, portable, and transaction-safe Backup and Restore Manager.
 *
 * Guarantees:
 * 1. Portability: Packages database metadata and real audio files into a structured, checksummed archive.
 * 2. Cross-Device Restoration: Automatically relocates and remaps media files to destination storage.
 * 3. Authenticated Encryption: AES-256-GCM with PBKDF2 (65,536 iterations) and authenticated AAD header.
 * 4. Restore Safety & Atomic Rollback: Validates archive structure and SHA-256 checksums before database modification.
 *    Executes all database operations inside a Room transaction and rolls back both database and filesystem on error.
 * 5. Path Traversal Protection: Enforces strict canonical boundaries against Zip Slip vulnerabilities.
 */
object BackupManager {

    const val BACKUP_VERSION = 3
    val MAGIC_HEADER = byteArrayOf('P'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), '3'.code.toByte()) // "PAB3"
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128
    private const val MIN_PASSPHRASE_LENGTH = 8

    // Header: Magic(4) + Version(4) + Salt(16) + IV(12) + Tag(16) = 52 bytes
    const val MIN_BACKUP_FILE_LENGTH = 4 + 4 + SALT_LENGTH + IV_LENGTH + 16
    const val MIN_LEGACY_BACKUP_FILE_LENGTH = SALT_LENGTH + IV_LENGTH + 16

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun createEncryptedBackup(
        context: Context,
        repository: AppRepository,
        passphrase: String,
        targetFile: File? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            return@withContext BackupResult(
                false,
                "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
            )
        }

        try {
            // 1. Collect all database entities
            val recordings = repository.getAllRecordings()
            val transcripts = repository.getAllTranscripts()
            val meetings = repository.getAllMeetings()
            val tasks = repository.getAllTasks()
            val notes = repository.getAllNotes()

            // 2. Build database.json and index physical audio files
            val dbRoot = JSONObject()
            dbRoot.put("version", BACKUP_VERSION)
            dbRoot.put("createdAt", System.currentTimeMillis())

            val mediaEntries = mutableListOf<JSONObject>()
            val recArray = JSONArray()

            for (r in recordings) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("originalFilePath", r.filePath)
                obj.put("startTimestamp", r.startTimestamp)
                if (r.endTimestamp != null) obj.put("endTimestamp", r.endTimestamp)
                obj.put("durationMs", r.durationMs)
                obj.put("status", r.status.name)
                obj.put("fileSizeBytes", r.fileSizeBytes)
                obj.put("mimeType", r.mimeType)
                obj.put("processingStatus", r.processingStatus.name)
                if (r.errorDetails != null) obj.put("errorDetails", r.errorDetails)

                // Check if audio file actually exists on disk
                if (r.filePath.isNotBlank()) {
                    val audioFile = File(r.filePath)
                    if (audioFile.exists() && audioFile.isFile && audioFile.length() > 0) {
                        val entryName = "media/audio_${r.id}.wav"
                        obj.put("mediaEntryName", entryName)

                        val mediaMeta = JSONObject()
                        mediaMeta.put("entryName", entryName)
                        mediaMeta.put("originalFilename", audioFile.name)
                        mediaMeta.put("sizeBytes", audioFile.length())
                        mediaMeta.put("sha256", computeSha256(audioFile))
                        mediaMeta.put("recordingId", r.id)
                        mediaMeta.put("sourceDiskPath", audioFile.absolutePath)
                        mediaEntries.add(mediaMeta)
                    } else {
                        obj.put("mediaEntryName", JSONObject.NULL)
                    }
                } else {
                    obj.put("mediaEntryName", JSONObject.NULL)
                }

                recArray.put(obj)
            }
            dbRoot.put("recordings", recArray)

            // Transcripts
            val trArray = JSONArray()
            for (t in transcripts) {
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("recordingId", t.recordingId)
                obj.put("segmentIndex", t.segmentIndex)
                obj.put("startOffsetMs", t.startOffsetMs)
                obj.put("endOffsetMs", t.endOffsetMs)
                obj.put("text", t.text)
                obj.put("language", t.language)
                if (t.confidence != null) obj.put("confidence", t.confidence.toDouble())
                obj.put("reviewStatus", t.reviewStatus)
                trArray.put(obj)
            }
            dbRoot.put("transcripts", trArray)

            // Meetings
            val meetArray = JSONArray()
            for (m in meetings) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("title", m.title)
                obj.put("startTimestamp", m.startTimestamp)
                if (m.endTimestamp != null) obj.put("endTimestamp", m.endTimestamp)
                if (m.summary != null) obj.put("summary", m.summary)
                if (m.sourceRecordingId != null) obj.put("sourceRecordingId", m.sourceRecordingId)
                meetArray.put(obj)
            }
            dbRoot.put("meetings", meetArray)

            // Tasks
            val taskArray = JSONArray()
            for (tsk in tasks) {
                val obj = JSONObject()
                obj.put("id", tsk.id)
                obj.put("title", tsk.title)
                if (tsk.description != null) obj.put("description", tsk.description)
                if (tsk.sourceMeetingId != null) obj.put("sourceMeetingId", tsk.sourceMeetingId)
                if (tsk.sourceRecordingId != null) obj.put("sourceRecordingId", tsk.sourceRecordingId)
                if (tsk.dueDate != null) obj.put("dueDate", tsk.dueDate)
                if (tsk.assignedPerson != null) obj.put("assignedPerson", tsk.assignedPerson)
                obj.put("status", tsk.status.name)
                taskArray.put(obj)
            }
            dbRoot.put("tasks", taskArray)

            // Notes
            val noteArray = JSONArray()
            for (n in notes) {
                val obj = JSONObject()
                obj.put("id", n.id)
                obj.put("title", n.title)
                obj.put("content", n.content)
                if (n.linkedRecordingId != null) obj.put("linkedRecordingId", n.linkedRecordingId)
                if (n.linkedMeetingId != null) obj.put("linkedMeetingId", n.linkedMeetingId)
                obj.put("isPinned", n.isPinned)
                noteArray.put(obj)
            }
            dbRoot.put("notes", noteArray)

            val dbJsonBytes = dbRoot.toString().toByteArray(StandardCharsets.UTF_8)
            val dbHash = computeSha256(dbJsonBytes)

            // 3. Build manifest.json
            val manifestRoot = JSONObject()
            manifestRoot.put("version", BACKUP_VERSION)
            manifestRoot.put("appVersion", "1.0.0")
            manifestRoot.put("createdAt", System.currentTimeMillis())

            val counts = JSONObject()
            counts.put("recordings", recordings.size)
            counts.put("transcripts", transcripts.size)
            counts.put("meetings", meetings.size)
            counts.put("tasks", tasks.size)
            counts.put("notes", notes.size)
            manifestRoot.put("recordCounts", counts)
            manifestRoot.put("metadataSha256", dbHash)

            val mediaFilesArray = JSONArray()
            for (m in mediaEntries) {
                val mObj = JSONObject(m.toString())
                mObj.remove("sourceDiskPath")
                mediaFilesArray.put(mObj)
            }
            manifestRoot.put("mediaFiles", mediaFilesArray)

            val manifestBytes = manifestRoot.toString().toByteArray(StandardCharsets.UTF_8)

            // 4. Create ZIP archive container
            val zipBaos = ByteArrayOutputStream()
            ZipOutputStream(BufferedOutputStream(zipBaos)).use { zos ->
                // Write manifest
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestBytes)
                zos.closeEntry()

                // Write database.json
                zos.putNextEntry(ZipEntry("database.json"))
                zos.write(dbJsonBytes)
                zos.closeEntry()

                // Write audio files
                for (m in mediaEntries) {
                    val entryName = m.getString("entryName")
                    val diskPath = m.getString("sourceDiskPath")
                    val sourceFile = File(diskPath)
                    if (sourceFile.exists()) {
                        zos.putNextEntry(ZipEntry(entryName))
                        sourceFile.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            val zipBytes = zipBaos.toByteArray()

            // 5. Authenticated Encryption with AES-256-GCM + PBKDF2
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH)
            random.nextBytes(salt)
            val iv = ByteArray(IV_LENGTH)
            random.nextBytes(iv)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))

            // Authenticate header & version metadata via GCM AAD
            val aad = ByteBuffer.allocate(4 + 4 + SALT_LENGTH)
                .put(MAGIC_HEADER)
                .putInt(BACKUP_VERSION)
                .put(salt)
                .array()
            cipher.updateAAD(aad)

            val cipherText = cipher.doFinal(zipBytes)

            // Output container format: [MAGIC(4)][VERSION(4)][SALT(16)][IV(12)][CIPHERTEXT+TAG]
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val backupFile = targetFile ?: File(backupDir, "PersonalAI_Backup_${System.currentTimeMillis()}.enc")

            FileOutputStream(backupFile).use { fos ->
                fos.write(MAGIC_HEADER)
                fos.write(ByteBuffer.allocate(4).putInt(BACKUP_VERSION).array())
                fos.write(salt)
                fos.write(iv)
                fos.write(cipherText)
            }

            BackupResult(
                true,
                "Backup created successfully (${backupFile.length()} bytes, ${mediaEntries.size} audio files included)",
                backupFile
            )
        } catch (e: Exception) {
            BackupResult(false, "Failed to create backup: ${e.message}")
        }
    }

    suspend fun restoreEncryptedBackup(
        context: Context,
        repository: AppRepository,
        backupFile: File,
        passphrase: String,
        strategy: RestoreStrategy = RestoreStrategy.REPLACE
    ): BackupResult = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) {
            return@withContext BackupResult(false, "Backup file does not exist: ${backupFile.path}")
        }

        if (backupFile.length() < MIN_LEGACY_BACKUP_FILE_LENGTH) {
            return@withContext BackupResult(
                false,
                "Invalid or corrupted backup file: file size too small (${backupFile.length()} bytes)."
            )
        }

        try {
            val fileBytes = backupFile.readBytes()

            // Detect container format: Version 3 (PAB3) vs Legacy Version 2
            val isVersion3 = fileBytes.size >= MIN_BACKUP_FILE_LENGTH &&
                    fileBytes[0] == MAGIC_HEADER[0] &&
                    fileBytes[1] == MAGIC_HEADER[1] &&
                    fileBytes[2] == MAGIC_HEADER[2] &&
                    fileBytes[3] == MAGIC_HEADER[3]

            if (isVersion3) {
                restoreVersion3Archive(context, repository, fileBytes, passphrase, strategy)
            } else {
                restoreLegacyV2(context, repository, fileBytes, passphrase, strategy)
            }
        } catch (e: AEADBadTagException) {
            BackupResult(false, "Authentication failed: invalid passphrase or corrupted backup file.")
        } catch (e: GeneralSecurityException) {
            BackupResult(false, "Decryption failed: cryptographic error (${e.message}).")
        } catch (e: Exception) {
            BackupResult(false, "Decryption or restore failed: ${e.message}")
        }
    }

    private suspend fun restoreVersion3Archive(
        context: Context,
        repository: AppRepository,
        fileBytes: ByteArray,
        passphrase: String,
        strategy: RestoreStrategy
    ): BackupResult {
        val version = ByteBuffer.wrap(fileBytes, 4, 4).int
        if (version > BACKUP_VERSION) {
            return BackupResult(false, "Unsupported backup version: $version (expected <= $BACKUP_VERSION)")
        }

        val salt = fileBytes.copyOfRange(8, 8 + SALT_LENGTH)
        val iv = fileBytes.copyOfRange(24, 24 + IV_LENGTH)
        val cipherText = fileBytes.copyOfRange(36, fileBytes.size)

        // Derive key
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))

        // Authenticate header & version metadata via GCM AAD
        val aad = ByteBuffer.allocate(4 + 4 + SALT_LENGTH)
            .put(MAGIC_HEADER)
            .putInt(version)
            .put(salt)
            .array()
        cipher.updateAAD(aad)

        val plainZipBytes = cipher.doFinal(cipherText)

        // Staging directory for extraction and pre-validation
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
        val createdMediaFiles = mutableListOf<File>()

        return try {
            // Extract ZIP with strict Zip Slip / Path Traversal Protection
            ZipInputStream(ByteArrayInputStream(plainZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(stagingDir, entry.name).canonicalFile
                    if (!targetFile.path.startsWith(stagingDir.canonicalPath + File.separator)) {
                        throw SecurityException("Malicious archive entry path traversal detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 1. Validate manifest.json
            val manifestFile = File(stagingDir, "manifest.json")
            if (!manifestFile.exists()) {
                return BackupResult(false, "Corrupted archive: manifest.json is missing.")
            }
            val manifestJson = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))

            // 2. Validate database.json and checksum
            val dbFile = File(stagingDir, "database.json")
            if (!dbFile.exists()) {
                return BackupResult(false, "Corrupted archive: database.json is missing.")
            }
            val expectedDbHash = manifestJson.getString("metadataSha256")
            val actualDbHash = computeSha256(dbFile)
            if (expectedDbHash != actualDbHash) {
                return BackupResult(false, "Checksum mismatch for database metadata (tampered archive).")
            }

            val dbJson = JSONObject(dbFile.readText(StandardCharsets.UTF_8))

            // 3. Validate media files against manifest checksums
            val mediaFilesArray = manifestJson.optJSONArray("mediaFiles") ?: JSONArray()
            val mediaPathMap = mutableMapOf<String, String>() // entryName -> destinationFilePath
            val destRecordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }

            for (i in 0 until mediaFilesArray.length()) {
                val mediaMeta = mediaFilesArray.getJSONObject(i)
                val entryName = mediaMeta.getString("entryName")
                val expectedHash = mediaMeta.getString("sha256")
                val originalFilename = mediaMeta.optString("originalFilename", "recording_$i.wav")

                val stagedMediaFile = File(stagingDir, entryName)
                if (!stagedMediaFile.exists()) {
                    return BackupResult(false, "Missing media file in archive: $entryName")
                }
                val actualMediaHash = computeSha256(stagedMediaFile)
                if (expectedHash != actualMediaHash) {
                    return BackupResult(false, "Checksum mismatch for media: $entryName (tampered audio).")
                }

                // Relocate media to destination storage with collision-free filenames
                val destFile = File(destRecordingsDir, "restored_${System.currentTimeMillis()}_${i}_$originalFilename")
                stagedMediaFile.copyTo(destFile, overwrite = true)
                createdMediaFiles.add(destFile)
                mediaPathMap[entryName] = destFile.absolutePath
            }

            // 4. Atomic Room Database Transaction
            var restoredRecCount = 0
            var restoredTrCount = 0
            var restoredMeetCount = 0
            var restoredTaskCount = 0
            var restoredNoteCount = 0

            try {
                repository.withTransaction {
                    if (strategy == RestoreStrategy.REPLACE) {
                        repository.clearAllData()
                    }

                    val oldToNewRecId = mutableMapOf<Long, Long>()
                    val oldToNewMeetingId = mutableMapOf<Long, Long>()

                    // A. Recordings
                    val recArray = dbJson.optJSONArray("recordings") ?: JSONArray()
                    for (i in 0 until recArray.length()) {
                        val obj = recArray.getJSONObject(i)
                        val oldId = obj.getLong("id")
                        val mediaEntryName = if (obj.has("mediaEntryName") && !obj.isNull("mediaEntryName")) obj.getString("mediaEntryName") else null

                        // Remap filePath to destination storage if media was in archive
                        val destinationFilePath = if (!mediaEntryName.isNullOrBlank() && mediaPathMap.containsKey(mediaEntryName)) {
                            mediaPathMap[mediaEntryName]!!
                        } else {
                            ""
                        }

                        val newId = repository.insertRecording(
                            RecordingEntity(
                                title = obj.getString("title"),
                                filePath = destinationFilePath,
                                startTimestamp = obj.getLong("startTimestamp"),
                                endTimestamp = if (obj.has("endTimestamp") && !obj.isNull("endTimestamp")) obj.getLong("endTimestamp") else null,
                                durationMs = obj.optLong("durationMs", 0L),
                                status = RecordingStatus.fromString(obj.optString("status", "COMPLETED")),
                                fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                                mimeType = obj.optString("mimeType", "audio/wav"),
                                processingStatus = ProcessingStatus.fromString(obj.optString("processingStatus", "COMPLETED")),
                                errorDetails = if (obj.has("errorDetails") && !obj.isNull("errorDetails")) obj.getString("errorDetails") else null
                            )
                        )
                        oldToNewRecId[oldId] = newId
                        restoredRecCount++
                    }

                    // B. Transcripts
                    val trArray = dbJson.optJSONArray("transcripts") ?: JSONArray()
                    val transcriptsToInsert = mutableListOf<TranscriptEntity>()
                    for (i in 0 until trArray.length()) {
                        val obj = trArray.getJSONObject(i)
                        val oldRecId = obj.getLong("recordingId")
                        val newRecId = oldToNewRecId[oldRecId]
                        if (newRecId != null) {
                            transcriptsToInsert.add(
                                TranscriptEntity(
                                    recordingId = newRecId,
                                    segmentIndex = obj.optInt("segmentIndex", i),
                                    startOffsetMs = obj.optLong("startOffsetMs", 0L),
                                    endOffsetMs = obj.optLong("endOffsetMs", 0L),
                                    text = obj.getString("text"),
                                    language = obj.optString("language", "en"),
                                    confidence = if (obj.has("confidence") && !obj.isNull("confidence")) obj.getDouble("confidence").toFloat() else null,
                                    reviewStatus = obj.optString("reviewStatus", "AUTOMATIC")
                                )
                            )
                        }
                    }
                    if (transcriptsToInsert.isNotEmpty()) {
                        repository.insertTranscripts(transcriptsToInsert)
                        restoredTrCount = transcriptsToInsert.size
                    }

                    // C. Meetings
                    val meetArray = dbJson.optJSONArray("meetings") ?: JSONArray()
                    for (i in 0 until meetArray.length()) {
                        val obj = meetArray.getJSONObject(i)
                        val oldMeetId = obj.getLong("id")
                        val oldSourceRecId = if (obj.has("sourceRecordingId") && !obj.isNull("sourceRecordingId")) obj.getLong("sourceRecordingId") else null
                        val newSourceRecId = if (oldSourceRecId != null) oldToNewRecId[oldSourceRecId] else null

                        val newMeetId = repository.insertMeeting(
                            MeetingEntity(
                                title = obj.getString("title"),
                                startTimestamp = obj.getLong("startTimestamp"),
                                endTimestamp = if (obj.has("endTimestamp") && !obj.isNull("endTimestamp")) obj.getLong("endTimestamp") else null,
                                summary = if (obj.has("summary") && !obj.isNull("summary")) obj.getString("summary") else null,
                                sourceRecordingId = newSourceRecId
                            )
                        )
                        oldToNewMeetingId[oldMeetId] = newMeetId
                        restoredMeetCount++
                    }

                    // D. Tasks
                    val taskArray = dbJson.optJSONArray("tasks") ?: JSONArray()
                    for (i in 0 until taskArray.length()) {
                        val obj = taskArray.getJSONObject(i)
                        val oldSourceMeetId = if (obj.has("sourceMeetingId") && !obj.isNull("sourceMeetingId")) obj.getLong("sourceMeetingId") else null
                        val oldSourceRecId = if (obj.has("sourceRecordingId") && !obj.isNull("sourceRecordingId")) obj.getLong("sourceRecordingId") else null

                        repository.insertTask(
                            TaskEntity(
                                title = obj.getString("title"),
                                description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                                sourceMeetingId = if (oldSourceMeetId != null) oldToNewMeetingId[oldSourceMeetId] else null,
                                sourceRecordingId = if (oldSourceRecId != null) oldToNewRecId[oldSourceRecId] else null,
                                dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.getLong("dueDate") else null,
                                assignedPerson = if (obj.has("assignedPerson") && !obj.isNull("assignedPerson")) obj.getString("assignedPerson") else null,
                                status = TaskStatus.fromString(obj.optString("status", "PENDING"))
                            )
                        )
                        restoredTaskCount++
                    }

                    // E. Notes
                    val noteArray = dbJson.optJSONArray("notes") ?: JSONArray()
                    for (i in 0 until noteArray.length()) {
                        val obj = noteArray.getJSONObject(i)
                        val oldLinkedRecId = if (obj.has("linkedRecordingId") && !obj.isNull("linkedRecordingId")) obj.getLong("linkedRecordingId") else null
                        val oldLinkedMeetId = if (obj.has("linkedMeetingId") && !obj.isNull("linkedMeetingId")) obj.getLong("linkedMeetingId") else null

                        repository.insertNote(
                            NoteEntity(
                                title = obj.getString("title"),
                                content = obj.getString("content"),
                                linkedRecordingId = if (oldLinkedRecId != null) oldToNewRecId[oldLinkedRecId] else null,
                                linkedMeetingId = if (oldLinkedMeetId != null) oldToNewMeetingId[oldLinkedMeetId] else null,
                                isPinned = obj.optBoolean("isPinned", false)
                            )
                        )
                        restoredNoteCount++
                    }
                }
            } catch (txError: Exception) {
                // Roll back created filesystem media files on database error
                for (f in createdMediaFiles) {
                    try { f.delete() } catch (ignored: Exception) {}
                }
                throw txError
            }

            BackupResult(
                true,
                "Restored $restoredRecCount recordings, $restoredTrCount transcripts, $restoredMeetCount meetings, $restoredTaskCount tasks, $restoredNoteCount notes."
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun restoreLegacyV2(
        context: Context,
        repository: AppRepository,
        fileBytes: ByteArray,
        passphrase: String,
        strategy: RestoreStrategy
    ): BackupResult {
        val bis = ByteArrayInputStream(fileBytes)
        val salt = ByteArray(SALT_LENGTH)
        bis.read(salt)
        val iv = ByteArray(IV_LENGTH)
        bis.read(iv)
        val cipherText = ByteArray(bis.available())
        bis.read(cipherText)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val plainBytes = cipher.doFinal(cipherText)

        val jsonStr = String(plainBytes, StandardCharsets.UTF_8)
        val root = JSONObject(jsonStr)

        var restoredRecCount = 0
        var restoredTrCount = 0
        var restoredMeetCount = 0
        var restoredTaskCount = 0
        var restoredNoteCount = 0

        repository.withTransaction {
            if (strategy == RestoreStrategy.REPLACE) {
                repository.clearAllData()
            }

            val oldToNewRecId = mutableMapOf<Long, Long>()
            val oldToNewMeetingId = mutableMapOf<Long, Long>()

            val recArray = root.optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until recArray.length()) {
                val obj = recArray.getJSONObject(i)
                val oldId = obj.getLong("id")
                val newId = repository.insertRecording(
                    RecordingEntity(
                        title = obj.getString("title"),
                        filePath = obj.optString("filePath", ""),
                        startTimestamp = obj.getLong("startTimestamp"),
                        endTimestamp = if (obj.has("endTimestamp") && !obj.isNull("endTimestamp")) obj.getLong("endTimestamp") else null,
                        durationMs = obj.optLong("durationMs", 0L),
                        status = RecordingStatus.fromString(obj.optString("status", "COMPLETED")),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        mimeType = obj.optString("mimeType", "audio/wav"),
                        processingStatus = ProcessingStatus.fromString(obj.optString("processingStatus", "COMPLETED")),
                        errorDetails = if (obj.has("errorDetails") && !obj.isNull("errorDetails")) obj.getString("errorDetails") else null
                    )
                )
                oldToNewRecId[oldId] = newId
                restoredRecCount++
            }

            val trArray = root.optJSONArray("transcripts") ?: JSONArray()
            val transcriptsToInsert = mutableListOf<TranscriptEntity>()
            for (i in 0 until trArray.length()) {
                val obj = trArray.getJSONObject(i)
                val oldRecId = obj.getLong("recordingId")
                val newRecId = oldToNewRecId[oldRecId]
                if (newRecId != null) {
                    transcriptsToInsert.add(
                        TranscriptEntity(
                            recordingId = newRecId,
                            segmentIndex = obj.optInt("segmentIndex", i),
                            startOffsetMs = obj.optLong("startOffsetMs", 0L),
                            endOffsetMs = obj.optLong("endOffsetMs", 0L),
                            text = obj.getString("text"),
                            language = obj.optString("language", "en"),
                            confidence = if (obj.has("confidence") && !obj.isNull("confidence")) obj.getDouble("confidence").toFloat() else null,
                            reviewStatus = obj.optString("reviewStatus", "AUTOMATIC")
                        )
                    )
                }
            }
            if (transcriptsToInsert.isNotEmpty()) {
                repository.insertTranscripts(transcriptsToInsert)
                restoredTrCount = transcriptsToInsert.size
            }

            val meetArray = root.optJSONArray("meetings") ?: JSONArray()
            for (i in 0 until meetArray.length()) {
                val obj = meetArray.getJSONObject(i)
                val oldMeetId = obj.getLong("id")
                val oldSourceRecId = if (obj.has("sourceRecordingId") && !obj.isNull("sourceRecordingId")) obj.getLong("sourceRecordingId") else null
                val newSourceRecId = if (oldSourceRecId != null) oldToNewRecId[oldSourceRecId] else null

                val newMeetId = repository.insertMeeting(
                    MeetingEntity(
                        title = obj.getString("title"),
                        startTimestamp = obj.getLong("startTimestamp"),
                        endTimestamp = if (obj.has("endTimestamp") && !obj.isNull("endTimestamp")) obj.getLong("endTimestamp") else null,
                        summary = if (obj.has("summary") && !obj.isNull("summary")) obj.getString("summary") else null,
                        sourceRecordingId = newSourceRecId
                    )
                )
                oldToNewMeetingId[oldMeetId] = newMeetId
                restoredMeetCount++
            }

            val taskArray = root.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until taskArray.length()) {
                val obj = taskArray.getJSONObject(i)
                val oldSourceMeetId = if (obj.has("sourceMeetingId") && !obj.isNull("sourceMeetingId")) obj.getLong("sourceMeetingId") else null
                val oldSourceRecId = if (obj.has("sourceRecordingId") && !obj.isNull("sourceRecordingId")) obj.getLong("sourceRecordingId") else null

                repository.insertTask(
                    TaskEntity(
                        title = obj.getString("title"),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        sourceMeetingId = if (oldSourceMeetId != null) oldToNewMeetingId[oldSourceMeetId] else null,
                        sourceRecordingId = if (oldSourceRecId != null) oldToNewRecId[oldSourceRecId] else null,
                        dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.getLong("dueDate") else null,
                        assignedPerson = if (obj.has("assignedPerson") && !obj.isNull("assignedPerson")) obj.getString("assignedPerson") else null,
                        status = TaskStatus.fromString(obj.optString("status", "PENDING"))
                    )
                )
                restoredTaskCount++
            }

            val noteArray = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until noteArray.length()) {
                val obj = noteArray.getJSONObject(i)
                val oldLinkedRecId = if (obj.has("linkedRecordingId") && !obj.isNull("linkedRecordingId")) obj.getLong("linkedRecordingId") else null
                val oldLinkedMeetId = if (obj.has("linkedMeetingId") && !obj.isNull("linkedMeetingId")) obj.getLong("linkedMeetingId") else null

                repository.insertNote(
                    NoteEntity(
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        linkedRecordingId = if (oldLinkedRecId != null) oldToNewRecId[oldLinkedRecId] else null,
                        linkedMeetingId = if (oldLinkedMeetId != null) oldToNewMeetingId[oldLinkedMeetId] else null,
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
                restoredNoteCount++
            }
        }

        return BackupResult(
            true,
            "Restored $restoredRecCount recordings, $restoredTrCount transcripts, $restoredMeetCount meetings, $restoredTaskCount tasks, $restoredNoteCount notes."
        )
    }
}
