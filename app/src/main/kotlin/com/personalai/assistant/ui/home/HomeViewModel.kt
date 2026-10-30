package com.personalai.assistant.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.WakeEventEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.recording.AudioRecordingService
import com.personalai.assistant.recording.RecordingState
import com.personalai.assistant.transcription.GoogleVoiceRecognitionManager
import com.personalai.assistant.transcription.models.ModelInfo
import com.personalai.assistant.transcription.models.ModelManager
import com.personalai.assistant.wakeword.JarvisAlwaysOnService
import com.personalai.assistant.wakeword.JarvisAudioState
import com.personalai.assistant.wakeword.SpeakerProfileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VoiceInputMode {
    HANDS_FREE_WAKE,   // Hands-free "Jarvis" wake word always-listening foreground service
    GOOGLE_VOICE,      // Real-time voice typing with live reading & whole sentence completion
    OFFLINE_RECORDING  // Native 16kHz PCM WAV with post-recording whole-sentence AI synthesis
}

class HomeViewModel(
    private val repository: AppRepository,
    private val modelManager: ModelManager,
    private val googleVoiceManager: GoogleVoiceRecognitionManager
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = AudioRecordingService.recordingState

    val todayRecordings: StateFlow<List<RecordingEntity>> =
        repository.getTodayRecordingsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentRecordings: StateFlow<List<RecordingEntity>> =
        repository.getRecentRecordingsFlow(5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentWakeEvents: StateFlow<List<WakeEventEntity>> =
        repository.getRecentWakeEventsFlow(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelId: StateFlow<String> = modelManager.activeModelId
    val modelsState: StateFlow<List<ModelInfo>> = modelManager.modelsState

    // Live Google Voice Engine states
    val isGoogleVoiceListening: StateFlow<Boolean> = googleVoiceManager.isListening
    val googleVoiceLiveHypothesis: StateFlow<String> = googleVoiceManager.liveHypothesis
    val googleVoiceCompletedSentences: StateFlow<List<String>> = googleVoiceManager.completedSentences
    val googleVoiceSoundLevel: StateFlow<Float> = googleVoiceManager.soundLevel
    val googleVoiceError: StateFlow<String?> = googleVoiceManager.errorMessage
    val isGoogleVoiceAvailable: Boolean = googleVoiceManager.isRecognitionAvailable
    val isOnDeviceGoogleVoice: Boolean = googleVoiceManager.isOnDeviceAvailable

    // Always-Ready Hands-Free Wake-Word states
    val alwaysOnState: StateFlow<JarvisAudioState> = JarvisAlwaysOnService.serviceState
    val alwaysOnSoundLevel: StateFlow<Float> = JarvisAlwaysOnService.soundLevel

    init {
        // Automatically reconcile incomplete recordings from crashes on launch
        viewModelScope.launch {
            repository.reconcileIncompleteRecordings()
            modelManager.refreshModelsState()
        }
    }

    fun setActiveModel(modelId: String) {
        modelManager.setActiveModel(modelId)
    }

    // Always-On Hands-Free Controls
    fun startAlwaysOn(context: Context) {
        JarvisAlwaysOnService.start(context)
    }

    fun pauseAlwaysOn(context: Context) {
        JarvisAlwaysOnService.pause(context)
    }

    fun resumeAlwaysOn(context: Context) {
        JarvisAlwaysOnService.resume(context)
    }

    fun stopAlwaysOn(context: Context) {
        JarvisAlwaysOnService.stop(context)
    }

    // Speaker Profile & Voice Verification
    fun isSpeakerEnrolled(context: Context): Boolean {
        return SpeakerProfileManager.getInstance(context).isEnrolled.value
    }

    fun isSpeakerVerificationEnabled(context: Context): Boolean {
        return SpeakerProfileManager.getInstance(context).isSpeakerVerificationEnabled.value
    }

    fun toggleSpeakerVerification(context: Context, enabled: Boolean) {
        SpeakerProfileManager.getInstance(context).setVerificationEnabled(enabled)
    }

    fun deleteSpeakerProfile(context: Context) {
        SpeakerProfileManager.getInstance(context).deleteProfile()
    }

    fun enrollVoiceSample(context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = SpeakerProfileManager.getInstance(context).recordAndProcessEnrollmentPass()
            result.onSuccess { pass ->
                onResult(true, "Captured sample #$pass")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to capture sample")
            }
        }
    }

    fun finalizeVoiceEnrollment(context: Context): Boolean {
        return SpeakerProfileManager.getInstance(context).finalizeEnrollment()
    }

    // Recording Controls
    fun startRecording(context: Context, title: String? = null) {
        AudioRecordingService.start(context, title)
    }

    fun pauseRecording(context: Context) {
        AudioRecordingService.pause(context)
    }

    fun resumeRecording(context: Context) {
        AudioRecordingService.resume(context)
    }

    fun stopRecording(context: Context) {
        AudioRecordingService.stop(context)
    }

    // Google Voice Keyboard style continuous listening
    fun startGoogleVoiceListening(languageCode: String = "hi-en") {
        googleVoiceManager.startListening(languageCode)
    }

    fun stopGoogleVoiceListening() {
        googleVoiceManager.stopListening()
    }

    fun saveGoogleVoiceSession(
        title: String? = null,
        languageCode: String = "hi-en",
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = googleVoiceManager.saveSession(title, languageCode)
            onSaved(id)
        }
    }

    fun reconcileRecordings() {
        viewModelScope.launch {
            repository.reconcileIncompleteRecordings()
        }
    }
}
