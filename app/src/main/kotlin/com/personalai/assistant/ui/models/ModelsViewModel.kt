package com.personalai.assistant.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.transcription.models.ModelDownloadStage
import com.personalai.assistant.transcription.models.ModelInfo
import com.personalai.assistant.transcription.models.ModelInstallResult
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.Job
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

    private val _downloadStage = MutableStateFlow<ModelDownloadStage>(ModelDownloadStage.Idle)
    val downloadStage: StateFlow<ModelDownloadStage> = _downloadStage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var downloadJob: Job? = null

    fun download(modelId: String) {
        if (_downloadingModelId.value != null) return
        _downloadingModelId.value = modelId
        _downloadProgress.value = 0f
        _statusMessage.value = "Connecting to server..."
        _downloadStage.value = ModelDownloadStage.Connecting

        downloadJob = viewModelScope.launch {
            val result = modelManager.installModel(modelId) { stage ->
                _downloadStage.value = stage
                when (stage) {
                    is ModelDownloadStage.Connecting -> {
                        _statusMessage.value = "Connecting to download server..."
                    }
                    is ModelDownloadStage.Downloading -> {
                        _downloadProgress.value = stage.progress
                        val mbDown = stage.bytesDownloaded / (1024 * 1024)
                        val mbTotal = stage.totalBytes / (1024 * 1024)
                        val pct = (stage.progress * 100).toInt()
                        _statusMessage.value = if (stage.totalBytes > 0) {
                            "Downloading: $pct% ($mbDown MB / $mbTotal MB)"
                        } else {
                            "Downloading: $mbDown MB"
                        }
                    }
                    is ModelDownloadStage.Extracting -> {
                        _statusMessage.value = "Extracting model files safely..."
                    }
                    is ModelDownloadStage.Verifying -> {
                        _statusMessage.value = "Verifying acoustic model integrity..."
                    }
                    is ModelDownloadStage.Completed -> {
                        _statusMessage.value = "Model verified and ready for offline transcription!"
                    }
                    is ModelDownloadStage.Failed -> {
                        _statusMessage.value = "Download failed: ${stage.error}"
                    }
                    is ModelDownloadStage.Cancelled -> {
                        _statusMessage.value = "Download cancelled."
                    }
                    else -> {}
                }
            }

            _downloadingModelId.value = null
            downloadJob = null

            when (result) {
                is ModelInstallResult.Success -> {
                    _statusMessage.value = "Model installed and activated for offline transcription!"
                }
                is ModelInstallResult.Cancelled -> {
                    _statusMessage.value = "Download cancelled by user."
                }
                is ModelInstallResult.Error -> {
                    _statusMessage.value = "Installation failed: ${result.message}"
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadingModelId.value = null
        _downloadProgress.value = 0f
        _downloadStage.value = ModelDownloadStage.Cancelled
        _statusMessage.value = "Download cancelled by user."
    }

    fun retry(modelId: String) {
        download(modelId)
    }

    fun selectActive(modelId: String) {
        val success = modelManager.setActiveModel(modelId)
        _statusMessage.value = if (success) {
            "Active model updated."
        } else {
            "Cannot activate model: model files are missing or incomplete."
        }
    }

    fun delete(modelId: String) {
        modelManager.deleteModel(modelId)
        _statusMessage.value = "Model deleted to reclaim storage."
    }
}
