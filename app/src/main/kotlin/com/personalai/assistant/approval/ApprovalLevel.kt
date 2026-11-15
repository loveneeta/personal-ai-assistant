package com.personalai.assistant.approval

/**
 * Universal Action Classification Hierarchy.
 *
 * Enforces strict human-in-the-loop authorization gates based on impact risk.
 */
enum class ApprovalLevel(val title: String, val requiresUserApproval: Boolean) {
    /** Level 1: Read-only operations (Reading email, search contacts, checking calendar) */
    LEVEL_1_READ("Read-Only Query", false),

    /** Level 2: Reversible local state changes (Drafting emails, local notes, staging calendar events) */
    LEVEL_2_REVERSIBLE("Reversible Draft", false),

    /** Level 3: External communications (Sending WhatsApp, SMS, Emails, Initiating Phone Calls) */
    LEVEL_3_COMMUNICATION("External Communication", true),

    /** Level 4: High-Risk operations (Deleting files, clearing data, financial/system operations) */
    LEVEL_4_HIGH_RISK("High-Risk Operation", true)
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    EXECUTED
}
