package com.personalai.assistant.approval

import java.util.UUID

/**
 * Single-use, time-limited Action Approval Contract.
 *
 * Guarantees:
 * 1. Action-specific: Authorizes ONLY this exact action and payload.
 * 2. Time-limited: Expires automatically after 120 seconds.
 * 3. Non-reusable: Once approved or rejected, cannot be reused.
 */
data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val level: ApprovalLevel,
    val actionType: String, // e.g. "SEND_WHATSAPP", "SEND_EMAIL", "SEND_SMS", "INITIATE_CALL", "DELETE_FILE"
    val recipient: String,
    val displayTitle: String,
    val messagePayload: String,
    val sourceInstruction: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 120_000L, // 2-minute validity
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val onExecution: (suspend (finalPayload: String) -> Boolean)? = null
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}
