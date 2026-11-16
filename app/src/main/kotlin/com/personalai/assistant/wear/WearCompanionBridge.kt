package com.personalai.assistant.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enterprise Wear OS Companion Bridge for JARVIS.
 * Bridges ambient wrist interactions, remote wake-word events,
 * and spoken response round-trips over the Wearable Data Layer API.
 */
object WearCompanionBridge {
    private const val TAG = "WearCompanionBridge"
    const val PATH_VOICE_COMMAND = "/jarvis/voice_command"
    const val PATH_WAKE_WORD = "/jarvis/wake_word"
    const val PATH_VOICE_RESPONSE = "/jarvis/voice_response"

    /**
     * Sends voice response back to the requesting Wear OS watch node.
     */
    suspend fun sendVoiceResponse(context: Context, targetNodeId: String, responseText: String): Boolean {
        return try {
            val messageClient = Wearable.getMessageClient(context)
            val data = responseText.toByteArray(Charsets.UTF_8)
            messageClient.sendMessage(targetNodeId, PATH_VOICE_RESPONSE, data).awaitTask()
            Log.i(TAG, "Sent voice response to Wear node $targetNodeId: '${responseText.take(50)}...'")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send response to Wear node $targetNodeId: ${e.message}")
            false
        }
    }

    /**
     * Broadcasts a notification or state sync to all currently connected Wear OS devices.
     */
    suspend fun broadcastToWatches(context: Context, path: String, payload: ByteArray): Int {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val connectedNodes = nodeClient.connectedNodes.awaitTask()
            val messageClient = Wearable.getMessageClient(context)
            var sentCount = 0
            for (node in connectedNodes) {
                try {
                    messageClient.sendMessage(node.id, path, payload).awaitTask()
                    sentCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send to node ${node.displayName}: ${e.message}")
                }
            }
            sentCount
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query connected Wear nodes: ${e.message}")
            0
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
