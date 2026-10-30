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
import java.util.regex.Pattern

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

    private val STOP_WORDS = setOf(
        "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
        "tell", "show", "give", "find", "search", "about", "with", "from", "that", "this",
        "have", "has", "had", "does", "did", "will", "would", "could", "should", "there",
        "their", "then", "them", "some", "more", "also", "just", "very", "into", "been",
        "the", "and", "for", "are", "was", "were", "check", "please", "summary", "details",
        // Hinglish & Hindi stop words
        "kya", "kaise", "kisko", "kiska", "kab", "kahan", "kyun", "batao", "dikhao",
        "hai", "hain", "tha", "the", "thi", "hoga", "hogi", "hoge", "aur", "par", "mein",
        "se", "ko", "ki", "ka", "ke", "karo", "karein", "bhi", "yeh", "woh"
    )

    suspend fun answerQuery(query: String): ChatMessage {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val citations = mutableListOf<Citation>()

        // Protocol A: Meeting Scheduling Intent (English / Hinglish)
        if ((lower.contains("schedule") && lower.contains("meeting")) ||
            lower.contains("meeting schedule") || lower.contains("meeting set") || lower.contains("meeting create")
        ) {
            val cal = java.util.Calendar.getInstance()
            if (lower.contains("kal") || lower.contains("tomorrow")) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            if (lower.contains("morning") || lower.contains("subah")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
                cal.set(java.util.Calendar.MINUTE, 0)
            } else if (lower.contains("afternoon") || lower.contains("dophar")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 14)
                cal.set(java.util.Calendar.MINUTE, 30)
            } else if (lower.contains("evening") || lower.contains("shaam")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 17)
                cal.set(java.util.Calendar.MINUTE, 0)
            }

            val meetingTitle = "Discussion & Strategy Sync"
            val newMeetingId = repository.insertMeeting(
                MeetingEntity(
                    title = meetingTitle,
                    startTimestamp = cal.timeInMillis,
                    summary = "Voice-scheduled protocol: '$trimmed'"
                )
            )
            val formattedDate = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault()).format(Date(cal.timeInMillis))
            return ChatMessage(
                isUser = false,
                content = "Protocol executed, Commander. I have scheduled '$meetingTitle' for $formattedDate.",
                citations = listOf(
                    Citation(type = "MEETING", referenceId = newMeetingId, title = meetingTitle, snippet = "Scheduled for $formattedDate")
                )
            )
        }

        // Protocol B: Task / Action Item Creation
        if (lower.startsWith("add task") || lower.startsWith("create task") || lower.startsWith("task:") ||
            lower.contains("task add karo") || lower.contains("action item:")
        ) {
            val cleanTitle = trimmed
                .replaceFirst(Regex("^(add task|create task|task:|task add karo|action item:)", RegexOption.IGNORE_CASE), "")
                .trim().ifEmpty { "Follow-up Action" }

            val newTaskId = repository.insertTask(
                TaskEntity(
                    title = cleanTitle,
                    status = "PENDING"
                )
            )
            return ChatMessage(
                isUser = false,
                content = "Action item recorded. Added task: '$cleanTitle' to your pending list.",
                citations = listOf(
                    Citation(type = "TASK", referenceId = newTaskId, title = cleanTitle, snippet = "Status: PENDING")
                )
            )
        }

        // Protocol C: Memory Note Persistence
        if (lower.contains("save kar do") || lower.contains("save note") || lower.contains("note this") || lower.contains("note down")) {
            val cleanNote = trimmed
                .replaceFirst(Regex("^(save note|note this down|note down|note:|save kar do)", RegexOption.IGNORE_CASE), "")
                .trim().ifEmpty { trimmed }

            val newNoteId = repository.insertNote(
                NoteEntity(
                    title = "Voice Note - ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())}",
                    content = cleanNote
                )
            )
            return ChatMessage(
                isUser = false,
                content = "I have saved this note into your local memory.",
                citations = listOf(
                    Citation(type = "NOTE", referenceId = newNoteId, title = "Voice Note", snippet = cleanNote)
                )
            )
        }

        // 1. Check for specific intent: Listing pending tasks
        if (lower.contains("task") || lower.contains("to do") || lower.contains("action item") || lower.contains("kaam")) {
            val allTasks = repository.getAllTasks()
            val pending = allTasks.filter { it.status != "COMPLETED" }
            if (pending.isNotEmpty()) {
                val sb = StringBuilder("Here are your pending action items (${pending.size}):\n\n")
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
                            snippet = task.description ?: "Status: ${task.status}"
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
            } else {
                return ChatMessage(
                    isUser = false,
                    content = "You have no action items recorded in your local memory yet. Tasks are automatically extracted from your meetings or voice notes.",
                    citations = emptyList()
                )
            }
        }

        // 2. Check for specific intent: Recent meetings
        if (lower.contains("meeting") || lower.contains("standup") || lower.contains("sync") || lower.contains("baithak")) {
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
            } else {
                return ChatMessage(
                    isUser = false,
                    content = "No meetings found in your local database yet. Recorded audio sessions can be converted into meeting minutes.",
                    citations = emptyList()
                )
            }
        }

        // 3. Check for specific intent: Notes
        if (lower.contains("notes") || lower.contains("note") || lower.contains("memo")) {
            val notes = repository.getAllNotes()
            if (notes.isNotEmpty()) {
                val recent = notes.take(4)
                val sb = StringBuilder("Here are your recent personal notes:\n\n")
                for (n in recent) {
                    val date = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(n.createdAt))
                    sb.append("• **${n.title}** ($date): ${n.content.take(120)}...\n")
                    citations.add(
                        Citation(
                            type = "NOTE",
                            referenceId = n.id,
                            title = n.title,
                            snippet = n.content
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

        // 4. General RAG Search across transcripts, notes, and meetings
        // Preserves both Latin letters/digits and Unicode Devanagari script (\p{L}\p{N})
        val cleanTerms = trimmed.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        val candidateTerms = cleanTerms.filter { it.lowercase(Locale.ROOT) !in STOP_WORDS }
        val searchWords = if (candidateTerms.isNotEmpty()) candidateTerms else cleanTerms
        val primaryTerm = searchWords.firstOrNull() ?: trimmed

        val matchedTranscripts = mutableListOf<TranscriptEntity>()
        val matchedNotes = mutableListOf<NoteEntity>()
        val matchedMeetings = mutableListOf<MeetingEntity>()
        val matchedTasks = mutableListOf<TaskEntity>()

        for (term in searchWords.take(3)) {
            val tr = repository.searchTranscripts(term)
            for (t in tr) {
                if (matchedTranscripts.none { it.id == t.id }) matchedTranscripts.add(t)
            }
            val nt = repository.searchNotes(term)
            for (n in nt) {
                if (matchedNotes.none { it.id == n.id }) matchedNotes.add(n)
            }
            val mt = repository.searchMeetings(term)
            for (m in mt) {
                if (matchedMeetings.none { it.id == m.id }) matchedMeetings.add(m)
            }
            val tk = repository.searchTasks(term)
            for (t in tk) {
                if (matchedTasks.none { it.id == t.id }) matchedTasks.add(t)
            }
        }

        if (matchedTranscripts.isEmpty() && matchedNotes.isEmpty() && matchedMeetings.isEmpty() && matchedTasks.isEmpty()) {
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
            for (t in matchedTranscripts.take(5)) {
                val timeStr = formatTime(t.startOffsetMs)
                sb.append("• [At $timeStr in Recording #${t.recordingId}] \"${t.text.trim()}\"\n")
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
            for (m in matchedMeetings.take(3)) {
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

        if (matchedTasks.isNotEmpty()) {
            sb.append("### Relevant Tasks\n")
            for (tk in matchedTasks.take(3)) {
                val status = if (tk.status == "COMPLETED") "[Completed]" else "[Pending]"
                sb.append("• **${tk.title}** $status\n")
                citations.add(
                    Citation(
                        type = "TASK",
                        referenceId = tk.id,
                        title = tk.title,
                        snippet = "Status: ${tk.status}"
                    )
                )
            }
            sb.append("\n")
        }

        if (matchedNotes.isNotEmpty()) {
            sb.append("### Relevant Notes\n")
            for (n in matchedNotes.take(3)) {
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
