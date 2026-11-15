package com.personalai.assistant.notifications

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Natural language conversational summarizer for incoming communications.
 * Generates concise spoken audio briefings and structured text for JARVIS.
 */
object NotificationSummarizer {

    /**
     * Generates a conversational executive briefing of recent messages.
     */
    fun summarizeRecent(messages: List<IncomingMessageRecord>): String {
        if (messages.isEmpty()) {
            return "You have no new incoming messages or unread notifications, Commander."
        }

        val byApp = messages.groupBy { it.appDisplayName }
        val sb = StringBuilder()
        sb.append("Here is your communication briefing, Commander: ")

        byApp.forEach { (app, list) ->
            sb.append("\n• In $app: ${list.size} message${if (list.size > 1) "s" else ""}.")
            val topSenders = list.groupBy { it.sender }.map { "${it.key} (${it.value.size})" }.take(3).joinToString(", ")
            sb.append(" From $topSenders.")
            val latest = list.first()
            sb.append(" Latest from ${latest.sender}: \"${latest.messageText.take(80)}\".")
        }

        return sb.toString()
    }

    /**
     * Generates a summary for messages from a specific sender (e.g. WhatsApp contact).
     */
    fun summarizeFromSender(sender: String, messages: List<IncomingMessageRecord>): String {
        if (messages.isEmpty()) {
            return "I found no recent messages from $sender, Commander."
        }

        val count = messages.size
        val sb = StringBuilder()
        sb.append("You have $count message${if (count > 1) "s" else ""} from $sender:")

        messages.take(5).forEachIndexed { idx, m ->
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(m.timestamp))
            sb.append("\n${idx + 1}. [$time] \"${m.messageText}\"")
        }

        return sb.toString()
    }
}
