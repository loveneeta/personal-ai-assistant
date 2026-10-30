package com.personalai.assistant.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class WavWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun writeWavHeader_createsValid44ByteRiffStructure() {
        val file = tempFolder.newFile("test_header.wav")
        FileOutputStream(file).use { fos ->
            WavWriter.writeWavHeader(fos, 0)
        }

        assertEquals(44L, file.length())

        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(4)
            raf.readFully(riff)
            assertEquals("RIFF", String(riff))

            raf.seek(8)
            val wave = ByteArray(4)
            raf.readFully(wave)
            assertEquals("WAVE", String(wave))

            raf.seek(12)
            val fmt = ByteArray(4)
            raf.readFully(fmt)
            assertEquals("fmt ", String(fmt))

            // Audio format (1 for PCM)
            raf.seek(20)
            val format = raf.read() or (raf.read() shl 8)
            assertEquals(1, format)

            // Channels (1 for Mono)
            raf.seek(22)
            val channels = raf.read() or (raf.read() shl 8)
            assertEquals(1, channels)

            // Sample rate (16000)
            raf.seek(24)
            val sampleRate = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
            assertEquals(16000, sampleRate)

            // Bit depth (16)
            raf.seek(34)
            val bitsPerSample = raf.read() or (raf.read() shl 8)
            assertEquals(16, bitsPerSample)

            // 'data' chunk
            raf.seek(36)
            val data = ByteArray(4)
            raf.readFully(data)
            assertEquals("data", String(data))
        }
    }

    @Test
    fun finalizeWavHeader_updatesSizesAccurately() {
        val file = tempFolder.newFile("test_finalize.wav")
        val sampleAudioBytes = 32000 // Exactly 1 second of 16kHz 16-bit mono audio
        val audioData = ByteArray(sampleAudioBytes) { 0x42 }

        FileOutputStream(file).use { fos ->
            WavWriter.writeWavHeader(fos, 0)
            fos.write(audioData)
        }

        WavWriter.finalizeWavHeader(file, sampleAudioBytes.toLong())

        assertEquals(44L + sampleAudioBytes, file.length())

        RandomAccessFile(file, "r").use { raf ->
            // ChunkSize at byte 4 should be sampleAudioBytes + 36
            raf.seek(4)
            val chunkSize = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
            assertEquals(sampleAudioBytes + 36, chunkSize)

            // Subchunk2Size at byte 40 should be sampleAudioBytes
            raf.seek(40)
            val dataSize = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
            assertEquals(sampleAudioBytes, dataSize)
        }
    }

    @Test
    fun validateWavHeader_validatesProperFile() {
        val file = tempFolder.newFile("test_valid.wav")
        val audioBytes = 64000 // Exactly 2 seconds
        FileOutputStream(file).use { fos ->
            WavWriter.writeWavHeader(fos, audioBytes.toLong())
            fos.write(ByteArray(audioBytes))
        }

        val result = WavWriter.validateWavHeader(file)
        assertTrue(result.isValid)
        assertEquals(16000, result.sampleRate)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(64000L, result.audioDataBytes)
        assertEquals(2000L, result.durationMs)
    }

    @Test
    fun validateWavHeader_rejectsInvalidOrCorruptFiles() {
        // Missing file
        val missing = File(tempFolder.root, "non_existent.wav")
        assertFalse(WavWriter.validateWavHeader(missing).isValid)

        // Empty file
        val empty = tempFolder.newFile("empty.wav")
        assertFalse(WavWriter.validateWavHeader(empty).isValid)

        // Non-WAV file (e.g. text file)
        val textFile = tempFolder.newFile("fake.wav")
        textFile.writeText("This is not a real audio file at all.")
        val textResult = WavWriter.validateWavHeader(textFile)
        assertFalse(textResult.isValid)
        assertNotNull(textResult.errorMessage)
    }

    @Test
    fun repairIncompleteWav_recoversInterruptedFileAfterCrash() {
        val file = tempFolder.newFile("crashed_recording.wav")
        val capturedAudioBytes = 96000 // 3 seconds at 32000 bytes/sec

        // Write header with 0 initial sizes, followed by audio data (simulating sudden crash before stop)
        FileOutputStream(file).use { fos ->
            WavWriter.writeWavHeader(fos, 0)
            fos.write(ByteArray(capturedAudioBytes) { 0x55 })
        }

        // Before repair: headers show 0 bytes audio
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(40)
            val dataSizeBefore = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
            assertEquals(0, dataSizeBefore)
        }

        // Run recovery repair
        val recoveredDurationMs = WavWriter.repairIncompleteWav(file)
        assertEquals(3000L, recoveredDurationMs)

        // After repair: header correctly matches actual data size
        val validation = WavWriter.validateWavHeader(file)
        assertTrue(validation.isValid)
        assertEquals(96000L, validation.audioDataBytes)
        assertEquals(3000L, validation.durationMs)
    }

    @Test
    fun calculateDurationMs_calculatesExactMillisecondValues() {
        assertEquals(0L, WavWriter.calculateDurationMs(0L))
        assertEquals(500L, WavWriter.calculateDurationMs(16000L)) // 0.5s = 16000 bytes
        assertEquals(1000L, WavWriter.calculateDurationMs(32000L)) // 1.0s = 32000 bytes
        assertEquals(60000L, WavWriter.calculateDurationMs(1920000L)) // 1 minute = 1,920,000 bytes
    }
}
