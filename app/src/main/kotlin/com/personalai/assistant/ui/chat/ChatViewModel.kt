package com.personalai.assistant.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.actions.AndroidActionExecutor
import com.personalai.assistant.actions.AssistantAction
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.LocalRagChatEngine
import com.personalai.assistant.ai.OpenJarvisManager
import com.personalai.assistant.data.local.entity.ChatMessageEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.orchestrator.AgentExecutionResult
import com.personalai.assistant.orchestrator.MasterJarvisAgent
import com.personalai.assistant.playback.JarvisTtsEngine
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

    private val chatEngine = LocalRagChatEngine(repository, context)
    private val actionExecutor = context?.let { AndroidActionExecutor(it) }

    private val _isOpenJarvisActive = MutableStateFlow(
        context?.let { OpenJarvisManager.isEnabled(it) } ?: false
    )
    val isOpenJarvisActive: StateFlow<Boolean> = _isOpenJarvisActive.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllChatMessagesFlow().collect { entities ->
                if (entities.isNotEmpty()) {
                    _messages.value = entities.map { entity ->
                        ChatMessage(
                            id = entity.id,
                            isUser = entity.isUser,
                            content = entity.content,
                            timestamp = entity.timestamp
                        )
                    }
                } else {
                    _messages.value = listOf(
                        ChatMessage(
                            isUser = false,
                            content = if (context != null && OpenJarvisManager.isEnabled(context)) {
                                "J.A.R.V.I.S. Neural Bridge connected to OpenJarvis. Ready to answer using NVIDIA Nemotron reasoning grounded in your local memory."
                            } else {
                                "Hello! I am your 100% offline Personal AI Assistant. Ask me anything about your recorded meetings, action items, or notes. I answer strictly based on your local memory."
                            }
                        )
                    )
                }
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isVoiceOutputEnabled = MutableStateFlow(true)
    val isVoiceOutputEnabled: StateFlow<Boolean> = _isVoiceOutputEnabled.asStateFlow()

    fun toggleVoiceOutput() {
        _isVoiceOutputEnabled.value = !_isVoiceOutputEnabled.value
        if (!_isVoiceOutputEnabled.value && context != null) {
            JarvisTtsEngine.getInstance(context).stop()
        }
    }

    fun speakMessage(text: String) {
        if (context != null) {
            JarvisTtsEngine.getInstance(context).speak(text)
        }
    }

    fun stopSpeech() {
        if (context != null) {
            JarvisTtsEngine.getInstance(context).stop()
        }
    }

    fun refreshOpenJarvisState() {
        context?.let {
            _isOpenJarvisActive.value = OpenJarvisManager.isEnabled(it)
        }
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Persist user message to conversational memory
                repository.saveChatMessage(isUser = true, content = trimmed)

                if (context != null) {
                    // 1. Evaluate Master Jarvis Agent commands (WhatsApp, SMS, Phone, Gmail, Calendar, Device)
                    val masterAgent = MasterJarvisAgent.getInstance(context)
                    val result = masterAgent.executeCommand(trimmed)
                    val actionFeedback: String? = when (result) {
                        is AgentExecutionResult.Immediate -> result.feedback
                        is AgentExecutionResult.StagedForApproval -> result.message
                        is AgentExecutionResult.Handled -> result.feedback
                        is AgentExecutionResult.Unhandled -> null
                    }

                    if (actionFeedback != null) {
                        repository.saveChatMessage(isUser = false, content = actionFeedback)
                        if (_isVoiceOutputEnabled.value) {
                            JarvisTtsEngine.getInstance(context).speak(actionFeedback)
                        }
                        return@launch
                    }

                    // 2. Evaluate Android actions (Apps, Settings, Alarms, Timers)
                    val parsedAction = actionExecutor?.parseAction(trimmed) ?: AssistantAction.None
                    if (parsedAction !is AssistantAction.None) {
                        val actionResult = actionExecutor!!.execute(parsedAction)
                        repository.saveChatMessage(isUser = false, content = actionResult.spokenFeedback)
                        if (_isVoiceOutputEnabled.value) {
                            JarvisTtsEngine.getInstance(context).speak(actionResult.spokenFeedback)
                        }
                        return@launch
                    }
                }

                val openJarvisEnabled = context != null && OpenJarvisManager.isEnabled(context)
                val replyContent: String
                if (openJarvisEnabled) {
                    val localContext = buildGroundingContext(trimmed)
                    val recentTurns = repository.getRecentChatContext(6).reversed().map {
                        (if (it.isUser) "user" else "assistant") to it.content
                    }
                    val result = OpenJarvisManager.query(context, trimmed, localContext, recentTurns)
                    if (result.isSuccess) {
                        replyContent = result.getOrThrow()
                        repository.saveChatMessage(isUser = false, content = replyContent)
                    } else {
                        // Fall back cleanly to local on-device RAG without leaking raw socket errors
                        val offlineFallback = chatEngine.answerQuery(trimmed)
                        replyContent = offlineFallback.content
                        repository.saveChatMessage(isUser = false, content = replyContent)
                    }
                } else {
                    val assistantMsg = chatEngine.answerQuery(trimmed)
                    replyContent = assistantMsg.content
                    repository.saveChatMessage(isUser = false, content = replyContent)
                }

                // Vocalize AI response via on-device TTS
                if (_isVoiceOutputEnabled.value && context != null) {
                    JarvisTtsEngine.getInstance(context).speak(replyContent)
                }
            } catch (e: Exception) {
                val err = "Sorry, an error occurred while processing your request: ${e.message}"
                repository.saveChatMessage(isUser = false, content = err)
                if (_isVoiceOutputEnabled.value && context != null) {
                    JarvisTtsEngine.getInstance(context).speak(err)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildGroundingContext(query: String): String {
        val sb = StringBuilder()

        // 1. Pending Tasks
        val pendingTasks = repository.getAllTasks().filter { !it.status.isCompleted }.take(5)
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
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }
}
