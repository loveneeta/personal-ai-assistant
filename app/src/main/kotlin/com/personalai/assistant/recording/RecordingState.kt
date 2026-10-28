package com.personalai.assistant.recording

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val durationMs: Long, val recordingId: Long, val title: String) : RecordingState()
    data class Paused(val durationMs: Long, val recordingId: Long, val title: String) : RecordingState()
    object Finalizing : RecordingState()
    data class Completed(val recordingId: Long, val durationMs: Long, val filePath: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}
