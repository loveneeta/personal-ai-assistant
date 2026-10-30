package com.personalai.assistant.transcription

import java.util.Locale

/**
 * Word Error Rate (WER) Calculator for speech recognition evaluation.
 *
 * Implements the standard Levenshtein distance algorithm at the word level:
 * WER = (Substitutions + Deletions + Insertions) / TotalReferenceWords
 *
 * Supports script-aware tokenization for Hindi (Devanagari), English (Latin),
 * and mixed code-switched Hinglish.
 */
object WerCalculator {

    data class WerResult(
        val wer: Double,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val referenceWordCount: Int,
        val hypothesisWordCount: Int,
        val accuracy: Double
    ) {
        val werPercentage: Double get() = wer * 100.0
        val accuracyPercentage: Double get() = accuracy * 100.0
    }

    /**
     * Calculates Word Error Rate (WER) between a ground truth reference and recognized hypothesis.
     */
    fun calculate(
        reference: String,
        hypothesis: String,
        ignorePunctuation: Boolean = true,
        ignoreCase: Boolean = true
    ): WerResult {
        val refWords = tokenize(reference, ignorePunctuation, ignoreCase)
        val hypWords = tokenize(hypothesis, ignorePunctuation, ignoreCase)

        val n = refWords.size
        val m = hypWords.size

        if (n == 0) {
            val wer = if (m == 0) 0.0 else 1.0
            return WerResult(
                wer = wer,
                substitutions = 0,
                deletions = 0,
                insertions = m,
                referenceWordCount = 0,
                hypothesisWordCount = m,
                accuracy = if (wer == 0.0) 1.0 else 0.0
            )
        }

        if (m == 0) {
            return WerResult(
                wer = 1.0,
                substitutions = 0,
                deletions = n,
                insertions = 0,
                referenceWordCount = n,
                hypothesisWordCount = 0,
                accuracy = 0.0
            )
        }

        // DP matrix of edit operations: dp[i][j] holds (cost, subs, dels, ins)
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) {
            dp[i][0] = i
        }
        for (j in 0..m) {
            dp[0][j] = j
        }

        for (i in 1..n) {
            for (j in 1..m) {
                if (refWords[i - 1] == hypWords[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val sub = dp[i - 1][j - 1] + 1
                    val del = dp[i - 1][j] + 1
                    val ins = dp[i][j - 1] + 1
                    dp[i][j] = minOf(sub, minOf(del, ins))
                }
            }
        }

        // Backtrack to count substitutions, deletions, and insertions
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && refWords[i - 1] == hypWords[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                substitutions++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                deletions++
                i--
            } else {
                insertions++
                j--
            }
        }

        val totalErrors = substitutions + deletions + insertions
        val wer = totalErrors.toDouble() / n.toDouble()
        val accuracy = (1.0 - wer).coerceIn(0.0, 1.0)

        return WerResult(
            wer = wer,
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            referenceWordCount = n,
            hypothesisWordCount = m,
            accuracy = accuracy
        )
    }

    private fun tokenize(text: String, ignorePunctuation: Boolean, ignoreCase: Boolean): List<String> {
        var processed = text
        if (ignoreCase) {
            processed = processed.lowercase(Locale.ROOT)
        }
        if (ignorePunctuation) {
            // Strip English and Hindi punctuation (.,!?:;|।) while keeping letters, digits, and Devanagari
            processed = processed.replace("""[.,!?:;|।\-_"'\(\)\[\]]""".toRegex(), " ")
        }
        return processed.split("""\s+""".toRegex()).filter { it.isNotBlank() }
    }
}
