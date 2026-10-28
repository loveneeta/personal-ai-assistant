package com.personalai.assistant.backup

import android.content.Context
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
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

    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    suspend fun createEncryptedBackup(
        context: Context,
        repository: AppRepository,
        passphrase: String
    ): BackupResult {
        if (passphrase.length < 6) {
            return BackupResult(false, "Passphrase must be at least 6 characters.")
        }

        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("createdAt", System.currentTimeMillis())

            // 1. Recordings
            val recArray = JSONArray()
            for (r in repository.getAllRecordings()) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("startTimestamp", r.startTimestamp)
                obj.put("endTimestamp", r.endTimestamp)
                obj.put("durationMs", r.durationMs)
                obj.put("status", r.status)
                obj.put("fileSizeBytes", r.fileSizeBytes)
                obj.put("mimeType", r.mimeType)
                obj.put("processingStatus", r.processingStatus)
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
                obj.put("endTimestamp", m.endTimestamp)
                obj.put("summary", m.summary)
                obj.put("sourceRecordingId", m.sourceRecordingId)
                meetArray.put(obj)
            }
            root.put("meetings", meetArray)

            // 4. Tasks
            val taskArray = JSONArray()
            for (tsk in repository.getAllTasks()) {
                val obj = JSONObject()
                obj.put("id", tsk.id)
                obj.put("title", tsk.title)
                obj.put("description", tsk.description)
                obj.put("sourceMeetingId", tsk.sourceMeetingId)
                obj.put("sourceRecordingId", tsk.sourceRecordingId)
                obj.put("dueDate", tsk.dueDate)
                obj.put("assignedPerson", tsk.assignedPerson)
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
                obj.put("linkedRecordingId", n.linkedRecordingId)
                obj.put("linkedMeetingId", n.linkedMeetingId)
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

            return BackupResult(true, "Backup created successfully (${backupFile.length()} bytes)", backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return BackupResult(false, "Failed to create backup: ${e.message}")
        }
    }

    suspend fun restoreEncryptedBackup(
        context: Context,
        repository: AppRepository,
        backupFile: File,
        passphrase: String
    ): BackupResult {
        if (!backupFile.exists() || backupFile.length() < SALT_LENGTH + IV_LENGTH + 16) {
            return BackupResult(false, "Invalid or corrupted backup file.")
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
            val plainBytes = cipher.doFinal(cipherText)

            val jsonStr = String(plainBytes, StandardCharsets.UTF_8)
            val root = JSONObject(jsonStr)

            // Restore Meetings
            val meetArray = root.optJSONArray("meetings") ?: JSONArray()
            for (i in 0 until meetArray.length()) {
                val obj = meetArray.getJSONObject(i)
                repository.insertMeeting(
                    MeetingEntity(
                        title = obj.getString("title"),
                        startTimestamp = obj.getLong("startTimestamp"),
                        endTimestamp = if (obj.has("endTimestamp")) obj.optLong("endTimestamp") else null,
                        summary = obj.optString("summary"),
                        sourceRecordingId = if (obj.has("sourceRecordingId")) obj.optLong("sourceRecordingId") else null
                    )
                )
            }

            // Restore Tasks
            val taskArray = root.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until taskArray.length()) {
                val obj = taskArray.getJSONObject(i)
                repository.insertTask(
                    TaskEntity(
                        title = obj.getString("title"),
                        description = obj.optString("description"),
                        sourceMeetingId = if (obj.has("sourceMeetingId")) obj.optLong("sourceMeetingId") else null,
                        dueDate = if (obj.has("dueDate")) obj.optLong("dueDate") else null,
                        assignedPerson = obj.optString("assignedPerson"),
                        status = obj.optString("status", "PENDING")
                    )
                )
            }

            // Restore Notes
            val noteArray = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until noteArray.length()) {
                val obj = noteArray.getJSONObject(i)
                repository.insertNote(
                    NoteEntity(
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }

            return BackupResult(true, "Restored ${meetArray.length()} meetings, ${taskArray.length()} tasks, ${noteArray.length()} notes.")
        } catch (e: Exception) {
            e.printStackTrace()
            return BackupResult(false, "Decryption failed. Check passphrase or file integrity.")
        }
    }
}
