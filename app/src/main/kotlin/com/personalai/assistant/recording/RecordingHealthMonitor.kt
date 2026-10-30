package com.personalai.assistant.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList
import kotlin.math.sqrt

data class RecordingHealthMetrics(
    val micState: String = "IDLE", // "IDLE", "CAPTURING", "PAUSED", "RECOVERING", "ERROR"
    val audioSource: String = "MIC (16000 Hz, 16-bit PCM Mono)",
    val sampleRate: Int = 16000,
    val lastReadTimestampMs: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val rollingRms: Double = 0.0,
    val clippingPercentage: Float = 0.0f,
    val silenceDurationMs: Long = 0L,
    val totalBytesWritten: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val activeDurationMs: Long = 0L,
    val interruptionCount: Int = 0,
    val recoveryAttempts: Int = 0,
    val lastError: String? = null
)

data class RecordingDiagnosticEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val details: String
)

object RecordingHealthMonitor {

    private const val MAX_EVENTS = 50
    private const val SILENCE_RMS_THRESHOLD = 150.0

    private val _metrics = MutableStateFlow(RecordingHealthMetrics())
    val metrics: StateFlow<RecordingHealthMetrics> = _metrics.asStateFlow()

    private val eventHistory = LinkedList<RecordingDiagnosticEvent>()
    private val _events = MutableStateFlow<List<RecordingDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<RecordingDiagnosticEvent>> = _events.asStateFlow()

    private var currentSilenceStartMs: Long = 0L
    private var windowBytesRead: Long = 0L
    private var lastRateCalcTimestampMs: Long = System.currentTimeMillis()

    @Synchronized
    fun reset() {
        _metrics.value = RecordingHealthMetrics()
        currentSilenceStartMs = 0L
        windowBytesRead = 0L
        lastRateCalcTimestampMs = System.currentTimeMillis()
        recordEvent("MONITOR_RESET", "Recording health monitor initialized to idle state.")
    }

    @Synchronized
    fun setMicState(state: String, error: String? = null) {
        _metrics.value = _metrics.value.copy(
            micState = state,
            lastError = error ?: _metrics.value.lastError
        )
        recordEvent("MIC_STATE_CHANGE", "Microphone state set to $state" + (if (error != null) ": $error" else ""))
    }

    @Synchronized
    fun onInterruption(type: String, details: String) {
        val currentCount = _metrics.value.interruptionCount + 1
        _metrics.value = _metrics.value.copy(
            interruptionCount = currentCount,
            lastError = details
        )
        recordEvent("INTERRUPTION_$type", details)
    }

    @Synchronized
    fun onRecoveryAttempt(successful: Boolean, details: String) {
        val attempts = _metrics.value.recoveryAttempts + 1
        _metrics.value = _metrics.value.copy(
            recoveryAttempts = attempts,
            micState = if (successful) "CAPTURING" else "ERROR",
            lastError = if (successful) null else details
        )
        recordEvent(if (successful) "RECOVERY_SUCCESS" else "RECOVERY_FAILURE", details)
    }

    @Synchronized
    fun processCapturedPcm(
        pcmData: ByteArray,
        bytesRead: Int,
        totalBytesWritten: Long,
        fileSizeBytes: Long,
        activeDurationMs: Long
    ) {
        if (bytesRead <= 0) return

        val now = System.currentTimeMillis()
        val sampleCount = bytesRead / 2
        var sumSquares = 0.0
        var clippedCount = 0

        // Parse 16-bit PCM samples
        for (i in 0 until sampleCount) {
            val byteLow = pcmData[i * 2].toInt() and 0xFF
            val byteHigh = pcmData[i * 2 + 1].toInt()
            val sample = (byteHigh shl 8) or byteLow

            if (sample >= 32700 || sample <= -32700) {
                clippedCount++
            }
            sumSquares += (sample.toDouble() * sample.toDouble())
        }

        val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0
        val clippingPercentage = if (sampleCount > 0) (clippedCount.toFloat() / sampleCount.toFloat()) * 100f else 0f

        // Silence tracking
        val isSilent = rms < SILENCE_RMS_THRESHOLD
        val currentSilenceDuration = if (isSilent) {
            if (currentSilenceStartMs == 0L) currentSilenceStartMs = now
            now - currentSilenceStartMs
        } else {
            currentSilenceStartMs = 0L
            0L
        }

        // Throughput calculation (bytes per second rolling window)
        windowBytesRead += bytesRead
        val deltaMs = (now - lastRateCalcTimestampMs).coerceAtLeast(1L)
        val bytesPerSecond = if (deltaMs >= 1000L) {
            val rate = (windowBytesRead * 1000L) / deltaMs
            windowBytesRead = 0L
            lastRateCalcTimestampMs = now
            rate
        } else {
            _metrics.value.bytesPerSecond
        }

        _metrics.value = _metrics.value.copy(
            micState = "CAPTURING",
            lastReadTimestampMs = now,
            bytesPerSecond = bytesPerSecond,
            rollingRms = rms,
            clippingPercentage = clippingPercentage,
            silenceDurationMs = currentSilenceDuration,
            totalBytesWritten = totalBytesWritten,
            fileSizeBytes = fileSizeBytes,
            activeDurationMs = activeDurationMs
        )
    }

    @Synchronized
    fun recordEvent(type: String, details: String) {
        val event = RecordingDiagnosticEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            details = details
        )
        if (eventHistory.size >= MAX_EVENTS) {
            eventHistory.removeFirst()
        }
        eventHistory.add(event)
        _events.value = eventHistory.toList()
    }
}
