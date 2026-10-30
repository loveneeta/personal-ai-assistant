package com.personalai.assistant.recording

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val durationMs: Long, val recordingId: Long, val title: String) : RecordingState()
    data class Paused(val durationMs: Long, val recordingId: Long, val title: String) : RecordingState()
    object Finalizing : RecordingState()
    data class Completed(val recordingId: Long, val durationMs: Long, val filePath: String) : RecordingState()
    data class Error(val message: String) : RecordingState()

    val isActive: Boolean
        get() = this is Recording || this is Paused || this is Finalizing

    val durationOrZero: Long
        get() = when (this) {
            is Recording -> durationMs
            is Paused -> durationMs
            is Completed -> durationMs
            else -> 0L
        }

    val recordingIdOrNull: Long?
        get() = when (this) {
            is Recording -> recordingId
            is Paused -> recordingId
            is Completed -> recordingId
            else -> null
        }

    fun canTransitionTo(target: RecordingState): Boolean {
        return when (this) {
            is Idle -> target is Recording || target is Error
            is Recording -> target is Recording || target is Paused || target is Finalizing || target is Error
            is Paused -> target is Recording || target is Finalizing || target is Error
            is Finalizing -> target is Completed || target is Error
            is Completed -> target is Idle || target is Recording
            is Error -> target is Idle || target is Recording
        }
    }
}
