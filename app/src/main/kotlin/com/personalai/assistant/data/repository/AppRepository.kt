package com.personalai.assistant.data.repository

import com.personalai.assistant.data.local.entity.WakeEventEntity

import android.content.Context
import com.personalai.assistant.data.local.AppDatabase
import com.personalai.assistant.recording.WavWriter
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Calendar

class AppRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val recordingDao = database.recordingDao()
    private val transcriptDao = database.transcriptDao()
    private val meetingDao = database.meetingDao()
    private val taskDao = database.taskDao()
    private val noteDao = database.noteDao()
    private val wakeEventDao = database.wakeEventDao()

    // --- Recordings ---
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordingsFlow()
    fun getRecentRecordingsFlow(limit: Int = 5): Flow<List<RecordingEntity>> = recordingDao.getRecentRecordingsFlow(limit)
    fun getRecordingFlow(id: Long): Flow<RecordingEntity?> = recordingDao.getByIdFlow(id)
    suspend fun getRecording(id: Long): RecordingEntity? = recordingDao.getById(id)

    fun getTodayRecordingsFlow(): Flow<List<RecordingEntity>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return recordingDao.getTodayRecordingsFlow(calendar.timeInMillis)
    }

    suspend fun insertRecording(recording: RecordingEntity): Long = withContext(Dispatchers.IO) {
        recordingDao.insert(recording)
    }

    suspend fun updateRecording(recording: RecordingEntity) = withContext(Dispatchers.IO) {
        recordingDao.update(recording)
    }

    suspend fun renameRecording(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        recordingDao.rename(id, newTitle, System.currentTimeMillis())
    }

    suspend fun deleteRecording(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rec = recordingDao.getById(id) ?: return@withContext false
        if (rec.status == "RECORDING" || rec.status == "PAUSED" || rec.processingStatus == "PROCESSING") {
            // Safety guard: Never delete audio currently being recorded or transcribed!
            return@withContext false
        }
        if (rec.filePath.isNotBlank()) {
            val file = File(rec.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    return@withContext false
                }
            }
        }
        recordingDao.deleteById(id)
        true
    }

    // --- Crash Recovery & Incomplete Session Reconciliation ---
    suspend fun reconcileIncompleteRecordings(): List<RecordingEntity> = withContext(Dispatchers.IO) {
        val incomplete = recordingDao.getIncompleteRecordings()
        val recovered = mutableListOf<RecordingEntity>()

        for (rec in incomplete) {
            val file = File(rec.filePath)
            if (!file.exists() || file.length() < 44) {
                // Audio file missing or empty - mark as ERROR
                recordingDao.finalizeRecording(
                    id = rec.id,
                    durationMs = 0,
                    fileSizeBytes = 0,
                    endTimestamp = System.currentTimeMillis(),
                    status = "ERROR",
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                // Audio file exists with data! Reconcile WAV header and mark as INTERRUPTED/RECOVERED
                val actualLength = file.length()
                val calculatedDurationMs = WavWriter.repairIncompleteWav(file)

                recordingDao.finalizeRecording(
                    id = rec.id,
                    durationMs = calculatedDurationMs,
                    fileSizeBytes = actualLength,
                    endTimestamp = System.currentTimeMillis(),
                    status = "INTERRUPTED",
                    updatedAt = System.currentTimeMillis()
                )
                recovered.add(rec)
            }
        }
        recovered
    }

    suspend fun getAllRecordings(): List<RecordingEntity> = withContext(Dispatchers.IO) {
        recordingDao.getAllRecordings()
    }

    suspend fun searchRecordings(query: String): List<RecordingEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        recordingDao.searchRecordings(trimmed)
    }

    // --- Transcripts ---
    fun getTranscriptsFlow(recordingId: Long): Flow<List<TranscriptEntity>> =
        transcriptDao.getByRecordingIdFlow(recordingId)

    suspend fun getTranscripts(recordingId: Long): List<TranscriptEntity> = withContext(Dispatchers.IO) {
        transcriptDao.getByRecordingId(recordingId)
    }

    suspend fun getAllTranscripts(): List<TranscriptEntity> = withContext(Dispatchers.IO) {
        transcriptDao.getAllTranscripts()
    }

    suspend fun insertTranscripts(transcripts: List<TranscriptEntity>) = withContext(Dispatchers.IO) {
        transcriptDao.insertAll(transcripts)
    }

    suspend fun updateTranscriptText(id: Long, newText: String, reviewStatus: String = "VERIFIED") = withContext(Dispatchers.IO) {
        transcriptDao.updateText(id, newText, reviewStatus)
    }

    suspend fun clearTranscripts(recordingId: Long) = withContext(Dispatchers.IO) {
        transcriptDao.deleteByRecordingId(recordingId)
    }

    suspend fun searchTranscripts(query: String): List<TranscriptEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        try {
            val sanitized = trimmed.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
            if (sanitized.isEmpty()) {
                return@withContext transcriptDao.searchTranscriptsLike(trimmed)
            }
            val tokens = sanitized.split(" ").filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                return@withContext transcriptDao.searchTranscriptsLike(trimmed)
            }
            val ftsQuery = tokens.joinToString(" ") { "$it*" }
            transcriptDao.searchTranscriptsFts(ftsQuery)
        } catch (_: Exception) {
            transcriptDao.searchTranscriptsLike(trimmed)
        }
    }

    // --- Meetings ---
    fun getAllMeetingsFlow(): Flow<List<MeetingEntity>> = meetingDao.getAllMeetingsFlow()
    fun getMeetingsForRecordingFlow(recordingId: Long): Flow<List<MeetingEntity>> =
        meetingDao.getMeetingsForRecordingFlow(recordingId)
    suspend fun getMeeting(id: Long): MeetingEntity? = withContext(Dispatchers.IO) {
        meetingDao.getById(id)
    }
    suspend fun getAllMeetings(): List<MeetingEntity> = withContext(Dispatchers.IO) {
        meetingDao.getAllMeetings()
    }
    suspend fun searchMeetings(query: String): List<MeetingEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        meetingDao.searchMeetings(trimmed)
    }
    suspend fun insertMeeting(meeting: MeetingEntity): Long = withContext(Dispatchers.IO) {
        meetingDao.insert(meeting)
    }
    suspend fun updateMeeting(meeting: MeetingEntity) = withContext(Dispatchers.IO) {
        meetingDao.update(meeting)
    }
    suspend fun deleteMeeting(id: Long) = withContext(Dispatchers.IO) {
        meetingDao.deleteById(id)
    }

    // --- Tasks ---
    fun getAllTasksFlow(): Flow<List<TaskEntity>> = taskDao.getAllTasksFlow()
    fun getPendingTasksFlow(): Flow<List<TaskEntity>> = taskDao.getPendingTasksFlow()
    fun getTasksForMeetingFlow(meetingId: Long): Flow<List<TaskEntity>> = taskDao.getTasksForMeetingFlow(meetingId)
    suspend fun getTasksForMeeting(meetingId: Long): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskDao.getTasksForMeeting(meetingId)
    }
    suspend fun getAllTasks(): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskDao.getAllTasks()
    }
    suspend fun searchTasks(query: String): List<TaskEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        taskDao.searchTasks(trimmed)
    }
    suspend fun insertTask(task: TaskEntity): Long = withContext(Dispatchers.IO) {
        taskDao.insert(task)
    }
    suspend fun updateTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.update(task)
    }
    suspend fun updateTaskStatus(id: Long, status: String) = withContext(Dispatchers.IO) {
        taskDao.updateStatus(id, status)
    }
    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        taskDao.deleteById(id)
    }

    // --- Notes ---
    fun getAllNotesFlow(): Flow<List<NoteEntity>> = noteDao.getAllNotesFlow()
    fun getPinnedNotesFlow(): Flow<List<NoteEntity>> = noteDao.getPinnedNotesFlow()
    suspend fun getAllNotes(): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.getAllNotes()
    }
    suspend fun searchNotes(query: String): List<NoteEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        noteDao.searchNotes(trimmed)
    }
    suspend fun insertNote(note: NoteEntity): Long = withContext(Dispatchers.IO) {
        noteDao.insert(note)
    }
    suspend fun updateNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        noteDao.update(note)
    }
    suspend fun togglePinNote(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setPinned(id, isPinned, System.currentTimeMillis())
    }
    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteById(id)
    }


    // --- Wake Events & Voice Protocol ---
    suspend fun insertWakeEvent(event: WakeEventEntity): Long = withContext(Dispatchers.IO) {
        wakeEventDao.insert(event)
    }

    fun getRecentWakeEventsFlow(limit: Int = 20): Flow<List<WakeEventEntity>> {
        return wakeEventDao.getRecentWakeEventsFlow(limit)
    }

    suspend fun getAllWakeEvents(): List<WakeEventEntity> = withContext(Dispatchers.IO) {
        wakeEventDao.getAllWakeEvents()
    }

    suspend fun clearWakeEvents() = withContext(Dispatchers.IO) {
        wakeEventDao.clearAll()
    }

    // --- Retention & Clean up ---
    suspend fun updateRecordingFilePath(id: Long, newPath: String) = withContext(Dispatchers.IO) {
        recordingDao.updateFilePath(id, newPath, System.currentTimeMillis())
    }

    suspend fun deleteAudioKeepTranscript(recordingId: Long): Boolean = withContext(Dispatchers.IO) {
        val rec = recordingDao.getById(recordingId) ?: return@withContext false
        if (rec.status == "RECORDING" || rec.status == "PAUSED" || rec.processingStatus == "PROCESSING") {
            // Safety guard: Never delete audio while capture or transcription is active
            return@withContext false
        }
        if (rec.filePath.isNotBlank()) {
            val file = File(rec.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    return@withContext false
                }
            }
        }
        recordingDao.updateFilePath(recordingId, "", System.currentTimeMillis())
        true
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Delete all physical audio files
        val recordings = recordingDao.getAllRecordings()
        for (rec in recordings) {
            val file = File(rec.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        // Delete all tables
        transcriptDao.deleteAll()
        taskDao.deleteAll()
        meetingDao.deleteAll()
        noteDao.deleteAll()
        recordingDao.deleteAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(
                    AppDatabase.getInstance(context),
                    context.applicationContext
                ).also { INSTANCE = it }
            }
        }
    }
}
