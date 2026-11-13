package com.personalai.assistant.wakeword

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

enum class AudioOwner(val priority: Int = 0) {
    NONE(0),
    ALWAYS_ON_LISTENER(10),
    VOICE_MATRIX(20),
    ENROLLMENT(30),
    WAV_RECORDER(40)
}

/**
 * Centralized Microphone Coordinator with Priority Preemption Stack.
 *
 * Prevents multiple independent modules from competing for Android's single hardware
 * microphone simultaneously, avoiding bus collisions, empty recordings, and audio crashes.
 * Higher priority owners preempt lower priority owners, and preempted owners are restored
 * via an ArrayDeque priority stack when higher priority sessions finish.
 */
object JarvisVoiceCoordinator {
    private const val TAG = "JarvisVoiceCoordinator"

    private val _currentOwner = MutableStateFlow(AudioOwner.NONE)
    val currentOwner: StateFlow<AudioOwner> = _currentOwner.asStateFlow()

    private val preemptionStack = ArrayDeque<AudioOwner>()

    @Synchronized
    fun requestMicrophone(requester: AudioOwner): Boolean {
        val current = _currentOwner.value
        Log.i(TAG, "Requesting microphone for $requester (priority ${requester.priority}) - Current: $current (priority ${current.priority}), Stack: $preemptionStack")

        if (current == requester) {
            return true
        }

        if (current == AudioOwner.NONE) {
            _currentOwner.value = requester
            return true
        }

        // Check priority preemption: strictly higher priority can preempt
        if (requester.priority > current.priority) {
            Log.i(TAG, "Preempting $current (priority ${current.priority}) for $requester (priority ${requester.priority})")
            preemptionStack.push(current)
            _currentOwner.value = requester
            return true
        }

        // Equal or lower priority is rejected
        Log.w(TAG, "Microphone request denied for $requester (${requester.priority}) because $current (${current.priority}) is active")
        return false
    }

    @Synchronized
    fun releaseMicrophone(requester: AudioOwner) {
        // If the requester was queued in the preemption stack, clean it up
        preemptionStack.remove(requester)

        if (_currentOwner.value == requester) {
            Log.i(TAG, "Releasing microphone from $requester")
            val nextOwner = if (preemptionStack.isNotEmpty()) {
                preemptionStack.pop()
            } else {
                AudioOwner.NONE
            }
            Log.i(TAG, "Restored microphone owner to $nextOwner (remaining stack: $preemptionStack)")
            _currentOwner.value = nextOwner
        }
    }

    @Synchronized
    fun forceRelease() {
        Log.i(TAG, "Force releasing all microphone claims and clearing preemption stack")
        preemptionStack.clear()
        _currentOwner.value = AudioOwner.NONE
    }
}
