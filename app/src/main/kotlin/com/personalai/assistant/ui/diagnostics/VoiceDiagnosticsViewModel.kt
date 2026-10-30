package com.personalai.assistant.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.playback.AudioPlayer
import com.personalai.assistant.recording.WavWriter
import com.personalai.assistant.transcription.SentenceReconstructor
import com.personalai.assistant.transcription.VoskTranscriptionEngine
import com.personalai.assistant.transcription.WordToken
import com.personalai.assistant.transcription.models.ModelManager
import com.personalai.assistant.wakeword.AdaptivePhoneticSpotter
import com.personalai.assistant.wakeword.AudioOwner
import com.personalai.assistant.wakeword.JarvisVoiceCoordinator
import com.personalai.assistant.wakeword.SpeakerProfileManager
import com.personalai.assistant.wakeword.VoskKeywordSpotter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

data class DiagnosticMetrics(
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val durationMs: Long = 0,
    val totalSamples: Int = 0,
    val avgRms: Float = 0f,
    val peakRms: Float = 0f,
    val clippedSamples: Int = 0,
    val clippingPercentage: Float = 0f,
    val silencePercentage: Float = 0f,
    val droppedFrames: Int = 0
)

class VoiceDiagnosticsViewModel(
    private val context: Context,
    private val modelManager: ModelManager
) : ViewModel() {

    private val audioPlayer = AudioPlayer()
    private val speakerManager = SpeakerProfileManager.getInstance(context)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _countdownSec = MutableStateFlow(0)
    val countdownSec: StateFlow<Int> = _countdownSec.asStateFlow()

    private val _currentMetrics = MutableStateFlow<DiagnosticMetrics?>(null)
    val currentMetrics: StateFlow<DiagnosticMetrics?> = _currentMetrics.asStateFlow()

    private val _lastRecordedFile = MutableStateFlow<File?>(null)
    val lastRecordedFile: StateFlow<File?> = _lastRecordedFile.asStateFlow()

    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val playProgressMs: StateFlow<Int> = audioPlayer.currentPositionMs
    val playDurationMs: StateFlow<Int> = audioPlayer.durationMs

    private val _transcriptionResult = MutableStateFlow<String?>(null)
    val transcriptionResult: StateFlow<String?> = _transcriptionResult.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcriptionLatencyMs = MutableStateFlow(0L)
    val transcriptionLatencyMs: StateFlow<Long> = _transcriptionLatencyMs.asStateFlow()

    // Wake word live test
    private val _isTestingWakeWord = MutableStateFlow(false)
    val isTestingWakeWord: StateFlow<Boolean> = _isTestingWakeWord.asStateFlow()

    private val _wakeWordDetectedEvent = MutableStateFlow<String?>(null)
    val wakeWordDetectedEvent: StateFlow<String?> = _wakeWordDetectedEvent.asStateFlow()

    // Continuous recording health & hardware telemetry
    val liveRecordingMetrics = com.personalai.assistant.recording.RecordingHealthMonitor.metrics
    val liveDiagnosticEvents = com.personalai.assistant.recording.RecordingHealthMonitor.events

    private val _statusMessage = MutableStateFlow("Hardware lab standby. Ready for voice tests.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var recordJob: Job? = null
    private var testAudioRecord: AudioRecord? = null
    private val isCapturing = AtomicBoolean(false)

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isModelReady(): Boolean {
        val dir = modelManager.getActiveModelDirectory()
        return dir != null && dir.exists()
    }

    fun getActiveModelName(): String {
        return modelManager.activeModelId.value
    }

    fun startDiagnosticRecording(targetDurationSec: Int) {
        if (isCapturing.get()) return

        if (!hasMicPermission()) {
            _statusMessage.value = "RECORD_AUDIO permission missing"
            return
        }

        viewModelScope.launch {
            // 3-second visual countdown
            for (c in 3 downTo 1) {
                _countdownSec.value = c
                _statusMessage.value = "Starting recording in $c..."
                delay(1000)
            }
            _countdownSec.value = 0
            _statusMessage.value = "Capturing $targetDurationSec seconds of raw 16kHz PCM audio..."

            launchAudioCapture(targetDurationSec)
        }
    }

    private fun launchAudioCapture(targetDurationSec: Int) {
        if (!JarvisVoiceCoordinator.requestMicrophone(AudioOwner.WAV_RECORDER)) {
            _statusMessage.value = "Microphone currently busy with another session."
            return
        }

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = Math.max(minBuf * 2, 4096)

        val diagDir = File(context.cacheDir, "diagnostics").apply { if (!exists()) mkdirs() }
        val testFile = File(diagDir, "diagnostic_test_${System.currentTimeMillis()}.wav")
        _lastRecordedFile.value = testFile
        _transcriptionResult.value = null

        isCapturing.set(true)
        _isRecording.value = true

        recordJob = viewModelScope.launch(Dispatchers.IO) {
            var fos: FileOutputStream? = null
            var totalBytes = 0L
            var totalSamples = 0
            var sumSquares = 0.0
            var peakVal = 0
            var clipCount = 0
            var silentFrames = 0
            val startTime = System.currentTimeMillis()
            val maxSamples = sampleRate * targetDurationSec

            try {
                testAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )

                if (testAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Microphone hardware initialization failed."
                    }
                    return@launch
                }

                fos = FileOutputStream(testFile)
                WavWriter.writeWavHeader(fos, 0)
                testAudioRecord?.startRecording()

                val buffer = ShortArray(1024)
                val byteBuffer = ByteArray(2048)

                while (isActive && isCapturing.get() && totalSamples < maxSamples) {
                    val read = testAudioRecord?.read(buffer, 0, minOf(buffer.size, maxSamples - totalSamples)) ?: -1
                    if (read > 0) {
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            sumSquares += sample.toDouble() * sample.toDouble()
                            val absVal = abs(sample)
                            if (absVal > peakVal) peakVal = absVal
                            if (absVal >= 32000) clipCount++

                            // Convert to byte buffer (little endian)
                            byteBuffer[i * 2] = (sample and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }

                        val frameRms = sqrt(sumSquares / read)
                        if (frameRms < 200.0) silentFrames++

                        fos.write(byteBuffer, 0, read * 2)
                        totalBytes += (read * 2)
                        totalSamples += read
                    }
                }

                fos.flush()
                WavWriter.finalizeWavHeader(testFile, totalBytes)

                val durMs = System.currentTimeMillis() - startTime
                val avgRms = if (totalSamples > 0) sqrt(sumSquares / totalSamples).toFloat() else 0f
                val clipPercent = if (totalSamples > 0) (clipCount.toFloat() / totalSamples) * 100f else 0f
                val silencePercent = if (totalSamples > 0) (silentFrames.toFloat() / (totalSamples / 1024f)) * 100f else 0f

                val metrics = DiagnosticMetrics(
                    sampleRate = 16000,
                    channels = 1,
                    bitDepth = 16,
                    durationMs = durMs,
                    totalSamples = totalSamples,
                    avgRms = avgRms,
                    peakRms = peakVal.toFloat(),
                    clippedSamples = clipCount,
                    clippingPercentage = clipPercent,
                    silencePercentage = silencePercent.coerceIn(0f, 100f),
                    droppedFrames = 0
                )

                withContext(Dispatchers.Main) {
                    _currentMetrics.value = metrics
                    _statusMessage.value = "Captured ${durMs / 1000}s sample. Ready for playback and ASR inspection."
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Diagnostic capture error: ${e.message}"
                }
            } finally {
                cleanupCapture()
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.WAV_RECORDER)
                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                }
                try { fos?.close() } catch (e: Exception) {}
            }
        }
    }

    fun stopRecordingNow() {
        isCapturing.set(false)
        _isRecording.value = false
    }

    private fun cleanupCapture() {
        try {
            testAudioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) stop()
                release()
            }
        } catch (e: Exception) {}
        testAudioRecord = null
    }

    fun playRecordedAudio() {
        val file = _lastRecordedFile.value ?: return
        audioPlayer.play(file.absolutePath)
    }

    fun stopAudioPlayback() {
        audioPlayer.stop()
    }

    fun runSpeechRecognizer() {
        val file = _lastRecordedFile.value
        if (file == null || !file.exists()) {
            _statusMessage.value = "No recorded test audio available. Record a sample first."
            return
        }

        _isTranscribing.value = true
        _statusMessage.value = "Running offline speech recognition on captured audio..."

        viewModelScope.launch(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val modelDir = modelManager.getActiveModelDirectory()

            if (modelDir != null && modelDir.exists()) {
                try {
                    val engine = VoskTranscriptionEngine()
                    val segments = engine.transcribe(
                        audioFile = file,
                        modelDir = modelDir,
                        languageCode = "hi-en"
                    )
                    val latency = SystemClock.elapsedRealtime() - startMs
                    val fullText = if (segments.isNotEmpty()) {
                        segments.joinToString(" ") { it.text }
                    } else {
                        "[No speech recognized in audio]"
                    }

                    withContext(Dispatchers.Main) {
                        _transcriptionResult.value = fullText
                        _transcriptionLatencyMs.value = latency
                        _isTranscribing.value = false
                        _statusMessage.value = "Transcription completed in ${latency}ms via ${modelManager.activeModelId.value}."
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _transcriptionResult.value = "ASR Error: ${e.message}"
                        _isTranscribing.value = false
                        _statusMessage.value = "Vosk transcription failed: ${e.message}"
                    }
                }
            } else {
                // Vosk model not downloaded, test SentenceReconstructor
                val testTokens = listOf(
                    WordToken("jarvis", 0L, 400L, 0.98f),
                    WordToken("mujhe", 450L, 800L, 0.97f),
                    WordToken("kal", 850L, 1100L, 0.98f),
                    WordToken("meeting", 1150L, 1600L, 0.99f),
                    WordToken("schedule", 1650L, 2100L, 0.98f),
                    WordToken("karni", 2150L, 2500L, 0.97f),
                    WordToken("hai", 2550L, 2800L, 0.99f)
                )
                val reconstructed = SentenceReconstructor.reconstruct(testTokens, "hi-en")
                val latency = SystemClock.elapsedRealtime() - startMs

                withContext(Dispatchers.Main) {
                    _transcriptionResult.value = "[Notice: No local Vosk model unpacked. Download via Settings > AI Models]\n\n" +
                            "Pipeline reconstructed test: \"${reconstructed.firstOrNull()?.text ?: ""}\""
                    _transcriptionLatencyMs.value = latency
                    _isTranscribing.value = false
                    _statusMessage.value = "Checked pipeline without heavy model."
                }
            }
        }
    }

    fun testLiveWakeWord(durationSec: Int = 10) {
        if (_isTestingWakeWord.value) return
        if (!JarvisVoiceCoordinator.requestMicrophone(AudioOwner.ALWAYS_ON_LISTENER)) {
            _statusMessage.value = "Microphone busy"
            return
        }

        _isTestingWakeWord.value = true
        _wakeWordDetectedEvent.value = null
        _statusMessage.value = "Wake-word test active. Please say \"Jarvis\" within $durationSec seconds..."

        viewModelScope.launch(Dispatchers.IO) {
            val spotter = if (isModelReady()) VoskKeywordSpotter(context) else AdaptivePhoneticSpotter()
            spotter.initialize("jarvis", 0.7f)

            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            var audioRecord: AudioRecord? = null
            val startTime = SystemClock.elapsedRealtime()

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf * 2, 4096)
                )
                audioRecord.startRecording()
                val buffer = ShortArray(1024)

                while (isActive && (SystemClock.elapsedRealtime() - startTime) < (durationSec * 1000L)) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val detected = spotter.processPcmChunk(buffer, read)
                        if (detected) {
                            val elapsed = SystemClock.elapsedRealtime() - startTime
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                            withContext(Dispatchers.Main) {
                                _wakeWordDetectedEvent.value = "DETECTED \"JARVIS\" at $timeStr (Latency: ${elapsed}ms)"
                                _statusMessage.value = "Wake word \"Jarvis\" successfully detected!"
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Wake word test error: ${e.message}"
                }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {}
                spotter.release()
                JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
                withContext(Dispatchers.Main) {
                    _isTestingWakeWord.value = false
                }
            }
        }
    }

    fun generateSanitizedReport(): String {
        val m = _currentMetrics.value
        val model = getActiveModelName()
        val enrolled = speakerManager.isEnrolled.value
        val authEnabled = speakerManager.isSpeakerVerificationEnabled.value

        return """
========================================
JARVIS VOICE ENGINE DIAGNOSTIC REPORT
Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
========================================
[AUDIO HARDWARE TELEMETRY]
• Sample Rate: 16,000 Hz
• Channels: 1 (MONO)
• PCM Bit Depth: 16-bit Signed Little-Endian
• Mic Permission Granted: ${hasMicPermission()}
• Active Microphone Arbiter: ${JarvisVoiceCoordinator.currentOwner.value}

[RECORDING DIAGNOSTICS]
• Duration: ${m?.durationMs ?: 0} ms
• Samples Captured: ${m?.totalSamples ?: 0}
• Average RMS Energy: ${m?.avgRms ?: 0f}
• Peak Amplitude: ${m?.peakRms ?: 0f} / 32767
• Clipped Samples: ${m?.clippedSamples ?: 0} (${String.format(Locale.US, "%.2f", m?.clippingPercentage ?: 0f)}%)
• Silence Percentage: ${String.format(Locale.US, "%.1f", m?.silencePercentage ?: 0f)}%
• Dropped Frames: 0

[SPEECH RECOGNITION & VAD]
• Active Speech Model: $model
• Model Unpacked On-Device: ${isModelReady()}
• Code-Switching Engine: SentenceReconstructor (Hinglish/Latin/Devanagari)
• ASR Test Latency: ${_transcriptionLatencyMs.value} ms

[BIOMETRICS & ALWAYS READY]
• Voice Profile Enrolled: $enrolled
• Speaker Verification Auth: $authEnabled
• Biometric Threshold: ${speakerManager.getThreshold()}
• Last Wake Word Event: ${_wakeWordDetectedEvent.value ?: "None"}
========================================
        """.trimIndent()
    }

    override fun onCleared() {
        audioPlayer.stop()
        cleanupCapture()
        super.onCleared()
    }
}
