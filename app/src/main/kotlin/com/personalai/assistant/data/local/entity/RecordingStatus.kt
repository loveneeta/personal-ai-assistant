package com.personalai.assistant.data.local.entity

/**
 * Type-safe lifecycle state for audio recordings in local Room database.
 */
enum class RecordingStatus {
    RECORDING,
    PAUSED,
    COMPLETED,
    INTERRUPTED,
    ERROR;

    val isActive: Boolean
        get() = this == RECORDING || this == PAUSED

    companion object {
        fun fromString(value: String?): RecordingStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COMPLETED
        }
    }
}
