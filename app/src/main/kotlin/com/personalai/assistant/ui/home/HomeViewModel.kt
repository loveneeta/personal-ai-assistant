package com.personalai.assistant.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.recording.AudioRecordingService
import com.personalai.assistant.recording.RecordingState
import com.personalai.assistant.transcription.models.ModelInfo
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: AppRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = AudioRecordingService.recordingState

    val todayRecordings: StateFlow<List<RecordingEntity>> =
        repository.getTodayRecordingsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentRecordings: StateFlow<List<RecordingEntity>> =
        repository.getRecentRecordingsFlow(5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelId: StateFlow<String> = modelManager.activeModelId
    val modelsState: StateFlow<List<ModelInfo>> = modelManager.modelsState

    init {
        // Automatically reconcile incomplete recordings from crashes on launch
        viewModelScope.launch {
            repository.reconcileIncompleteRecordings()
            modelManager.refreshModelsState()
        }
    }

    fun setActiveModel(modelId: String) {
        modelManager.setActiveModel(modelId)
    }

    fun startRecording(context: Context, title: String? = null) {
        AudioRecordingService.start(context, title)
    }

    fun pauseRecording(context: Context) {
        AudioRecordingService.pause(context)
    }

    fun resumeRecording(context: Context) {
        AudioRecordingService.resume(context)
    }

    fun stopRecording(context: Context) {
        AudioRecordingService.stop(context)
    }

    fun reconcileRecordings() {
        viewModelScope.launch {
            repository.reconcileIncompleteRecordings()
        }
    }
}
