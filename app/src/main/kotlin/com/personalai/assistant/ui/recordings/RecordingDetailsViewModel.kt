package com.personalai.assistant.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.playback.AudioPlayer
import com.personalai.assistant.transcription.TranscriptionProgress
import com.personalai.assistant.transcription.TranscriptionQueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class RecordingDetailsViewModel(
    private val repository: AppRepository,
    private val queueManager: TranscriptionQueueManager,
    private val recordingId: Long
) : ViewModel() {

    private val _recording = MutableStateFlow<RecordingEntity?>(null)
    val recording: StateFlow<RecordingEntity?> = _recording.asStateFlow()

    val transcripts: StateFlow<List<TranscriptEntity>> =
        repository.getTranscriptsFlow(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueProgress: StateFlow<TranscriptionProgress> = queueManager.progressState

    private val _fileIntegrityStatus = MutableStateFlow("Verifying...")
    val fileIntegrityStatus: StateFlow<String> = _fileIntegrityStatus.asStateFlow()

    val audioPlayer = AudioPlayer()

    init {
        loadRecording()
    }

    private fun loadRecording() {
        viewModelScope.launch {
            val rec = repository.getRecording(recordingId)
            _recording.value = rec

            if (rec != null) {
                val file = File(rec.filePath)
                _fileIntegrityStatus.value = when {
                    !file.exists() -> "Missing audio file at ${rec.filePath}"
                    file.length() < 44 -> "Corrupt file (size < 44 bytes)"
                    else -> "Valid PCM WAV (${file.length()} bytes, readable)"
                }

                // If processing status is still PENDING, automatically trigger transcription
                if (rec.processingStatus == "PENDING" && file.exists() && file.length() > 44) {
                    queueManager.enqueue(recordingId)
                }
            } else {
                _fileIntegrityStatus.value = "Recording not found in database"
            }
        }
    }

    fun startTranscription() {
        queueManager.enqueue(recordingId)
    }

    fun reprocessTranscription(targetModelId: String? = null) {
        viewModelScope.launch {
            queueManager.reprocess(recordingId, targetModelId)
            loadRecording()
        }
    }

    fun updateTranscript(id: Long, newText: String) {
        viewModelScope.launch {
            repository.updateTranscriptText(id, newText, "VERIFIED")
        }
    }

    fun play() {
        val path = _recording.value?.filePath
        if (path != null) {
            audioPlayer.play(path)
        }
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun resume() {
        audioPlayer.resume()
    }

    fun seekTo(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    fun rename(newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(recordingId, newTitle)
            loadRecording()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteRecording(recordingId)
            onDeleted()
        }
    }

    fun generateMeetingMinutes(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val rec = _recording.value ?: return@launch
            val trList = transcripts.value
            val minutes = com.personalai.assistant.ai.MeetingMinutesExtractor.extract(rec, trList)

            val meetingId = repository.insertMeeting(
                com.personalai.assistant.data.local.entity.MeetingEntity(
                    title = minutes.title,
                    startTimestamp = rec.startTimestamp,
                    endTimestamp = rec.endTimestamp,
                    summary = minutes.summary,
                    sourceRecordingId = rec.id
                )
            )

            // Insert extracted action items as Tasks linked to this meeting
            for (action in minutes.actionItems) {
                repository.insertTask(
                    com.personalai.assistant.data.local.entity.TaskEntity(
                        title = action.title,
                        description = action.description,
                        sourceMeetingId = meetingId,
                        sourceRecordingId = rec.id,
                        dueDate = action.dueDate,
                        assignedPerson = action.assignedPerson
                    )
                )
            }

            onSuccess(meetingId)
        }
    }

    fun exportJson(context: android.content.Context): File? {
        val rec = _recording.value ?: return null
        return com.personalai.assistant.export.ExportManager.exportRecordingToJson(context, rec, transcripts.value)
    }

    fun shareJson(context: android.content.Context) {
        val f = exportJson(context) ?: return
        com.personalai.assistant.export.ExportManager.shareFile(context, f, "application/json")
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
