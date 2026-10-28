package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Citation(
    val type: String, // "RECORDING", "MEETING", "TASK", "NOTE"
    val referenceId: Long,
    val title: String,
    val offsetMs: Long? = null,
    val snippet: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val citations: List<Citation> = emptyList()
)

class LocalRagChatEngine(private val repository: AppRepository) {

    suspend fun answerQuery(query: String): ChatMessage {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val citations = mutableListOf<Citation>()

        // 1. Check for specific intent: Listing pending tasks
        if (lower.contains("task") || lower.contains("to do") || lower.contains("action item")) {
            val allTasks = repository.getAllTasks()
            val pending = allTasks.filter { it.status != "COMPLETED" }
            if (pending.isNotEmpty()) {
                val sb = StringBuilder("Here are your pending action items:\n\n")
                for ((idx, task) in pending.withIndex()) {
                    val assignee = if (!task.assignedPerson.isNullOrBlank()) " (Assigned to: ${task.assignedPerson})" else ""
                    val due = if (task.dueDate != null) {
                        " • Due: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(task.dueDate))}"
                    } else ""
                    sb.append("${idx + 1}. **${task.title}**$assignee$due\n")
                    citations.add(
                        Citation(
                            type = "TASK",
                            referenceId = task.id,
                            title = task.title,
                            snippet = task.description
                        )
                    )
                }
                return ChatMessage(
                    isUser = false,
                    content = sb.toString().trim(),
                    citations = citations
                )
            } else if (allTasks.isNotEmpty()) {
                return ChatMessage(
                    isUser = false,
                    content = "All ${allTasks.size} tasks in your workspace are marked as COMPLETED! Great job.",
                    citations = emptyList()
                )
            }
        }

        // 2. Check for specific intent: Recent meetings
        if (lower.contains("meeting") || lower.contains("standup") || lower.contains("sync")) {
            val meetings = repository.getAllMeetings()
            if (meetings.isNotEmpty()) {
                val recent = meetings.take(3)
                val sb = StringBuilder("Here are your recent meetings:\n\n")
                for (m in recent) {
                    val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(m.startTimestamp))
                    sb.append("• **${m.title}** ($date)\n")
                    if (!m.summary.isNullOrBlank()) {
                        sb.append("  Summary: ${m.summary.take(150)}...\n")
                    }
                    citations.add(
                        Citation(
                            type = "MEETING",
                            referenceId = m.id,
                            title = m.title,
                            snippet = m.summary
                        )
                    )
                }
                return ChatMessage(
                    isUser = false,
                    content = sb.toString().trim(),
                    citations = citations
                )
            }
        }

        // 3. General RAG Search: FTS across transcripts
        val cleanTerms = trimmed.replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(" ")
            .filter { it.length > 3 }
        val searchWord = cleanTerms.firstOrNull() ?: trimmed

        val matchedTranscripts = repository.searchTranscripts(searchWord).take(5)
        val matchedNotes = repository.searchNotes(searchWord).take(3)
        val matchedMeetings = repository.searchMeetings(searchWord).take(3)

        if (matchedTranscripts.isEmpty() && matchedNotes.isEmpty() && matchedMeetings.isEmpty()) {
            return ChatMessage(
                isUser = false,
                content = "I searched your recorded transcripts, meetings, and personal notes, but couldn't find any information matching \"$trimmed\".\n\nTip: You can ask about recorded meetings, action items, or specific topics spoken in your audio sessions.",
                citations = emptyList()
            )
        }

        val sb = StringBuilder()
        sb.append("Based on your local voice memories and notes:\n\n")

        if (matchedTranscripts.isNotEmpty()) {
            sb.append("### Spoken Transcripts\n")
            for (t in matchedTranscripts) {
                val timeStr = formatTime(t.startOffsetMs)
                sb.append("• [At $timeStr] \"${t.text.trim()}\"\n")
                citations.add(
                    Citation(
                        type = "RECORDING",
                        referenceId = t.recordingId,
                        title = "Recording #${t.recordingId}",
                        offsetMs = t.startOffsetMs,
                        snippet = t.text
                    )
                )
            }
            sb.append("\n")
        }

        if (matchedMeetings.isNotEmpty()) {
            sb.append("### Relevant Meetings\n")
            for (m in matchedMeetings) {
                sb.append("• **${m.title}:** ${m.summary ?: "No summary"}\n")
                citations.add(
                    Citation(
                        type = "MEETING",
                        referenceId = m.id,
                        title = m.title,
                        snippet = m.summary
                    )
                )
            }
            sb.append("\n")
        }

        if (matchedNotes.isNotEmpty()) {
            sb.append("### Relevant Notes\n")
            for (n in matchedNotes) {
                sb.append("• **${n.title}:** ${n.content.take(120)}...\n")
                citations.add(
                    Citation(
                        type = "NOTE",
                        referenceId = n.id,
                        title = n.title,
                        snippet = n.content
                    )
                )
            }
        }

        return ChatMessage(
            isUser = false,
            content = sb.toString().trim(),
            citations = citations
        )
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
