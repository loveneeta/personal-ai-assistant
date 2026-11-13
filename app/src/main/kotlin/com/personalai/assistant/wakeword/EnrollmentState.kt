package com.personalai.assistant.wakeword

/**
 * Explicit state machine for speaker profile calibration & enrollment.
 */
sealed class EnrollmentState {
    object Unenrolled : EnrollmentState()
    data class InProgress(val currentPass: Int, val totalRequired: Int = SpeakerProfileManager.SAMPLES_REQUIRED) : EnrollmentState()
    data class ReadyToFinalize(val totalPasses: Int = SpeakerProfileManager.SAMPLES_REQUIRED) : EnrollmentState()
    object Enrolled : EnrollmentState()
    data class Error(val message: String) : EnrollmentState()
}
