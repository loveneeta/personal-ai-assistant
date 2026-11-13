package com.personalai.assistant.wakeword

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.data.local.entity.WakeEventEntity
import com.personalai.assistant.data.repository.AppRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlin.math.sin

class FakeWakeWordEngine : WakeWordEngine {
    override val engineName: String = "FakeWakeWordEngine"
    override var isReady: Boolean = true
    override fun initialize(wakeWord: String, sensitivity: Float): Boolean = true
    override fun processPcmChunk(pcmData: ShortArray, length: Int): Boolean = false
    override fun reset() {}
    override fun release() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeWordStateMachineTest {

    private lateinit var context: Context
    private lateinit var speakerManager: SpeakerProfileManager
    private lateinit var repository: AppRepository
    private lateinit var fakeWakeWordEngine: FakeWakeWordEngine
    private lateinit var stateMachine: JarvisVoiceStateMachine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val app = Shadows.shadowOf(context as Application)
        app.grantPermissions(Manifest.permission.RECORD_AUDIO)

        JarvisVoiceCoordinator.forceRelease()
        speakerManager = SpeakerProfileManager(context)
        speakerManager.deleteProfile()
        repository = AppRepository.getInstance(context)
        fakeWakeWordEngine = FakeWakeWordEngine()
        stateMachine = JarvisVoiceStateMachine(context, repository, speakerManager, fakeWakeWordEngine)
    }

    @After
    fun tearDown() {
        stateMachine.release()
        JarvisVoiceCoordinator.forceRelease()
    }

    @Test
    fun testAudioStates_completeness() {
        val states = JarvisAudioState.values()
        assertTrue(states.contains(JarvisAudioState.IDLE_DISABLED))
        assertTrue(states.contains(JarvisAudioState.STARTING))
        assertTrue(states.contains(JarvisAudioState.WAKE_LISTENING))
        assertTrue(states.contains(JarvisAudioState.WAKE_DETECTED))
        assertTrue(states.contains(JarvisAudioState.VERIFYING_SPEAKER))
        assertTrue(states.contains(JarvisAudioState.COMMAND_LISTENING))
        assertTrue(states.contains(JarvisAudioState.PROCESSING))
        assertTrue(states.contains(JarvisAudioState.SPEAKING))
        assertTrue(states.contains(JarvisAudioState.PAUSED))
        assertTrue(states.contains(JarvisAudioState.STOPPED))
        assertTrue(states.contains(JarvisAudioState.PERMISSION_DENIED))
        assertTrue(states.contains(JarvisAudioState.ERROR))
    }

    @Test
    fun testMicrophonePause_actuallyStopsCaptureAndReleasesCoordinator() {
        stateMachine.startHandsFree()
        assertEquals(JarvisAudioState.WAKE_LISTENING, stateMachine.currentState.value)
        assertTrue("Microphone must be capturing", stateMachine.isCapturing())
        assertTrue("Desired listening must be true", stateMachine.isDesiredListening())
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)

        // Pause hands-free
        stateMachine.pauseHandsFree()

        assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
        assertFalse("PAUSED must mean microphone capture has actually stopped", stateMachine.isCapturing())
        assertFalse("PAUSED must mean desired listening is false", stateMachine.isDesiredListening())
        assertEquals("PAUSED must reset sound level", 0f, stateMachine.soundLevel.value, 0.001f)
        assertEquals("PAUSED must release microphone coordinator", AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testMicrophoneResume_startsCaptureLoopSafely() {
        stateMachine.startHandsFree()
        stateMachine.pauseHandsFree()
        assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())

        // Resume hands-free
        stateMachine.resumeHandsFree()

        assertEquals(JarvisAudioState.WAKE_LISTENING, stateMachine.currentState.value)
        assertTrue(stateMachine.isCapturing())
        assertTrue(stateMachine.isDesiredListening())
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testMicrophoneStop_releasesAllResources() {
        stateMachine.startHandsFree()
        assertTrue(stateMachine.isCapturing())

        // Stop hands-free
        stateMachine.stopHandsFree()

        assertEquals(JarvisAudioState.IDLE_DISABLED, stateMachine.currentState.value)
        assertFalse("STOPPED must mean capture resources released", stateMachine.isCapturing())
        assertFalse(stateMachine.isDesiredListening())
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testRepeatedPauseResume_doesNotDuplicateLoops() {
        stateMachine.startHandsFree()

        for (i in 1..5) {
            stateMachine.pauseHandsFree()
            assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
            assertFalse(stateMachine.isCapturing())
            assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)

            stateMachine.resumeHandsFree()
            assertEquals(JarvisAudioState.WAKE_LISTENING, stateMachine.currentState.value)
            assertTrue(stateMachine.isCapturing())
            assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)
        }

        // Duplicate resume calls while already in WAKE_LISTENING should be safely ignored
        stateMachine.resumeHandsFree()
        assertEquals(JarvisAudioState.WAKE_LISTENING, stateMachine.currentState.value)
        assertTrue(stateMachine.isCapturing())

        stateMachine.stopHandsFree()
        assertFalse(stateMachine.isCapturing())
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testPermissionRevocation_transitionsToPermissionDenied() {
        val app = Shadows.shadowOf(context as Application)
        app.denyPermissions(Manifest.permission.RECORD_AUDIO)

        stateMachine.startHandsFree()

        assertEquals(JarvisAudioState.PERMISSION_DENIED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testCoordinatorCollision_transitionsToPausedWhenMicUnavailable() {
        // High priority owner acquires mic
        assertTrue(JarvisVoiceCoordinator.requestMicrophone(AudioOwner.WAV_RECORDER))
        assertEquals(AudioOwner.WAV_RECORDER, JarvisVoiceCoordinator.currentOwner.value)

        stateMachine.startHandsFree()

        assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())
        assertEquals(AudioOwner.WAV_RECORDER, JarvisVoiceCoordinator.currentOwner.value)

        // Release WAV recorder
        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.WAV_RECORDER)
    }

    @Test
    fun testTtsOnDoneAfterPause_doesNotRestartListening() {
        val sessionId = stateMachine.simulateSpeakingState()
        assertEquals(JarvisAudioState.SPEAKING, stateMachine.currentState.value)

        // User pauses hands-free during TTS speech
        stateMachine.pauseHandsFree()
        assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())
        assertFalse(stateMachine.isDesiredListening())

        // In-flight TTS onDone callback arrives
        val job = stateMachine.handleTtsCompletion("JARVIS_RESPONSE_$sessionId", skipDelay = true)
        runBlocking { job?.join() }

        // Must remain PAUSED and microphone must NOT be reactivated
        assertEquals(JarvisAudioState.PAUSED, stateMachine.currentState.value)
        assertFalse("PAUSED must never capture audio after TTS completion", stateMachine.isCapturing())
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testTtsOnErrorAfterStop_doesNotRestartListening() {
        val sessionId = stateMachine.simulateSpeakingState()
        assertEquals(JarvisAudioState.SPEAKING, stateMachine.currentState.value)

        // User stops hands-free during speech
        stateMachine.stopHandsFree()
        assertEquals(JarvisAudioState.IDLE_DISABLED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())
        assertFalse(stateMachine.isDesiredListening())

        // TTS error callback arrives
        val job = stateMachine.handleTtsCompletion("JARVIS_RESPONSE_$sessionId", skipDelay = true)
        runBlocking { job?.join() }

        assertEquals(JarvisAudioState.IDLE_DISABLED, stateMachine.currentState.value)
        assertFalse(stateMachine.isCapturing())
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testStaleTtsCallbackRejection() {
        val session1 = stateMachine.simulateSpeakingState(101L)
        assertEquals(JarvisAudioState.SPEAKING, stateMachine.currentState.value)

        // User speaks a new command, advancing session ID
        val session2 = stateMachine.simulateSpeakingState(102L)
        assertEquals(102L, session2)

        // Stale callback from session 101 arrives
        val staleJob = stateMachine.handleTtsCompletion("JARVIS_RESPONSE_101", skipDelay = true)
        runBlocking { staleJob?.join() }

        // Must be rejected and remain in SPEAKING
        assertEquals(JarvisAudioState.SPEAKING, stateMachine.currentState.value)

        // Active callback from session 102 arrives
        val activeJob = stateMachine.handleTtsCompletion("JARVIS_RESPONSE_102", skipDelay = true)
        runBlocking { activeJob?.join() }

        // Transitions cleanly to WAKE_LISTENING
        assertEquals(JarvisAudioState.WAKE_LISTENING, stateMachine.currentState.value)
        assertTrue(stateMachine.isCapturing())
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testSpeakerEnrollment_threePassFlow() {
        speakerManager.startEnrollment()
        assertFalse(speakerManager.isEnrolled.value)

        // Generate synthetic speech-like acoustic PCM audio (440Hz harmonic at 16kHz)
        val sample1 = generateHarmonicPcm(440.0, 16000)
        val pass1 = speakerManager.addEnrollmentSample(sample1, sample1.size)
        assertEquals(1, pass1)

        val sample2 = generateHarmonicPcm(440.0, 16000)
        val pass2 = speakerManager.addEnrollmentSample(sample2, sample2.size)
        assertEquals(2, pass2)

        val sample3 = generateHarmonicPcm(440.0, 16000)
        val pass3 = speakerManager.addEnrollmentSample(sample3, sample3.size)
        assertEquals(3, pass3)

        val finalized = speakerManager.finalizeEnrollment()
        assertTrue(finalized)
        assertTrue(speakerManager.isEnrolled.value)
        assertTrue(speakerManager.isSpeakerVerificationEnabled.value)

        // Verify with matching speaker audio
        val testMatchingSample = generateHarmonicPcm(440.0, 16000)
        val result = speakerManager.verifySpeaker(testMatchingSample, testMatchingSample.size)
        assertTrue("Matching harmonic should be verified", result.isVerified)
        assertTrue("Similarity should be high", result.confidence >= speakerManager.getThreshold())

        // Verify with completely different voice pitch (180Hz male voice vs enrolled 440Hz)
        val testDifferentSample = generateHarmonicPcm(180.0, 16000)
        val diffResult = speakerManager.verifySpeaker(testDifferentSample, testDifferentSample.size)
        assertFalse("Different speaker pitch should be rejected", diffResult.isVerified)

        // Verify with noise
        val testNoiseSample = generateNoisePcm(16000)
        val noiseResult = speakerManager.verifySpeaker(testNoiseSample, testNoiseSample.size)
        assertFalse("Noise profile should be rejected", noiseResult.isVerified)

        // Test delete profile
        val deleted = speakerManager.deleteProfile()
        assertTrue(deleted)
        assertFalse(speakerManager.isEnrolled.value)
    }

    @Test
    fun testWakeEventEntity_modelIntegrity() {
        val event = WakeEventEntity(
            id = 1L,
            timestamp = 1729000000000L,
            device = "ANDROID",
            wakeWord = "Jarvis",
            speakerVerified = true,
            recognizedCommand = "What are my meetings today?",
            responseSummary = "You have 2 meetings scheduled.",
            status = "COMPLETED"
        )

        assertEquals("ANDROID", event.device)
        assertEquals("Jarvis", event.wakeWord)
        assertTrue(event.speakerVerified)
        assertEquals("What are my meetings today?", event.recognizedCommand)
        assertEquals("COMPLETED", event.status)

        // Windows cross-device parity
        val winEvent = event.copy(id = 2L, device = "WINDOWS")
        assertEquals("WINDOWS", winEvent.device)
    }

    private fun generateHarmonicPcm(freq: Double, samples: Int): ShortArray {
        val pcm = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / 16000.0
            val v = (sin(2.0 * Math.PI * freq * t) * 12000.0 + sin(2.0 * Math.PI * (freq * 2) * t) * 6000.0).toInt()
            pcm[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun generateNoisePcm(samples: Int): ShortArray {
        val pcm = ShortArray(samples)
        var seed = 123456789L
        for (i in 0 until samples) {
            seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
            val r = ((seed shr 16) and 0xFFFF) - 32768
            pcm[i] = r.toShort()
        }
        return pcm
    }

    @Test
    fun testVoiceCoordinator_mutualExclusion() {
        JarvisVoiceCoordinator.forceRelease()
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)

        // Always On requests mic
        val acquired1 = JarvisVoiceCoordinator.requestMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
        assertTrue(acquired1)
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)

        // User starts WAV recording -> overrides Always On cleanly
        val acquired2 = JarvisVoiceCoordinator.requestMicrophone(AudioOwner.WAV_RECORDER)
        assertTrue(acquired2)
        assertEquals(AudioOwner.WAV_RECORDER, JarvisVoiceCoordinator.currentOwner.value)

        // Voice Matrix attempts to acquire while WAV is running -> denied to avoid hardware conflict
        val acquired3 = JarvisVoiceCoordinator.requestMicrophone(AudioOwner.VOICE_MATRIX)
        assertFalse(acquired3)

        // WAV finishes -> restores Always On
        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.WAV_RECORDER)
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)

        // Always on releases
        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testVoiceCoordinator_multiLevelPriorityPreemptionStack() {
        JarvisVoiceCoordinator.forceRelease()
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)

        // 1. Background listener acquires mic (priority 10)
        assertTrue(JarvisVoiceCoordinator.requestMicrophone(AudioOwner.ALWAYS_ON_LISTENER))
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)

        // 2. STT Voice Matrix preempts Always-On (priority 20 > 10)
        assertTrue(JarvisVoiceCoordinator.requestMicrophone(AudioOwner.VOICE_MATRIX))
        assertEquals(AudioOwner.VOICE_MATRIX, JarvisVoiceCoordinator.currentOwner.value)

        // 3. User Voice Profile Enrollment preempts STT (priority 30 > 20)
        assertTrue(JarvisVoiceCoordinator.requestMicrophone(AudioOwner.ENROLLMENT))
        assertEquals(AudioOwner.ENROLLMENT, JarvisVoiceCoordinator.currentOwner.value)

        // 4. Foreground WAV recorder preempts Enrollment (priority 40 > 30)
        assertTrue(JarvisVoiceCoordinator.requestMicrophone(AudioOwner.WAV_RECORDER))
        assertEquals(AudioOwner.WAV_RECORDER, JarvisVoiceCoordinator.currentOwner.value)

        // Unwinding the stack in reverse priority order:
        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.WAV_RECORDER)
        assertEquals(AudioOwner.ENROLLMENT, JarvisVoiceCoordinator.currentOwner.value)

        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ENROLLMENT)
        assertEquals(AudioOwner.VOICE_MATRIX, JarvisVoiceCoordinator.currentOwner.value)

        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.VOICE_MATRIX)
        assertEquals(AudioOwner.ALWAYS_ON_LISTENER, JarvisVoiceCoordinator.currentOwner.value)

        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.ALWAYS_ON_LISTENER)
        assertEquals(AudioOwner.NONE, JarvisVoiceCoordinator.currentOwner.value)
    }

    @Test
    fun testSpeakerProfile_explicitEnrollmentStateMachine() {
        speakerManager.deleteProfile()
        assertTrue(speakerManager.enrollmentState.value is EnrollmentState.Unenrolled)

        speakerManager.startEnrollment()
        val inProgress0 = speakerManager.enrollmentState.value as EnrollmentState.InProgress
        assertEquals(0, inProgress0.currentPass)

        val sample = generateHarmonicPcm(440.0, 16000)
        speakerManager.addEnrollmentSample(sample, sample.size)
        val inProgress1 = speakerManager.enrollmentState.value as EnrollmentState.InProgress
        assertEquals(1, inProgress1.currentPass)

        speakerManager.addEnrollmentSample(sample, sample.size)
        val inProgress2 = speakerManager.enrollmentState.value as EnrollmentState.InProgress
        assertEquals(2, inProgress2.currentPass)

        speakerManager.addEnrollmentSample(sample, sample.size)
        assertTrue(speakerManager.enrollmentState.value is EnrollmentState.ReadyToFinalize)

        val success = speakerManager.finalizeEnrollment()
        assertTrue(success)
        assertTrue(speakerManager.enrollmentState.value is EnrollmentState.Enrolled)

        speakerManager.deleteProfile()
        assertTrue(speakerManager.enrollmentState.value is EnrollmentState.Unenrolled)
    }
}
