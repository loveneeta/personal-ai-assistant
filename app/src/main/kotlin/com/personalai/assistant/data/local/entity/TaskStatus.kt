package com.personalai.assistant.data.local.entity

/**
 * Type-safe state for extracted meeting action items and personal tasks.
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    val isPending: Boolean
        get() = this == PENDING

    val isInProgress: Boolean
        get() = this == IN_PROGRESS

    val isCompleted: Boolean
        get() = this == COMPLETED

    companion object {
        fun fromString(value: String?): TaskStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}
