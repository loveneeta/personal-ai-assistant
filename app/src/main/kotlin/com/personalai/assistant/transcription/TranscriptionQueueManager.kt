package com.personalai.assistant.transcription

import android.content.Context
import android.util.Log
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobMap = ConcurrentHashMap<Long, Boolean>()

    private val _progressState = MutableStateFlow<TranscriptionProgress>(TranscriptionProgress.Idle)
    val progressState: StateFlow<TranscriptionProgress> = _progressState.asStateFlow()

    private val engine = VoskTranscriptionEngine()

    init {
        // Automatically scan and resume pending transcriptions on startup once
        resumePendingTranscriptions()
    }

    /**
     * Reconciles interrupted tasks on startup (Requirement 8) and enqueues uncompleted
     * sessions via a one-shot query instead of an unbounded Flow collector (RC-06).
     */
    fun resumePendingTranscriptions() {
        scope.launch {
            try {
                val allRecordings = repository.getAllRecordings()

                // Prevent interrupted sessions from remaining stuck in PROCESSING permanently
                for (rec in allRecordings) {
                    if (rec.processingStatus == "PROCESSING") {
                        Log.i(TAG, "Reconciling interrupted recording #${rec.id} from PROCESSING to PENDING")
                        repository.updateRecording(
                            rec.copy(
                                processingStatus = "PENDING",
                                errorDetails = "Processing was interrupted by application shutdown. Queued for retry.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Filter valid items ready for transcription
                val pending = allRecordings.filter {
                    (it.processingStatus == "PENDING" || it.processingStatus == "PROCESSING") &&
                            (it.status == "COMPLETED" || it.status == "INTERRUPTED")
                }

                for (rec in pending) {
                    enqueue(rec.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming pending transcriptions: ${e.message}", e)
            }
        }
    }

    /**
     * Enqueues a recording ID for background processing with atomic deduplication.
     */
    fun enqueue(recordingId: Long, explicitModelId: String? = null): Boolean {
        if (activeJobMap.putIfAbsent(recordingId, true) != null) {
            Log.d(TAG, "Recording #$recordingId is already queued or actively transcribing. Ignoring duplicate enqueue.")
            return false
        }

        scope.launch {
            processRecording(recordingId, explicitModelId)
        }
        return true
    }

    /**
     * Reprocesses a recording with an optional explicit model.
     * Preserves previous good transcripts if replacement transcription fails (Requirement 9).
     */
    suspend fun reprocess(recordingId: Long, explicitModelId: String? = null) = withContext(Dispatchers.IO) {
        val rec = repository.getRecording(recordingId)
        if (rec != null) {
            repository.updateRecording(
                rec.copy(
                    processingStatus = "PENDING",
                    errorDetails = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            // Remove from active map if previously held so it can be re-enqueued
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
            if (!audioFile.exists() || audioFile.length() < 44L) {
                val errorMsg = "Audio file is missing or contains no data"
                repository.updateRecording(
                    recording.copy(
                        processingStatus = "FAILED",
                        errorDetails = errorMsg,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _progressState.value = TranscriptionProgress.Failed(recordingId, errorMsg)
                activeJobMap.remove(recordingId)
                return
            }

            // Validate model availability before starting transcription (Requirement 6)
            // Never silently substitute an unrelated language model (Requirement 5)
            val modelIdToUse = explicitModelId ?: modelManager.activeModelId.value
            val modelDir = modelManager.getModelDirectory(modelIdToUse)

            if (modelDir == null) {
                val errorMsg = if (explicitModelId != null) {
                    "Requested speech model ($explicitModelId) is not installed. Go to Settings > AI Models."
                } else {
                    "Active speech model ($modelIdToUse) is not installed. Go to Settings > AI Models."
                }
                Log.w(TAG, errorMsg)
                repository.updateRecording(
                    recording.copy(
                        processingStatus = "FAILED",
                        errorDetails = errorMsg,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _progressState.value = TranscriptionProgress.Failed(recordingId, errorMsg)
                activeJobMap.remove(recordingId)
                return
            }

            // Transition status to PROCESSING
            repository.updateRecording(
                recording.copy(
                    processingStatus = "PROCESSING",
                    errorDetails = null,
                    updatedAt = System.currentTimeMillis()
                )
            )

            _progressState.value = TranscriptionProgress.Processing(recordingId, 0.05f, "Initializing speech model...")

            val langCode = when {
                modelIdToUse.contains("en-in") -> "hi-en"
                modelIdToUse.contains("hi") -> "hi"
                else -> "en"
            }

            // Run on-device transcription
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

            // Safe replacement (Requirement 9):
            // Only clear old transcripts AFTER new transcription has successfully finished
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
            Log.e(TAG, "Transcription failed for recording #$recordingId: ${e.message}", e)
            val errorMessage = e.message ?: "Transcription processing error"

            // Previous transcripts are preserved in repository because clearTranscripts was not called!
            repository.updateRecording(
                repository.getRecording(recordingId)?.copy(
                    processingStatus = "FAILED",
                    errorDetails = errorMessage,
                    updatedAt = System.currentTimeMillis()
                ) ?: return
            )
            _progressState.value = TranscriptionProgress.Failed(recordingId, errorMessage)
        } finally {
            activeJobMap.remove(recordingId)
        }
    }

    companion object {
        private const val TAG = "TranscriptionQueue"

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
