package com.personalai.assistant.ai

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

enum class QueryType {
    TASKS,
    MEETINGS,
    NOTES,
    GENERAL_RAG
}

sealed class UserIntent {
    data class CreateMeeting(
        val title: String?,
        val startEpochMs: Long?,
        val rawTimeExpression: String?,
        val participants: List<String> = emptyList()
    ) : UserIntent()

    data class CreateTask(
        val title: String?,
        val dueDate: Long? = null,
        val rawDueExpression: String? = null
    ) : UserIntent()

    data class CreateNote(
        val content: String?
    ) : UserIntent()

    data class InformationQuery(
        val queryType: QueryType,
        val query: String
    ) : UserIntent()

    data class NegativeRequest(
        val targetAction: String,
        val explanation: String
    ) : UserIntent()

    data class AmbiguousOrContradictory(
        val reason: String
    ) : UserIntent()

    data class Confirmation(
        val isConfirmed: Boolean
    ) : UserIntent()

    data class Unknown(val rawText: String) : UserIntent()
}

/**
 * Intelligent AI Intent Classifier.
 *
 * Separates voice intent classification from database execution.
 * Guarantees:
 * 1. Strict separation of intent parsing from record mutations.
 * 2. Extracts required fields (title, timestamp, description) without inventing details.
 * 3. Detects missing fields and informs calling components to query for clarification.
 * 4. Recognizes negative requests ("I don't want to schedule a meeting").
 * 5. Identifies and rejects ambiguous and contradictory instructions.
 * 6. Handles multilingual English and Hinglish expressions.
 */
object IntentClassifier {

    private val CONFIRMATION_AFFIRMATIVE = setOf(
        "yes", "confirm", "proceed", "sure", "yeah", "yep", "do it", "ok", "okay",
        "haan", "haan kar do", "haan kardo", "sahi hai", "theek hai", "yes please", "yes confirm"
    )

    private val CONFIRMATION_NEGATIVE = setOf(
        "no", "cancel", "don't", "stop", "never mind", "nah", "nope",
        "nahi", "mat karo", "cancel karo", "nahi karna", "abort"
    )

    fun classify(query: String, pendingIntent: UserIntent? = null): UserIntent {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("[.,?!]+$"), "")
            .trim()

        val normalized = lower.replace(Regex("[,;?!]"), " ").replace(Regex("\\s+"), " ").trim()

        if (normalized.isEmpty()) {
            return UserIntent.Unknown(trimmed)
        }

        // 1. Pending Intent Confirmation / Cancellation
        if (pendingIntent != null) {
            if (CONFIRMATION_AFFIRMATIVE.contains(normalized) ||
                normalized.startsWith("yes ") || normalized == "yes" ||
                normalized.startsWith("haan ") || normalized == "haan" ||
                normalized.contains("confirm") || normalized.contains("proceed")
            ) {
                return UserIntent.Confirmation(isConfirmed = true)
            }
            if (CONFIRMATION_NEGATIVE.contains(normalized) ||
                normalized.startsWith("no ") || normalized == "no" ||
                normalized.startsWith("nahi ") || normalized == "nahi" ||
                normalized.contains("cancel") || normalized.contains("don't")
            ) {
                return UserIntent.Confirmation(isConfirmed = false)
            }
        }

        // 2. Ambiguous and Contradictory Instructions Detection
        val hasSchedule = lower.contains("schedule") || lower.contains("create meeting") || lower.contains("set up a meeting")
        val hasCancelOrDont = lower.contains("cancel") || lower.contains("don't") || lower.contains("dont") || lower.contains("do not") || lower.contains("mat")
        val isExplicitNegativeStatement = lower.startsWith("don't") || lower.startsWith("dont") ||
                lower.startsWith("do not") || lower.startsWith("i don't") || lower.startsWith("i dont") ||
                lower.contains("don't want to") || lower.contains("dont want to") || lower.contains("meeting nahi chahiye")

        if (hasSchedule && hasCancelOrDont && !isExplicitNegativeStatement) {
            return UserIntent.AmbiguousOrContradictory(
                reason = "Contradictory instruction: request contains both scheduling and cancellation commands."
            )
        }

        // Past time contradiction (e.g. scheduling a meeting yesterday)
        if (hasSchedule && (lower.contains("yesterday") || lower.contains("beete kal") || lower.contains("last week"))) {
            return UserIntent.AmbiguousOrContradictory(
                reason = "Cannot schedule an event in the past (yesterday/last week)."
            )
        }

        // 3. Negative Requests Detection
        val negativeMeetingPatterns = listOf(
            "don't want to schedule", "dont want to schedule", "do not schedule",
            "don't schedule", "dont schedule", "no meeting", "meeting nahi chahiye",
            "mat schedule karna", "nahi schedule karni", "nahi schedule karna",
            "cancel meeting creation", "don't create meeting", "dont create meeting"
        )
        for (pattern in negativeMeetingPatterns) {
            if (lower.contains(pattern)) {
                return UserIntent.NegativeRequest(
                    targetAction = "meeting",
                    explanation = "Understood. I will not schedule any meeting."
                )
            }
        }

        val negativeTaskPatterns = listOf(
            "don't want to add task", "do not add task", "don't add task",
            "dont add task", "task mat banao", "task nahi chahiye", "don't create task"
        )
        for (pattern in negativeTaskPatterns) {
            if (lower.contains(pattern)) {
                return UserIntent.NegativeRequest(
                    targetAction = "task",
                    explanation = "Understood. I will not add any task."
                )
            }
        }

        // 4. Create Meeting Intent
        val isMeetingSchedule = (lower.contains("schedule") && lower.contains("meeting")) ||
                lower.contains("meeting schedule") ||
                lower.contains("meeting set") ||
                lower.contains("meeting create") ||
                lower.contains("create a meeting") ||
                lower.contains("create meeting") ||
                lower.contains("set up a meeting") ||
                lower.contains("baithak schedule")

        if (isMeetingSchedule) {
            return extractMeetingDetails(trimmed, lower)
        }

        // 5. Create Task Intent
        val isTaskCreate = lower.startsWith("add task") ||
                lower.startsWith("create task") ||
                lower.startsWith("task:") ||
                lower.contains("task add karo") ||
                lower.startsWith("action item:") ||
                lower.startsWith("remind me to")

        if (isTaskCreate) {
            val cleanTitle = trimmed
                .replaceFirst(Regex("^(add task|create task|task:|task add karo|action item:|remind me to)", RegexOption.IGNORE_CASE), "")
                .trim()

            val title = if (cleanTitle.isBlank()) null else cleanTitle
            return UserIntent.CreateTask(title = title)
        }

        // 6. Create Note Intent
        val isNoteCreate = lower.contains("save kar do") ||
                lower.contains("save note") ||
                lower.contains("note this down") ||
                lower.contains("note down") ||
                lower.startsWith("take a note")

        if (isNoteCreate) {
            val cleanNote = trimmed
                .replaceFirst(Regex("^(save note|note this down|note down|note:|save kar do|take a note)", RegexOption.IGNORE_CASE), "")
                .trim()

            val content = if (cleanNote.isBlank()) null else cleanNote
            return UserIntent.CreateNote(content = content)
        }

        // 7. Information Queries
        if (lower.contains("task") || lower.contains("to do") || lower.contains("action item") || lower.contains("kaam")) {
            return UserIntent.InformationQuery(queryType = QueryType.TASKS, query = trimmed)
        }

        if (lower.contains("meeting") || lower.contains("standup") || lower.contains("sync") || lower.contains("baithak")) {
            return UserIntent.InformationQuery(queryType = QueryType.MEETINGS, query = trimmed)
        }

        if (lower.contains("notes") || lower.contains("note") || lower.contains("memo")) {
            return UserIntent.InformationQuery(queryType = QueryType.NOTES, query = trimmed)
        }

        // 8. General Knowledge / RAG Search
        if (trimmed.isNotBlank()) {
            return UserIntent.InformationQuery(queryType = QueryType.GENERAL_RAG, query = trimmed)
        }

        return UserIntent.Unknown(trimmed)
    }

    private fun extractMeetingDetails(trimmed: String, lower: String): UserIntent.CreateMeeting {
        var rawTime: String? = null
        var epochMs: Long? = null

        val cal = Calendar.getInstance()
        var hasExplicitDate = false
        var hasExplicitTime = false

        // Date detection
        if (lower.contains("tomorrow") || lower.contains("kal")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            hasExplicitDate = true
            rawTime = if (lower.contains("kal")) "kal" else "tomorrow"
        } else if (lower.contains("today") || lower.contains("aaj")) {
            hasExplicitDate = true
            rawTime = if (lower.contains("aaj")) "aaj" else "today"
        }

        // Time detection via regex: e.g. "3pm", "3:30 pm", "10 am", "4 baje"
        val timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)", Pattern.CASE_INSENSITIVE)
        val matcher = timePattern.matcher(lower)
        if (matcher.find()) {
            hasExplicitTime = true
            val hourStr = matcher.group(1) ?: "10"
            val minStr = matcher.group(2) ?: "00"
            val meridiem = matcher.group(3)?.lowercase(Locale.ROOT) ?: "am"

            var hour = hourStr.toIntOrNull() ?: 10
            val min = minStr.toIntOrNull() ?: 0

            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0

            // If "baje" without am/pm, infer morning vs afternoon
            if (meridiem == "baje") {
                if (lower.contains("subah") || lower.contains("morning")) {
                    if (hour == 12) hour = 0
                } else if (lower.contains("shaam") || lower.contains("evening") || lower.contains("dophar") || hour in 1..7) {
                    if (hour < 12) hour += 12
                }
            }

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            rawTime = (rawTime?.let { "$it " } ?: "") + matcher.group(0)
        } else if (lower.contains("morning") || lower.contains("subah")) {
            hasExplicitTime = true
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            rawTime = (rawTime?.let { "$it " } ?: "") + "morning"
        } else if (lower.contains("afternoon") || lower.contains("dophar")) {
            hasExplicitTime = true
            cal.set(Calendar.HOUR_OF_DAY, 14)
            cal.set(Calendar.MINUTE, 30)
            cal.set(Calendar.SECOND, 0)
            rawTime = (rawTime?.let { "$it " } ?: "") + "afternoon"
        } else if (lower.contains("evening") || lower.contains("shaam")) {
            hasExplicitTime = true
            cal.set(Calendar.HOUR_OF_DAY, 17)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            rawTime = (rawTime?.let { "$it " } ?: "") + "evening"
        }

        if (hasExplicitDate || hasExplicitTime) {
            epochMs = cal.timeInMillis
        }

        // Title extraction
        // Check for "with <Person/Team>", "for <Topic>", "titled <Title>", "about <Topic>"
        var title: String? = null
        val titleRegexes = listOf(
            Regex("(?:schedule|create|set up|book)\\s+(?:a\\s+)?meeting\\s+(?:with|for|about|titled)\\s+([^,.]+?)(?:\\s+(?:tomorrow|today|kal|aaj|at|on|subah|shaam|\\d{1,2}\\s*(?:am|pm|baje))|$)", RegexOption.IGNORE_CASE),
            Regex("(?:meeting\\s+with|meeting\\s+for|meeting\\s+about)\\s+([^,.]+?)(?:\\s+(?:tomorrow|today|kal|aaj|at|on|subah|shaam|\\d{1,2}\\s*(?:am|pm|baje))|$)", RegexOption.IGNORE_CASE),
            Regex("([^,.]+?)\\s+(?:ki\\s+meeting|meeting\\s+schedule)", RegexOption.IGNORE_CASE)
        )

        for (rx in titleRegexes) {
            val match = rx.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty() && !candidate.equals("a", ignoreCase = true) && !candidate.equals("the", ignoreCase = true)) {
                    title = candidate
                    break
                }
            }
        }

        // Participants extraction (e.g. "with John and Alice")
        val participants = mutableListOf<String>()
        val withMatch = Regex("with\\s+([A-Za-z]+(?:\\s+and\\s+[A-Za-z]+)?)", RegexOption.IGNORE_CASE).find(trimmed)
        if (withMatch != null) {
            val pStr = withMatch.groupValues[1]
            pStr.split(Regex("\\s+and\\s+|,")).forEach { p ->
                val cleanP = p.trim()
                if (cleanP.isNotEmpty()) participants.add(cleanP)
            }
        }

        return UserIntent.CreateMeeting(
            title = title,
            startEpochMs = epochMs,
            rawTimeExpression = rawTime,
            participants = participants
        )
    }
}
