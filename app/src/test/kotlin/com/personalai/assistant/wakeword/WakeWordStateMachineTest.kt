package com.personalai.assistant.wakeword

import androidx.test.core.app.ApplicationProvider
import com.personalai.assistant.data.local.entity.WakeEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeWordStateMachineTest {

    private lateinit var speakerManager: SpeakerProfileManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        speakerManager = SpeakerProfileManager(context)
        speakerManager.deleteProfile()
    }

    @Test
    fun testAudioStates_completeness() {
        // Ensure all required state transitions exist in the state machine
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

}
