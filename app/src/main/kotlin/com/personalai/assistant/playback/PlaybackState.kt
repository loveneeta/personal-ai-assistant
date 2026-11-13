package com.personalai.assistant.playback

/**
 * Type-safe explicit state machine governing the complete lifecycle of audio playback.
 * Eliminates out-of-order MediaPlayer calls and prevents native IllegalStateException crashes.
 */
sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Loading(val filePath: String) : PlaybackState()
    data class Playing(val filePath: String, val positionMs: Int, override val durationMs: Int) : PlaybackState()
    data class Paused(val filePath: String, val positionMs: Int, override val durationMs: Int) : PlaybackState()
    object Stopped : PlaybackState()
    data class Completed(val filePath: String, override val durationMs: Int) : PlaybackState()
    data class Error(val filePath: String?, val message: String) : PlaybackState()

    val isPlaying: Boolean
        get() = this is Playing

    val isPaused: Boolean
        get() = this is Paused

    val isLoading: Boolean
        get() = this is Loading

    val isActive: Boolean
        get() = this is Playing || this is Paused || this is Loading

    val currentPath: String?
        get() = when (this) {
            is Loading -> filePath
            is Playing -> filePath
            is Paused -> filePath
            is Completed -> filePath
            is Error -> null
            else -> null
        }

    val currentPositionMs: Int
        get() = when (this) {
            is Playing -> positionMs
            is Paused -> positionMs
            is Completed -> durationMs
            else -> 0
        }

    open val durationMs: Int
        get() = 0

    fun canTransitionTo(target: PlaybackState): Boolean {
        return when (this) {
            is Idle -> target is Loading || target is Error
            is Loading -> target is Playing || target is Error || target is Stopped || target is Idle
            is Playing -> target is Paused || target is Stopped || target is Completed || target is Error || target is Loading
            is Paused -> target is Playing || target is Stopped || target is Error || target is Loading
            is Stopped -> target is Loading || target is Idle || target is Error
            is Completed -> target is Loading || target is Idle || target is Playing || target is Error
            is Error -> target is Loading || target is Idle || target is Stopped
        }
    }
}
