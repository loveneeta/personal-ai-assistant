package com.personalai.assistant.integrations.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult
import com.personalai.assistant.integrations.contacts.ContactsAgent
import com.personalai.assistant.notifications.JarvisNotificationBuffer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Executive WhatsApp Agent for J.A.R.V.I.S.
 *
 * Prepares messages and replies for WhatsApp and WhatsApp Business.
 * Guarantees:
 * 1. ZERO autonomous sending: All messages must pass Level 3 user approval.
 * 2. Uses official Android Notification RemoteInput or official WhatsApp URI scheme.
 * 3. Never bypasses encryption or inspects private app databases.
 */
class WhatsAppAgent(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppAgent"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    private val contactsAgent = ContactsAgent(context)
    private val notificationBuffer = JarvisNotificationBuffer.getInstance(context)

    /**
     * Stages a WhatsApp message for human verification and sends upon approval.
     */
    fun stageWhatsAppMessage(
        recipientQuery: String,
        messageText: String,
        sourceCommand: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        // Resolve contact phone number or notification handle
        val contacts = contactsAgent.findContactByName(recipientQuery)
        val resolvedNumber = contacts.firstOrNull()?.phoneNumber
        val displayName = contacts.firstOrNull()?.name ?: recipientQuery

        // Check if there is an active notification reply handle from this sender
        val matchingNotif = notificationBuffer.getMessagesBySender(recipientQuery)
            .firstOrNull { it.canReplyDirectly }

        val request = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_WHATSAPP",
            recipient = displayName,
            displayTitle = "Send WhatsApp Message",
            messagePayload = messageText,
            sourceInstruction = sourceCommand
        )

        ActionApprovalEngine.getInstance().submitRequest(request) { result ->
            when (result) {
                is ApprovalResult.Approved -> {
                    val finalMessage = result.finalPayload
                    val success: Boolean

                    if (matchingNotif != null) {
                        // Dispatch via official Android Notification RemoteInput
                        success = notificationBuffer.dispatchApprovedReply(
                            context = context,
                            notificationKey = matchingNotif.notificationKey,
                            replyText = finalMessage
                        )
                        if (!success && resolvedNumber != null) {
                            // Fallback to official WhatsApp URL scheme
                            dispatchViaWhatsAppIntent(resolvedNumber, finalMessage)
                        }
                    } else if (resolvedNumber != null) {
                        success = dispatchViaWhatsAppIntent(resolvedNumber, finalMessage)
                    } else {
                        // Dispatch via generic text share intent to WhatsApp
                        success = dispatchGenericWhatsAppShare(finalMessage)
                    }

                    if (success) {
                        onComplete(true, "WhatsApp message sent to $displayName: \"$finalMessage\"")
                    } else {
                        onComplete(false, "Could not open WhatsApp for $displayName.")
                    }
                }
                is ApprovalResult.Rejected -> {
                    onComplete(false, "WhatsApp message cancelled by user.")
                }
                is ApprovalResult.Expired -> {
                    onComplete(false, "WhatsApp approval timed out.")
                }
                is ApprovalResult.Cancelled -> {
                    onComplete(false, "WhatsApp message aborted.")
                }
            }
        }
    }

    private fun dispatchViaWhatsAppIntent(phoneNumber: String, text: String): Boolean {
        return try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "").removePrefix("+")
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(WHATSAPP_PACKAGE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched WhatsApp conversation for $cleanPhone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp URL intent", e)
            false
        }
    }

    private fun dispatchGenericWhatsAppShare(text: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(WHATSAPP_PACKAGE)
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Dispatched text share to WhatsApp")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch generic WhatsApp share", e)
            false
        }
    }
}
