package com.personalai.assistant.ai

import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailySummaryReport(
    val title: String,
    val dateString: String,
    val markdownContent: String,
    val totalRecordings: Int,
    val totalAudioDurationMs: Long,
    val pendingTasksCount: Int,
    val completedTasksCount: Int,
    val meetingsCount: Int
)

object DailySummarySynthesizer {

    fun synthesize(
        date: Date = Date(),
        todayRecordings: List<RecordingEntity>,
        todayMeetings: List<MeetingEntity>,
        allTasks: List<TaskEntity>,
        todayNotes: List<NoteEntity> = emptyList()
    ): DailySummaryReport {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(date)
        val shortDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)

        val totalDurationMs = todayRecordings.sumOf { it.durationMs }
        val completedToday = allTasks.filter { it.status == "COMPLETED" }
        val pendingTasks = allTasks.filter { it.status != "COMPLETED" }

        val sb = StringBuilder()
        sb.append("# Daily Personal AI Summary\n")
        sb.append("**Date:** $dateStr\n\n")

        sb.append("## Overview\n")
        val mins = totalDurationMs / 1000 / 60
        sb.append("- **Audio Logged:** ${todayRecordings.size} sessions ($mins minutes recorded)\n")
        sb.append("- **Meetings Held:** ${todayMeetings.size}\n")
        sb.append("- **Pending Action Items:** ${pendingTasks.size}\n")
        sb.append("- **Completed Tasks:** ${completedToday.size}\n\n")

        if (todayMeetings.isNotEmpty()) {
            sb.append("## Meetings & Discussion Highlights\n")
            for (m in todayMeetings) {
                sb.append("### ${m.title}\n")
                if (!m.summary.isNullOrBlank()) {
                    sb.append("${m.summary}\n\n")
                } else {
                    sb.append("_No detailed summary available._\n\n")
                }
            }
        }

        if (pendingTasks.isNotEmpty()) {
            sb.append("## Open Action Items & Priorities\n")
            for (task in pendingTasks) {
                val assignee = if (!task.assignedPerson.isNullOrBlank()) " (@${task.assignedPerson})" else ""
                val due = if (task.dueDate != null) {
                    " [Due: ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(task.dueDate))}]"
                } else ""
                sb.append("- [ ] **${task.title}**$assignee$due\n")
            }
            sb.append("\n")
        }

        if (completedToday.isNotEmpty()) {
            sb.append("## Completed Accomplishments\n")
            for (task in completedToday) {
                sb.append("- [x] ~~${task.title}~~\n")
            }
            sb.append("\n")
        }

        if (todayNotes.isNotEmpty()) {
            sb.append("## Personal Notes Captured\n")
            for (n in todayNotes) {
                sb.append("- **${n.title}:** ${n.content.take(120)}...\n")
            }
            sb.append("\n")
        }

        sb.append("---\n")
        sb.append("_Generated 100% on-device by Personal AI Assistant. Zero cloud cost._\n")

        return DailySummaryReport(
            title = "Daily Summary - $shortDate",
            dateString = dateStr,
            markdownContent = sb.toString(),
            totalRecordings = todayRecordings.size,
            totalAudioDurationMs = totalDurationMs,
            pendingTasksCount = pendingTasks.size,
            completedTasksCount = completedToday.size,
            meetingsCount = todayMeetings.size
        )
    }
}
