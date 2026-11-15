package com.personalai.assistant.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates the live Android RemoteInput action for official notification quick replies.
 */
data class LiveReplyAction(
    val notificationKey: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val resultKey: String
)

/**
 * Thread-safe In-Memory & Persistent Communication Buffer for J.A.R.V.I.S.
 *
 * Captures, indexes, and maintains incoming messages from WhatsApp, Gmail, and SMS.
 * Holds live Android RemoteInput handles so approved replies can be transmitted
 * reliably without screen scraping.
 */
class JarvisNotificationBuffer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "JarvisNotifBuffer"
        private const val PREFS_NAME = "jarvis_notifications_buffer"
        private const val KEY_MESSAGES_JSON = "cached_messages_json"
        private const val MAX_BUFFER_SIZE = 150

        @Volatile
        private var INSTANCE: JarvisNotificationBuffer? = null

        fun getInstance(context: Context): JarvisNotificationBuffer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisNotificationBuffer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val messagesList = Collections.synchronizedList(mutableListOf<IncomingMessageRecord>())
    private val liveReplyActions = ConcurrentHashMap<String, LiveReplyAction>()

    private val _messagesFlow = MutableStateFlow<List<IncomingMessageRecord>>(emptyList())
    val messagesFlow: StateFlow<List<IncomingMessageRecord>> = _messagesFlow.asStateFlow()

    init {
        loadPersistedMessages()
    }

    /**
     * Adds an incoming communication record to the buffer and updates reactive flows.
     */
    fun addMessage(record: IncomingMessageRecord, replyAction: LiveReplyAction? = null) {
        synchronized(messagesList) {
            // Deduplicate identical message text from same sender within 5 seconds
            val isDuplicate = messagesList.any {
                it.packageName == record.packageName &&
                        it.sender.equals(record.sender, ignoreCase = true) &&
                        it.messageText == record.messageText &&
                        Math.abs(it.timestamp - record.timestamp) < 5000
            }

            if (!isDuplicate) {
                messagesList.add(0, record)
                if (messagesList.size > MAX_BUFFER_SIZE) {
                    val removed = messagesList.removeAt(messagesList.lastIndex)
                    liveReplyActions.remove(removed.notificationKey)
                }
            }
        }

        if (replyAction != null) {
            liveReplyActions[record.notificationKey] = replyAction
        }

        updateFlowAndPersist()
    }

    /**
     * Retrieves the most recent communication records.
     */
    fun getRecentMessages(limit: Int = 20): List<IncomingMessageRecord> {
        synchronized(messagesList) {
            return messagesList.take(limit).toList()
        }
    }

    /**
     * Searches messages matching a keyword across sender, group, or body text.
     */
    fun searchMessages(query: String): List<IncomingMessageRecord> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return emptyList()

        synchronized(messagesList) {
            return messagesList.filter {
                it.sender.lowercase().contains(trimmed) ||
                        it.messageText.lowercase().contains(trimmed) ||
                        (it.groupTitle?.lowercase()?.contains(trimmed) == true)
            }
        }
    }

    /**
     * Retrieves all messages received from a specific contact or sender.
     */
    fun getMessagesBySender(senderName: String): List<IncomingMessageRecord> {
        val lower = senderName.trim().lowercase()
        synchronized(messagesList) {
            return messagesList.filter {
                it.sender.lowercase().contains(lower) ||
                        lower.contains(it.sender.lowercase())
            }
        }
    }

    /**
     * Dispatches an approved reply through the official Android Notification RemoteInput channel.
     * This method is strictly invoked AFTER explicit user authorization.
     */
    fun dispatchApprovedReply(context: Context, notificationKey: String, replyText: String): Boolean {
        val action = liveReplyActions[notificationKey]
        if (action == null) {
            Log.w(TAG, "Cannot dispatch reply: No active LiveReplyAction for key $notificationKey")
            return false
        }

        try {
            val replyBundle = Bundle().apply {
                putCharSequence(action.resultKey, replyText)
            }

            val fillInIntent = Intent().apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

            RemoteInput.addResultsToIntent(
                arrayOf(action.remoteInput),
                fillInIntent,
                replyBundle
            )

            action.pendingIntent.send(context, 0, fillInIntent)
            Log.i(TAG, "Successfully dispatched approved reply to $notificationKey via RemoteInput: '$replyText'")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing RemoteInput reply for $notificationKey", e)
            return false
        }
    }

    /**
     * Clears buffer and active reply handles.
     */
    fun clear() {
        synchronized(messagesList) {
            messagesList.clear()
        }
        liveReplyActions.clear()
        updateFlowAndPersist()
    }

    private fun updateFlowAndPersist() {
        val copy: List<IncomingMessageRecord>
        synchronized(messagesList) {
            copy = messagesList.toList()
        }
        _messagesFlow.value = copy

        try {
            val jsonArray = JSONArray()
            copy.take(50).forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("packageName", item.packageName)
                    put("appName", item.appDisplayName)
                    put("sender", item.sender)
                    put("groupTitle", item.groupTitle ?: "")
                    put("messageText", item.messageText)
                    put("timestamp", item.timestamp)
                    put("notificationKey", item.notificationKey)
                    put("canReplyDirectly", item.canReplyDirectly)
                    put("isRead", item.isRead)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_MESSAGES_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist notifications buffer", e)
        }
    }

    private fun loadPersistedMessages() {
        val raw = prefs.getString(KEY_MESSAGES_JSON, null) ?: return
        try {
            val array = JSONArray(raw)
            val loaded = mutableListOf<IncomingMessageRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val group = obj.optString("groupTitle", "").ifBlank { null }
                loaded.add(
                    IncomingMessageRecord(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        appDisplayName = obj.getString("appName"),
                        sender = obj.getString("sender"),
                        groupTitle = group,
                        messageText = obj.getString("messageText"),
                        timestamp = obj.getLong("timestamp"),
                        notificationKey = obj.getString("notificationKey"),
                        canReplyDirectly = obj.optBoolean("canReplyDirectly", false),
                        isRead = obj.optBoolean("isRead", false)
                    )
                )
            }
            synchronized(messagesList) {
                messagesList.addAll(loaded)
            }
            _messagesFlow.value = loaded
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persisted notifications", e)
        }
    }
}
