package com.personalai.assistant.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.LocalRagChatEngine
import com.personalai.assistant.ai.OpenJarvisManager
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val repository: AppRepository,
    private val context: Context? = null
) : ViewModel() {

    private val chatEngine = LocalRagChatEngine(repository)

    private val _isOpenJarvisActive = MutableStateFlow(
        context?.let { OpenJarvisManager.isEnabled(it) } ?: false
    )
    val isOpenJarvisActive: StateFlow<Boolean> = _isOpenJarvisActive.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                content = if (context != null && OpenJarvisManager.isEnabled(context)) {
                    "J.A.R.V.I.S. Neural Bridge connected to OpenJarvis. Ready to answer using NVIDIA Nemotron reasoning grounded in your local memory."
                } else {
                    "Hello! I am your 100% offline Personal AI Assistant. Ask me anything about your recorded meetings, action items, or notes. I answer strictly based on your local memory."
                }
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refreshOpenJarvisState() {
        context?.let {
            _isOpenJarvisActive.value = OpenJarvisManager.isEnabled(it)
        }
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(isUser = true, content = trimmed)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val openJarvisEnabled = context != null && OpenJarvisManager.isEnabled(context)
                if (openJarvisEnabled) {
                    val localContext = buildGroundingContext(trimmed)
                    val result = OpenJarvisManager.query(context!!, trimmed, localContext)
                    if (result.isSuccess) {
                        _messages.value = _messages.value + ChatMessage(
                            isUser = false,
                            content = result.getOrThrow()
                        )
                    } else {
                        // Fall back to local RAG if OpenJarvis is offline/unreachable
                        val offlineFallback = chatEngine.answerQuery(trimmed)
                        val note = "*(OpenJarvis offline: ${result.exceptionOrNull()?.message ?: "unreachable"}, switched to on-device RAG)*\n\n"
                        _messages.value = _messages.value + offlineFallback.copy(
                            content = note + offlineFallback.content
                        )
                    }
                } else {
                    val assistantMsg = chatEngine.answerQuery(trimmed)
                    _messages.value = _messages.value + assistantMsg
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    isUser = false,
                    content = "Sorry, an error occurred while processing your request: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildGroundingContext(query: String): String {
        val sb = StringBuilder()

        // 1. Pending Tasks
        val pendingTasks = repository.getAllTasks().filter { it.status != "COMPLETED" }.take(5)
        if (pendingTasks.isNotEmpty()) {
            sb.append("--- PENDING ACTION ITEMS ---\n")
            for (t in pendingTasks) {
                val assignee = if (!t.assignedPerson.isNullOrBlank()) " (Assigned: ${t.assignedPerson})" else ""
                val due = if (t.dueDate != null) {
                    " (Due: ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(t.dueDate))})"
                } else ""
                sb.append("- ${t.title}$assignee$due\n")
            }
            sb.append("\n")
        }

        // 2. Recent Meetings & Summaries
        val recentMeetings = repository.getAllMeetings().take(3)
        if (recentMeetings.isNotEmpty()) {
            sb.append("--- RECENT MEETINGS ---\n")
            for (m in recentMeetings) {
                val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(m.startTimestamp))
                sb.append("• ${m.title} ($date):\n  Summary: ${m.summary ?: "No summary"}\n")
            }
            sb.append("\n")
        }

        // 3. Notes matching keywords
        val lower = query.lowercase(Locale.ROOT)
        val keywords = lower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }
        val matchingNotes = repository.getAllNotes().filter { note ->
            keywords.any { k ->
                note.title.lowercase(Locale.ROOT).contains(k) || note.content.lowercase(Locale.ROOT).contains(k)
            }
        }.take(3)
        if (matchingNotes.isNotEmpty()) {
            sb.append("--- RELEVANT NOTES ---\n")
            for (n in matchingNotes) {
                sb.append("• ${n.title}: ${n.content.take(150)}\n")
            }
        }

        return sb.toString().trim()
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                isUser = false,
                content = "Chat cleared. What would you like to review from your voice memory?"
            )
        )
    }
}
