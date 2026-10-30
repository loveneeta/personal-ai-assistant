package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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

    @Test
    fun testHinglishActionItemAndDeadlinesWithoutHallucinatingAttendees() {
        // Target prompt test case:
        // "Kal customer ki meeting hai, please quotation ready kar dena before five PM."
        val recording = RecordingEntity(id = 3, title = "Client Discussion", filePath = "/dummy/3.wav", startTimestamp = 2000L)
        val transcripts = listOf(
            TranscriptEntity(
                recordingId = 3,
                segmentIndex = 0,
                startOffsetMs = 0,
                endOffsetMs = 4500,
                text = "Kal customer ki meeting hai, please quotation ready kar dena before five PM."
            ),
            TranscriptEntity(
                recordingId = 3,
                segmentIndex = 1,
                startOffsetMs = 5000,
                endOffsetMs = 9000,
                text = "Yeh final hua ki discount ten percent rahega."
            ),
            TranscriptEntity(
                recordingId = 3,
                segmentIndex = 2,
                startOffsetMs = 10000,
                endOffsetMs = 14000,
                text = "Delivery timeline ka kya hoga?"
            )
        )

        val result = MeetingMinutesExtractor.extract(recording, transcripts)

        // 1. Verify NO attendees were hallucinated (no person was introduced or called out)
        assertTrue("No attendees should be hallucinated", result.attendees.isEmpty())

        // 2. Verify Hinglish Action Item
        assertEquals(1, result.actionItems.size)
        val action = result.actionItems[0]
        assertTrue("Action title should be cleaned: ${action.title}",
            action.title.contains("Quotation ready kar dena", ignoreCase = true))

        // 3. Verify Due Date extracted: tomorrow at 17:00 (5 PM)
        assertNotNull("Due date must be set for tomorrow", action.dueDate)
        val cal = Calendar.getInstance().apply { timeInMillis = action.dueDate!! }
        assertEquals(17, cal.get(Calendar.HOUR_OF_DAY))

        // 4. Verify Hinglish Decision
        assertEquals(1, result.decisions.size)
        assertTrue(result.decisions[0].text.contains("final hua"))

        // 5. Verify Hinglish Question
        assertEquals(1, result.openQuestions.size)
        assertTrue(result.openQuestions[0].text.contains("kya hoga"))
    }

    @Test
    fun testCommonWordsNotTreatedAsAttendees() {
        val recording = RecordingEntity(id = 4, title = "Strategy Review", filePath = "/dummy/4.wav", startTimestamp = 3000L)
        val transcripts = listOf(
            TranscriptEntity(
                recordingId = 4,
                segmentIndex = 0,
                startOffsetMs = 0,
                endOffsetMs = 5000,
                text = "We studied the market situation and customer response."
            )
        )

        val result = MeetingMinutesExtractor.extract(recording, transcripts)
        assertTrue("Common nouns should not trigger attendees", result.attendees.isEmpty())
    }
}
