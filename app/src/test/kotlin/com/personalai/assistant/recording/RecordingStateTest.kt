package com.personalai.assistant.recording

import com.personalai.assistant.ui.home.formatMs
import org.junit.Assert.assertEquals
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

        val rec: RecordingState = RecordingState.Recording(5000, 1L, "Morning Sync")
        assertEquals(5000L, (rec as RecordingState.Recording).durationMs)
        assertEquals("Morning Sync", rec.title)

        val paused: RecordingState = RecordingState.Paused(5000, 1L, "Morning Sync")
        assertTrue(paused is RecordingState.Paused)

        val completed: RecordingState = RecordingState.Completed(1L, 10000L, "/path/rec.wav")
        assertEquals("/path/rec.wav", (completed as RecordingState.Completed).filePath)
    }
}
