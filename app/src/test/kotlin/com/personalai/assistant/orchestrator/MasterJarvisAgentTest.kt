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
}
