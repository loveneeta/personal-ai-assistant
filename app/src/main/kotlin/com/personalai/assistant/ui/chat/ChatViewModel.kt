package com.personalai.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.LocalRagChatEngine
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: AppRepository) : ViewModel() {

    private val chatEngine = LocalRagChatEngine(repository)

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                content = "Hello! I am your 100% offline Personal AI Assistant. Ask me anything about your recorded meetings, action items, or notes. I answer strictly based on your local memory."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(isUser = true, content = trimmed)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val assistantMsg = chatEngine.answerQuery(trimmed)
                _messages.value = _messages.value + assistantMsg
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    isUser = false,
                    content = "Sorry, an error occurred while searching your local memory: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
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
