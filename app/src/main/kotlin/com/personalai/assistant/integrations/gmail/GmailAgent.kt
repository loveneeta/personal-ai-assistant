package com.personalai.assistant.integrations.gmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult

data class EmailDraft(
    val recipientEmail: String,
    val subject: String,
    val body: String
)

/**
 * Executive Gmail Agent for J.A.R.V.I.S.
 *
 * Prepares drafts, summarizes emails, and stages outbound communications.
 * All sending operations are strictly gated by the Level 3 Action Approval Engine.
 */
class GmailAgent(private val context: Context) {

    companion object {
        private const val TAG = "GmailAgent"
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private const val PREFS_GMAIL = "jarvis_gmail_auth"
        private const val KEY_OAUTH_TOKEN = "gmail_oauth_token"
    }

    private val prefs = context.getSharedPreferences(PREFS_GMAIL, Context.MODE_PRIVATE)

    fun isConnected(): Boolean {
        return prefs.getString(KEY_OAUTH_TOKEN, null) != null
    }

    /**
     * Stages an email for human verification and dispatches upon Level 3 approval.
     */
    fun stageSendEmail(
        recipientEmail: String,
        subject: String,
        bodyText: String,
        sourceCommand: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val request = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_EMAIL",
            recipient = recipientEmail,
            displayTitle = "Send Email via Gmail",
            messagePayload = "Subject: $subject\n\n$bodyText",
            sourceInstruction = sourceCommand
        )

        ActionApprovalEngine.getInstance().submitRequest(request) { result ->
            when (result) {
                is ApprovalResult.Approved -> {
                    val finalContent = result.finalPayload
                    val success = dispatchEmailIntent(recipientEmail, subject, finalContent)
                    if (success) {
                        onComplete(true, "Email prepared and dispatched for $recipientEmail.")
                    } else {
                        onComplete(false, "Could not open email client for $recipientEmail.")
                    }
                }
                is ApprovalResult.Rejected -> {
                    onComplete(false, "Email sending cancelled by user.")
                }
                is ApprovalResult.Expired -> {
                    onComplete(false, "Email approval timed out.")
                }
                is ApprovalResult.Cancelled -> {
                    onComplete(false, "Email dispatch aborted.")
                }
            }
        }
    }

    private fun dispatchEmailIntent(recipient: String, subject: String, body: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                setPackage(GMAIL_PACKAGE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched Gmail compose for $recipient")
            true
        } catch (e: Exception) {
            // Fallback to generic mailto
            try {
                val genericIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$recipient")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to launch email compose", e2)
                false
            }
        }
    }
}
