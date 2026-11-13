package com.personalai.assistant.transcription

import android.content.Context
import android.util.Log
import com.personalai.assistant.transcription.models.AvailableModels
import com.personalai.assistant.transcription.models.ModelManager
import com.personalai.assistant.transcription.models.TranscriptionLanguageMode
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

data class AsrHypothesis(
    val text: String,
    val confidence: Float,
    val language: String
)

data class AsrResult(
    val text: String,
    val confidence: Float,
    val language: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val alternatives: List<AsrHypothesis> = emptyList(),
    val isModelAvailable: Boolean = true,
    val errorMessage: String? = null
)

interface AsrEngine {
    val engineName: String
    val isReady: Boolean

    suspend fun transcribeAudioFile(
        audioFile: File,
        languageMode: TranscriptionLanguageMode = TranscriptionLanguageMode.AUTO_HINGLISH,
        onProgress: (Float) -> Unit = {}
    ): AsrResult

    suspend fun transcribePcm(
        pcmData: ShortArray,
        sampleRate: Int = 16000,
        languageMode: TranscriptionLanguageMode = TranscriptionLanguageMode.AUTO_HINGLISH
    ): AsrResult
}

/**
 * 100% Offline, Privacy-Preserving Multilingual Speech Recognition Engine.
 *
 * Implements code-switched Hindi-English (Hinglish) recognition with:
 * 1. Clean ASR abstraction separating acoustic hypothesis generation from formatting.
 * 2. Sequential model fallback without loading multiple heavy models concurrently
 *    (strictly preserving memory on devices with 8GB RAM or less, down to 2GB budget devices).
 * 3. Confidence scoring and alternative hypotheses generation.
 * 4. Authentic ASR output preservation: does not rely on artificial sentence reconstruction
 *    to synthesize words that the acoustic model did not recognize.
 * 5. Preservation of Devanagari and Latin scripts and technical terminology.
 */
class MultilingualOfflineAsrEngine(
    private val context: Context?,
    private val modelManager: ModelManager
) : AsrEngine {

    companion object {
        private const val TAG = "MultilingualAsrEngine"
        private const val LOW_CONFIDENCE_THRESHOLD = 0.45f
    }

    override val engineName: String = "Vosk Multilingual Offline ASR Engine"
    override val isReady: Boolean
        get() = modelManager.getActiveModelDirectory() != null

    override suspend fun transcribeAudioFile(
        audioFile: File,
        languageMode: TranscriptionLanguageMode,
        onProgress: (Float) -> Unit
    ): AsrResult {
        if (!audioFile.exists() || audioFile.length() < 44L) {
            return AsrResult(
                text = "",
                confidence = 0f,
                language = "unknown",
                errorMessage = "Audio file missing or empty"
            )
        }

        val primaryModelInfo = resolvePrimaryModel(languageMode)
        val primaryDir = modelManager.getModelDirectory(primaryModelInfo.id)

        if (primaryDir == null) {
            Log.w(TAG, "Primary speech model ${primaryModelInfo.id} is not downloaded.")
            return AsrResult(
                text = "",
                confidence = 0f,
                language = primaryModelInfo.language,
                isModelAvailable = false,
                errorMessage = "Model ${primaryModelInfo.name} is not installed"
            )
        }

        val engine = VoskTranscriptionEngine()
        val langCode = when (languageMode) {
            TranscriptionLanguageMode.PURE_HINDI -> "hi"
            TranscriptionLanguageMode.GENERAL_ENGLISH -> "en"
            else -> "hi-en"
        }

        val segments = engine.transcribe(
            audioFile = audioFile,
            modelDir = primaryDir,
            languageCode = langCode,
            onProgress = onProgress
        )

        val fullText = segments.joinToString(" ") { it.text }.trim()
        val avgConfidence = if (segments.isNotEmpty()) {
            segments.map { it.confidence }.average().toFloat()
        } else {
            0f
        }

        val alternatives = mutableListOf<AsrHypothesis>()

        // Sequential fallback for AUTO_HINGLISH:
        // If primary hypothesis is weak or empty, test secondary Hindi model sequentially
        // (Only one model loaded at any point to protect 2GB-8GB RAM ceiling)
        if (languageMode == TranscriptionLanguageMode.AUTO_HINGLISH &&
            (avgConfidence < LOW_CONFIDENCE_THRESHOLD || fullText.isBlank())
        ) {
            val fallbackModelInfo = AvailableModels.HINDI
            val fallbackDir = modelManager.getModelDirectory(fallbackModelInfo.id)
            if (fallbackDir != null && fallbackDir != primaryDir) {
                Log.i(TAG, "Executing sequential fallback to Hindi model: ${fallbackModelInfo.id}")
                val fallbackSegments = engine.transcribe(
                    audioFile = audioFile,
                    modelDir = fallbackDir,
                    languageCode = "hi",
                    onProgress = {}
                )
                val fallbackText = fallbackSegments.joinToString(" ") { it.text }.trim()
                val fallbackConf = if (fallbackSegments.isNotEmpty()) {
                    fallbackSegments.map { it.confidence }.average().toFloat()
                } else {
                    0f
                }

                if (fallbackConf > avgConfidence && fallbackText.isNotBlank()) {
                    alternatives.add(AsrHypothesis(text = fullText, confidence = avgConfidence, language = langCode))
                    return AsrResult(
                        text = fallbackText,
                        confidence = fallbackConf,
                        language = "hi",
                        segments = fallbackSegments,
                        alternatives = alternatives
                    )
                } else if (fallbackText.isNotBlank()) {
                    alternatives.add(AsrHypothesis(text = fallbackText, confidence = fallbackConf, language = "hi"))
                }
            }
        }

        return AsrResult(
            text = fullText,
            confidence = avgConfidence,
            language = if (segments.isNotEmpty()) segments.first().language else langCode,
            segments = segments,
            alternatives = alternatives
        )
    }

    override suspend fun transcribePcm(
        pcmData: ShortArray,
        sampleRate: Int,
        languageMode: TranscriptionLanguageMode
    ): AsrResult {
        if (pcmData.isEmpty()) {
            return AsrResult(text = "", confidence = 0f, language = "unknown")
        }

        val primaryModelInfo = resolvePrimaryModel(languageMode)
        val primaryDir = modelManager.getModelDirectory(primaryModelInfo.id)

        if (primaryDir == null) {
            Log.w(TAG, "Primary speech model ${primaryModelInfo.id} not installed")
            return AsrResult(
                text = "",
                confidence = 0f,
                language = primaryModelInfo.language,
                isModelAvailable = false,
                errorMessage = "Speech model ${primaryModelInfo.name} is not installed"
            )
        }

        // Primary model pass
        val primaryHypothesis = runPcmRecognition(primaryDir, pcmData, sampleRate)
        val alternatives = mutableListOf<AsrHypothesis>()

        // Sequential fallback for AUTO_HINGLISH without holding two models in RAM
        if (languageMode == TranscriptionLanguageMode.AUTO_HINGLISH &&
            (primaryHypothesis.confidence < LOW_CONFIDENCE_THRESHOLD || primaryHypothesis.text.isBlank())
        ) {
            val fallbackModelInfo = AvailableModels.HINDI
            val fallbackDir = modelManager.getModelDirectory(fallbackModelInfo.id)
            if (fallbackDir != null && fallbackDir != primaryDir) {
                val fallbackHypothesis = runPcmRecognition(fallbackDir, pcmData, sampleRate)
                if (fallbackHypothesis.confidence > primaryHypothesis.confidence && fallbackHypothesis.text.isNotBlank()) {
                    if (primaryHypothesis.text.isNotBlank()) {
                        alternatives.add(primaryHypothesis)
                    }
                    val detectedLang = SentenceReconstructor.detectLanguageTag(fallbackHypothesis.text)
                    return AsrResult(
                        text = fallbackHypothesis.text,
                        confidence = fallbackHypothesis.confidence,
                        language = detectedLang,
                        alternatives = alternatives
                    )
                } else if (fallbackHypothesis.text.isNotBlank()) {
                    alternatives.add(fallbackHypothesis)
                }
            }
        }

        val detectedLang = if (primaryHypothesis.text.isNotBlank()) {
            SentenceReconstructor.detectLanguageTag(primaryHypothesis.text)
        } else {
            "en"
        }

        return AsrResult(
            text = primaryHypothesis.text,
            confidence = primaryHypothesis.confidence,
            language = detectedLang,
            alternatives = alternatives
        )
    }

    private fun runPcmRecognition(modelDir: File, pcmData: ShortArray, sampleRate: Int): AsrHypothesis {
        var model: Model? = null
        var recognizer: Recognizer? = null
        return try {
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                setWords(true)
            }
            recognizer.acceptWaveForm(pcmData, pcmData.size)
            val jsonResult = recognizer.finalResult
            val json = JSONObject(jsonResult)
            val rawText = json.optString("text", "").trim()

            var totalConf = 0.0
            var wordCount = 0
            val wordsArray = json.optJSONArray("result")
            if (wordsArray != null) {
                for (i in 0 until wordsArray.length()) {
                    val item = wordsArray.getJSONObject(i)
                    totalConf += item.optDouble("conf", 0.90)
                    wordCount++
                }
            }

            val avgConf = if (wordCount > 0) {
                (totalConf / wordCount).toFloat()
            } else if (rawText.isNotEmpty()) {
                0.85f
            } else {
                0f
            }

            // Authentic ASR formatting: format casing and punctuation without altering tokens
            val formatted = if (rawText.isNotEmpty()) {
                val tokens = rawText.split("\\s+".toRegex()).map {
                    WordToken(word = it, startMs = 0L, endMs = 0L, confidence = avgConf)
                }
                SentenceReconstructor.formatSentence(tokens, "hi-en")
            } else {
                ""
            }

            AsrHypothesis(
                text = formatted,
                confidence = avgConf,
                language = SentenceReconstructor.detectLanguageTag(rawText)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in native Kaldi PCM recognition: ${e.message}")
            AsrHypothesis(text = "", confidence = 0f, language = "unknown")
        } finally {
            try { recognizer?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: Exception) {}
        }
    }

    private fun resolvePrimaryModel(languageMode: TranscriptionLanguageMode) = when (languageMode) {
        TranscriptionLanguageMode.AUTO_HINGLISH -> AvailableModels.INDIAN_ENGLISH
        TranscriptionLanguageMode.INDIAN_ENGLISH -> AvailableModels.INDIAN_ENGLISH
        TranscriptionLanguageMode.PURE_HINDI -> AvailableModels.HINDI
        TranscriptionLanguageMode.GENERAL_ENGLISH -> AvailableModels.GENERAL_ENGLISH
    }
}
