package com.personalai.assistant.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.personalai.assistant.actions.AndroidActionExecutor
import com.personalai.assistant.actions.AssistantAction
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.LocalRagChatEngine
import com.personalai.assistant.data.local.entity.WakeEventEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.transcription.SentenceReconstructor
import com.personalai.assistant.transcription.models.ModelManager
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Robust, race-safe single audio state machine driving the hands-free
 * JARVIS assistant lifecycle for both Android and multi-platform coordination.
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
    private val fallbackSpotter = AdaptivePhoneticSpotter()
    private var lastWakeTimestamp = 0L

    // Configurable settings
    var configuredWakeWord: String = "Jarvis"
    var commandTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
    var rawAudioRetentionEnabled: Boolean = false

    private val actionExecutor = AndroidActionExecutor(context)
    private val ragEngine = LocalRagChatEngine(repository)
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
                        scope.launch {
                            delay(400) // Grace period to avoid self-speech echo
                            transitionTo(JarvisAudioState.WAKE_LISTENING)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking.set(false)
                        scope.launch { transitionTo(JarvisAudioState.WAKE_LISTENING) }
                    }
                })
            }
        }
    }

    @Synchronized
    fun startHandsFree(wakeWord: String = "Jarvis") {
        configuredWakeWord = wakeWord
        if (_currentState.value == JarvisAudioState.WAKE_LISTENING) return

        transitionTo(JarvisAudioState.STARTING)
        val ready = wakeWordEngine.initialize(configuredWakeWord, 0.7f)
        fallbackSpotter.initialize(configuredWakeWord, 0.7f)
        Log.i(TAG, "Initialized wake engines. Primary Vosk ready=$ready")

        startAudioLoop()
    }

    @Synchronized
    fun pauseHandsFree() {
        transitionTo(JarvisAudioState.PAUSED)
        stopTts()
    }

    @Synchronized
    fun resumeHandsFree() {
        if (_currentState.value == JarvisAudioState.PAUSED) {
            transitionTo(JarvisAudioState.WAKE_LISTENING)
        }
    }

    @Synchronized
    fun stopHandsFree() {
        transitionTo(JarvisAudioState.STOPPED)
        stopAudioLoop()
        stopTts()
        transitionTo(JarvisAudioState.IDLE_DISABLED)
    }

    fun interruptSpeech() {
        if (_currentState.value == JarvisAudioState.SPEAKING) {
            stopTts()
            transitionTo(JarvisAudioState.WAKE_LISTENING)
        }
    }

    private fun startAudioLoop() {
        if (isAudioCapturing.get()) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = Math.max(minBufSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed initialization")
                transitionTo(JarvisAudioState.ERROR)
                return
            }

            audioRecord?.startRecording()
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

                while (isActive && isAudioCapturing.get()) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (read <= 0) {
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
                            // Continuously record to rolling pre-buffer
                            for (i in 0 until read) {
                                preBuffer[preBufferWriteIdx % preBuffer.size] = shortBuffer[i]
                                preBufferWriteIdx++
                            }

                            val detected = if (wakeWordEngine.isReady) {
                                wakeWordEngine.processPcmChunk(shortBuffer, read)
                            } else {
                                fallbackSpotter.processPcmChunk(shortBuffer, read)
                            }
                            val now = SystemClock.elapsedRealtime()

                            if (detected && (now - lastWakeTimestamp) > WAKE_DEBOUNCE_MS) {
                                lastWakeTimestamp = now
                                // Silent haptic pulse acknowledgement
                                try {
                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator?.vibrate(120)
                                    }
                                } catch (e: Exception) {}
                                handleWakeWordDetected(shortBuffer, read)
                                commandPcmAccumulator.clear()
                                // Prepend rolling pre-buffer to capture utterance onset cleanly
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

                            // Natural pause endpointing:
                            // If speech has occurred and is followed by 1.4s of natural breath pause,
                            // or if total command length reached 9 seconds, complete utterance.
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
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied: ${e.message}")
            transitionTo(JarvisAudioState.PERMISSION_DENIED)
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in audio capture loop: ${e.message}", e)
            transitionTo(JarvisAudioState.ERROR)
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
                    transitionTo(JarvisAudioState.WAKE_LISTENING)
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
            if (_currentState.value == JarvisAudioState.COMMAND_LISTENING) {
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
        transitionTo(JarvisAudioState.SPEAKING)
        isTtsSpeaking.set(true)
        val cleanSpeech = text.replace(Regex("[#*`_]"), "").take(400)
        textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_RESPONSE")
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
        isAudioCapturing.set(false)
        loopJob?.cancel()
        commandTimeoutJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord: ${e.message}")
        }
        audioRecord = null
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
     * Decodes 16kHz PCM audio on-device using the installed Vosk Kaldi speech model.
     * Preserves mixed Hindi and English without cloud dependencies.
     */
    private fun decodePcmCommand(pcmList: List<Short>): String {
        if (pcmList.size < SAMPLE_RATE * 0.5) return ""
        val modelDir = ModelManager.getInstance(context).getActiveModelDirectory() ?: return ""
        if (!modelDir.exists()) return ""

        return try {
            val model = Model(modelDir.absolutePath)
            val rec = Recognizer(model, SAMPLE_RATE.toFloat())
            rec.setWords(true)

            val shortArray = pcmList.toShortArray()
            rec.acceptWaveForm(shortArray, shortArray.size)
            val finalJson = rec.finalResult
            rec.close()
            model.close()

            val json = JSONObject(finalJson)
            val rawText = json.optString("text", "").trim()
            if (rawText.isNotEmpty()) {
                val words = rawText.split("\\s+".toRegex()).map {
                    com.personalai.assistant.transcription.WordToken(it, 0L, 0L, 0.95f)
                }
                SentenceReconstructor.formatSentence(words, "hi-en")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding PCM command with local Vosk: ${e.message}")
            ""
        }
    }

}
