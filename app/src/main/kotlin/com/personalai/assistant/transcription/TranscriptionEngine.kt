package com.personalai.assistant.transcription

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.util.Locale

data class TranscriptSegment(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
    val confidence: Float,
    val language: String
)

class VoskTranscriptionEngine {

    fun transcribe(
        audioFile: File,
        modelDir: File,
        languageCode: String = "en",
        onProgress: (Float) -> Unit = {}
    ): List<TranscriptSegment> {
        if (!audioFile.exists() || audioFile.length() < 44) {
            throw IllegalArgumentException("Audio file is missing or contains no data")
        }

        val model = Model(modelDir.absolutePath)
        val recognizer = Recognizer(model, 16000.0f)
        recognizer.setWords(true)

        val segments = mutableListOf<TranscriptSegment>()
        val totalAudioBytes = audioFile.length() - 44
        var processedBytes: Long = 0

        FileInputStream(audioFile).use { fis ->
            // Skip 44-byte WAV header
            fis.skip(44)
            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                processedBytes += bytesRead
                if (totalAudioBytes > 0) {
                    onProgress((processedBytes.toFloat() / totalAudioBytes.toFloat()).coerceIn(0f, 1f))
                }

                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    val resultJson = recognizer.result
                    val seg = parseVoskJsonResult(resultJson, languageCode)
                    if (seg != null && seg.text.isNotBlank()) {
                        segments.add(seg)
                    }
                }
            }

            // Final tail result
            val finalJson = recognizer.finalResult
            val finalSeg = parseVoskJsonResult(finalJson, languageCode)
            if (finalSeg != null && finalSeg.text.isNotBlank()) {
                segments.add(finalSeg)
            }
        }

        recognizer.close()
        model.close()

        return segments
    }

    private fun parseVoskJsonResult(jsonStr: String, languageCode: String): TranscriptSegment? {
        try {
            val json = JSONObject(jsonStr)
            val text = json.optString("text", "").trim()
            if (text.isEmpty()) return null

            var startMs = 0L
            var endMs = 0L
            var totalConf = 0.0
            var wordCount = 0

            val wordsArray = json.optJSONArray("result")
            if (wordsArray != null && wordsArray.length() > 0) {
                val firstWord = wordsArray.getJSONObject(0)
                val lastWord = wordsArray.getJSONObject(wordsArray.length() - 1)

                startMs = (firstWord.optDouble("start", 0.0) * 1000.0).toLong()
                endMs = (lastWord.optDouble("end", 0.0) * 1000.0).toLong()

                for (i in 0 until wordsArray.length()) {
                    val w = wordsArray.getJSONObject(i)
                    totalConf += w.optDouble("conf", 1.0)
                    wordCount++
                }
            }

            val avgConf = if (wordCount > 0) (totalConf / wordCount).toFloat() else 0.95f
            val formattedText = formatSentence(text, languageCode)

            return TranscriptSegment(
                startOffsetMs = startMs,
                endOffsetMs = if (endMs > startMs) endMs else startMs + 3000L,
                text = formattedText,
                confidence = avgConf,
                language = languageCode
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun formatSentence(raw: String, languageCode: String): String {
        if (raw.isBlank()) return raw
        val trimmed = raw.trim()
        if (languageCode == "hi") {
            return if (!trimmed.endsWith("।") && !trimmed.endsWith(".") && !trimmed.endsWith("?")) {
                "$trimmed।"
            } else {
                trimmed
            }
        }
        val capitalized = trimmed.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        return if (!capitalized.endsWith(".") && !capitalized.endsWith("?") && !capitalized.endsWith("!")) {
            "$capitalized."
        } else {
            capitalized
        }
    }
}
