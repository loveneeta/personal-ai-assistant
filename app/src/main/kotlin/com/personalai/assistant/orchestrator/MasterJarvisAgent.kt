package com.personalai.assistant.orchestrator

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult
import com.personalai.assistant.integrations.calendar.CalendarAgent
import com.personalai.assistant.integrations.contacts.ContactsAgent
import com.personalai.assistant.integrations.device.DeviceAgent
import com.personalai.assistant.integrations.files.FileAgent
import com.personalai.assistant.integrations.gmail.GmailAgent
import com.personalai.assistant.integrations.sms.SmsAgent
import com.personalai.assistant.integrations.whatsapp.WhatsAppAgent
import com.personalai.assistant.notifications.JarvisNotificationBuffer
import com.personalai.assistant.notifications.NotificationSummarizer
import kotlinx.coroutines.CompletableDeferred
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class AgentExecutionResult {
    data class Immediate(val feedback: String) : AgentExecutionResult()
    data class StagedForApproval(val requestId: String, val message: String) : AgentExecutionResult()
    data class Handled(val feedback: String) : AgentExecutionResult()
    object Unhandled : AgentExecutionResult()
}

/**
 * Master AI Agent Architecture for J.A.R.V.I.S.
 *
 * Coordinates specialized executive sub-agents:
 * - Communication Agent (WhatsApp, Gmail, SMS, Phone)
 * - Productivity Agent (Calendar, Tasks, Notes)
 * - Device Agent (Battery, Volume, Wi-Fi, Flashlight)
 * - File Agent (Documents, SAF)
 *
 * Guarantees that no sub-agent can bypass the Action Approval Engine.
 */
class MasterJarvisAgent(private val context: Context) {

    private val whatsAppAgent = WhatsAppAgent(context)
    private val gmailAgent = GmailAgent(context)
    private val smsAgent = SmsAgent(context)
    private val contactsAgent = ContactsAgent(context)
    private val calendarAgent = CalendarAgent(context)
    private val deviceAgent = DeviceAgent(context)
    private val fileAgent = FileAgent(context)
    private val notificationBuffer = JarvisNotificationBuffer.getInstance(context)
    private val auditLogger = AuditLogger.getInstance(context)

    /**
     * Evaluates and routes a natural language command (Hindi, English, Hinglish).
     */
    suspend fun executeCommand(command: String): AgentExecutionResult {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. WhatsApp Command
        if (lower.contains("whatsapp") && (lower.contains("message") || lower.contains("bhejo") || lower.contains("send"))) {
            return handleWhatsAppCommand(trimmed, lower)
        }

        // 2. SMS Command
        if ((lower.contains("sms") || lower.contains("text")) && (lower.contains("message") || lower.contains("bhejo") || lower.contains("send"))) {
            return handleSmsCommand(trimmed, lower)
        }

        // 3. Phone Call Command
        if (lower.contains("call") || lower.contains("phone lagao") || lower.contains("phone karo") || lower.contains("dial")) {
            return handlePhoneCallCommand(trimmed, lower)
        }

        // 4. Calendar Queries & Scheduling
        if (lower.contains("meeting") || lower.contains("calendar") || lower.contains("schedule") || lower.contains("baithak")) {
            if (lower.contains("batao") || lower.contains("check") || lower.contains("show") || lower.contains("kya hai") || lower.contains("list")) {
                val events = calendarAgent.getUpcomingEvents(3)
                if (events.isEmpty()) {
                    return AgentExecutionResult.Immediate("You have no upcoming calendar meetings scheduled for the next 3 days, Commander.")
                }
                val sb = StringBuilder("Here are your upcoming meetings (${events.size}):")
                events.forEachIndexed { idx, ev ->
                    val time = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault()).format(Date(ev.startEpochMs))
                    sb.append("\n${idx + 1}. ${ev.title} ($time)")
                }
                return AgentExecutionResult.Immediate(sb.toString())
            }
        }

        // 5. Check Messages / Notifications Summary
        if (lower.contains("check message") || lower.contains("kya message") || lower.contains("koi message") ||
            lower.contains("recent message") || lower.contains("messages summarize")) {
            val messages = notificationBuffer.getRecentMessages(10)
            val summary = NotificationSummarizer.summarizeRecent(messages)
            return AgentExecutionResult.Immediate(summary)
        }

        // 6. Device Status & Diagnostics
        if (lower.contains("battery") || lower.contains("charging")) {
            val diag = deviceAgent.getDeviceStatus()
            val chargingText = if (diag.isCharging) "and currently charging" else "not charging"
            return AgentExecutionResult.Immediate("Battery is at ${diag.batteryPct}%, $chargingText, Commander.")
        }

        if (lower.contains("volume")) {
            val numMatch = Regex("(\\d+)").find(lower)
            if (numMatch != null) {
                val pct = numMatch.groupValues[1].toIntOrNull() ?: 50
                deviceAgent.setMediaVolume(pct)
                return AgentExecutionResult.Immediate("Media volume adjusted to $pct%, Commander.")
            }
        }

        if (lower.contains("flashlight") || lower.contains("torch")) {
            val enable = !lower.contains("off") && !lower.contains("band")
            deviceAgent.toggleFlashlight(enable)
            return AgentExecutionResult.Immediate(if (enable) "Flashlight engaged, Commander." else "Flashlight turned off, Commander.")
        }

        return AgentExecutionResult.Unhandled
    }

    private suspend fun handleWhatsAppCommand(trimmed: String, lower: String): AgentExecutionResult {
        // Example: "Aakanksha ko WhatsApp par message bhejo ki main 20 minute mein aa raha hoon"
        val recipient = extractRecipient(lower) ?: "Contact"
        val messageBody = extractMessageBody(trimmed, lower) ?: "Hello from J.A.R.V.I.S."

        val deferred = CompletableDeferred<AgentExecutionResult>()
        whatsAppAgent.stageWhatsAppMessage(
            recipientQuery = recipient,
            messageText = messageBody,
            sourceCommand = trimmed
        ) { success, feedback ->
            auditLogger.logAction(
                id = java.util.UUID.randomUUID().toString(),
                actionType = "SEND_WHATSAPP",
                recipient = recipient,
                summary = messageBody,
                approvalLevel = "LEVEL_3_COMMUNICATION",
                status = if (success) "COMPLETED" else "CANCELLED"
            )
            deferred.complete(AgentExecutionResult.Handled(feedback))
        }

        return AgentExecutionResult.StagedForApproval(
            requestId = "pending_wa",
            message = "I have staged a WhatsApp message for $recipient: \"$messageBody\". Please confirm on the approval prompt."
        )
    }

    private suspend fun handleSmsCommand(trimmed: String, lower: String): AgentExecutionResult {
        val recipient = extractRecipient(lower) ?: "Recipient"
        val messageBody = extractMessageBody(trimmed, lower) ?: "Hello"
        val contacts = contactsAgent.findContactByName(recipient)
        val phone = contacts.firstOrNull()?.phoneNumber ?: recipient

        smsAgent.stageSms(
            recipientName = recipient,
            phoneNumber = phone,
            messageText = messageBody,
            sourceCommand = trimmed
        ) { success, feedback ->
            auditLogger.logAction(
                id = java.util.UUID.randomUUID().toString(),
                actionType = "SEND_SMS",
                recipient = "$recipient ($phone)",
                summary = messageBody,
                approvalLevel = "LEVEL_3_COMMUNICATION",
                status = if (success) "COMPLETED" else "CANCELLED"
            )
        }

        return AgentExecutionResult.StagedForApproval(
            requestId = "pending_sms",
            message = "Staged SMS for $recipient ($phone): \"$messageBody\". Please approve on screen."
        )
    }

    private fun handlePhoneCallCommand(trimmed: String, lower: String): AgentExecutionResult {
        val recipient = extractRecipient(lower) ?: "Contact"
        val contacts = contactsAgent.findContactByName(recipient)
        val phone = contacts.firstOrNull()?.phoneNumber

        if (phone != null) {
            val request = ApprovalRequest(
                level = ApprovalLevel.LEVEL_3_COMMUNICATION,
                actionType = "INITIATE_CALL",
                recipient = "$recipient ($phone)",
                displayTitle = "Initiate Phone Call",
                messagePayload = "Place outgoing voice call to $recipient at $phone",
                sourceInstruction = trimmed
            )

            ActionApprovalEngine.getInstance().submitRequest(request) { result ->
                if (result is ApprovalResult.Approved) {
                    val uri = Uri.parse("tel:${Uri.encode(phone)}")
                    val intent = Intent(Intent.ACTION_CALL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(dialIntent)
                    }
                }
            }

            return AgentExecutionResult.StagedForApproval(
                requestId = request.id,
                message = "Initiating phone call to $recipient ($phone). Awaiting your approval on screen."
            )
        } else {
            return AgentExecutionResult.Immediate("I could not find phone number for $recipient in your contacts.")
        }
    }

    private fun extractRecipient(lower: String): String? {
        // Pattern 1: "... <name> ko whatsapp ..." or "... <name> ko message/sms/call ..."
        val koBefore = Regex("([a-zA-Z0-9]+)\\s+ko\\s+(?:whatsapp|sms|message|phone|call)").find(lower)
        if (koBefore != null) {
            val candidate = koBefore.groupValues[1].trim()
            if (candidate != "jarvis" && candidate != "par") return candidate.replaceFirstChar { it.uppercase() }
        }

        // Pattern 2: "jarvis, <name> ko ..."
        val jarvisKo = Regex("(?:jarvis[,\\s]+)?([a-zA-Z0-9]+)\\s+ko").find(lower)
        if (jarvisKo != null) {
            val candidate = jarvisKo.groupValues[1].trim()
            if (candidate != "jarvis" && candidate != "whatsapp" && candidate != "sms") {
                return candidate.replaceFirstChar { it.uppercase() }
            }
        }

        // Pattern 3: "... to <name> ..."
        val toMatch = Regex("(?:to|call)\\s+([a-zA-Z0-9]+)").find(lower)
        if (toMatch != null) {
            val candidate = toMatch.groupValues[1].trim()
            if (candidate != "whatsapp" && candidate != "sms" && candidate != "message") {
                return candidate.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun extractMessageBody(trimmed: String, lower: String): String? {
        val markers = listOf("ki ", "that ", "saying ", "message ", "bhejo ")
        for (m in markers) {
            val idx = lower.indexOf(m)
            if (idx != -1) {
                val extracted = trimmed.substring(idx + m.length).trim()
                if (extracted.isNotBlank()) return extracted
            }
        }
        return null
    }
}
