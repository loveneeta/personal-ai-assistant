package com.personalai.assistant.notifications

/**
 * Structured model representing an incoming communication message captured
 * via Android's NotificationListenerService (WhatsApp, Gmail, SMS, etc.).
 */
data class IncomingMessageRecord(
    val id: String,
    val packageName: String,
    val appDisplayName: String,
    val sender: String,
    val groupTitle: String? = null,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notificationKey: String,
    val canReplyDirectly: Boolean = false,
    val isRead: Boolean = false
) {
    val isGroupMessage: Boolean get() = !groupTitle.isNullOrBlank()

    /**
     * Formats this message into a conversational summary string for JARVIS RAG & speech.
     */
    fun toConversationalFormat(): String {
        return if (isGroupMessage) {
            "[$appDisplayName - Group '$groupTitle'] $sender: \"$messageText\""
        } else {
            "[$appDisplayName] $sender: \"$messageText\""
        }
    }
}
