package com.personalai.assistant.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
}
