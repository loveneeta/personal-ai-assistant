package com.personalai.assistant.transcription

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.wakeword.AudioOwner
import com.personalai.assistant.wakeword.JarvisVoiceCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Google Speech Recognition & Live Multilingual Dictation Engine.
 *
 * Implements Google Voice Typing with native Hindi + English (Hinglish) code-switching:
 * 1. Automatically recognizes mixed Hindi and English in the same sentence without manual toggling.
 * 2. Real-time live speech readout ([liveHypothesis]) as words are spoken.
 * 3. Script-aware clause completion and punctuation ([completedSentences]) preserving Romanized Hinglish and Devanagari.
 * 4. Continuous dictation loop that persists across pauses and sentence boundaries.
 */
class GoogleVoiceRecognitionManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private val isRunning = AtomicBoolean(false)
    private var sessionStartTimeMs: Long = 0

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _liveHypothesis = MutableStateFlow("")
    val liveHypothesis: StateFlow<String> = _liveHypothesis.asStateFlow()

    private val _completedSentences = MutableStateFlow<List<String>>(emptyList())
    val completedSentences: StateFlow<List<String>> = _completedSentences.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val isOnDeviceAvailable: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

    fun startListening(languageCode: String = "hi-en") {
        if (isRunning.get()) return

        if (!isRecognitionAvailable) {
            _errorMessage.value = "Google Speech Recognition is not installed or enabled on this device."
            return
        }

        if (!JarvisVoiceCoordinator.requestMicrophone(AudioOwner.VOICE_MATRIX)) {
            _errorMessage.value = "Microphone is currently held by another recording session."
            return
        }

        _errorMessage.value = null
        _liveHypothesis.value = ""
        _completedSentences.value = emptyList()
        sessionStartTimeMs = System.currentTimeMillis()
        isRunning.set(true)
        _isListening.value = true

        mainHandler.post {
            initRecognizerAndListen(languageCode)
        }
    }

    private fun initRecognizerAndListen(languageCode: String) {
        destroyRecognizer()

        try {
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isOnDeviceAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _errorMessage.value = null
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _soundLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _soundLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _soundLevel.value = 0f
                    if (!isRunning.get()) return

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Do NOT immediately restart in an infinite loop that produces repetitive beeps.
                            // If user is idle, remain quiet without rapid beeping chimes.
                            if (isRunning.get()) {
                                mainHandler.postDelayed({
                                    if (isRunning.get()) {
                                        restartListeningQuietly(languageCode)
                                    }
                                }, 1500)
                            }
                        }
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            val err = when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording hardware error"
                                else -> "Speech service error ($error)"
                            }
                            _errorMessage.value = err
                            stopListening()
                        }
                        else -> {
                            if (isRunning.get()) {
                                mainHandler.postDelayed({
                                    if (isRunning.get()) {
                                        restartListeningQuietly(languageCode)
                                    }
                                }, 2000)
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    _soundLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val rawResult = matches?.firstOrNull()?.trim()

                    if (!rawResult.isNullOrBlank()) {
                        val formatted = formatGoogleSentence(rawResult)
                        _completedSentences.value = _completedSentences.value + formatted
                    }
                    _liveHypothesis.value = ""

                    if (isRunning.get()) {
                        mainHandler.postDelayed({
                            if (isRunning.get()) {
                                restartListeningQuietly(languageCode)
                            }
                        }, 500)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim() ?: ""
                    if (partial.isNotBlank()) {
                        _liveHypothesis.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = createSpeechIntent(languageCode)
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Failed to start speech engine: ${e.message}"
            stopListening()
        }
    }

    private fun restartListeningQuietly(languageCode: String) {
        if (!isRunning.get()) return
        try {
            speechRecognizer?.cancel()
            val intent = createSpeechIntent(languageCode)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            initRecognizerAndListen(languageCode)
        }
    }

    private fun createSpeechIntent(languageCode: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            if (languageCode == "hi-en" || languageCode.isBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                val allowedLanguages = arrayListOf("hi-IN", "en-IN", "hi-Latn-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", allowedLanguages.toTypedArray())
                putExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayOf("hi-IN", "en-IN"))
                putExtra("android.speech.extra.MULTI_LANG_ALLOWED", true)
                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                }
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            }

            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
    }

    fun stopListening() {
        isRunning.set(false)
        _isListening.value = false
        _soundLevel.value = 0f
        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.VOICE_MATRIX)
        mainHandler.post {
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
        }
    }

    /**
     * Saves the live dictation session into SQLite Room database.
     * Generates a completed recording entry and all associated sentence transcripts.
     */
    suspend fun saveSession(
        sessionTitle: String? = null,
        languageCode: String = "hi-en"
    ): Long = withContext(Dispatchers.IO) {
        stopListening()

        val sentences = _completedSentences.value
        val now = System.currentTimeMillis()
        val durationMs = (now - sessionStartTimeMs).coerceAtLeast(1000L)

        val defaultTitle = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(sessionStartTimeMs))
        val title = sessionTitle?.ifBlank { null } ?: "Voice Note $defaultTitle"

        val recordingEntity = RecordingEntity(
            title = title,
            filePath = "[Live Google Voice Dictation]",
            startTimestamp = sessionStartTimeMs,
            endTimestamp = now,
            durationMs = durationMs,
            status = "COMPLETED",
            fileSizeBytes = 0L,
            mimeType = "text/transcript",
            processingStatus = "COMPLETED"
        )

        val recordingId = repository.insertRecording(recordingEntity)

        if (sentences.isNotEmpty()) {
            val approxDurationPerSentence = durationMs / sentences.size
            val transcriptEntities = sentences.mapIndexed { index, text ->
                val startMs = index * approxDurationPerSentence
                val endMs = ((index + 1) * approxDurationPerSentence).coerceAtLeast(startMs + 500L)
                TranscriptEntity(
                    recordingId = recordingId,
                    segmentIndex = index,
                    startOffsetMs = startMs,
                    endOffsetMs = endMs,
                    text = text,
                    language = languageCode,
                    confidence = 0.98f,
                    reviewStatus = "VERIFIED"
                )
            }
            repository.insertTranscripts(transcriptEntities)
        } else {
            val emptyEntity = TranscriptEntity(
                recordingId = recordingId,
                segmentIndex = 0,
                startOffsetMs = 0L,
                endOffsetMs = durationMs,
                text = "[No speech captured during voice dictation]",
                language = languageCode,
                confidence = 1.0f,
                reviewStatus = "AUTOMATIC"
            )
            repository.insertTranscripts(listOf(emptyEntity))
        }

        _completedSentences.value = emptyList()
        _liveHypothesis.value = ""

        recordingId
    }

    fun formatGoogleSentence(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val containsDevanagari = SentenceReconstructor.hasDevanagari(trimmed)

        // Capitalize first character in Latin script
        val capitalized = if (!containsDevanagari) {
            trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            trimmed
        }

        // Script-aware punctuation
        return if (containsDevanagari) {
            if (!capitalized.endsWith("।") && !capitalized.endsWith("?") && !capitalized.endsWith(".")) {
                "$capitalized।"
            } else capitalized
        } else {
            if (!capitalized.endsWith(".") && !capitalized.endsWith("?") && !capitalized.endsWith("!")) {
                "$capitalized."
            } else capitalized
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GoogleVoiceRecognitionManager? = null

        fun getInstance(context: Context): GoogleVoiceRecognitionManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val repo = AppRepository.getInstance(appCtx)
                INSTANCE ?: GoogleVoiceRecognitionManager(appCtx, repo).also { INSTANCE = it }
            }
        }
    }
}
