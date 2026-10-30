package com.personalai.assistant.backup

import android.content.Context
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
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

object BackupManager {

    const val BACKUP_VERSION = 2
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128
    const val MIN_BACKUP_FILE_LENGTH = SALT_LENGTH + IV_LENGTH + 16 // 44 bytes

    suspend fun createEncryptedBackup(
        context: Context,
        repository: AppRepository,
        passphrase: String
    ): BackupResult = withContext(Dispatchers.IO) {
        if (passphrase.length < 6) {
            return@withContext BackupResult(false, "Passphrase must be at least 6 characters.")
        }

        try {
            val root = JSONObject()
            root.put("version", BACKUP_VERSION)
            root.put("createdAt", System.currentTimeMillis())

            // 1. Recordings
            val recArray = JSONArray()
            for (r in repository.getAllRecordings()) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("filePath", r.filePath)
                obj.put("startTimestamp", r.startTimestamp)
                if (r.endTimestamp != null) obj.put("endTimestamp", r.endTimestamp)
                obj.put("durationMs", r.durationMs)
                obj.put("status", r.status)
                obj.put("fileSizeBytes", r.fileSizeBytes)
                obj.put("mimeType", r.mimeType)
                obj.put("processingStatus", r.processingStatus)
                if (r.errorDetails != null) obj.put("errorDetails", r.errorDetails)
                recArray.put(obj)
            }
            root.put("recordings", recArray)

            // 2. Transcripts
            val trArray = JSONArray()
            for (t in repository.getAllTranscripts()) {
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
            root.put("transcripts", trArray)

            // 3. Meetings
            val meetArray = JSONArray()
            for (m in repository.getAllMeetings()) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("title", m.title)
                obj.put("startTimestamp", m.startTimestamp)
                if (m.endTimestamp != null) obj.put("endTimestamp", m.endTimestamp)
                if (m.summary != null) obj.put("summary", m.summary)
                if (m.sourceRecordingId != null) obj.put("sourceRecordingId", m.sourceRecordingId)
                meetArray.put(obj)
            }
            root.put("meetings", meetArray)

            // 4. Tasks
            val taskArray = JSONArray()
            for (tsk in repository.getAllTasks()) {
                val obj = JSONObject()
                obj.put("id", tsk.id)
                obj.put("title", tsk.title)
                if (tsk.description != null) obj.put("description", tsk.description)
                if (tsk.sourceMeetingId != null) obj.put("sourceMeetingId", tsk.sourceMeetingId)
                if (tsk.sourceRecordingId != null) obj.put("sourceRecordingId", tsk.sourceRecordingId)
                if (tsk.dueDate != null) obj.put("dueDate", tsk.dueDate)
                if (tsk.assignedPerson != null) obj.put("assignedPerson", tsk.assignedPerson)
                obj.put("status", tsk.status)
                taskArray.put(obj)
            }
            root.put("tasks", taskArray)

            // 5. Notes
            val noteArray = JSONArray()
            for (n in repository.getAllNotes()) {
                val obj = JSONObject()
                obj.put("id", n.id)
                obj.put("title", n.title)
                obj.put("content", n.content)
                if (n.linkedRecordingId != null) obj.put("linkedRecordingId", n.linkedRecordingId)
                if (n.linkedMeetingId != null) obj.put("linkedMeetingId", n.linkedMeetingId)
                obj.put("isPinned", n.isPinned)
                noteArray.put(obj)
            }
            root.put("notes", noteArray)

            val plainBytes = root.toString().toByteArray(StandardCharsets.UTF_8)

            // Generate salt & IV
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH)
            random.nextBytes(salt)
            val iv = ByteArray(IV_LENGTH)
            random.nextBytes(iv)

            // Derive key
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val cipherText = cipher.doFinal(plainBytes)

            // Write output file [SALT][IV][CIPHERTEXT]
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val backupFile = File(backupDir, "PersonalAI_Backup_${System.currentTimeMillis()}.enc")
            FileOutputStream(backupFile).use { fos ->
                fos.write(salt)
                fos.write(iv)
                fos.write(cipherText)
            }

            BackupResult(true, "Backup created successfully (${backupFile.length()} bytes)", backupFile)
        } catch (e: Exception) {
            BackupResult(false, "Failed to create backup: ${e.message}")
        }
    }

    suspend fun restoreEncryptedBackup(
        @Suppress("UNUSED_PARAMETER") context: Context,
        repository: AppRepository,
        backupFile: File,
        passphrase: String
    ): BackupResult = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) {
            return@withContext BackupResult(false, "Backup file does not exist: ${backupFile.path}")
        }

        if (backupFile.length() < MIN_BACKUP_FILE_LENGTH) {
            return@withContext BackupResult(false, "Invalid or corrupted backup file: file size too small (${backupFile.length()} bytes).")
        }

        try {
            val fileBytes = backupFile.readBytes()
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

            val plainBytes: ByteArray = try {
                cipher.doFinal(cipherText)
            } catch (e: AEADBadTagException) {
                return@withContext BackupResult(false, "Authentication failed: invalid passphrase or corrupted backup file.")
            } catch (e: GeneralSecurityException) {
                return@withContext BackupResult(false, "Decryption failed: cryptographic error (${e.message}).")
            }

            val jsonStr = String(plainBytes, StandardCharsets.UTF_8)
            val root = JSONObject(jsonStr)

            // Map old IDs to new IDs to maintain relational integrity
            val oldToNewRecId = mutableMapOf<Long, Long>()
            val oldToNewMeetingId = mutableMapOf<Long, Long>()

            // 1. Restore Recordings
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
                        status = obj.optString("status", "COMPLETED"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        mimeType = obj.optString("mimeType", "audio/wav"),
                        processingStatus = obj.optString("processingStatus", "COMPLETED"),
                        errorDetails = if (obj.has("errorDetails") && !obj.isNull("errorDetails")) obj.getString("errorDetails") else null
                    )
                )
                oldToNewRecId[oldId] = newId
            }

            // 2. Restore Transcripts
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
            }

            // 3. Restore Meetings
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
            }

            // 4. Restore Tasks
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
                        status = obj.optString("status", "PENDING")
                    )
                )
            }

            // 5. Restore Notes
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
            }

            BackupResult(
                true,
                "Restored ${recArray.length()} recordings, ${transcriptsToInsert.size} transcripts, ${meetArray.length()} meetings, ${taskArray.length()} tasks, ${noteArray.length()} notes."
            )
        } catch (e: Exception) {
            BackupResult(false, "Decryption or restore failed: ${e.message}")
        }
    }
}
