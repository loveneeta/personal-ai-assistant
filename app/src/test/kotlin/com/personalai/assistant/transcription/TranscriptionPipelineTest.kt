package com.personalai.assistant.transcription

import com.personalai.assistant.transcription.models.AvailableModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionPipelineTest {

    @Test
    fun availableModels_containConfiguredOfflineEngines() {
        val models = AvailableModels.ALL_MODELS
        assertEquals(3, models.size)

        val inEnglish = AvailableModels.INDIAN_ENGLISH
        assertEquals("vosk-model-small-en-in-0.4", inEnglish.id)
        assertEquals("English (India)", inEnglish.language)
        assertTrue(inEnglish.downloadUrl.endsWith(".zip"))

        val hindi = AvailableModels.HINDI
        assertEquals("vosk-model-small-hi-0.22", hindi.id)
        assertEquals("Hindi", hindi.language)
        assertTrue(hindi.downloadUrl.endsWith(".zip"))
    }

    @Test
    fun transcriptSegment_preservesTimeOffsetsAndConfidence() {
        val segment = TranscriptSegment(
            startOffsetMs = 1500L,
            endOffsetMs = 4500L,
            text = "Namaste team, let us review the action items.",
            confidence = 0.98f,
            language = "en"
        )

        assertEquals(1500L, segment.startOffsetMs)
        assertEquals(4500L, segment.endOffsetMs)
        assertEquals(3000L, segment.endOffsetMs - segment.startOffsetMs)
        assertEquals("Namaste team, let us review the action items.", segment.text)
        assertTrue(segment.confidence > 0.9f)
    }

    @Test
    fun transcriptionProgress_stateTransitions() {
        val idle: TranscriptionProgress = TranscriptionProgress.Idle
        assertTrue(idle is TranscriptionProgress.Idle)

        val processing: TranscriptionProgress = TranscriptionProgress.Processing(1L, 0.45f, "Transcribing 45%...")
        assertEquals(1L, (processing as TranscriptionProgress.Processing).recordingId)
        assertEquals(0.45f, processing.progress, 0.001f)

        val completed: TranscriptionProgress = TranscriptionProgress.Completed(1L, 5)
        assertEquals(5, (completed as TranscriptionProgress.Completed).segmentCount)

        val failed: TranscriptionProgress = TranscriptionProgress.Failed(1L, "Model missing")
        assertEquals("Model missing", (failed as TranscriptionProgress.Failed).reason)
    }
}
