package com.personalai.assistant.orchestrator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.approval.ActionApprovalEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MasterJarvisAgentTest {

    private lateinit var context: Context
    private lateinit var agent: MasterJarvisAgent

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        agent = MasterJarvisAgent(context)
        ActionApprovalEngine.getInstance().cancelAll()
    }

    @Test
    fun testWhatsAppCommand_stagesForApproval() = runBlocking {
        val cmd = "Jarvis, Aakanksha ko WhatsApp par message bhejo ki main 20 minute mein aa raha hoon"
        val result = agent.executeCommand(cmd)

        assertTrue("Expected command to be staged for approval", result is AgentExecutionResult.StagedForApproval)

        val pending = ActionApprovalEngine.getInstance().currentApprovalRequest.value
        assertNotNull("Expected pending approval request in engine", pending)
        assertEquals("Aakanksha", pending?.recipient)
        assertTrue(pending?.messagePayload?.contains("20 minute") == true)
    }

    @Test
    fun testBatteryQuery_returnsImmediateDiagnostics() = runBlocking {
        val cmd = "Jarvis, battery percentage batao"
        val result = agent.executeCommand(cmd)

        assertTrue("Expected immediate diagnostics response", result is AgentExecutionResult.Immediate)
        val feedback = (result as AgentExecutionResult.Immediate).feedback
        assertTrue(feedback.contains("Battery is at"))
    }

    @Test
    fun testMultiWordRecipient_extractsFullName() = runBlocking {
        val cmd = "Rahul Sharma ko WhatsApp message bhejo ki 5 baje milte hain"
        val result = agent.executeCommand(cmd)

        assertTrue("Expected staged for approval", result is AgentExecutionResult.StagedForApproval)
        val pending = ActionApprovalEngine.getInstance().currentApprovalRequest.value
        assertNotNull("Expected pending request", pending)
        assertEquals("Rahul Sharma", pending?.recipient)
        assertTrue(pending?.messagePayload?.contains("5 baje milte hain") == true)
    }

    @Test
    fun testGmailCommand_stagesEmailForApproval() = runBlocking {
        val cmd = "Send email to client@company.com saying Project milestone completed"
        val result = agent.executeCommand(cmd)

        assertTrue("Expected staged for approval", result is AgentExecutionResult.StagedForApproval)
        val pending = ActionApprovalEngine.getInstance().currentApprovalRequest.value
        assertNotNull("Expected pending request", pending)
        assertEquals("client@company.com", pending?.recipient)
        assertTrue(pending?.messagePayload?.contains("Project milestone completed") == true)
    }

    @Test
    fun testVolumeAdjustments_withoutDigits() = runBlocking {
        val cmdUp = "Jarvis, volume badhao"
        val resUp = agent.executeCommand(cmdUp)
        assertTrue(resUp is AgentExecutionResult.Immediate)
        assertTrue((resUp as AgentExecutionResult.Immediate).feedback.contains("increased"))

        val cmdMute = "Jarvis, mute kar do"
        val resMute = agent.executeCommand(cmdMute)
        assertTrue(resMute is AgentExecutionResult.Immediate)
        assertTrue((resMute as AgentExecutionResult.Immediate).feedback.contains("muted"))
    }

    @Test
    fun testFlashlightHindiPhrasing() = runBlocking {
        val cmdOn = "Torch jalao"
        val resOn = agent.executeCommand(cmdOn)
        assertTrue(resOn is AgentExecutionResult.Immediate)
        assertTrue((resOn as AgentExecutionResult.Immediate).feedback.contains("engaged"))

        val cmdOff = "Torch band karo"
        val resOff = agent.executeCommand(cmdOff)
        assertTrue(resOff is AgentExecutionResult.Immediate)
        assertTrue((resOff as AgentExecutionResult.Immediate).feedback.contains("turned off"))
    }
}
