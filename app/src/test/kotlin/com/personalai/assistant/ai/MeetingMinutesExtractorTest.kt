package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingMinutesExtractorTest {

    @Test
    fun testEmptyTranscriptsHandledGracefully() {
        val recording = RecordingEntity(id = 1, title = "Empty Session", filePath = "/dummy/1.wav", startTimestamp = 1000L)
        val result = MeetingMinutesExtractor.extract(recording, emptyList())

        assertEquals("Empty Session", result.title)
        assertTrue(result.actionItems.isEmpty())
        assertTrue(result.decisions.isEmpty())
        assertFalse(result.needsReview)
    }

    @Test
    fun testDecisionsAndActionItemsExtraction() {
        val recording = RecordingEntity(id = 2, title = "Sprint Planning", filePath = "/dummy/2.wav", startTimestamp = 1000L)
        val transcripts = listOf(
            TranscriptEntity(
                recordingId = 2,
                segmentIndex = 0,
                startOffsetMs = 0,
                endOffsetMs = 4000,
                text = "Hello everyone, welcome to the sprint planning session with Rahul and Priya."
            ),
            TranscriptEntity(
                recordingId = 2,
                segmentIndex = 1,
                startOffsetMs = 5000,
                endOffsetMs = 9000,
                text = "We agreed to migrate the database schema to Room v2."
            ),
            TranscriptEntity(
                recordingId = 2,
                segmentIndex = 2,
                startOffsetMs = 10000,
                endOffsetMs = 15000,
                text = "Rahul will do the user authentication module by tomorrow."
            ),
            TranscriptEntity(
                recordingId = 2,
                segmentIndex = 3,
                startOffsetMs = 16000,
                endOffsetMs = 20000,
                text = "What about the network timeout blocker?"
            )
        )

        val result = MeetingMinutesExtractor.extract(recording, transcripts)

        // Verify detected attendees
        assertTrue("Expected Rahul to be detected", result.attendees.contains("Rahul"))
        assertTrue("Expected Priya to be detected", result.attendees.contains("Priya"))

        // Verify decision
        assertEquals(1, result.decisions.size)
        assertTrue(result.decisions[0].text.contains("agreed to migrate"))

        // Verify action item
        assertEquals(1, result.actionItems.size)
        val task = result.actionItems[0]
        assertEquals("Rahul", task.assignedPerson)
        assertNotNull("Expected due date to be inferred for tomorrow", task.dueDate)

        // Verify open question
        assertEquals(1, result.openQuestions.size)
        assertTrue(result.openQuestions[0].text.contains("blocker"))
    }
}
