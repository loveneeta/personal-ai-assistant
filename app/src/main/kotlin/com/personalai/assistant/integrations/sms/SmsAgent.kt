package com.personalai.assistant.integrations.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult
import com.personalai.assistant.permissions.JarvisPermissionManager

data class SmsRecord(
    val address: String,
    val body: String,
    val date: Long
)

/**
 * Executive SMS Communications Agent.
 *
 * Reads incoming SMS threads and stages outbound messages strictly through
 * the Level 3 Action Approval Engine.
 */
class SmsAgent(private val context: Context) {

    companion object {
        private const val TAG = "SmsAgent"
    }

    /**
     * Reads recent inbox SMS messages if permission is granted.
     */
    fun readRecentSms(limit: Int = 10): List<SmsRecord> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot read SMS: READ_SMS permission not granted.")
            return emptyList()
        }

        JarvisPermissionManager.getInstance(context).recordPermissionAccess("sms")
        val records = mutableListOf<SmsRecord>()

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.DATE),
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )

            cursor?.use { c ->
                val addrIdx = c.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.Inbox.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.Inbox.DATE)

                var count = 0
                while (c.moveToNext() && count < limit) {
                    val addr = if (addrIdx != -1) c.getString(addrIdx) else "Unknown"
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    records.add(SmsRecord(address = addr, body = body, date = date))
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS inbox", e)
        }

        return records
    }

    /**
     * Stages an outbound SMS for human approval via Level 3 Action Approval Engine.
     */
    fun stageSms(
        recipientName: String,
        phoneNumber: String,
        messageText: String,
        sourceCommand: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val request = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_SMS",
            recipient = "$recipientName ($phoneNumber)",
            displayTitle = "Send SMS Message",
            messagePayload = messageText,
            sourceInstruction = sourceCommand
        )

        ActionApprovalEngine.getInstance().submitRequest(request) { result ->
            when (result) {
                is ApprovalResult.Approved -> {
                    val finalMsg = result.finalPayload
                    val success = executeSendSms(phoneNumber, finalMsg)
                    if (success) {
                        onComplete(true, "SMS sent to $recipientName: \"$finalMsg\"")
                    } else {
                        onComplete(false, "Failed to send SMS to $recipientName.")
                    }
                }
                is ApprovalResult.Rejected -> {
                    onComplete(false, "SMS sending cancelled by user.")
                }
                is ApprovalResult.Expired -> {
                    onComplete(false, "SMS approval request timed out.")
                }
                is ApprovalResult.Cancelled -> {
                    onComplete(false, "SMS action aborted.")
                }
            }
        }
    }

    private fun executeSendSms(phoneNumber: String, messageText: String): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(messageText)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                Log.i(TAG, "Direct SMS successfully transmitted to $phoneNumber")
                true
            } else {
                // Fallback to system SMS intent
                val uri = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", messageText)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.i(TAG, "Opened system SMS compose with pre-filled text")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitting SMS", e)
            false
        }
    }
}
