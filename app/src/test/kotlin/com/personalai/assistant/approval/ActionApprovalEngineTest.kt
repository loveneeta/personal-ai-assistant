package com.personalai.assistant.approval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionApprovalEngineTest {

    private lateinit var engine: ActionApprovalEngine

    @Before
    fun setUp() {
        engine = ActionApprovalEngine.getInstance()
        engine.cancelAll()
    }

    @Test
    fun testLevel1_executesImmediatelyWithoutPrompt() {
        var result: ApprovalResult? = null
        val req = ApprovalRequest(
            level = ApprovalLevel.LEVEL_1_READ,
            actionType = "READ_CALENDAR",
            recipient = "Self",
            displayTitle = "Read Calendar",
            messagePayload = "Get events",
            sourceInstruction = "check my meetings"
        )

        engine.submitRequest(req) { result = it }

        assertTrue(result is ApprovalResult.Approved)
        assertEquals("Get events", (result as ApprovalResult.Approved).finalPayload)
        assertNull(engine.currentApprovalRequest.value)
    }

    @Test
    fun testLevel3_requiresApprovalAndSupportsEdit() {
        var result: ApprovalResult? = null
        val req = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_WHATSAPP",
            recipient = "Aakanksha",
            displayTitle = "Send WhatsApp",
            messagePayload = "I am on the way",
            sourceInstruction = "Aakanksha ko WhatsApp bhejo"
        )

        engine.submitRequest(req) { result = it }

        // Must be held in pending queue
        assertNotNull(engine.currentApprovalRequest.value)
        assertEquals(req.id, engine.currentApprovalRequest.value?.id)
        assertNull(result)

        // Approve with edited message
        val edited = "I am reaching in 10 minutes"
        engine.approve(req.id, edited)

        assertTrue(result is ApprovalResult.Approved)
        assertEquals(edited, (result as ApprovalResult.Approved).finalPayload)
        assertNull(engine.currentApprovalRequest.value)
    }

    @Test
    fun testReject_triggersRejectedResult() {
        var result: ApprovalResult? = null
        val req = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_SMS",
            recipient = "Rohit",
            displayTitle = "Send SMS",
            messagePayload = "Meeting starting",
            sourceInstruction = "Rohit ko SMS karo"
        )

        engine.submitRequest(req) { result = it }
        assertEquals(1, engine.approvalQueue.value.size)

        engine.reject(req.id)

        assertTrue(result is ApprovalResult.Rejected)
        assertEquals(0, engine.approvalQueue.value.size)
    }

    @Test
    fun testCancelAll_emergencyKillSwitch() {
        var res1: ApprovalResult? = null
        var res2: ApprovalResult? = null

        val req1 = ApprovalRequest(
            level = ApprovalLevel.LEVEL_3_COMMUNICATION,
            actionType = "SEND_WHATSAPP",
            recipient = "User1",
            displayTitle = "WA",
            messagePayload = "Msg1",
            sourceInstruction = "cmd1"
        )
        val req2 = ApprovalRequest(
            level = ApprovalLevel.LEVEL_4_HIGH_RISK,
            actionType = "DELETE_FILE",
            recipient = "File1",
            displayTitle = "Delete",
            messagePayload = "Delete",
            sourceInstruction = "cmd2"
        )

        engine.submitRequest(req1) { res1 = it }
        engine.submitRequest(req2) { res2 = it }

        assertEquals(2, engine.approvalQueue.value.size)

        engine.cancelAll()

        assertTrue(res1 is ApprovalResult.Cancelled)
        assertTrue(res2 is ApprovalResult.Cancelled)
        assertEquals(0, engine.approvalQueue.value.size)
    }
}
