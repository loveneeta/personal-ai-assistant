package com.personalai.assistant.transcription

import android.content.Context
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

sealed class TranscriptionProgress {
    object Idle : TranscriptionProgress()
    data class Processing(val recordingId: Long, val progress: Float, val statusText: String) : TranscriptionProgress()
    data class Completed(val recordingId: Long, val segmentCount: Int) : TranscriptionProgress()
    data class Failed(val recordingId: Long, val reason: String) : TranscriptionProgress()
}

class TranscriptionQueueManager(
    private val context: Context,
    private val repository: AppRepository,
    private val modelManager: ModelManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(false)
    private val activeJobMap = ConcurrentHashMap<Long, Boolean>()

    private val _progressState = MutableStateFlow<TranscriptionProgress>(TranscriptionProgress.Idle)
    val progressState: StateFlow<TranscriptionProgress> = _progressState.asStateFlow()

    private val engine = VoskTranscriptionEngine()

    init {
        // Automatically scan and resume pending transcriptions on startup
        resumePendingTranscriptions()
    }

    fun resumePendingTranscriptions() {
        scope.launch {
            val allRecordings = repository.getAllRecordingsFlow()
            // We launch one collection to check for pending items
            launch {
                allRecordings.collect { list ->
                    val pending = list.filter {
                        (it.processingStatus == "PENDING" || it.processingStatus == "PROCESSING") &&
                                (it.status == "COMPLETED" || it.status == "INTERRUPTED")
                    }
                    for (rec in pending) {
                        enqueue(rec.id)
                    }
                }
            }
        }
    }

    fun enqueue(recordingId: Long, explicitModelId: String? = null) {
        if (activeJobMap.putIfAbsent(recordingId, true) != null) {
            // Already queued or running, prevent duplicate processing
            return
        }

        scope.launch {
            processRecording(recordingId, explicitModelId)
        }
    }

    suspend fun reprocess(recordingId: Long, explicitModelId: String? = null) = withContext(Dispatchers.IO) {
        // Clear previous transcripts to avoid duplicates
        repository.clearTranscripts(recordingId)
        val rec = repository.getRecording(recordingId)
        if (rec != null) {
            repository.updateRecording(
                rec.copy(
                    processingStatus = "PENDING",
                    errorDetails = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            activeJobMap.remove(recordingId)
            enqueue(recordingId, explicitModelId)
        }
    }

    private suspend fun processRecording(recordingId: Long, explicitModelId: String? = null) {
        try {
            val recording = repository.getRecording(recordingId)
            if (recording == null) {
                activeJobMap.remove(recordingId)
                return
            }

            val audioFile = File(recording.filePath)
            if (!audioFile.exists() || audioFile.length() < 44) {
                repository.updateRecording(
                    recording.copy(
                        processingStatus = "FAILED",
                        errorDetails = "Audio file missing or empty",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _progressState.value = TranscriptionProgress.Failed(recordingId, "Audio file missing or empty")
                activeJobMap.remove(recordingId)
                return
            }

            val modelDir = if (explicitModelId != null) {
                modelManager.getModelDirectory(explicitModelId)
            } else {
                modelManager.getActiveModelDirectory()
            }

            if (modelDir == null) {
                _progressState.value = TranscriptionProgress.Failed(
                    recordingId,
                    "No AI speech model downloaded yet. Open Settings > AI Models to download."
                )
                repository.updateRecording(
                    recording.copy(
                        processingStatus = "FAILED",
                        errorDetails = "Speech model not downloaded. Go to Settings > AI Models.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                activeJobMap.remove(recordingId)
                return
            }

            // Mark status as PROCESSING
            repository.updateRecording(
                recording.copy(
                    processingStatus = "PROCESSING",
                    updatedAt = System.currentTimeMillis()
                )
            )

            _progressState.value = TranscriptionProgress.Processing(recordingId, 0.05f, "Initializing speech model...")

            // Run on-device transcription
            val modelIdToUse = explicitModelId ?: modelManager.activeModelId.value
            val langCode = if (modelIdToUse.contains("hi")) "hi" else "en"

            val segments = engine.transcribe(
                audioFile = audioFile,
                modelDir = modelDir,
                languageCode = langCode,
                onProgress = { frac ->
                    _progressState.value = TranscriptionProgress.Processing(
                        recordingId,
                        frac,
                        "Transcribing speech (${(frac * 100).toInt()}%)..."
                    )
                }
            )

            // Clear any lingering segments and persist new ones into Room
            repository.clearTranscripts(recordingId)
            if (segments.isNotEmpty()) {
                val entities = segments.mapIndexed { index, seg ->
                    TranscriptEntity(
                        recordingId = recordingId,
                        segmentIndex = index,
                        startOffsetMs = seg.startOffsetMs,
                        endOffsetMs = seg.endOffsetMs,
                        text = seg.text,
                        language = seg.language,
                        confidence = seg.confidence,
                        reviewStatus = "AUTOMATIC"
                    )
                }
                repository.insertTranscripts(entities)

                repository.updateRecording(
                    recording.copy(
                        processingStatus = "COMPLETED",
                        errorDetails = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _progressState.value = TranscriptionProgress.Completed(recordingId, segments.size)
            } else {
                // Audio contained no detectable speech / silence
                val emptySegment = TranscriptEntity(
                    recordingId = recordingId,
                    segmentIndex = 0,
                    startOffsetMs = 0,
                    endOffsetMs = recording.durationMs.coerceAtLeast(1000L),
                    text = "[No speech detected in audio]",
                    language = langCode,
                    confidence = 1.0f,
                    reviewStatus = "AUTOMATIC"
                )
                repository.insertTranscripts(listOf(emptySegment))

                repository.updateRecording(
                    recording.copy(
                        processingStatus = "COMPLETED",
                        errorDetails = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _progressState.value = TranscriptionProgress.Completed(recordingId, 1)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            repository.updateRecording(
                repository.getRecording(recordingId)?.copy(
                    processingStatus = "FAILED",
                    errorDetails = e.message ?: "Transcription error",
                    updatedAt = System.currentTimeMillis()
                ) ?: return
            )
            _progressState.value = TranscriptionProgress.Failed(recordingId, e.message ?: "Transcription error")
        } finally {
            activeJobMap.remove(recordingId)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TranscriptionQueueManager? = null

        fun getInstance(context: Context): TranscriptionQueueManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val repo = AppRepository.getInstance(appCtx)
                val modelMgr = ModelManager.getInstance(appCtx)
                INSTANCE ?: TranscriptionQueueManager(appCtx, repo, modelMgr).also { INSTANCE = it }
            }
        }
    }
}
