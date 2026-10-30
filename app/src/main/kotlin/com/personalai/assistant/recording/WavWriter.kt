package com.personalai.assistant.recording

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

data class WavValidationResult(
    val isValid: Boolean,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val audioDataBytes: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

object WavWriter {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) // 32000

    fun writeWavHeader(out: OutputStream, totalAudioBytes: Long = 0) {
        val totalDataLen = totalAudioBytes + 36
        val byteRate = BYTES_PER_SECOND
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)

        val header = ByteArray(44)
        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        // Subchunk1Size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // AudioFormat (1 for PCM)
        header[20] = 1
        header[21] = 0
        // NumChannels
        header[22] = CHANNELS.toByte()
        header[23] = 0
        // SampleRate
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        // ByteRate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        // BlockAlign
        header[32] = blockAlign.toByte()
        header[33] = 0
        // BitsPerSample
        header[34] = BITS_PER_SAMPLE.toByte()
        header[35] = 0
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioBytes and 0xff).toByte()
        header[41] = ((totalAudioBytes shr 8) and 0xff).toByte()
        header[42] = ((totalAudioBytes shr 16) and 0xff).toByte()
        header[43] = ((totalAudioBytes shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    fun finalizeWavHeader(file: File, totalAudioBytes: Long) {
        val totalDataLen = totalAudioBytes + 36
        RandomAccessFile(file, "rw").use { raf ->
            // Update RIFF chunk size at byte 4
            raf.seek(4)
            raf.write(
                byteArrayOf(
                    (totalDataLen and 0xff).toByte(),
                    ((totalDataLen shr 8) and 0xff).toByte(),
                    ((totalDataLen shr 16) and 0xff).toByte(),
                    ((totalDataLen shr 24) and 0xff).toByte()
                )
            )
            // Update data subchunk size at byte 40
            raf.seek(40)
            raf.write(
                byteArrayOf(
                    (totalAudioBytes and 0xff).toByte(),
                    ((totalAudioBytes shr 8) and 0xff).toByte(),
                    ((totalAudioBytes shr 16) and 0xff).toByte(),
                    ((totalAudioBytes shr 24) and 0xff).toByte()
                )
            )
        }
    }

    fun calculateDurationMs(audioBytes: Long): Long {
        return (audioBytes.coerceAtLeast(0L) * 1000L) / BYTES_PER_SECOND
    }

    /**
     * Inspects a WAV file on disk and verifies all structural RIFF/WAVE header invariants.
     */
    fun validateWavHeader(file: File): WavValidationResult {
        if (!file.exists()) {
            return WavValidationResult(isValid = false, errorMessage = "File does not exist: ${file.absolutePath}")
        }
        val fileLength = file.length()
        if (fileLength < 44L) {
            return WavValidationResult(isValid = false, errorMessage = "File too small for WAV header (length: $fileLength)")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "RIFF") {
                    return WavValidationResult(isValid = false, errorMessage = "Missing RIFF magic header")
                }

                // Chunk size
                val riffChunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL

                raf.seek(8)
                val waveMagic = ByteArray(4)
                raf.readFully(waveMagic)
                if (String(waveMagic, Charsets.US_ASCII) != "WAVE") {
                    return WavValidationResult(isValid = false, errorMessage = "Missing WAVE format identifier")
                }

                raf.seek(12)
                val fmtMagic = ByteArray(4)
                raf.readFully(fmtMagic)
                if (String(fmtMagic, Charsets.US_ASCII) != "fmt ") {
                    return WavValidationResult(isValid = false, errorMessage = "Missing fmt chunk")
                }

                raf.seek(20)
                val format = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                if (format != 1) {
                    return WavValidationResult(isValid = false, errorMessage = "Unsupported audio format: $format (expected 1 for linear PCM)")
                }

                val channels = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                val sampleRate = Integer.reverseBytes(raf.readInt())
                raf.seek(34)
                val bitsPerSample = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF

                raf.seek(36)
                val dataMagic = ByteArray(4)
                raf.readFully(dataMagic)
                if (String(dataMagic, Charsets.US_ASCII) != "data") {
                    return WavValidationResult(isValid = false, errorMessage = "Missing data subchunk")
                }

                val dataSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
                val calculatedDuration = calculateDurationMs(dataSize)

                WavValidationResult(
                    isValid = true,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    audioDataBytes = dataSize,
                    durationMs = calculatedDuration
                )
            }
        } catch (e: Exception) {
            WavValidationResult(isValid = false, errorMessage = "Failed reading WAV header: ${e.message}")
        }
    }

    /**
     * Repairs an incomplete or abruptly terminated WAV file by calculating
     * actual recorded audio payload bytes and rewriting the RIFF chunk sizes.
     * Returns the recalculated duration in milliseconds.
     */
    fun repairIncompleteWav(file: File): Long {
        if (!file.exists() || file.length() < 44L) return 0L
        val audioBytes = (file.length() - 44L).coerceAtLeast(0L)
        finalizeWavHeader(file, audioBytes)
        return calculateDurationMs(audioBytes)
    }
}
