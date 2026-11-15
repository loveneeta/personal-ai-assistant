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

    companion object {
        @Volatile
        private var INSTANCE: MasterJarvisAgent? = null

        fun getInstance(context: Context): MasterJarvisAgent {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MasterJarvisAgent(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val REGEX_EMAIL = Regex("([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)")
        private val REGEX_KO_BEFORE = Regex("(?:jarvis[,\\s]+)?([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:ko|को)\\s+(?:whatsapp|sms|message|phone|call|email|gmail|mail|संदेश|मैसेज|कॉल)")
        private val REGEX_JARVIS_KO = Regex("(?:jarvis[,\\s]+)?([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:ko|को)")
        private val REGEX_TO_MATCH = Regex("(?:to|call|dial)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)")
        private val REGEX_DIGITS = Regex("(\\d+)")
        private val REGEX_WHITESPACE = Regex("\\s+")

        private val STOP_WORDS = setOf(
            "jarvis", "par", "ko", "whatsapp", "sms", "message", "email",
            "gmail", "mail", "phone", "call", "bhejo", "send", "the", "a", "an", "mein"
        )
        private val MESSAGE_MARKERS = listOf("ki ", "that ", "saying ", "message ", "bhejo ", "bolo ", "likho ", "body ")
    }

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

        // 1. WhatsApp Command (Hindi, English, Hinglish)
        if ((lower.contains("whatsapp") || lower.contains("व्हाट्सएप")) &&
            (lower.contains("message") || lower.contains("bhejo") || lower.contains("send") ||
             lower.contains("karo") || lower.contains("bolo") || lower.contains("text") ||
             lower.contains("likho") || lower.contains("संदेश") || lower.contains("मैसेज"))
        ) {
            return handleWhatsAppCommand(trimmed, lower)
        }

        // 2. SMS Command (Hindi, English, Hinglish)
        if ((lower.contains("sms") || lower.contains("text") || lower.contains("एसएमएस")) &&
            (lower.contains("message") || lower.contains("bhejo") || lower.contains("send") ||
             lower.contains("karo") || lower.contains("bolo") || lower.contains("likho") ||
             lower.contains("संदेश") || lower.contains("मैसेज"))
        ) {
            return handleSmsCommand(trimmed, lower)
        }

        // 3. Gmail / Email Command (Hindi, English, Hinglish)
        if ((lower.contains("email") || lower.contains("gmail") || lower.contains("ईमेल") || lower.contains("मेल")) &&
            (lower.contains("send") || lower.contains("draft") || lower.contains("bhejo") ||
             lower.contains("likho") || lower.contains("karo") || lower.contains("भेजो") || lower.contains("लिखो"))
        ) {
            return handleGmailCommand(trimmed, lower)
        }

        // 4. Phone Call Command
        if (lower.contains("call") || lower.contains("phone lagao") || lower.contains("phone karo") ||
            lower.contains("call lagao") || lower.contains("call karo") || lower.contains("dial") ||
            lower.contains("फोन लगाओ") || lower.contains("कॉल करो")
        ) {
            return handlePhoneCallCommand(trimmed, lower)
        }

        // 5. Calendar Queries & Scheduling
        if (lower.contains("meeting") || lower.contains("calendar") || lower.contains("schedule") || lower.contains("baithak") || lower.contains("बैठक")) {
            if (lower.contains("batao") || lower.contains("check") || lower.contains("show") || lower.contains("kya hai") || lower.contains("list") || lower.contains("दिखाओ") || lower.contains("बताओ")) {
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

        // 6. Check Messages / Notifications Summary
        if (lower.contains("check message") || lower.contains("kya message") || lower.contains("koi message") ||
            lower.contains("recent message") || lower.contains("messages summarize") || lower.contains("संदेश पढ़ो") || lower.contains("मैसेज पढ़ो")
        ) {
            val messages = notificationBuffer.getRecentMessages(10)
            val summary = NotificationSummarizer.summarizeRecent(messages)
            return AgentExecutionResult.Immediate(summary)
        }

        // 7. Device Status & Diagnostics
        if (lower.contains("battery") || lower.contains("charging") || lower.contains("बैटरी")) {
            val diag = deviceAgent.getDeviceStatus()
            val chargingText = if (diag.isCharging) "and currently charging" else "not charging"
            return AgentExecutionResult.Immediate("Battery is at ${diag.batteryPct}%, $chargingText, Commander.")
        }

        // 8. Volume Control (Numeric, Incremental, Mute, Max)
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("awaz") || lower.contains("आवाज़") || lower.contains("mute")) {
            val numMatch = REGEX_DIGITS.find(lower)
            if (numMatch != null) {
                val pct = numMatch.groupValues[1].toIntOrNull() ?: 50
                deviceAgent.setMediaVolume(pct)
                return AgentExecutionResult.Immediate("Media volume adjusted to $pct%, Commander.")
            } else if (lower.contains("mute") || lower.contains("band") || lower.contains("बंद")) {
                deviceAgent.setMediaVolume(0)
                return AgentExecutionResult.Immediate("Media volume muted, Commander.")
            } else if (lower.contains("max") || lower.contains("full") || lower.contains("poori") || lower.contains("पूरी")) {
                deviceAgent.setMediaVolume(100)
                return AgentExecutionResult.Immediate("Media volume set to maximum 100%, Commander.")
            } else if (lower.contains("up") || lower.contains("badha") || lower.contains("increase") || lower.contains("tez") || lower.contains("बढ़ा")) {
                val current = deviceAgent.getDeviceStatus().mediaVolumePercent
                val target = (current + 20).coerceAtMost(100)
                deviceAgent.setMediaVolume(target)
                return AgentExecutionResult.Immediate("Media volume increased to $target%, Commander.")
            } else if (lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("dheere") || lower.contains("कम")) {
                val current = deviceAgent.getDeviceStatus().mediaVolumePercent
                val target = (current - 20).coerceAtLeast(0)
                deviceAgent.setMediaVolume(target)
                return AgentExecutionResult.Immediate("Media volume decreased to $target%, Commander.")
            }
        }

        // 9. Flashlight / Torch Control
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("टॉर्च")) {
            val isTurnOff = lower.contains("off") || lower.contains("band") || lower.contains("bujha") || lower.contains("बंद") || lower.contains("बुझा")
            val isTurnOn = lower.contains("on") || lower.contains("chalu") || lower.contains("jala") || lower.contains("चालू") || lower.contains("जला")
            val enable = if (isTurnOff) false else if (isTurnOn) true else !lower.contains("off") && !lower.contains("band")
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

    private suspend fun handleGmailCommand(trimmed: String, lower: String): AgentExecutionResult {
        val recipient = extractRecipient(lower) ?: "Recipient"
        val messageBody = extractMessageBody(trimmed, lower) ?: "Hello from J.A.R.V.I.S."
        val email = if (recipient.contains("@")) {
            recipient
        } else {
            val contacts = contactsAgent.findContactByName(recipient)
            contacts.firstOrNull()?.email ?: "$recipient@example.com"
        }
        val subject = "Message from Loveneet via J.A.R.V.I.S."

        gmailAgent.stageSendEmail(
            recipientEmail = email,
            subject = subject,
            bodyText = messageBody,
            sourceCommand = trimmed
        ) { success, feedback ->
            auditLogger.logAction(
                id = java.util.UUID.randomUUID().toString(),
                actionType = "SEND_EMAIL",
                recipient = "$recipient ($email)",
                summary = messageBody,
                approvalLevel = "LEVEL_3_COMMUNICATION",
                status = if (success) "COMPLETED" else "CANCELLED"
            )
        }

        return AgentExecutionResult.StagedForApproval(
            requestId = "pending_gmail",
            message = "Staged Email for $recipient ($email): \"$messageBody\". Please approve on screen."
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
        // Pattern 0: Direct Email Address
        val emailMatch = REGEX_EMAIL.find(lower)
        if (emailMatch != null) {
            return emailMatch.groupValues[1]
        }

        // Pattern 1: "... <Name> ko ..." or "... <Name> को ..."
        val koBefore = REGEX_KO_BEFORE.find(lower)
        if (koBefore != null) {
            val candidate = koBefore.groupValues[1].trim()
            if (!STOP_WORDS.contains(candidate)) return formatName(candidate)
        }

        // Pattern 2: "jarvis, <Name> ko ..." or "<Name> ko ..."
        val jarvisKo = REGEX_JARVIS_KO.find(lower)
        if (jarvisKo != null) {
            val candidate = jarvisKo.groupValues[1].trim()
            if (!STOP_WORDS.contains(candidate)) {
                return formatName(candidate)
            }
        }

        // Pattern 3: English "... to <Name> ..." or "... call <Name> ..."
        val toMatch = REGEX_TO_MATCH.find(lower)
        if (toMatch != null) {
            val candidate = toMatch.groupValues[1].trim()
            if (!STOP_WORDS.contains(candidate)) {
                return formatName(candidate)
            }
        }
        return null
    }

    private fun formatName(raw: String): String {
        return raw.split(REGEX_WHITESPACE).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun extractMessageBody(trimmed: String, lower: String): String? {
        for (m in MESSAGE_MARKERS) {
            val idx = lower.indexOf(m)
            if (idx != -1) {
                val extracted = trimmed.substring(idx + m.length).trim()
                if (extracted.isNotBlank()) return extracted
            }
        }
        return null
    }
}
