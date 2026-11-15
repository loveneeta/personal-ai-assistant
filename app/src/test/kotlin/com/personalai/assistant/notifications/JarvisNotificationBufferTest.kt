package com.personalai.assistant.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JarvisNotificationBufferTest {

    private lateinit var context: Context
    private lateinit var buffer: JarvisNotificationBuffer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        buffer = JarvisNotificationBuffer.getInstance(context)
        buffer.clear()
    }

    @Test
    fun testAddAndRetrieveMessages() {
        val msg1 = IncomingMessageRecord(
            id = "msg_1",
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            sender = "Rohit Sharma",
            messageText = "Where are you meeting today?",
            notificationKey = "key_wa_1"
        )
        val msg2 = IncomingMessageRecord(
            id = "msg_2",
            packageName = "com.google.android.gm",
            appDisplayName = "Gmail",
            sender = "HR Team",
            messageText = "Sprint Planning at 3 PM",
            notificationKey = "key_gm_1"
        )

        buffer.addMessage(msg1)
        buffer.addMessage(msg2)

        val recent = buffer.getRecentMessages(10)
        assertEquals(2, recent.size)
        // Latest first
        assertEquals("msg_2", recent[0].id)
        assertEquals("msg_1", recent[1].id)
    }

    @Test
    fun testSearchMessages() {
        val msg1 = IncomingMessageRecord(
            id = "msg_1",
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            sender = "Aakanksha",
            messageText = "Document review completed.",
            notificationKey = "key_1"
        )
        val msg2 = IncomingMessageRecord(
            id = "msg_2",
            packageName = "com.google.android.apps.messaging",
            appDisplayName = "Messages",
            sender = "Bank",
            messageText = "OTP for transaction is 482910.",
            notificationKey = "key_2"
        )

        buffer.addMessage(msg1)
        buffer.addMessage(msg2)

        val searchAakanksha = buffer.searchMessages("Aakanksha")
        assertEquals(1, searchAakanksha.size)
        assertEquals("Aakanksha", searchAakanksha[0].sender)

        val searchOtp = buffer.searchMessages("OTP")
        assertEquals(1, searchOtp.size)
        assertEquals("Bank", searchOtp[0].sender)
    }

    @Test
    fun testGetMessagesBySender() {
        val msg1 = IncomingMessageRecord(
            id = "msg_1",
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            sender = "Aakanksha",
            messageText = "Are you reaching in 20 mins?",
            notificationKey = "key_1"
        )
        val msg2 = IncomingMessageRecord(
            id = "msg_2",
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            sender = "Aakanksha",
            messageText = "Let me know once you reach.",
            notificationKey = "key_2"
        )

        buffer.addMessage(msg1)
        buffer.addMessage(msg2)

        val messages = buffer.getMessagesBySender("aakanksha")
        assertEquals(2, messages.size)
    }

    @Test
    fun testConversationalSummarizer() {
        val msg1 = IncomingMessageRecord(
            id = "msg_1",
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            sender = "Rohit",
            messageText = "Kab aa rahe ho?",
            notificationKey = "key_1"
        )
        val summary = NotificationSummarizer.summarizeRecent(listOf(msg1))
        assertTrue(summary.contains("WhatsApp"))
        assertTrue(summary.contains("Rohit"))
        assertTrue(summary.contains("Kab aa rahe ho?"))

        val senderSummary = NotificationSummarizer.summarizeFromSender("Rohit", listOf(msg1))
        assertTrue(senderSummary.contains("Rohit"))
        assertTrue(senderSummary.contains("Kab aa rahe ho?"))
    }
}
