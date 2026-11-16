package com.personalai.assistant.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.personalai.assistant.orchestrator.AgentExecutionResult
import com.personalai.assistant.orchestrator.MasterJarvisAgent
import com.personalai.assistant.translation.TranslationEngine
import com.personalai.assistant.wakeword.JarvisAlwaysOnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Enterprise Wear OS Communication Bridge for JARVIS.
 * Receives remote speech commands and wake-word triggers from paired Wear OS watches,
 * routes them through the voice state machine and master agent,
 * and transmits the synthesized response back to the watch.
 */
class JarvisWearableListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "JarvisWearListener"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
        val payload = String(messageEvent.data, Charsets.UTF_8).trim()

        Log.i(TAG, "Wearable message on path '$path' from node $sourceNodeId: '$payload'")

        when (path) {
            WearCompanionBridge.PATH_VOICE_COMMAND -> {
                handleWearVoiceCommand(sourceNodeId, payload)
            }
            WearCompanionBridge.PATH_WAKE_WORD -> {
                handleWearWakeWord(sourceNodeId, payload)
            }
            else -> {
                Log.d(TAG, "Unhandled wearable message path: $path")
            }
        }
    }

    private fun handleWearVoiceCommand(nodeId: String, command: String) {
        if (command.isBlank()) return

        serviceScope.launch {
            // 1. Multilingual translation layer
            val translation = TranslationEngine.getInstance(this@JarvisWearableListenerService).translateToEnglish(command)
            val englishCommand = translation.translatedText
            Log.i(TAG, "Wear command translated: original='$command', english='$englishCommand'")

            // 2. If AlwaysOnService is running, submit to state machine
            val activeSm = JarvisAlwaysOnService.getActiveStateMachine()
            if (activeSm != null) {
                activeSm.submitDirectCommand(englishCommand)
                WearCompanionBridge.sendVoiceResponse(
                    context = this@JarvisWearableListenerService,
                    targetNodeId = nodeId,
                    responseText = "Instruction received: $englishCommand"
                )
                return@launch
            }

            // 3. Fallback: Execute via MasterJarvisAgent directly
            val agent = MasterJarvisAgent.getInstance(this@JarvisWearableListenerService)
            val responseText = when (val result = agent.executeCommand(englishCommand)) {
                is AgentExecutionResult.Immediate -> result.feedback
                is AgentExecutionResult.StagedForApproval -> result.message
                is AgentExecutionResult.Handled -> result.feedback
                is AgentExecutionResult.Unhandled -> "Command processed: $englishCommand"
            }

            WearCompanionBridge.sendVoiceResponse(
                context = this@JarvisWearableListenerService,
                targetNodeId = nodeId,
                responseText = responseText
            )
        }
    }

    private fun handleWearWakeWord(nodeId: String, wakeWord: String) {
        serviceScope.launch {
            val activeSm = JarvisAlwaysOnService.getActiveStateMachine()
            if (activeSm != null) {
                activeSm.resumeHandsFree()
            }
            WearCompanionBridge.sendVoiceResponse(
                context = this@JarvisWearableListenerService,
                targetNodeId = nodeId,
                responseText = "Yes, what can I help you with?"
            )
        }
    }
}
