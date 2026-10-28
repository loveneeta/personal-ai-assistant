package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class DailySummarySynthesizerTest {

    @Test
    fun testDailySummarySynthesis() {
        val today = Date()
        val recordings = listOf(
            RecordingEntity(id = 1, title = "Standup", filePath = "/dummy/1.wav", startTimestamp = System.currentTimeMillis(), durationMs = 600000L),
            RecordingEntity(id = 2, title = "Design Review", filePath = "/dummy/2.wav", startTimestamp = System.currentTimeMillis(), durationMs = 1200000L)
        )
        val meetings = listOf(
            MeetingEntity(id = 1, title = "Morning Standup", startTimestamp = System.currentTimeMillis(), summary = "Discussed release schedule.")
        )
        val tasks = listOf(
            TaskEntity(id = 1, title = "Fix UI padding", status = "COMPLETED"),
            TaskEntity(id = 2, title = "Submit Play Store build", status = "PENDING", assignedPerson = "Loveneet")
        )
        val notes = listOf(
            NoteEntity(id = 1, title = "Idea", content = "Add widget for recording tile")
        )

        val report = DailySummarySynthesizer.synthesize(
            date = today,
            todayRecordings = recordings,
            todayMeetings = meetings,
            allTasks = tasks,
            todayNotes = notes
        )

        assertEquals(2, report.totalRecordings)
        assertEquals(1800000L, report.totalAudioDurationMs)
        assertEquals(1, report.pendingTasksCount)
        assertEquals(1, report.completedTasksCount)
        assertEquals(1, report.meetingsCount)

        assertTrue(report.markdownContent.contains("Daily Personal AI Summary"))
        assertTrue(report.markdownContent.contains("Morning Standup"))
        assertTrue(report.markdownContent.contains("Submit Play Store build"))
        assertTrue(report.markdownContent.contains("Fix UI padding"))
    }
}
