package com.personalai.assistant.wakeword

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOwner {
    NONE,
    ALWAYS_ON_LISTENER,
    WAV_RECORDER,
    VOICE_MATRIX,
    ENROLLMENT
}

/**
 * Centralized Microphone Coordinator.
 *
 * Prevents multiple independent modules from competing for Android's single hardware
 * microphone simultaneously, avoiding bus collisions, empty recordings, and audio crashes.
 */
object JarvisVoiceCoordinator {
    private const val TAG = "JarvisVoiceCoordinator"

    private val _currentOwner = MutableStateFlow(AudioOwner.NONE)
    val currentOwner: StateFlow<AudioOwner> = _currentOwner.asStateFlow()

    private var previousOwnerBeforeOverride: AudioOwner = AudioOwner.NONE

    @Synchronized
    fun requestMicrophone(requester: AudioOwner): Boolean {
        val current = _currentOwner.value
        Log.i(TAG, "Requesting microphone for $requester (current owner: $current)")

        if (current == requester) {
            return true
        }

        // Higher priority transient owners can temporarily pause Always-On
        if (current == AudioOwner.ALWAYS_ON_LISTENER) {
            previousOwnerBeforeOverride = current
            _currentOwner.value = requester
            return true
        }

        if (current == AudioOwner.NONE) {
            _currentOwner.value = requester
            return true
        }

        // Microphone is actively held by another session
        Log.w(TAG, "Microphone request denied for $requester because $current is active")
        return false
    }

    @Synchronized
    fun releaseMicrophone(requester: AudioOwner) {
        if (_currentOwner.value == requester) {
            Log.i(TAG, "Releasing microphone from $requester")
            if (previousOwnerBeforeOverride == AudioOwner.ALWAYS_ON_LISTENER) {
                _currentOwner.value = AudioOwner.ALWAYS_ON_LISTENER
                previousOwnerBeforeOverride = AudioOwner.NONE
            } else {
                _currentOwner.value = AudioOwner.NONE
            }
        }
    }

    @Synchronized
    fun forceRelease() {
        Log.i(TAG, "Force releasing all microphone claims")
        _currentOwner.value = AudioOwner.NONE
        previousOwnerBeforeOverride = AudioOwner.NONE
    }
}
