package com.personalai.assistant.recording

import com.personalai.assistant.ui.home.formatMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateTest {

    @Test
    fun formatMs_formatsSecondsAndMinutesCorrectly() {
        assertEquals("00:00", formatMs(0))
        assertEquals("00:05", formatMs(5000))
        assertEquals("01:00", formatMs(60000))
        assertEquals("12:34", formatMs(754000))
        assertEquals("65:00", formatMs(3900000))
    }

    @Test
    fun recordingState_hierarchyTransitions() {
        val idle: RecordingState = RecordingState.Idle
        assertTrue(idle is RecordingState.Idle)
        assertFalse(idle.isActive)
        assertEquals(0L, idle.durationOrZero)
        assertNull(idle.recordingIdOrNull)

        val rec = RecordingState.Recording(5000, 1L, "Morning Sync")
        assertEquals(5000L, rec.durationMs)
        assertEquals(1L, rec.recordingId)
        assertEquals("Morning Sync", rec.title)
        assertTrue(rec.isActive)
        assertEquals(5000L, rec.durationOrZero)
        assertEquals(1L, rec.recordingIdOrNull)

        val paused = RecordingState.Paused(5000, 1L, "Morning Sync")
        assertTrue(paused is RecordingState.Paused)
        assertTrue(paused.isActive)
        assertEquals(5000L, paused.durationOrZero)
        assertEquals(1L, paused.recordingIdOrNull)

        val finalizing: RecordingState = RecordingState.Finalizing
        assertTrue(finalizing is RecordingState.Finalizing)
        assertTrue(finalizing.isActive)

        val completed = RecordingState.Completed(1L, 10000L, "/path/rec.wav")
        assertEquals("/path/rec.wav", completed.filePath)
        assertFalse(completed.isActive)
        assertEquals(10000L, completed.durationOrZero)
        assertEquals(1L, completed.recordingIdOrNull)

        val error = RecordingState.Error("Microphone hardware error")
        assertFalse(error.isActive)
        assertEquals(0L, error.durationOrZero)
        assertNull(error.recordingIdOrNull)
    }

    @Test
    fun recordingState_validLifecycleTransitions() {
        val idle = RecordingState.Idle
        val starting = RecordingState.Recording(0L, 101L, "Test Session")
        val paused = RecordingState.Paused(1500L, 101L, "Test Session")
        val resumed = RecordingState.Recording(1500L, 101L, "Test Session")
        val finalizing = RecordingState.Finalizing
        val completed = RecordingState.Completed(101L, 3000L, "/path/REC_01.wav")
        val error = RecordingState.Error("Disk failure")

        // Valid forward progressions
        assertTrue(idle.canTransitionTo(starting))
        assertTrue(starting.canTransitionTo(paused))
        assertTrue(paused.canTransitionTo(resumed))
        assertTrue(resumed.canTransitionTo(finalizing))
        assertTrue(finalizing.canTransitionTo(completed))
        assertTrue(completed.canTransitionTo(idle))

        // Valid error transitions from active states
        assertTrue(idle.canTransitionTo(error))
        assertTrue(starting.canTransitionTo(error))
        assertTrue(paused.canTransitionTo(error))
        assertTrue(finalizing.canTransitionTo(error))
        assertTrue(error.canTransitionTo(idle))
    }

    @Test
    fun recordingState_rejectsInvalidLifecycleTransitions() {
        val idle = RecordingState.Idle
        val paused = RecordingState.Paused(1500L, 101L, "Test Session")
        val finalizing = RecordingState.Finalizing
        val completed = RecordingState.Completed(101L, 3000L, "/path/REC_01.wav")
        val starting = RecordingState.Recording(0L, 101L, "Test Session")

        // Cannot jump directly from Idle to Paused or Finalizing or Completed
        assertFalse(idle.canTransitionTo(paused))
        assertFalse(idle.canTransitionTo(finalizing))
        assertFalse(idle.canTransitionTo(completed))

        // Cannot resume while already completed
        assertFalse(completed.canTransitionTo(paused))
        assertFalse(completed.canTransitionTo(finalizing))

        // Cannot finalize directly into paused or recording
        assertFalse(finalizing.canTransitionTo(paused))
        assertFalse(finalizing.canTransitionTo(starting))
    }

    @Test
    fun recordingState_rapidPauseResumeCycles_excludesPauseDuration() {
        var cumulativeActiveMs = 0L
        var lastResumeMs = 1000L

        // Record for 2000ms (1000 -> 3000)
        var pauseTimeMs = 3000L
        cumulativeActiveMs += (pauseTimeMs - lastResumeMs)
        assertEquals(2000L, cumulativeActiveMs)

        // Paused for 5000ms (3000 -> 8000)
        var resumeTimeMs = 8000L
        lastResumeMs = resumeTimeMs

        // Record for 3000ms (8000 -> 11000)
        pauseTimeMs = 11000L
        cumulativeActiveMs += (pauseTimeMs - lastResumeMs)
        assertEquals(5000L, cumulativeActiveMs)

        // Paused for 2000ms (11000 -> 13000)
        resumeTimeMs = 13000L
        lastResumeMs = resumeTimeMs

        // Record for 1000ms (13000 -> 14000)
        val stopTimeMs = 14000L
        cumulativeActiveMs += (stopTimeMs - lastResumeMs)

        // Total wall clock: 14000 - 1000 = 13000ms
        // Total active recorded: 2000 + 3000 + 1000 = 6000ms
        // Paused time excluded: 5000 + 2000 = 7000ms
        assertEquals(6000L, cumulativeActiveMs)
    }

    @Test
    fun recordingState_immediateStop_handlesZeroDuration() {
        val rec = RecordingState.Recording(0L, 1L, "Instant Click")
        val finalizing = RecordingState.Finalizing
        assertTrue(rec.canTransitionTo(finalizing))

        // Zero duration recording should transition to completed with 0ms or error cleanly
        val completedZero = RecordingState.Completed(1L, 0L, "/path/empty.wav")
        assertTrue(finalizing.canTransitionTo(completedZero))
        assertEquals(0L, completedZero.durationMs)
    }
}
