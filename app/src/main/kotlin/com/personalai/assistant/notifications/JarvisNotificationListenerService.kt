package com.personalai.assistant.notifications

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.personalai.assistant.permissions.JarvisPermissionManager
import java.util.UUID

/**
 * Android Notification Listener Service for J.A.R.V.I.S.
 *
 * Intercepts communication notifications (WhatsApp, Gmail, SMS, Telegram)
 * and safely buffers their textual contents and quick-reply intents.
 * Operates in strict compliance with Android sandboxing—no private databases
 * or encrypted files are probed.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotifListener"

        private val SUPPORTED_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.google.android.gm" to "Gmail",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Messages",
            "org.telegram.messenger" to "Telegram"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "JarvisNotificationListenerService connected and active.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val appDisplayName = SUPPORTED_PACKAGES[packageName] ?: return

        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            // Audit access in Permission Manager
            JarvisPermissionManager.getInstance(this).recordPermissionAccess("notification_listener")

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

            // Skip empty or generic system noise
            if (title.isBlank() && text.isBlank()) return
            if (text.equals("Checking for new messages", ignoreCase = true)) return
            if (text.contains("messages from", ignoreCase = true) && title.contains("WhatsApp", ignoreCase = true)) return

            // Determine sender vs group title
            var sender = title
            var groupTitle: String? = null

            if (!subText.isNullOrBlank()) {
                groupTitle = subText
            } else if (title.contains(": ")) {
                val parts = title.split(": ", limit = 2)
                groupTitle = parts[0].trim()
                sender = parts[1].trim()
            }

            // Extract live RemoteInput reply action if available
            val replyAction = extractQuickReplyAction(notification, sbn.key)
            val canReply = replyAction != null

            val record = IncomingMessageRecord(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                appDisplayName = appDisplayName,
                sender = if (sender.isNotBlank()) sender else appDisplayName,
                groupTitle = groupTitle,
                messageText = text,
                timestamp = sbn.postTime.let { if (it > 0) it else System.currentTimeMillis() },
                notificationKey = sbn.key,
                canReplyDirectly = canReply
            )

            JarvisNotificationBuffer.getInstance(this).addMessage(record, replyAction)
            Log.d(TAG, "Captured incoming message: ${record.toConversationalFormat()} (QuickReply=$canReply)")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming notification from $packageName", e)
        }
    }

    /**
     * Finds and extracts an official RemoteInput action (such as WhatsApp 'Reply') from the notification.
     */
    private fun extractQuickReplyAction(notification: Notification, notificationKey: String): LiveReplyAction? {
        val actions = NotificationCompat.getActionCount(notification)
        if (actions > 0) {
            for (i in 0 until actions) {
                val action = NotificationCompat.getAction(notification, i) ?: continue
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val resultKey = remoteInput.resultKey
                    val pendingIntent = action.actionIntent
                    if (resultKey != null && pendingIntent != null) {
                        return LiveReplyAction(
                            notificationKey = notificationKey,
                            pendingIntent = pendingIntent,
                            remoteInput = remoteInput,
                            resultKey = resultKey
                        )
                    }
                }
            }
        }

        // Native notification actions fallback
        notification.actions?.forEach { nativeAction ->
            nativeAction.remoteInputs?.forEach { nativeRemoteInput ->
                val resultKey = nativeRemoteInput.resultKey
                val pendingIntent = nativeAction.actionIntent
                if (resultKey != null && pendingIntent != null) {
                    val compatRemoteInput = RemoteInput.Builder(resultKey)
                        .setLabel(nativeRemoteInput.label)
                        .setChoices(nativeRemoteInput.choices)
                        .setAllowFreeFormInput(nativeRemoteInput.allowFreeFormInput)
                        .build()
                    return LiveReplyAction(
                        notificationKey = notificationKey,
                        pendingIntent = pendingIntent,
                        remoteInput = compatRemoteInput,
                        resultKey = resultKey
                    )
                }
            }
        }

        return null
    }
}
