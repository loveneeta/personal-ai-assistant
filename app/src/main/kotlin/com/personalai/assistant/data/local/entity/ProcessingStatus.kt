package com.personalai.assistant.data.local.entity

/**
 * Type-safe background processing and transcription status for audio recordings.
 */
enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED;

    val isPending: Boolean
        get() = this == PENDING

    val isProcessing: Boolean
        get() = this == PROCESSING

    val isCompleted: Boolean
        get() = this == COMPLETED

    val isFailed: Boolean
        get() = this == FAILED

    companion object {
        fun fromString(value: String?): ProcessingStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}
