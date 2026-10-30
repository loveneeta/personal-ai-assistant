package com.personalai.assistant.transcription

import com.personalai.assistant.recording.WavWriter
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

data class TranscriptSegment(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
    val confidence: Float,
    val language: String
)

class VoskTranscriptionEngine {

    /**
     * Transcribes an on-device 16kHz WAV audio file into whole, completed, grammatically sound sentences.
     *
     * Ensures native Kaldi C++ [Model] and [Recognizer] instances are guaranteed to close under all
     * conditions (success, exception, or coroutine cancellation).
     */
    fun transcribe(
        audioFile: File,
        modelDir: File,
        languageCode: String = "en",
        pauseThresholdMs: Long = SentenceReconstructor.PAUSE_THRESHOLD_BALANCED,
        onProgress: (Float) -> Unit = {}
    ): List<TranscriptSegment> {
        if (!audioFile.exists() || audioFile.length() < 44L) {
            throw IllegalArgumentException("Audio file is missing or contains no data: ${audioFile.absolutePath}")
        }

        // Validate WAV file format invariants
        val validation = WavWriter.validateWavHeader(audioFile)
        if (!validation.isValid) {
            throw IllegalArgumentException("Invalid WAV audio format: ${validation.errorMessage}")
        }

        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IllegalArgumentException("Speech model directory does not exist: ${modelDir.absolutePath}")
        }

        var model: Model? = null
        var recognizer: Recognizer? = null

        val allWords = mutableListOf<WordToken>()

        try {
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f).apply {
                setWords(true)
            }

            val dataOffset = findAudioDataOffset(audioFile)
            val audioDataLen = (audioFile.length() - dataOffset).coerceAtLeast(0L)
            var processedBytes: Long = 0

            FileInputStream(audioFile).use { fis ->
                if (dataOffset > 0) {
                    fis.skip(dataOffset)
                }
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    processedBytes += bytesRead
                    if (audioDataLen > 0) {
                        onProgress((processedBytes.toFloat() / audioDataLen.toFloat()).coerceIn(0f, 0.95f))
                    }

                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        val resultJson = recognizer.result
                        val streamTimeMs = (processedBytes * 1000L) / 32000L
                        val words = extractWords(resultJson, streamTimeMs)
                        appendDeduplicatedWords(allWords, words)
                    }
                }

                // Extract final recognition hypothesis without dropping terminal words
                val finalJson = recognizer.finalResult
                val finalStreamTimeMs = (processedBytes * 1000L) / 32000L
                val finalWords = extractWords(finalJson, finalStreamTimeMs)
                appendDeduplicatedWords(allWords, finalWords)
            }

            onProgress(0.98f)

            // Reconstruct whole completed sentences with timestamp continuity
            val completedSegments = SentenceReconstructor.reconstruct(
                words = allWords,
                languageCode = languageCode,
                pauseThresholdMs = pauseThresholdMs
            )

            onProgress(1.0f)
            return completedSegments

        } finally {
            // Guaranteed native cleanup under all circumstances (errors, exceptions, cancellations)
            try {
                recognizer?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                model?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun appendDeduplicatedWords(target: MutableList<WordToken>, newWords: List<WordToken>) {
        for (w in newWords) {
            val isDuplicate = target.isNotEmpty() &&
                    target.last().word.equals(w.word, ignoreCase = true) &&
                    target.last().startMs == w.startMs
            if (!isDuplicate) {
                // Enforce monotonic non-decreasing timestamp invariants across streaming buffers
                val adjustedWord = if (target.isNotEmpty() && w.startMs < target.last().endMs) {
                    val newStart = target.last().endMs + 20L
                    val originalDuration = (w.endMs - w.startMs).coerceAtLeast(100L)
                    w.copy(startMs = newStart, endMs = newStart + originalDuration)
                } else {
                    w
                }
                target.add(adjustedWord)
            }
        }
    }

    fun extractWords(jsonStr: String, currentStreamTimeMs: Long = 0L): List<WordToken> {
        val list = mutableListOf<WordToken>()
        try {
            val json = JSONObject(jsonStr)
            val wordsArray = json.optJSONArray("result")
            if (wordsArray != null && wordsArray.length() > 0) {
                for (i in 0 until wordsArray.length()) {
                    val item = wordsArray.getJSONObject(i)
                    val word = item.optString("word", "").trim()
                    val start = (item.optDouble("start", 0.0) * 1000.0).toLong()
                    val end = (item.optDouble("end", 0.0) * 1000.0).toLong()
                    val conf = item.optDouble("conf", 0.95).toFloat()
                    if (word.isNotEmpty()) {
                        list.add(WordToken(word, start, end, conf))
                    }
                }
            } else {
                // Fallback if result array is absent but text is present - synthesize monotonic timestamps
                val text = json.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    val tokens = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val wordDurationMs = 280L
                    val wordGapMs = 40L
                    val totalDuration = tokens.size * (wordDurationMs + wordGapMs)
                    val baseStartMs = (currentStreamTimeMs - totalDuration).coerceAtLeast(0L)
                    tokens.forEachIndexed { idx, t ->
                        val wordStart = baseStartMs + idx * (wordDurationMs + wordGapMs)
                        val wordEnd = wordStart + wordDurationMs
                        list.add(WordToken(t, wordStart, wordEnd, 0.90f))
                    }
                }
            }
        } catch (e: Exception) {
            // ignore malformed partial json
        }
        return list
    }

    private fun findAudioDataOffset(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 12) return 44L
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "RIFF") return 44L
                raf.seek(12)
                while (raf.filePointer < raf.length() - 8) {
                    val chunkId = ByteArray(4)
                    raf.readFully(chunkId)
                    val idStr = String(chunkId, Charsets.US_ASCII)
                    val chunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
                    if (idStr == "data") {
                        return raf.filePointer
                    }
                    if (chunkSize > 0 && raf.filePointer + chunkSize <= raf.length()) {
                        raf.seek(raf.filePointer + chunkSize)
                    } else {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to standard 44 bytes
        }
        return 44L
    }
}
