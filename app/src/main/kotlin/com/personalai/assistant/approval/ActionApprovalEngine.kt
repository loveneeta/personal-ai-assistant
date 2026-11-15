package com.personalai.assistant.approval

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

sealed class ApprovalResult {
    data class Approved(val finalPayload: String) : ApprovalResult()
    object Rejected : ApprovalResult()
    object Expired : ApprovalResult()
    object Cancelled : ApprovalResult()
}

/**
 * Centralized, Autonomous Action Approval Engine for J.A.R.V.I.S.
 *
 * Gating point for all outbound communications (WhatsApp, SMS, Gmail) and high-impact actions.
 * Sub-agents CANNOT bypass this engine.
 */
class ActionApprovalEngine private constructor() {

    companion object {
        private const val TAG = "ActionApprovalEngine"

        @Volatile
        private var INSTANCE: ActionApprovalEngine? = null

        fun getInstance(): ActionApprovalEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActionApprovalEngine().also { INSTANCE = it }
            }
        }
    }

    private val pendingRequests = Collections.synchronizedList(mutableListOf<ApprovalRequest>())
    private val callbacks = ConcurrentHashMap<String, (ApprovalResult) -> Unit>()

    private val _currentApprovalRequest = MutableStateFlow<ApprovalRequest?>(null)
    val currentApprovalRequest: StateFlow<ApprovalRequest?> = _currentApprovalRequest.asStateFlow()

    private val _approvalQueue = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val approvalQueue: StateFlow<List<ApprovalRequest>> = _approvalQueue.asStateFlow()

    /**
     * Submits a new action for human approval.
     */
    fun submitRequest(request: ApprovalRequest, onResult: (ApprovalResult) -> Unit) {
        // Level 1 (Read-Only) executes immediately without prompting
        if (request.level == ApprovalLevel.LEVEL_1_READ) {
            onResult(ApprovalResult.Approved(request.messagePayload))
            return
        }

        callbacks[request.id] = onResult

        synchronized(pendingRequests) {
            pendingRequests.add(request)
        }

        updateQueue()
        Log.i(TAG, "Queued Level ${request.level} approval request: ${request.actionType} for ${request.recipient}")
    }

    /**
     * User approves the request (with optional payload edits).
     */
    fun approve(requestId: String, modifiedPayload: String? = null) {
        val request: ApprovalRequest?
        synchronized(pendingRequests) {
            val idx = pendingRequests.indexOfFirst { it.id == requestId }
            if (idx != -1) {
                request = pendingRequests.removeAt(idx)
            } else {
                request = null
            }
        }

        val callback = callbacks.remove(requestId)
        updateQueue()

        if (request == null || callback == null) {
            Log.w(TAG, "Approve failed: Request $requestId not found or already processed")
            return
        }

        if (request.isExpired) {
            Log.w(TAG, "Request $requestId has expired")
            callback(ApprovalResult.Expired)
            return
        }

        val finalPayload = modifiedPayload ?: request.messagePayload
        Log.i(TAG, "User APPROVED action: ${request.actionType} to ${request.recipient}")
        callback(ApprovalResult.Approved(finalPayload))
    }

    /**
     * User explicitly rejects the action.
     */
    fun reject(requestId: String) {
        val request: ApprovalRequest?
        synchronized(pendingRequests) {
            val idx = pendingRequests.indexOfFirst { it.id == requestId }
            if (idx != -1) {
                request = pendingRequests.removeAt(idx)
            } else {
                request = null
            }
        }

        val callback = callbacks.remove(requestId)
        updateQueue()

        if (request != null && callback != null) {
            Log.i(TAG, "User REJECTED action: ${request.actionType} to ${request.recipient}")
            callback(ApprovalResult.Rejected)
        }
    }

    /**
     * Emergency Kill Switch: Cancels all pending actions immediately.
     */
    fun cancelAll() {
        val pendingToCancel: List<ApprovalRequest>
        synchronized(pendingRequests) {
            pendingToCancel = pendingRequests.toList()
            pendingRequests.clear()
        }

        pendingToCancel.forEach { req ->
            callbacks.remove(req.id)?.invoke(ApprovalResult.Cancelled)
        }

        updateQueue()
        Log.w(TAG, "Emergency STOP: Cancelled ${pendingToCancel.size} pending approval requests")
    }

    private fun updateQueue() {
        // Prune expired requests
        synchronized(pendingRequests) {
            val iterator = pendingRequests.iterator()
            while (iterator.hasNext()) {
                val req = iterator.next()
                if (req.isExpired) {
                    iterator.remove()
                    callbacks.remove(req.id)?.invoke(ApprovalResult.Expired)
                }
            }
            _approvalQueue.value = pendingRequests.toList()
            _currentApprovalRequest.value = pendingRequests.firstOrNull()
        }
    }
}
