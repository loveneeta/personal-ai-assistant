package com.personalai.assistant.wakeword

/**
 * Explicit audio state machine representing the lifecycle of JARVIS's
 * hands-free always-listening, wake detection, speaker verification,
 * and command execution pipeline.
 */
enum class JarvisAudioState {
    IDLE_DISABLED,       // Hands-free mode is toggled off in settings
    STARTING,            // Initializing audio capture and wake-word detector
    WAKE_LISTENING,      // Low-power background listening for "Jarvis" wake-word
    WAKE_DETECTED,       // Wake-word spotted; audible/visual chime triggered
    VERIFYING_SPEAKER,   // Checking user voice template fingerprint (if enabled)
    COMMAND_LISTENING,   // Actively capturing the user's spoken command (Hindi/English/Hinglish)
    PROCESSING,          // Running local NLU / RAG query against memory index
    SPEAKING,            // Generating and speaking out assistant response via TTS
    PAUSED,              // Temporarily paused by user or phone call / audio focus loss
    STOPPED,             // Explicitly stopped by user command ("Jarvis, stop listening")
    PERMISSION_DENIED,   // Record audio permission missing or revoked
    ERROR                // Hardware, microphone, or model initialization error
}
