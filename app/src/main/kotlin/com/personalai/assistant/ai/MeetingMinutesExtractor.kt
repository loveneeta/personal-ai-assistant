package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ExtractedTask(
    val title: String,
    val description: String? = null,
    val assignedPerson: String? = null,
    val dueDate: Long? = null,
    val timestampMs: Long? = null
)

data class ExtractedPoint(
    val text: String,
    val timestampMs: Long
)

data class ExtractedMeetingMinutes(
    val title: String,
    val summary: String,
    val attendees: List<String>,
    val discussionPoints: List<ExtractedPoint>,
    val decisions: List<ExtractedPoint>,
    val actionItems: List<ExtractedTask>,
    val openQuestions: List<ExtractedPoint>,
    val needsReview: Boolean
)

object MeetingMinutesExtractor {

    private val DECISION_CUES = listOf(
        "decided", "agreed", "consensus", "conclusion", "approved",
        "will go with", "going with", "resolved", "final decision", "we should choose"
    )

    private val ACTION_CUES = listOf(
        "will do", "action item", "take care of", "need to", "needs to",
        "assigned to", "follow up", "make sure", "have to", "responsible for",
        "let's ensure", "can you", "please handle", "please check"
    )

    private val QUESTION_CUES = listOf(
        "risk", "blocker", "uncertain", "not sure", "how do we", "how can we",
        "what about", "who is", "when will", "could we", "is it possible"
    )

    private val COMMON_NAMES = listOf(
        "Alex", "Rahul", "Priya", "Amit", "Neha", "Loveneet", "John", "Sarah", "David",
        "Michael", "Emily", "Vikram", "Anjali", "Suresh", "Pooja", "Arjun", "Karan"
    )

    fun extract(
        recording: RecordingEntity,
        transcripts: List<TranscriptEntity>
    ): ExtractedMeetingMinutes {
        if (transcripts.isEmpty()) {
            return ExtractedMeetingMinutes(
                title = recording.title.ifBlank { "Meeting Session" },
                summary = "No speech detected in this recording session.",
                attendees = emptyList(),
                discussionPoints = emptyList(),
                decisions = emptyList(),
                actionItems = emptyList(),
                openQuestions = emptyList(),
                needsReview = false
            )
        }

        val allSentences = mutableListOf<ExtractedPoint>()
        for (seg in transcripts) {
            val raw = seg.text.trim()
            if (raw.isNotEmpty()) {
                val parts = raw.split(Pattern.compile("(?<=[.!?])\\s+"))
                for (part in parts) {
                    val trimmed = part.trim()
                    if (trimmed.length > 5) {
                        allSentences.add(ExtractedPoint(trimmed, seg.startOffsetMs))
                    }
                }
            }
        }

        // 1. Detect Attendees / Names mentioned
        val detectedAttendees = mutableSetOf<String>()
        val fullText = transcripts.joinToString(" ") { it.text }
        for (name in COMMON_NAMES) {
            val pattern = Pattern.compile("\\b$name\\b", Pattern.CASE_INSENSITIVE)
            if (pattern.matcher(fullText).find()) {
                detectedAttendees.add(name)
            }
        }

        // 2. Extract Decisions
        val decisions = mutableListOf<ExtractedPoint>()
        // 3. Extract Action Items / Tasks
        val actionItems = mutableListOf<ExtractedTask>()
        // 4. Extract Questions & Risks
        val openQuestions = mutableListOf<ExtractedPoint>()
        // 5. Discussion Points
        val discussionPoints = mutableListOf<ExtractedPoint>()

        for (item in allSentences) {
            val lower = item.text.lowercase(Locale.ROOT)

            // Check Decisions
            if (DECISION_CUES.any { lower.contains(it) }) {
                decisions.add(item)
            }

            // Check Action Items
            if (ACTION_CUES.any { lower.contains(it) }) {
                val assignee = extractAssignee(item.text, detectedAttendees)
                val dueDate = extractDueDate(item.text)
                actionItems.add(
                    ExtractedTask(
                        title = cleanActionTitle(item.text),
                        description = "Extracted from recording at ${formatTime(item.timestampMs)}",
                        assignedPerson = assignee,
                        dueDate = dueDate,
                        timestampMs = item.timestampMs
                    )
                )
            }

            // Check Questions / Risks
            if (item.text.endsWith("?") || QUESTION_CUES.any { lower.contains(it) }) {
                openQuestions.add(item)
            }

            // General discussion points (key informative sentences)
            if (item.text.length > 25 && !item.text.startsWith("Yeah") && !item.text.startsWith("Okay")) {
                discussionPoints.add(item)
            }
        }

        // 6. Generate Executive Summary
        val summary = generateExecutiveSummary(allSentences)

        // Title generation
        val generatedTitle = if (recording.title.startsWith("Recording #") || recording.title.startsWith("Voice Note")) {
            val topic = extractTopicTitle(allSentences)
            topic ?: recording.title
        } else {
            recording.title
        }

        return ExtractedMeetingMinutes(
            title = generatedTitle,
            summary = summary,
            attendees = detectedAttendees.toList(),
            discussionPoints = discussionPoints.take(8),
            decisions = decisions,
            actionItems = actionItems,
            openQuestions = openQuestions,
            needsReview = actionItems.isEmpty() && decisions.isEmpty()
        )
    }

    private fun extractAssignee(sentence: String, knownAttendees: Set<String>): String? {
        // Look for "assigned to [Name]" or "[Name] will"
        for (name in knownAttendees) {
            if (sentence.contains(name, ignoreCase = true)) {
                return name
            }
        }
        for (name in COMMON_NAMES) {
            if (sentence.contains(name, ignoreCase = true)) {
                return name
            }
        }
        return null
    }

    private fun extractDueDate(sentence: String): Long? {
        val lower = sentence.lowercase(Locale.ROOT)
        val calendar = Calendar.getInstance()
        return when {
            lower.contains("today") || lower.contains("by end of day") || lower.contains("eod") -> {
                calendar.set(Calendar.HOUR_OF_DAY, 18)
                calendar.set(Calendar.MINUTE, 0)
                calendar.timeInMillis
            }
            lower.contains("tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 18)
                calendar.timeInMillis
            }
            lower.contains("next week") -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.timeInMillis
            }
            lower.contains("by monday") -> getNextDayOfWeek(Calendar.MONDAY)
            lower.contains("by friday") -> getNextDayOfWeek(Calendar.FRIDAY)
            else -> null
        }
    }

    private fun getNextDayOfWeek(dayOfWeek: Int): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 18)
        cal.set(Calendar.MINUTE, 0)
        return cal.timeInMillis
    }

    private fun cleanActionTitle(sentence: String): String {
        var clean = sentence.trim()
        val removePrefixes = listOf(
            "we need to", "you need to", "i will", "we will", "let's",
            "action item:", "action item is to", "please", "can you", "make sure to"
        )
        for (pref in removePrefixes) {
            if (clean.lowercase(Locale.ROOT).startsWith(pref)) {
                clean = clean.substring(pref.length).trim()
                break
            }
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun generateExecutiveSummary(sentences: List<ExtractedPoint>): String {
        if (sentences.isEmpty()) return "No content available."
        if (sentences.size <= 3) {
            return sentences.joinToString(" ") { it.text }
        }

        // Extractive TextRank / word-frequency scoring algorithm
        val wordFreq = mutableMapOf<String, Int>()
        val stopWords = setOf(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "with",
            "that", "this", "it", "of", "or", "as", "by", "from", "be", "are", "was", "were",
            "have", "has", "had", "do", "does", "did", "so", "if", "out", "up", "about"
        )

        for (s in sentences) {
            val words = s.text.lowercase(Locale.ROOT).split(Pattern.compile("\\W+"))
            for (w in words) {
                if (w.length > 3 && w !in stopWords) {
                    wordFreq[w] = (wordFreq[w] ?: 0) + 1
                }
            }
        }

        // Score sentences
        val scored = sentences.map { s ->
            val words = s.text.lowercase(Locale.ROOT).split(Pattern.compile("\\W+"))
            var score = 0
            for (w in words) {
                score += wordFreq[w] ?: 0
            }
            Pair(s, score)
        }

        val topSentences = scored.sortedByDescending { it.second }
            .take(3)
            .map { it.first }
            .sortedBy { it.timestampMs }

        return topSentences.joinToString(" ") { it.text }
    }

    private fun extractTopicTitle(sentences: List<ExtractedPoint>): String? {
        for (s in sentences) {
            val lower = s.text.lowercase(Locale.ROOT)
            if (lower.contains("meeting about") || lower.contains("discussing") || lower.contains("sync on")) {
                return s.text.take(45)
            }
        }
        val first = sentences.firstOrNull()?.text
        return if (first != null && first.length > 10) {
            first.take(40) + "..."
        } else null
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
