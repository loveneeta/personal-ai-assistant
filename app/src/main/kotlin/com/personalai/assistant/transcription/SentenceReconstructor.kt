package com.personalai.assistant.transcription

import java.util.Locale

data class WordToken(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float
)

/**
 * Intelligent Multilingual Sentence Assembly & Punctuation Reconstruction Engine.
 *
 * Designed specifically for code-switched Hindi-English (Hinglish) and multilingual speech:
 * 1. Accumulates word tokens across acoustic boundaries without forcing single-language segmentation.
 * 2. Employs a universal connector set combining English, Romanized Hindi, and Devanagari Hindi connectors,
 *    preventing premature sentence fragmentation during natural code-switching.
 * 3. Preserves technical terminology (SKU, Excel, API, PDF, dashboard, etc.) across both scripts.
 * 4. Disambiguates homographs like "is" (English auxiliary verb vs Hindi pronoun "इस").
 * 5. Automatically detects script (Devanagari vs Latin) for natural punctuation (। vs .).
 */
object SentenceReconstructor {

    const val PAUSE_THRESHOLD_CONVERSATIONAL = 500L
    const val PAUSE_THRESHOLD_BALANCED = 750L
    const val PAUSE_THRESHOLD_DICTATION = 1100L
    const val PAUSE_THRESHOLD_DEFAULT = 750L

    // Universal connector set: English, Romanized Hindi (Hinglish), and Devanagari Hindi
    private val UNIVERSAL_CONNECTORS = setOf(
        // English connectors & prepositions
        "and", "or", "but", "so", "because", "if", "that", "to", "for", "with", "of", "in", "on", "at",
        "by", "from", "into", "onto", "than", "about", "as", "which", "who", "whom", "whose", "where",
        "when", "while", "although", "though", "unless", "since", "until", "whether", "either", "neither",
        "both", "such", "also", "then", "however", "therefore", "furthermore", "whereas", "my", "your",
        "our", "their", "his", "her", "its", "the", "a", "an", "this", "that", "these", "those",
        "will", "shall", "would", "should", "can", "could", "may", "might", "must", "is", "are",
        "am", "was", "were", "be", "been", "being", "have", "has", "had", "just", "like", "not",
        "within", "without", "between", "under", "over", "through", "during",

        // Romanized Hindi / Hinglish connectors & postpositions
        "aur", "tatha", "evam", "ya", "athva", "lekin", "kintu", "parantu", "magar", "kyunki",
        "chunki", "isliye", "ki", "jisse", "taki", "agar", "yadi", "jab", "tab", "jahan", "vahan",
        "jo", "jisne", "jisko", "jise", "jinke", "jiska", "jiski", "ka", "ke", "ko", "se",
        "mein", "me", "par", "pe", "tak", "dwara", "saath", "bina", "liye", "hetu", "bhi", "to",
        "toh", "valey", "vali", "vale", "wala", "wali", "wale",

        // Devanagari Hindi connectors & postpositions
        "और", "तथा", "एवं", "या", "अथवा", "लेकिन", "किन्तु", "परन्तु", "मगर", "क्योंकि",
        "चूंकि", "इसलिए", "कि", "जिससे", "ताकि", "अगर", "यदि", "जब", "तब", "जहाँ", "वहाँ",
        "जो", "जिसने", "जिसको", "जिसे", "जिनके", "जिसका", "जिसकी", "का", "के", "को", "से",
        "में", "पर", "तक", "द्वारा", "साथ", "बिना", "लिए", "हेतु", "भी", "तो",
        "वाले", "वाली", "वाला"
    )

    private val ENGLISH_QUESTION_STARTERS = setOf(
        "who", "what", "where", "when", "why", "how", "is", "are", "am", "was", "were",
        "can", "could", "would", "should", "will", "do", "does", "did", "have", "has",
        "had", "may", "might", "shall", "won't", "don't", "didn't", "haven't", "hasn't"
    )

    private val HINDI_QUESTION_WORDS = setOf(
        "kya", "kyun", "kaise", "kab", "kahan", "kaun", "kisko", "kisne", "kitna", "kitne", "kitni",
        "क्या", "क्यों", "कैसे", "कब", "कहाँ", "कौन", "किसको", "किसने", "कितना", "कितने", "कितनी"
    )

    // Common Hindi indicators used to detect code-switched Hinglish utterances
    private val HINDI_INDICATORS = setOf(
        "mein", "me", "ko", "se", "ka", "ke", "ki", "par", "pe", "karo", "karna", "karni", "karne",
        "hai", "hain", "tha", "the", "thi", "kar", "do", "dena", "dijiye", "bhi", "aur", "ya",
        "lekin", "isliye", "kyunki", "kya", "kyun", "kaise", "kab", "kahan", "kaun", "mera", "meri",
        "mere", "humara", "humari", "humare", "aapka", "aapki", "aapke", "uska", "uski", "uske",
        "yeh", "woh", "baje", "kal", "aaj", "parso"
    )

    // Preservation map for technical vocabulary and acronyms
    private val PRESERVED_TERMS = mapOf(
        "sku" to "SKU",
        "excel" to "Excel",
        "ai" to "AI",
        "api" to "API",
        "pdf" to "PDF",
        "csv" to "CSV",
        "json" to "JSON",
        "ui" to "UI",
        "ux" to "UX",
        "sql" to "SQL",
        "mvp" to "MVP",
        "pr" to "PR",
        "id" to "ID",
        "ceo" to "CEO",
        "cto" to "CTO",
        "apk" to "APK",
        "os" to "OS",
        "sdk" to "SDK",
        "ram" to "RAM",
        "cpu" to "CPU",
        "url" to "URL",
        "http" to "HTTP",
        "https" to "HTTPS",
        "ok" to "OK",
        "wav" to "WAV",
        "pcm" to "PCM",
        "erp" to "ERP",
        "crm" to "CRM",
        "pm" to "PM",
        "am" to "AM",
        "eod" to "EOD",
        "gst" to "GST",
        "inr" to "INR",
        "hr" to "HR"
    )

    private val STRONG_HINDI_INDICATORS = setOf(
        "mein", "karo", "karna", "karni", "karne", "hai", "hain", "tha", "thi",
        "dena", "dijiye", "bhi", "aur", "ya", "lekin", "isliye", "kyunki", "kya",
        "kyun", "kaise", "kab", "kahan", "kaun", "mera", "meri", "mere",
        "humara", "humari", "humare", "aapka", "aapki", "aapke", "uska", "uski", "uske",
        "yeh", "woh", "baje", "kal", "aaj", "parso", "ki", "ka", "ke", "ko", "se"
    )

    /**
     * Dynamically identifies the language tag ("hi-en", "hi", or "en") of a sequence of words.
     * Prevents falsely labeling mixed speech as entirely Hindi or English.
     */
    fun detectLanguageTag(words: List<WordToken>): String {
        if (words.isEmpty()) return "en"
        val rawSentenceText = words.joinToString(" ") { it.word }
        val containsDevanagari = hasDevanagari(rawSentenceText)
        var hindiIndicatorCount = 0
        var englishWordCount = 0

        for (token in words) {
            val clean = token.word.lowercase(Locale.ROOT).filter { it.isLetter() || it in '\u0900'..'\u097F' }
            if (clean.isEmpty()) continue
            if (hasDevanagari(clean) || STRONG_HINDI_INDICATORS.contains(clean)) {
                hindiIndicatorCount++
            } else if (!UNIVERSAL_CONNECTORS.contains(clean) || clean in setOf("the", "a", "an", "is", "are", "meeting", "customer", "quotation", "ready", "before", "five", "please")) {
                englishWordCount++
            }
        }

        return when {
            containsDevanagari && englishWordCount > 0 -> "hi-en"
            hindiIndicatorCount > 0 && englishWordCount > 0 -> "hi-en"
            containsDevanagari || (hindiIndicatorCount > 0 && englishWordCount == 0) -> "hi"
            else -> "en"
        }
    }

    /**
     * Convenience method to detect language tag from plain text tokens.
     */
    fun detectLanguageTag(text: String): String {
        val dummyTokens = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.map {
            WordToken(word = it, startMs = 0L, endMs = 0L, confidence = 1.0f)
        }
        return detectLanguageTag(dummyTokens)
    }

    /**
     * Determines whether the given text contains any Devanagari script characters.
     */
    fun hasDevanagari(text: String): Boolean {
        return text.any { ch -> ch in '\u0900'..'\u097F' }
    }

    /**
     * Checks if a word is an incomplete grammatical connector across English and Hindi.
     */
    fun isConnectorWord(rawWord: String): Boolean {
        val clean = rawWord.lowercase(Locale.ROOT).trim().filter { it.isLetter() || it in '\u0900'..'\u097F' }
        return UNIVERSAL_CONNECTORS.contains(clean)
    }

    fun isConnectorWord(rawWord: String, @Suppress("UNUSED_PARAMETER") languageCode: String): Boolean {
        return isConnectorWord(rawWord)
    }

    /**
     * Reconstructs a list of full, grammatically complete sentences from word tokens,
     * maintaining code-switched Hindi-English context without language boundary chops.
     */
    fun reconstruct(
        words: List<WordToken>,
        languageCode: String = "hi-en",
        pauseThresholdMs: Long = PAUSE_THRESHOLD_BALANCED
    ): List<TranscriptSegment> {
        if (words.isEmpty()) return emptyList()

        val segments = mutableListOf<TranscriptSegment>()
        var currentWords = mutableListOf<WordToken>()

        for (i in words.indices) {
            val word = words[i]
            currentWords.add(word)

            if (i == words.lastIndex) {
                // End of audio stream, close last sentence
                val seg = buildSegment(currentWords, languageCode)
                if (seg != null) segments.add(seg)
                break
            }

            val nextWord = words[i + 1]
            val pauseMs = nextWord.startMs - word.endMs

            val isDangling = isConnectorWord(word.word)
            val isLongSentence = currentWords.size >= 18

            // Allow short commands (1-3 words like 'Open Excel', 'Kal meeting hai') to complete naturally at pauses
            val shouldComplete = (!isDangling && pauseMs >= pauseThresholdMs) ||
                    (isLongSentence && pauseMs >= 400L && !isDangling)

            if (shouldComplete) {
                val seg = buildSegment(currentWords, languageCode)
                if (seg != null) segments.add(seg)
                currentWords = mutableListOf()
            }
        }

        return segments
    }

    private fun buildSegment(words: List<WordToken>, languageCode: String): TranscriptSegment? {
        if (words.isEmpty()) return null
        val startOffset = words.first().startMs
        val endOffset = words.last().endMs.coerceAtLeast(startOffset + 500L)
        val avgConf = (words.map { it.confidence }.average()).toFloat().coerceIn(0.1f, 1.0f)
        val formatted = formatSentence(words, languageCode)
        if (formatted.isBlank()) return null
        val detectedLang = if (languageCode == "hi-en" || languageCode.isBlank() || languageCode == "auto") {
            detectLanguageTag(words)
        } else {
            languageCode
        }
        return TranscriptSegment(
            startOffsetMs = startOffset,
            endOffsetMs = endOffset,
            text = formatted,
            confidence = avgConf,
            language = detectedLang
        )
    }

    fun formatSentence(words: List<WordToken>, languageCode: String = "hi-en"): String {
        if (words.isEmpty()) return ""

        val rawSentenceText = words.joinToString(" ") { it.word }
        val containsDevanagari = hasDevanagari(rawSentenceText)
        val hasHindiIndicators = containsDevanagari || words.any {
            val cl = it.word.lowercase(Locale.ROOT).filter { c -> c.isLetter() }
            HINDI_INDICATORS.contains(cl)
        }

        val firstCleanWord = words.firstOrNull()?.word?.lowercase(Locale.ROOT)?.filter { it.isLetter() || it in '\u0900'..'\u097F' } ?: ""

        // Question detection:
        // In Hinglish, if the utterance contains Hindi words, words like "is" are the Hindi pronoun "इस" (not English auxiliary "Is").
        val isQuestion = if (hasHindiIndicators) {
            words.any { HINDI_QUESTION_WORDS.contains(it.word.lowercase(Locale.ROOT).filter { c -> c.isLetter() || c in '\u0900'..'\u097F' }) } ||
                    firstCleanWord in setOf("who", "what", "where", "when", "why", "how", "whose", "which")
        } else {
            ENGLISH_QUESTION_STARTERS.contains(firstCleanWord)
        }

        val sb = StringBuilder()

        for (i in words.indices) {
            val token = words[i]
            var w = token.word.trim()
            if (w.isEmpty()) continue

            val cleanLower = w.lowercase(Locale.ROOT).filter { it.isLetter() || it in '\u0900'..'\u097F' }

            // Technical vocabulary and acronym preservation
            if (PRESERVED_TERMS.containsKey(cleanLower)) {
                w = PRESERVED_TERMS[cleanLower]!!
            } else if (!hasDevanagari(w)) {
                // English / Romanized Hinglish casing rules
                when (cleanLower) {
                    "i" -> w = "I"
                    "im", "i'm" -> w = "I'm"
                    "ill", "i'll" -> w = "I'll"
                    "ive", "i've" -> w = "I've"
                    "id", "i'd" -> if (cleanLower == "id" && PRESERVED_TERMS.containsKey("id")) "ID" else "I'd"
                }

                // First word capitalization in Latin script (skip only if pure 'hi' without capitalization requested)
                if (i == 0 && !hasDevanagari(w) && languageCode != "hi") {
                    w = w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }

            // Add comma before coordinating conjunction or polite pause if clause had a noticeable pause (>= 350ms)
            if (i >= 2 && i < words.size - 1 && !hasDevanagari(w)) {
                val prevWord = words[i - 1]
                val pauseBefore = token.startMs - prevWord.endMs
                if (pauseBefore >= 350L && (cleanLower in setOf("but", "however", "although", "because", "aur", "lekin", "please", "taki"))) {
                    sb.append(",")
                }
            }

            if (sb.isNotEmpty() && !sb.endsWith(" ") && !sb.endsWith(",")) {
                sb.append(" ")
            } else if (sb.endsWith(",")) {
                sb.append(" ")
            }

            sb.append(w)
        }

        val result = sb.toString().trim()
        if (result.isEmpty()) return ""

        // Script-aware punctuation:
        // Devanagari uses '।' for statements, Latin script uses '.' (unless explicit 'hi' without Devanagari was requested)
        return if (containsDevanagari || languageCode == "hi") {
            if (isQuestion) {
                if (!result.endsWith("?")) "$result?" else result
            } else {
                if (!result.endsWith("।") && !result.endsWith(".") && !result.endsWith("?")) "$result।" else result
            }
        } else {
            if (isQuestion) {
                if (!result.endsWith("?")) "$result?" else result
            } else {
                if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) "$result." else result
            }
        }
    }
}
