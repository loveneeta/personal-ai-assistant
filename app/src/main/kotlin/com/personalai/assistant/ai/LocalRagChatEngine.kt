package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TaskStatus
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

    private var pendingIntent: UserIntent? = null

    fun getPendingIntent(): UserIntent? = pendingIntent

    fun clearPendingIntent() {
        pendingIntent = null
    }

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
        val classified = IntentClassifier.classify(trimmed, pendingIntent)

        // 1. Negative Intent
        if (classified is UserIntent.NegativeRequest) {
            pendingIntent = null
            return ChatMessage(
                isUser = false,
                content = classified.explanation,
                citations = emptyList()
            )
        }

        // 2. Ambiguous or Contradictory Intent
        if (classified is UserIntent.AmbiguousOrContradictory) {
            return ChatMessage(
                isUser = false,
                content = "I cannot process this request: ${classified.reason}",
                citations = emptyList()
            )
        }

        // 3. User Confirmation on Pending Action
        if (classified is UserIntent.Confirmation) {
            val pending = pendingIntent
            pendingIntent = null

            if (pending == null) {
                return ChatMessage(
                    isUser = false,
                    content = "There is no pending action to confirm.",
                    citations = emptyList()
                )
            }

            if (!classified.isConfirmed) {
                return ChatMessage(
                    isUser = false,
                    content = "Action cancelled. No records were modified.",
                    citations = emptyList()
                )
            }

            // Confirmed execution
            return when (pending) {
                is UserIntent.CreateMeeting -> {
                    val title = pending.title ?: "Meeting"
                    val startMs = pending.startEpochMs ?: System.currentTimeMillis()
                    val newMeetingId = repository.insertMeeting(
                        MeetingEntity(
                            title = title,
                            startTimestamp = startMs,
                            summary = "Scheduled via voice confirmation: '$title'"
                        )
                    )
                    val formattedDate = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault()).format(Date(startMs))
                    ChatMessage(
                        isUser = false,
                        content = "Confirmed. I have scheduled '$title' for $formattedDate.",
                        citations = listOf(
                            Citation(type = "MEETING", referenceId = newMeetingId, title = title, snippet = "Scheduled for $formattedDate")
                        )
                    )
                }

                is UserIntent.CreateTask -> {
                    val title = pending.title ?: "New Task"
                    val newTaskId = repository.insertTask(
                        TaskEntity(
                            title = title,
                            status = TaskStatus.PENDING
                        )
                    )
                    ChatMessage(
                        isUser = false,
                        content = "Confirmed. Added task: '$title' to your pending list.",
                        citations = listOf(
                            Citation(type = "TASK", referenceId = newTaskId, title = title, snippet = "Status: PENDING")
                        )
                    )
                }

                is UserIntent.CreateNote -> {
                    val content = pending.content ?: ""
                    val newNoteId = repository.insertNote(
                        NoteEntity(
                            title = "Voice Note - ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())}",
                            content = content
                        )
                    )
                    ChatMessage(
                        isUser = false,
                        content = "Confirmed. Saved note into your local memory.",
                        citations = listOf(
                            Citation(type = "NOTE", referenceId = newNoteId, title = "Voice Note", snippet = content)
                        )
                    )
                }

                else -> {
                    ChatMessage(
                        isUser = false,
                        content = "Action completed.",
                        citations = emptyList()
                    )
                }
            }
        }

        // 4. Create Meeting Intent with Missing Field Extraction & Confirmation Gating
        if (classified is UserIntent.CreateMeeting) {
            if (classified.title.isNullOrBlank()) {
                return ChatMessage(
                    isUser = false,
                    content = "What is the title or subject of the meeting you would like to schedule?",
                    citations = emptyList()
                )
            }

            if (classified.startEpochMs == null) {
                return ChatMessage(
                    isUser = false,
                    content = "When would you like to schedule the meeting '${classified.title}'? Please specify a date and time.",
                    citations = emptyList()
                )
            }

            // Both required fields are present: require explicit confirmation before execution
            pendingIntent = classified
            val formattedDate = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault()).format(Date(classified.startEpochMs))
            return ChatMessage(
                isUser = false,
                content = "I have prepared the meeting: '${classified.title}' for $formattedDate. Would you like me to confirm and create this meeting?",
                citations = emptyList()
            )
        }

        // 5. Create Task Intent with Missing Field Extraction & Confirmation Gating
        if (classified is UserIntent.CreateTask) {
            if (classified.title.isNullOrBlank()) {
                return ChatMessage(
                    isUser = false,
                    content = "What task would you like me to add?",
                    citations = emptyList()
                )
            }

            // Require explicit confirmation before writing to database
            pendingIntent = classified
            return ChatMessage(
                isUser = false,
                content = "I have prepared the task: '${classified.title}'. Should I confirm and add this to your tasks?",
                citations = emptyList()
            )
        }

        // 6. Create Note Intent
        if (classified is UserIntent.CreateNote) {
            if (classified.content.isNullOrBlank()) {
                return ChatMessage(
                    isUser = false,
                    content = "What note would you like me to save?",
                    citations = emptyList()
                )
            }

            pendingIntent = classified
            return ChatMessage(
                isUser = false,
                content = "Would you like me to confirm and save this note: '${classified.content}'?",
                citations = emptyList()
            )
        }

        // 7. Information Queries (Tasks, Meetings, Notes, RAG)
        if (classified is UserIntent.InformationQuery) {
            return when (classified.queryType) {
                QueryType.TASKS -> handleTasksQuery()
                QueryType.MEETINGS -> handleMeetingsQuery()
                QueryType.NOTES -> handleNotesQuery()
                QueryType.GENERAL_RAG -> handleGeneralRagSearch(trimmed)
            }
        }

        return handleGeneralRagSearch(trimmed)
    }

    private suspend fun handleTasksQuery(): ChatMessage {
        val citations = mutableListOf<Citation>()
        val allTasks = repository.getAllTasks()
        val pending = allTasks.filter { !it.status.isCompleted }
        return if (pending.isNotEmpty()) {
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
            ChatMessage(
                isUser = false,
                content = sb.toString().trim(),
                citations = citations
            )
        } else if (allTasks.isNotEmpty()) {
            ChatMessage(
                isUser = false,
                content = "All ${allTasks.size} tasks in your workspace are marked as COMPLETED! Great job.",
                citations = emptyList()
            )
        } else {
            ChatMessage(
                isUser = false,
                content = "You have no action items recorded in your local memory yet. Tasks are automatically extracted from your meetings or voice notes.",
                citations = emptyList()
            )
        }
    }

    private suspend fun handleMeetingsQuery(): ChatMessage {
        val citations = mutableListOf<Citation>()
        val meetings = repository.getAllMeetings()
        return if (meetings.isNotEmpty()) {
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
            ChatMessage(
                isUser = false,
                content = sb.toString().trim(),
                citations = citations
            )
        } else {
            ChatMessage(
                isUser = false,
                content = "No meetings found in your local database yet. Recorded audio sessions can be converted into meeting minutes.",
                citations = emptyList()
            )
        }
    }

    private suspend fun handleNotesQuery(): ChatMessage {
        val citations = mutableListOf<Citation>()
        val notes = repository.getAllNotes()
        return if (notes.isNotEmpty()) {
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
            ChatMessage(
                isUser = false,
                content = sb.toString().trim(),
                citations = citations
            )
        } else {
            ChatMessage(
                isUser = false,
                content = "No personal notes found in your local database yet.",
                citations = emptyList()
            )
        }
    }

    private suspend fun handleGeneralRagSearch(trimmed: String): ChatMessage {
        val citations = mutableListOf<Citation>()
        val cleanTerms = trimmed.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        val candidateTerms = cleanTerms.filter { it.lowercase(Locale.ROOT) !in STOP_WORDS }
        val searchWords = if (candidateTerms.isNotEmpty()) candidateTerms else cleanTerms

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
                val status = if (tk.status.isCompleted) "[Completed]" else "[Pending]"
                sb.append("• **${tk.title}** $status\n")
                citations.add(
                    Citation(
                        type = "TASK",
                        referenceId = tk.id,
                        title = tk.title,
                        snippet = "Status: ${tk.status.name}"
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
