package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.RecordingEntity
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
        "will go with", "going with", "resolved", "final decision", "we should choose",
        // Hinglish & Hindi decision cues
        "final hua", "final kiya", "tay hua", "tay kiya", "faisla hua", "faisla kiya",
        "decide kiya", "decide hua", "agree kiya", "pakka hua", "pakka kiya", "manzoor hua",
        "sahmati hui", "fix kiya", "conclude hua", "nishchay kiya",
        // Devanagari
        "तय हुआ", "तय किया", "फैसला हुआ", "फैसला किया", "सहमति हुई", "अंतिम निर्णय", "मंजूर हुआ"
    )

    private val ACTION_CUES = listOf(
        "will do", "action item", "take care of", "need to", "needs to",
        "assigned to", "follow up", "make sure", "have to", "responsible for",
        "let's ensure", "can you", "please handle", "please check",
        // Hinglish & Hindi action cues
        "ready kar dena", "ready karna", "tayyar kar dena", "tayyar karna",
        "karna hai", "kar dena", "kar dena chahiye", "bhej dena", "send kar dena",
        "dekh lena", "bata dena", "karenge", "karega", "karegi", "sambhal lena",
        "share kar dena", "complete kar dena", "update kar dena", "kaam karna hai",
        // Devanagari
        "करना है", "कर देना", "भेज देना", "देख लेना", "तैयार करना", "तैयार कर देना", "साझा करना"
    )

    private val QUESTION_CUES = listOf(
        "risk", "blocker", "uncertain", "not sure", "how do we", "how can we",
        "what about", "who is", "when will", "could we", "is it possible",
        // Hinglish & Hindi question / blocker cues
        "kya hoga", "kaise hoga", "kaise karein", "kab tak", "kiska hai", "kisko",
        "kaun karega", "dikkat", "samasya", "rok",
        // Devanagari
        "क्या होगा", "कैसे होगा", "कैसे करें", "कब तक", "समस्या", "दिक्कत"
    )

    private val COMMON_NAMES = listOf(
        "Alex", "Rahul", "Priya", "Amit", "Neha", "Loveneet", "John", "Sarah", "David",
        "Michael", "Emily", "Vikram", "Anjali", "Suresh", "Pooja", "Arjun", "Karan"
    )

    private val ATTENDEE_PATTERNS = listOf(
        Pattern.compile("(?:meeting with|sync with|call with|joined by|attendees?:|present:)\\s+([A-Za-z, ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:hi|hello|hey|thanks|thank you)\\s+([A-Z][a-z]+)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([A-Z][a-z]+)\\s+(?:will|is going to|can you|ko bol dena|sambhal lega|bhej dega)\\b"),
        Pattern.compile("(?:with|aur)\\s+([A-Z][a-z]+)(?:\\s+and\\s+([A-Z][a-z]+))?\\b", Pattern.CASE_INSENSITIVE)
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

        // 1. Detect Attendees contextually to prevent hallucinating names
        val detectedAttendees = mutableSetOf<String>()
        val fullText = transcripts.joinToString(" ") { it.text }

        for (pattern in ATTENDEE_PATTERNS) {
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                val match = matcher.group(1)?.trim()
                if (!match.isNullOrBlank()) {
                    for (candidate in match.split(Pattern.compile("[,&]"))) {
                        val clean = candidate.trim()
                        if (clean.length in 2..25 && clean.all { it.isLetter() || it.isWhitespace() }) {
                            val recognized = COMMON_NAMES.firstOrNull { it.equals(clean, ignoreCase = true) }
                            if (recognized != null) {
                                detectedAttendees.add(recognized)
                            } else if (clean.firstOrNull()?.isUpperCase() == true && !isCommonEnglishWord(clean)) {
                                detectedAttendees.add(clean)
                            }
                        }
                    }
                }
                if (matcher.groupCount() >= 2) {
                    val second = matcher.group(2)?.trim()
                    if (!second.isNullOrBlank()) {
                        val recognized = COMMON_NAMES.firstOrNull { it.equals(second, ignoreCase = true) }
                        if (recognized != null) {
                            detectedAttendees.add(recognized)
                        } else if (second.firstOrNull()?.isUpperCase() == true && !isCommonEnglishWord(second)) {
                            detectedAttendees.add(second)
                        }
                    }
                }
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
        // Look for explicitly known attendees first
        for (name in knownAttendees) {
            if (sentence.contains(name, ignoreCase = true)) {
                return name
            }
        }
        // Look for assignment cues like "assigned to [Name]" or "[Name] will"
        val assignPattern = Pattern.compile("(?:assigned to|assign to|for)\\s+([A-Z][a-z]+)", Pattern.CASE_INSENSITIVE)
        val m = assignPattern.matcher(sentence)
        if (m.find()) {
            val found = m.group(1)
            if (!found.isNullOrBlank() && !isCommonEnglishWord(found)) {
                return found
            }
        }
        // Check if common name is preceded or followed by action verb
        for (name in COMMON_NAMES) {
            val namePattern = Pattern.compile("\\b$name\\b", Pattern.CASE_INSENSITIVE)
            if (namePattern.matcher(sentence).find()) {
                val lower = sentence.lowercase(Locale.ROOT)
                val nLower = name.lowercase(Locale.ROOT)
                if (lower.contains("$nLower will") || lower.contains("$nLower should") ||
                    lower.contains("$nLower to") || lower.contains("ask $nLower") ||
                    lower.contains("$nLower ko") || lower.contains("$nLower sambhal")
                ) {
                    return name
                }
            }
        }
        return null
    }

    fun extractDueDate(sentence: String): Long? {
        val lower = sentence.lowercase(Locale.ROOT)
        val calendar = Calendar.getInstance()

        // 1. Parse specific time if mentioned (e.g. "before five PM", "by 5 PM", "at 4 PM")
        var targetHour = 18 // Default EOD 6 PM
        var targetMinute = 0

        val timePattern = Pattern.compile(
            "(?:before|by|at)?\\s*(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s*(pm|am|baje)?",
            Pattern.CASE_INSENSITIVE
        )
        val timeMatcher = timePattern.matcher(lower)
        while (timeMatcher.find()) {
            val rawNum = timeMatcher.group(1)?.lowercase(Locale.ROOT) ?: continue
            val amPm = timeMatcher.group(2)?.lowercase(Locale.ROOT)
            val parsedHour = parseHourString(rawNum)
            if (parsedHour != null) {
                targetHour = when {
                    amPm == "pm" && parsedHour < 12 -> parsedHour + 12
                    amPm == "am" && parsedHour == 12 -> 0
                    amPm == "baje" && parsedHour in 1..6 -> parsedHour + 12 // Spoken Hindi business hours
                    amPm == null && parsedHour in 1..6 -> parsedHour + 12 // Default to PM for afternoon numbers
                    else -> parsedHour
                }
                break
            }
        }

        // 2. Parse relative date
        return when {
            lower.contains("tomorrow") || lower.contains("kal") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, targetHour)
                calendar.set(Calendar.MINUTE, targetMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            lower.contains("parso") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 2)
                calendar.set(Calendar.HOUR_OF_DAY, targetHour)
                calendar.set(Calendar.MINUTE, targetMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            lower.contains("today") || lower.contains("aaj") || lower.contains("by end of day") || lower.contains("eod") -> {
                calendar.set(Calendar.HOUR_OF_DAY, targetHour)
                calendar.set(Calendar.MINUTE, targetMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            lower.contains("next week") -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, targetHour)
                calendar.set(Calendar.MINUTE, targetMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            lower.contains("by monday") || lower.contains("monday") -> getNextDayOfWeek(Calendar.MONDAY, targetHour)
            lower.contains("by tuesday") || lower.contains("tuesday") -> getNextDayOfWeek(Calendar.TUESDAY, targetHour)
            lower.contains("by wednesday") || lower.contains("wednesday") -> getNextDayOfWeek(Calendar.WEDNESDAY, targetHour)
            lower.contains("by thursday") || lower.contains("thursday") -> getNextDayOfWeek(Calendar.THURSDAY, targetHour)
            lower.contains("by friday") || lower.contains("friday") -> getNextDayOfWeek(Calendar.FRIDAY, targetHour)
            else -> null
        }
    }

    private fun parseHourString(raw: String): Int? {
        raw.toIntOrNull()?.let { return if (it in 1..24) it else null }
        return when (raw) {
            "one", "ek" -> 1
            "two", "do" -> 2
            "three", "teen" -> 3
            "four", "chaar" -> 4
            "five", "paanch" -> 5
            "six", "che" -> 6
            "seven", "saat" -> 7
            "eight", "aath" -> 8
            "nine", "nau" -> 9
            "ten", "das" -> 10
            "eleven", "gyarah" -> 11
            "twelve", "barah" -> 12
            else -> null
        }
    }

    private fun getNextDayOfWeek(dayOfWeek: Int, targetHour: Int = 18): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, targetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun cleanActionTitle(sentence: String): String {
        var clean = sentence.trim()

        // Handle compound sentences with introductory context (e.g. "Kal customer ki meeting hai, please quotation ready kar dena before five PM.")
        val commaIdx = clean.indexOf(',')
        if (commaIdx in 1..45) {
            val afterComma = clean.substring(commaIdx + 1).trim()
            val lowerAfter = afterComma.lowercase(Locale.ROOT)
            if (ACTION_CUES.any { lowerAfter.contains(it) }) {
                clean = afterComma
            }
        }

        val removePrefixes = listOf(
            "we need to", "you need to", "i will", "we will", "let's",
            "action item:", "action item is to", "please", "can you", "make sure to",
            "kripya", "kripya karke", "ek kaam karo", "ek kaam karna"
        )
        for (pref in removePrefixes) {
            if (clean.lowercase(Locale.ROOT).startsWith(pref)) {
                clean = clean.substring(pref.length).trim()
                break
            }
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun isCommonEnglishWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT)
        val common = setOf(
            "the", "this", "that", "there", "then", "team", "client", "customer",
            "company", "project", "product", "meeting", "sync", "call", "all",
            "everyone", "somebody", "nobody", "someone", "action", "today", "tomorrow"
        )
        return lower in common
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
            "have", "has", "had", "do", "does", "did", "so", "if", "out", "up", "about",
            "hai", "aur", "ki", "ka", "ke", "mein", "par", "se", "ko", "bhi"
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
            if (lower.contains("meeting about") || lower.contains("discussing") || lower.contains("sync on") ||
                lower.contains("meeting regarding") || lower.contains("discussion on")
            ) {
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
