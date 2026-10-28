package com.personalai.assistant.ui.meetings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.export.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MeetingDetailsViewModel(
    private val repository: AppRepository,
    private val meetingId: Long
) : ViewModel() {

    private val _meeting = MutableStateFlow<MeetingEntity?>(null)
    val meeting: StateFlow<MeetingEntity?> = _meeting.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.getTasksForMeetingFlow(meetingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _transcripts = MutableStateFlow<List<TranscriptEntity>>(emptyList())
    val transcripts: StateFlow<List<TranscriptEntity>> = _transcripts.asStateFlow()

    init {
        loadMeeting()
    }

    private fun loadMeeting() {
        viewModelScope.launch {
            val m = repository.getMeeting(meetingId)
            _meeting.value = m
            if (m?.sourceRecordingId != null) {
                _transcripts.value = repository.getTranscripts(m.sourceRecordingId)
            }
        }
    }

    fun toggleTaskStatus(taskId: Long, currentStatus: String) {
        viewModelScope.launch {
            val newStatus = if (currentStatus == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateTaskStatus(taskId, newStatus)
        }
    }

    fun addTask(title: String, assignee: String?) {
        viewModelScope.launch {
            val m = _meeting.value ?: return@launch
            repository.insertTask(
                TaskEntity(
                    title = title,
                    sourceMeetingId = meetingId,
                    sourceRecordingId = m.sourceRecordingId,
                    assignedPerson = assignee?.ifBlank { null }
                )
            )
        }
    }

    fun exportPdf(context: Context): File? {
        val m = _meeting.value ?: return null
        return ExportManager.exportMeetingToPdf(
            context = context,
            meeting = m,
            tasks = tasks.value,
            transcripts = _transcripts.value
        )
    }

    fun exportMarkdown(context: Context): File? {
        val m = _meeting.value ?: return null
        return ExportManager.exportMeetingToMarkdown(
            context = context,
            meeting = m,
            tasks = tasks.value,
            transcripts = _transcripts.value
        )
    }

    fun shareMeeting(context: Context) {
        val pdf = exportPdf(context) ?: return
        ExportManager.shareFile(context, pdf, "application/pdf")
    }

    fun deleteMeeting(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMeeting(meetingId)
            onDeleted()
        }
    }
}
