package com.personalai.assistant.recording

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class RecordingDiagnosticsTest {

    @Test
    fun testPcmAudioMetricsCalculation() {
        // Generate 1600 samples of 16-bit PCM (100ms at 16kHz)
        // Alternate between quiet and clipping values (+32767)
        val sampleCount = 1600
        val buffer = ShortArray(sampleCount)
        var sumSquares = 0.0
        var clippedCount = 0

        for (i in 0 until sampleCount) {
            val sample = if (i % 100 == 0) {
                clippedCount++
                Short.MAX_VALUE // 32767
            } else {
                (1000 * Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toInt().toShort()
            }
            buffer[i] = sample
            sumSquares += (sample.toDouble() * sample.toDouble())
        }

        val rms = sqrt(sumSquares / sampleCount)
        val clippingPercentage = (clippedCount.toFloat() / sampleCount.toFloat()) * 100f

        assertTrue("RMS must be greater than 0: $rms", rms > 500.0)
        assertTrue("Clipping percentage should match 1%: $clippingPercentage", clippingPercentage in 0.9f..1.1f)
    }

    @Test
    fun testSilenceDetectionThreshold() {
        // True silence: all zero PCM bytes
        val silentBuffer = ShortArray(1600) { 0 }
        var sumSquares = 0.0
        for (sample in silentBuffer) {
            sumSquares += (sample.toDouble() * sample.toDouble())
        }
        val silentRms = sqrt(sumSquares / silentBuffer.size)
        assertEquals(0.0, silentRms, 0.001)

        // Threshold for silence detection: RMS below 150.0 is considered silence
        val isSilent = silentRms < 150.0
        assertTrue("Zero buffer must be detected as silent", isSilent)

        // Low ambient noise: random noise with max amplitude 50
        val ambientBuffer = ShortArray(1600) { ((it % 30) - 15).toShort() }
        var ambientSum = 0.0
        for (sample in ambientBuffer) {
            ambientSum += (sample.toDouble() * sample.toDouble())
        }
        val ambientRms = sqrt(ambientSum / ambientBuffer.size)
        assertTrue("Ambient noise RMS is low: $ambientRms", ambientRms < 150.0)
    }

    @Test
    fun testNegativeReadCodeCategorization() {
        val ERROR_INVALID_OPERATION = -3
        val ERROR_BAD_VALUE = -2
        val ERROR_DEAD_OBJECT = -6
        val ERROR = -1

        fun categorizeErrorCode(code: Int): String {
            return when (code) {
                ERROR_DEAD_OBJECT -> "HARDWARE_DEAD_OBJECT"
                ERROR_INVALID_OPERATION -> "INVALID_STATE"
                ERROR_BAD_VALUE -> "BAD_PARAMETERS"
                ERROR -> "GENERIC_ERROR"
                else -> if (code < 0) "UNKNOWN_ERROR" else "SUCCESS"
            }
        }

        assertEquals("HARDWARE_DEAD_OBJECT", categorizeErrorCode(ERROR_DEAD_OBJECT))
        assertEquals("INVALID_STATE", categorizeErrorCode(ERROR_INVALID_OPERATION))
        assertEquals("BAD_PARAMETERS", categorizeErrorCode(ERROR_BAD_VALUE))
        assertEquals("GENERIC_ERROR", categorizeErrorCode(ERROR))
        assertEquals("SUCCESS", categorizeErrorCode(1024))
    }

    @Test
    fun testWavHeaderFinalizationRecoversInterruptedFrames() {
        val tempFile = File.createTempFile("test_recovery", ".wav")
        try {
            // Write initial 44-byte placeholder header
            tempFile.outputStream().use { fos ->
                WavWriter.writeWavHeader(fos, 0)
                // Write 3200 bytes of PCM audio (0.1s of audio at 16kHz mono)
                val pcmData = ByteArray(3200) { (it % 128).toByte() }
                fos.write(pcmData)
            }

            // Verify file size is 44 + 3200 = 3244
            assertEquals(3244L, tempFile.length())

            // Finalize header with exact written bytes
            WavWriter.finalizeWavHeader(tempFile, 3200L)

            // Validate header contents
            val validation = WavWriter.validateWavHeader(tempFile)
            assertTrue("Finalized header must be valid: ${validation.errorMessage}", validation.isValid)
            assertEquals(3200L, validation.audioDataBytes)

            val durationMs = WavWriter.calculateDurationMs(3200L)
            assertEquals(100L, durationMs)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testRecordingHealthMonitorProcessing() {
        RecordingHealthMonitor.reset()
        val initial = RecordingHealthMonitor.metrics.value
        assertEquals("IDLE", initial.micState)
        assertEquals(0L, initial.totalBytesWritten)

        // Process a simulated 16kHz PCM buffer of 3200 bytes (1600 samples)
        val pcm = ByteArray(3200)
        // Fill with a non-zero sine wave
        for (i in 0 until 1600) {
            val sample = (2000 * Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toInt().toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        RecordingHealthMonitor.processCapturedPcm(
            pcmData = pcm,
            bytesRead = 3200,
            totalBytesWritten = 3200L,
            fileSizeBytes = 3244L,
            activeDurationMs = 100L
        )

        val updated = RecordingHealthMonitor.metrics.value
        assertEquals("CAPTURING", updated.micState)
        assertEquals(3200L, updated.totalBytesWritten)
        assertEquals(3244L, updated.fileSizeBytes)
        assertTrue("RMS must be greater than 0: ${updated.rollingRms}", updated.rollingRms > 500.0)
        assertEquals(0L, updated.silenceDurationMs)
    }

    @Test
    fun testRecordingHealthMonitorInterruptionAndRecovery() {
        RecordingHealthMonitor.reset()
        RecordingHealthMonitor.onInterruption("AUDIOFOCUS_LOSS", "Phone call incoming")
        val interrupted = RecordingHealthMonitor.metrics.value
        assertEquals(1, interrupted.interruptionCount)
        assertEquals("Phone call incoming", interrupted.lastError)

        RecordingHealthMonitor.onRecoveryAttempt(successful = true, details = "AudioRecord restarted")
        val recovered = RecordingHealthMonitor.metrics.value
        assertEquals(1, recovered.recoveryAttempts)
        assertEquals("CAPTURING", recovered.micState)

        val events = RecordingHealthMonitor.events.value
        assertTrue("Events must record focus loss", events.any { it.type == "INTERRUPTION_AUDIOFOCUS_LOSS" })
        assertTrue("Events must record recovery", events.any { it.type == "RECOVERY_SUCCESS" })
    }

    @Test
    fun testBoundedAudioChannelDecouplesCaptureFromDisk() = runTest {
        val channel = Channel<ByteArray>(capacity = 64)
        val receivedChunks = mutableListOf<ByteArray>()

        // Consumer coroutine simulating disk writer
        val consumerJob = launch {
            for (chunk in channel) {
                receivedChunks.add(chunk)
            }
        }

        // Producer simulating high-priority audio record
        for (i in 0 until 10) {
            val dummyFrame = ByteArray(64) { i.toByte() }
            channel.send(dummyFrame)
        }
        channel.close()
        consumerJob.join()

        assertEquals(10, receivedChunks.size)
        assertEquals(0.toByte(), receivedChunks.first()[0])
        assertEquals(9.toByte(), receivedChunks.last()[0])
    }
}
