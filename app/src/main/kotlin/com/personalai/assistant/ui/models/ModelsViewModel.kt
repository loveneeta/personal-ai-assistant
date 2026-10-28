package com.personalai.assistant.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.transcription.models.ModelInfo
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelsViewModel(private val modelManager: ModelManager) : ViewModel() {

    val models: StateFlow<List<ModelInfo>> = modelManager.modelsState
    val activeModelId: StateFlow<String> = modelManager.activeModelId

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun download(modelId: String) {
        if (_downloadingModelId.value != null) return
        _downloadingModelId.value = modelId
        _downloadProgress.value = 0f
        _statusMessage.value = "Starting download..."

        viewModelScope.launch {
            val success = modelManager.downloadModel(modelId) { progress ->
                _downloadProgress.value = progress
                _statusMessage.value = "Downloading... ${(progress * 100).toInt()}%"
            }
            _downloadingModelId.value = null
            _statusMessage.value = if (success) {
                "Model downloaded and ready for on-device transcription!"
            } else {
                "Download failed. Check your internet connection."
            }
        }
    }

    fun selectActive(modelId: String) {
        modelManager.setActiveModel(modelId)
        _statusMessage.value = "Active model updated."
    }

    fun delete(modelId: String) {
        modelManager.deleteModel(modelId)
        _statusMessage.value = "Model deleted to reclaim storage."
    }
}
