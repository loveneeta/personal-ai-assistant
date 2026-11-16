package com.personalai.assistant.translation

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedLanguage: String,
    val isTranslated: Boolean
)

/**
 * High-performance, 100% on-device translation engine.
 * Combines Google ML Kit On-Device Translation for Devanagari Hindi
 * and entity-preserving rule-based transliteration for Romanized Hinglish.
 * Zero recurring API costs.
 */
class TranslationEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
        private val DEVANAGARI_REGEX = Pattern.compile("[\\u0900-\\u097F]")

        // Unambiguous Romanized Hinglish markers (excluding English collisions like 'the' and 'do')
        private val HINGLISH_WORDS = setOf(
            "kal", "aaj", "parson", "subah", "shaam", "dopahar", "raat", "baje",
            "karo", "kar", "karna", "karni", "hai", "hain", "tha", "thi",
            "bhejo", "bhejna", "laga", "lagao", "chalu", "band", "bujhao", "jalao",
            "mera", "meri", "mere", "mujhe", "mujhko", "hum", "hume", "tum", "aap",
            "kaun", "kahan", "kab", "kaise", "kyun", "kya", "kripya", "theek",
            "wala", "wali", "wale", "sab", "saare", "batao", "dikhao", "suno",
            "jana", "jao", "khol", "kholo", "chahiye", "rakho", "bataiye"
        )

        @Volatile
        private var INSTANCE: TranslationEngine? = null

        fun getInstance(context: Context): TranslationEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranslationEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var mlKitTranslator: Translator? = null
    private var isModelDownloaded = false

    init {
        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.HINDI)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            mlKitTranslator = Translation.getClient(options)
        } catch (e: Throwable) {
            Log.w(TAG, "ML Kit Translator initialization deferred: ${e.message}")
        }
    }

    /**
     * Translates input text into English while preserving the original transcript.
     * Automatically identifies Devanagari Hindi, Romanized Hinglish, or English.
     */
    suspend fun translateToEnglish(text: String): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return TranslationResult(
                originalText = "",
                translatedText = "",
                detectedLanguage = "en",
                isTranslated = false
            )
        }

        val lang = detectLanguage(trimmed)
        if (lang == "en") {
            return TranslationResult(
                originalText = trimmed,
                translatedText = trimmed,
                detectedLanguage = "en",
                isTranslated = false
            )
        }

        // 1. If Devanagari Hindi: attempt on-device ML Kit translation
        if (lang == "hi") {
            val mlKitResult = translateDevanagari(trimmed)
            if (mlKitResult != null && mlKitResult.isNotBlank()) {
                return TranslationResult(
                    originalText = trimmed,
                    translatedText = mlKitResult,
                    detectedLanguage = "hi",
                    isTranslated = true
                )
            }
            // Fallback dictionary for common Devanagari commands if model is downloading
            val fallback = translateDevanagariFallback(trimmed)
            return TranslationResult(
                originalText = trimmed,
                translatedText = fallback,
                detectedLanguage = "hi",
                isTranslated = fallback != trimmed
            )
        }

        // 2. Romanized Hinglish: Translate using entity-preserving normalizer
        val hinglishTranslated = translateHinglish(trimmed)
        return TranslationResult(
            originalText = trimmed,
            translatedText = hinglishTranslated,
            detectedLanguage = "hi-Latn",
            isTranslated = true
        )
    }

    /**
     * Detects if input text is Devanagari Hindi ('hi'), Romanized Hinglish ('hi-Latn'), or English ('en').
     */
    fun detectLanguage(text: String): String {
        if (DEVANAGARI_REGEX.matcher(text).find()) {
            return "hi"
        }
        val tokens = text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val hinglishCount = tokens.count { HINGLISH_WORDS.contains(it) }
        return if (hinglishCount >= 1) "hi-Latn" else "en"
    }

    private suspend fun translateDevanagari(devanagariText: String): String? {
        val translator = mlKitTranslator ?: return null
        return try {
            if (!isModelDownloaded) {
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).awaitTask()
                isModelDownloaded = true
            }
            translator.translate(devanagariText).awaitTask()
        } catch (e: Throwable) {
            Log.w(TAG, "ML Kit on-device translation unavailable: ${e.message}")
            null
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    /**
     * Fallback dictionary translation for common Devanagari assistant instructions.
     */
    fun translateDevanagariFallback(text: String): String {
        var translated = text
        val devanagariReplacements = listOf(
            "फ्लैशलाइट चालू कर दो" to "turn on flashlight",
            "फ्लैशलाइट बंद कर दो" to "turn off flashlight",
            "टॉर्च चालू करो" to "turn on torch",
            "टॉर्च बंद करो" to "turn off torch",
            "वॉल्यूम म्यूट कर दो" to "mute volume",
            "वॉल्यूम बढ़ाओ" to "increase volume",
            "वॉल्यूम कम करो" to "decrease volume",
            "कॉल करो" to "call",
            "फोन लगा दो" to "call",
            "को फोन लगाओ" to "call",
            "मैसेज भेजो" to "send message",
            "को मैसेज करो" to "send message to",
            "ईमेल भेजो" to "send email",
            "कल" to "tomorrow",
            "आज" to "today",
            "सुबह" to "morning",
            "शाम" to "evening",
            "मीटिंग शेड्यूल करो" to "schedule meeting"
        )
        for ((hi, en) in devanagariReplacements) {
            translated = translated.replace(hi, en)
        }
        return translated
    }

    /**
     * Entity-preserving Romanized Hinglish to English translation.
     * Preserves proper names, numbers, email addresses, and app targets.
     */
    fun translateHinglish(text: String): String {
        var result = text

        // 1. Phrasal temporal & action mapping
        val phraseMappings = listOf(
            Regex("(?i)\\bkal\\s+subah\\b") to "tomorrow morning",
            Regex("(?i)\\bkal\\s+shaam\\b") to "tomorrow evening",
            Regex("(?i)\\bkal\\s+dopahar\\b") to "tomorrow afternoon",
            Regex("(?i)\\baaj\\s+subah\\b") to "this morning",
            Regex("(?i)\\baaj\\s+shaam\\b") to "this evening",
            Regex("(?i)\\bkal\\s+raat\\b") to "tomorrow night",
            Regex("(?i)\\bparson\\b") to "day after tomorrow",
            Regex("(?i)\\bmeeting\\s+schedule\\s+karni\\s+hai\\b") to "schedule a meeting",
            Regex("(?i)\\bmeeting\\s+schedule\\s+karo\\b") to "schedule meeting",
            Regex("(?i)\\bmeeting\\s+rakh\\s+lo\\b") to "schedule a meeting",
            Regex("(?i)\\bphone\\s+laga\\s+do\\b") to "call",
            Regex("(?i)\\bphone\\s+lagao\\b") to "call",
            Regex("(?i)\\bcall\\s+karo\\b") to "call",
            Regex("(?i)\\bmessage\\s+bhejo\\b") to "send message",
            Regex("(?i)\\bmessage\\s+kar\\s+do\\b") to "send message",
            Regex("(?i)\\bemail\\s+bhejo\\b") to "send email",
            Regex("(?i)\\bflashlight\\s+chalu\\s+kar\\s+do\\b") to "turn on flashlight",
            Regex("(?i)\\bflashlight\\s+band\\s+kar\\s+do\\b") to "turn off flashlight",
            Regex("(?i)\\btorch\\s+jalao\\b") to "turn on torch",
            Regex("(?i)\\btorch\\s+bujhao\\b") to "turn off torch",
            Regex("(?i)\\bvolume\\s+badha\\s+do\\b") to "increase volume",
            Regex("(?i)\\bvolume\\s+kam\\s+kar\\s+do\\b") to "decrease volume",
            Regex("(?i)\\bvolume\\s+mute\\s+kar\\s+do\\b") to "mute volume"
        )

        for ((regex, replacement) in phraseMappings) {
            result = regex.replace(result, replacement)
        }

        // 2. Word-level grammatical normalizations
        val wordMappings = listOf(
            Regex("(?i)\\bkal\\b") to "tomorrow",
            Regex("(?i)\\baaj\\b") to "today",
            Regex("(?i)\\bsubah\\b") to "morning",
            Regex("(?i)\\bshaam\\b") to "evening",
            Regex("(?i)\\bdopahar\\b") to "afternoon",
            Regex("(?i)\\braat\\b") to "night",
            Regex("(?i)\\bbaje\\b") to "o'clock",
            Regex("(?i)\\bmujhe\\b") to "I need to",
            Regex("(?i)\\bhume\\b") to "we need to",
            Regex("(?i)\\bkripya\\b") to "please",
            Regex("(?i)\\bkaro\\b") to "",
            Regex("(?i)\\bkar\\s+do\\b") to "",
            Regex("(?i)\\bko\\b") to "to"
        )

        for ((regex, replacement) in wordMappings) {
            result = regex.replace(result, replacement)
        }

        // Clean up excess whitespace
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
