package com.personalai.assistant.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.personalai.assistant.actions.AndroidActionExecutor
import com.personalai.assistant.actions.AssistantAction
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.LocalRagChatEngine
import com.personalai.assistant.data.local.entity.WakeEventEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.transcription.AsrEngine
import com.personalai.assistant.transcription.MultilingualOfflineAsrEngine
import com.personalai.assistant.transcription.SentenceReconstructor
import com.personalai.assistant.transcription.models.ModelManager
import com.personalai.assistant.transcription.models.TranscriptionLanguageMode
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Robust, race-safe single audio state machine driving the hands-free
 * JARVIS assistant lifecycle for both Android and multi-platform coordination.
 *
 * Implements strict lifecycle guarantees:
 * - PAUSED stops microphone capture and releases hardware resources.
 * - STOPPED halts capture and completely frees audio resources.
 * - Stale TTS callbacks are rejected via unique session IDs and desired-listening gating.
 * - Thread-safe teardown guarantees that AudioRecord is never released concurrently while reading.
 */
class JarvisVoiceStateMachine(
    private val context: Context,
    private val repository: AppRepository,
    private val speakerManager: SpeakerProfileManager,
    private val wakeWordEngine: WakeWordEngine
) {
    companion object {
        private const val TAG = "JarvisStateMachine"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAKE_DEBOUNCE_MS = 2500L
        private const val DEFAULT_COMMAND_TIMEOUT_MS = 6000L
        private const val SILENCE_THRESHOLD_RMS = 250f
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var loopJob: Job? = null
    private var commandTimeoutJob: Job? = null

    private val _currentState = MutableStateFlow(JarvisAudioState.IDLE_DISABLED)
    val currentState: StateFlow<JarvisAudioState> = _currentState.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _lastWakeEvent = MutableStateFlow<WakeEventEntity?>(null)
    val lastWakeEvent: StateFlow<WakeEventEntity?> = _lastWakeEvent.asStateFlow()

    private val _activeUtterance = MutableStateFlow<String?>(null)
    val activeUtterance: StateFlow<String?> = _activeUtterance.asStateFlow()

    private val _lastAssistantResponse = MutableStateFlow<String?>(null)
    val lastAssistantResponse: StateFlow<String?> = _lastAssistantResponse.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private val isAudioCapturing = AtomicBoolean(false)
    private val isDesiredListening = AtomicBoolean(false)
    private val currentTtsSessionId = AtomicLong(0L)
    private val captureLock = Any()

    private var lastWakeTimestamp = 0L

    // Configurable settings
    var configuredWakeWord: String = "Jarvis"
    var commandTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
    var rawAudioRetentionEnabled: Boolean = false

    private val actionExecutor = AndroidActionExecutor(context)
    private val ragEngine = LocalRagChatEngine(repository)
    private val asrEngine: AsrEngine = MultilingualOfflineAsrEngine(context, ModelManager.getInstance(context))
    private var textToSpeech: TextToSpeech? = null
    private val isTtsSpeaking = AtomicBoolean(false)

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isTtsSpeaking.set(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking.set(false)
                        handleTtsCompletion(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking.set(false)
                        handleTtsCompletion(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isTtsSpeaking.set(false)
                        handleTtsCompletion(utteranceId)
                    }
                })
            }
        }
    }

    @Synchronized
    fun startHandsFree(wakeWord: String = "Jarvis") {
        configuredWakeWord = wakeWord
        isDesiredListening.set(true)

        if (_currentState.value == JarvisAudioState.WAKE_LISTENING && isAudioCapturing.get()) {
            Log.d(TAG, "Already active in WAKE_LISTENING")
            return
        }

        transitionTo(JarvisAudioState.STARTING)
        val ready = wakeWordEngine.initialize(configuredWakeWord, 0.7f)
        if (!ready) {
            Log.w(TAG, "Offline wake-word model is unavailable. Speech model needs to be downloaded in Settings.")
        } else {
            Log.i(TAG, "Initialized wake engine: ${wakeWordEngine.engineName}")
        }

        startAudioLoop()
    }

    @Synchronized
    fun pauseHandsFree() {
        Log.i(TAG, "Pausing hands-free listening")
        isDesiredListening.set(false)
        currentTtsSessionId.incrementAndGet()
        stopTts()
        stopAudioLoop()
        transitionTo(JarvisAudioState.PAUSED)
    }

    @Synchronized
    fun resumeHandsFree() {
        Log.i(TAG, "Resuming hands-free listening from state ${_currentState.value}")
        if (_currentState.value != JarvisAudioState.PAUSED) {
            Log.d(TAG, "resumeHandsFree called when not in PAUSED state (${_currentState.value}), ignoring")
            return
        }
        isDesiredListening.set(true)
        startAudioLoop()
    }

    @Synchronized
    fun stopHandsFree() {
        Log.i(TAG, "Stopping hands-free listening")
        isDesiredListening.set(false)
        currentTtsSessionId.incrementAndGet()
        stopTts()
        stopAudioLoop()
        transitionTo(JarvisAudioState.STOPPED)
        transitionTo(JarvisAudioState.IDLE_DISABLED)
    }

    fun interruptSpeech() {
        if (_currentState.value == JarvisAudioState.SPEAKING) {
            stopTts()
            currentTtsSessionId.incrementAndGet()
            if (isDesiredListening.get()) {
                transitionTo(JarvisAudioState.WAKE_LISTENING)
            } else {
                transitionTo(JarvisAudioState.PAUSED)
            }
        }
    }

    fun isCapturing(): Boolean = isAudioCapturing.get()

    fun isDesiredListening(): Boolean = isDesiredListening.get()

    fun getCurrentTtsSessionId(): Long = currentTtsSessionId.get()

    @VisibleForTesting
    internal fun simulateSpeakingState(sessionId: Long? = null): Long {
        val id = sessionId ?: currentTtsSessionId.incrementAndGet()
        if (sessionId != null) {
            currentTtsSessionId.set(sessionId)
        }
        isDesiredListening.set(true)
        isTtsSpeaking.set(true)
        transitionTo(JarvisAudioState.SPEAKING)
        return id
    }

    @VisibleForTesting
    internal fun handleTtsCompletion(utteranceId: String?, skipDelay: Boolean = false): Job? {
        val currentId = currentTtsSessionId.get()
        val expectedId = "JARVIS_RESPONSE_$currentId"
        if (utteranceId != expectedId) {
            Log.d(TAG, "Rejecting stale TTS callback: received '$utteranceId', expected '$expectedId'")
            return null
        }

        return scope.launch {
            if (!skipDelay) {
                delay(400) // Grace period to avoid self-speech echo
            }
            synchronized(this@JarvisVoiceStateMachine) {
                if (utteranceId == "JARVIS_RESPONSE_${currentTtsSessionId.get()}" &&
                    isDesiredListening.get() &&
                    _currentState.value == JarvisAudioState.SPEAKING
                ) {
                    if (isAudioCapturing.get()) {
                        transitionTo(JarvisAudioState.WAKE_LISTENING)
                    } else {
                        startAudioLoop()
                    }
                } else {
                    Log.d(TAG, "Ignoring TTS completion: desiredListening=${isDesiredListening.get()}, state=${_currentState.value}")
                }
            }
        }
    }

    private fun startAudioLoop() {
        synchronized(captureLock) {
            if (isAudioCapturing.get()) {
                Log.d(TAG, "Audio capture loop already active, ignoring duplicate start request")
                return
            }

            // Check microphone permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Microphone permission RECORD_AUDIO not granted or revoked")
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.PERMISSION_DENIED)
                return
            }

            // Coordinate microphone access to prevent hardware collision
            if (!JarvisVoiceCoordinator.requestMicrophone(AudioOwner.ALWAYS_ON_LISTENER)) {
                Log.w(TAG, "Cannot start audio capture: mic owned by ${JarvisVoiceCoordinator.currentOwner.value}")
                transitionTo(JarvisAudioState.PAUSED)
                return
            }

            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufSize <= 0) {
                Log.e(TAG, "Invalid AudioRecord minBufferSize: $minBufSize")
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.ERROR)
                return
            }
            val bufferSize = Math.max(minBufSize * 2, 4096)

            val record: AudioRecord
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException creating AudioRecord: ${e.message}")
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.PERMISSION_DENIED)
                return
            } catch (e: Throwable) {
                Log.e(TAG, "AudioRecord creation failed: ${e.message}", e)
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.ERROR)
                return
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed initialization (state != STATE_INITIALIZED)")
                try { record.release() } catch (ignored: Exception) {}
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.ERROR)
                return
            }

            try {
                record.startRecording()
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on startRecording: ${e.message}")
                try { record.release() } catch (ignored: Exception) {}
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.PERMISSION_DENIED)
                return
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to startRecording: ${e.message}", e)
                try { record.release() } catch (ignored: Exception) {}
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                transitionTo(JarvisAudioState.ERROR)
                return
            }

            audioRecord = record
            isAudioCapturing.set(true)
            transitionTo(JarvisAudioState.WAKE_LISTENING)

            loopJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(1024)
                val commandPcmAccumulator = ArrayList<Short>()
                // 500ms rolling pre-buffer (8000 samples at 16kHz)
                val preBuffer = ShortArray(8000)
                var preBufferWriteIdx = 0
                var hasSpeechStarted = false
                var silentSamplesAfterSpeech = 0

                try {
                    while (isActive && isAudioCapturing.get()) {
                        val currentRecord = audioRecord ?: break
                        val read = try {
                            currentRecord.read(shortBuffer, 0, shortBuffer.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception reading AudioRecord: ${e.message}")
                            -1
                        }

                        if (!isAudioCapturing.get() || !isActive) break

                        if (read <= 0) {
                            if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                                if (!isAudioCapturing.get()) break
                            }
                            delay(20)
                            continue
                        }

                        // Calculate RMS sound level for visualizer
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            sumSquares += shortBuffer[i].toDouble() * shortBuffer[i].toDouble()
                        }
                        val rms = sqrt(sumSquares / read).toFloat()
                        _soundLevel.value = (rms / 32768f).coerceIn(0f, 1f)

                        // SELF-FEEDBACK PROTECTION: Never process wake words while TTS is speaking
                        if (isTtsSpeaking.get() || _currentState.value == JarvisAudioState.SPEAKING) {
                            delay(50)
                            continue
                        }

                        when (_currentState.value) {
                            JarvisAudioState.WAKE_LISTENING -> {
                                for (i in 0 until read) {
                                    preBuffer[preBufferWriteIdx % preBuffer.size] = shortBuffer[i]
                                    preBufferWriteIdx++
                                }

                                val detected = if (wakeWordEngine.isReady) {
                                    wakeWordEngine.processPcmChunk(shortBuffer, read)
                                } else {
                                    // Acoustic heuristics disabled per Phase 3 accuracy requirements
                                    false
                                }
                                val now = SystemClock.elapsedRealtime()

                                if (detected && (now - lastWakeTimestamp) > WAKE_DEBOUNCE_MS) {
                                    lastWakeTimestamp = now
                                    try {
                                        @Suppress("DEPRECATION")
                                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator?.vibrate(120)
                                        }
                                    } catch (e: Exception) {}
                                    handleWakeWordDetected(shortBuffer, read)
                                    commandPcmAccumulator.clear()
                                    val preLen = minOf(preBufferWriteIdx, preBuffer.size)
                                    val startOffset = if (preBufferWriteIdx > preBuffer.size) preBufferWriteIdx % preBuffer.size else 0
                                    for (i in 0 until preLen) {
                                        commandPcmAccumulator.add(preBuffer[(startOffset + i) % preBuffer.size])
                                    }
                                    hasSpeechStarted = false
                                    silentSamplesAfterSpeech = 0
                                }
                            }

                            JarvisAudioState.COMMAND_LISTENING -> {
                                for (i in 0 until read) commandPcmAccumulator.add(shortBuffer[i])

                                if (rms >= SILENCE_THRESHOLD_RMS) {
                                    hasSpeechStarted = true
                                    silentSamplesAfterSpeech = 0
                                } else if (hasSpeechStarted) {
                                    silentSamplesAfterSpeech += read
                                }

                                val isNaturalPause = hasSpeechStarted && silentSamplesAfterSpeech >= (SAMPLE_RATE * 1.4)
                                val isMaxUtterance = commandPcmAccumulator.size >= (SAMPLE_RATE * 9)

                                if (isNaturalPause || isMaxUtterance) {
                                    finishCommandListening(ArrayList(commandPcmAccumulator))
                                    commandPcmAccumulator.clear()
                                    hasSpeechStarted = false
                                    silentSamplesAfterSpeech = 0
                                }
                            }

                            else -> {
                                // Other states do not process raw audio stream
                            }
                        }
                    }
                } finally {
                    // Guaranteed thread-safe cleanup on IO coroutine termination
                    _soundLevel.value = 0f
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping record in finally: ${e.message}")
                    }
                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing record in finally: ${e.message}")
                    }
                    if (audioRecord == record) {
                        audioRecord = null
                    }
                    isAudioCapturing.set(false)
                }
            }
        }
    }

    private fun handleWakeWordDetected(sampleChunk: ShortArray, readLen: Int) {
        scope.launch {
            transitionTo(JarvisAudioState.WAKE_DETECTED)
            Log.i(TAG, ">>> Wake word '$configuredWakeWord' detected!")

            // Speaker Verification Phase (if configured)
            if (speakerManager.isSpeakerVerificationEnabled.value && speakerManager.isEnrolled.value) {
                transitionTo(JarvisAudioState.VERIFYING_SPEAKER)
                val verification = speakerManager.verifySpeaker(sampleChunk, readLen)
                if (!verification.isVerified) {
                    Log.w(TAG, "Speaker verification failed: ${verification.reason}")
                    logWakeEvent(
                        wakeWord = configuredWakeWord,
                        verified = false,
                        command = null,
                        response = null,
                        status = "REJECTED_SPEAKER"
                    )
                    delay(1200)
                    if (isDesiredListening.get() && _currentState.value == JarvisAudioState.VERIFYING_SPEAKER) {
                        transitionTo(JarvisAudioState.WAKE_LISTENING)
                    }
                    return@launch
                }
            }

            // Move to Command Listening
            transitionTo(JarvisAudioState.COMMAND_LISTENING)
            startCommandTimeout()
        }
    }

    private fun startCommandTimeout() {
        commandTimeoutJob?.cancel()
        commandTimeoutJob = scope.launch {
            delay(commandTimeoutMs)
            if (_currentState.value == JarvisAudioState.COMMAND_LISTENING && isDesiredListening.get()) {
                Log.i(TAG, "Command listening timed out without speech.")
                logWakeEvent(
                    wakeWord = configuredWakeWord,
                    verified = speakerManager.isSpeakerVerificationEnabled.value,
                    command = null,
                    response = null,
                    status = "TIMEOUT"
                )
                transitionTo(JarvisAudioState.WAKE_LISTENING)
            }
        }
    }

    private fun finishCommandListening(pcmList: List<Short>) {
        commandTimeoutJob?.cancel()
        scope.launch {
            transitionTo(JarvisAudioState.PROCESSING)

            // Decode real PCM audio samples using on-device speech recognizer
            val decodedSpeech = decodePcmCommand(pcmList)
            val spokenText = if (decodedSpeech.isNotBlank()) {
                decodedSpeech
            } else if (!_activeUtterance.value.isNullOrBlank()) {
                _activeUtterance.value!!
            } else {
                ""
            }

            Log.i(TAG, "Recognized spoken voice command: '$spokenText'")

            if (spokenText.isBlank()) {
                Log.w(TAG, "No clear speech recognized in command buffer")
                speakResponse("I am listening, Commander. Please give an instruction.")
                return@launch
            }

            // Check for cancel / stop commands:
            val lower = spokenText.lowercase().trim()
            if (lower == "stop listening" || lower == "jarvis stop listening" || lower == "cancel") {
                speakResponse("Hands-free listening stopped, sir.")
                logWakeEvent(configuredWakeWord, true, spokenText, "Listening stopped", "CANCELLED")
                stopHandsFree()
                return@launch
            }

            // First evaluate native Android Actions (Apps, Settings, Dialer, Search, Maps)
            val actionStart = SystemClock.elapsedRealtime()
            val parsedAction = actionExecutor.parseAction(spokenText)
            if (parsedAction !is AssistantAction.None) {
                val actionResult = actionExecutor.execute(parsedAction)
                val actionDuration = SystemClock.elapsedRealtime() - actionStart
                Log.i(TAG, "Native Android action executed in ${actionDuration}ms: '${actionResult.spokenFeedback}'")

                _lastAssistantResponse.value = actionResult.spokenFeedback
                logWakeEvent(
                    wakeWord = configuredWakeWord,
                    verified = speakerManager.isSpeakerVerificationEnabled.value,
                    command = spokenText,
                    response = actionResult.spokenFeedback,
                    status = if (actionResult.handled) "COMPLETED" else "FAILED"
                )
                speakResponse(actionResult.spokenFeedback)
                return@launch
            }

            // Fallback: Query Local RAG Chat Engine & Personal Memory
            val chatResponse: ChatMessage = ragEngine.answerQuery(spokenText)
            _lastAssistantResponse.value = chatResponse.content

            // Log event to database
            logWakeEvent(
                wakeWord = configuredWakeWord,
                verified = speakerManager.isSpeakerVerificationEnabled.value,
                command = spokenText,
                response = chatResponse.content,
                status = "COMPLETED"
            )

            // Speak out answer
            speakResponse(chatResponse.content)
        }
    }

    fun submitDirectCommand(command: String) {
        _activeUtterance.value = command
        scope.launch {
            transitionTo(JarvisAudioState.PROCESSING)

            val parsedAction = actionExecutor.parseAction(command)
            if (parsedAction !is AssistantAction.None) {
                val actionResult = actionExecutor.execute(parsedAction)
                _lastAssistantResponse.value = actionResult.spokenFeedback
                logWakeEvent(configuredWakeWord, true, command, actionResult.spokenFeedback, if (actionResult.handled) "COMPLETED" else "FAILED")
                speakResponse(actionResult.spokenFeedback)
                return@launch
            }

            val chatResponse = ragEngine.answerQuery(command)
            _lastAssistantResponse.value = chatResponse.content
            logWakeEvent(configuredWakeWord, true, command, chatResponse.content, "COMPLETED")
            speakResponse(chatResponse.content)
        }
    }

    private fun speakResponse(text: String) {
        val sessionId = currentTtsSessionId.incrementAndGet()
        val utteranceId = "JARVIS_RESPONSE_$sessionId"
        transitionTo(JarvisAudioState.SPEAKING)
        isTtsSpeaking.set(true)
        val cleanSpeech = text.replace(Regex("[#*`_]"), "").take(400)
        textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun stopTts() {
        isTtsSpeaking.set(false)
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopAudioLoop() {
        synchronized(captureLock) {
            isAudioCapturing.set(false)
            commandTimeoutJob?.cancel()
            commandTimeoutJob = null

            val record = audioRecord
            audioRecord = null

            if (record != null) {
                try {
                    // Calling stop unblocks any blocking read() call immediately
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
                }
            }

            loopJob?.cancel()
            val runningJob = loopJob
            loopJob = null

            // If coroutine was not active, release record directly
            if (runningJob == null && record != null) {
                try {
                    record.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing AudioRecord directly: ${e.message}")
                }
            }

            JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
            _soundLevel.value = 0f
        }
    }

    private suspend fun logWakeEvent(
        wakeWord: String,
        verified: Boolean,
        command: String?,
        response: String?,
        status: String
    ) {
        val event = WakeEventEntity(
            timestamp = System.currentTimeMillis(),
            device = "ANDROID",
            wakeWord = wakeWord,
            speakerVerified = verified,
            recognizedCommand = command,
            responseSummary = response?.take(150),
            status = status
        )
        try {
            val id = repository.insertWakeEvent(event)
            _lastWakeEvent.value = event.copy(id = id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log wake event: ${e.message}")
        }
    }

    private fun transitionTo(newState: JarvisAudioState) {
        Log.d(TAG, "Audio State Transition: ${_currentState.value} -> $newState")
        _currentState.value = newState
    }

    fun release() {
        stopHandsFree()
        wakeWordEngine.release()
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        textToSpeech = null
    }

    /**
     * Decodes 16kHz PCM audio on-device using the abstracted multilingual ASR engine.
     * Preserves mixed Hindi and English without cloud dependencies and leverages sequential fallback.
     */
    private suspend fun decodePcmCommand(pcmList: List<Short>): String {
        if (pcmList.size < SAMPLE_RATE * 0.5) return ""
        val shortArray = pcmList.toShortArray()
        val asrResult = asrEngine.transcribePcm(
            pcmData = shortArray,
            sampleRate = SAMPLE_RATE,
            languageMode = TranscriptionLanguageMode.AUTO_HINGLISH
        )
        return asrResult.text
    }
}
