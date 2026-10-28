package com.personalai.assistant.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.personalai.assistant.ai.ExtractedMeetingMinutes
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    fun exportMeetingToPdf(
        context: Context,
        meeting: MeetingEntity,
        tasks: List<TaskEntity>,
        transcripts: List<TranscriptEntity>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(24, 76, 120)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 40f
        val left = 40f
        val right = 555f

        // Header
        canvas.drawText("PERSONAL AI ASSISTANT — MEETING MINUTES", left, y, headerPaint)
        y += 24f

        // Title
        canvas.drawText(meeting.title, left, y, titlePaint)
        y += 18f

        // Date
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(meeting.startTimestamp))
        canvas.drawText("Date: $dateStr", left, y, bodyPaint)
        y += 16f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 24f

        // Executive Summary
        canvas.drawText("Executive Summary", left, y, sectionPaint)
        y += 16f
        val summaryText = meeting.summary ?: "No executive summary provided."
        val summaryLines = wrapText(summaryText, 515, bodyPaint)
        for (line in summaryLines) {
            canvas.drawText(line, left, y, bodyPaint)
            y += 14f
        }
        y += 16f

        // Action Items Table
        if (tasks.isNotEmpty()) {
            canvas.drawText("Action Items & Tasks (${tasks.size})", left, y, sectionPaint)
            y += 16f

            for (task in tasks) {
                val statusSymbol = if (task.status == "COMPLETED") "[x]" else "[ ]"
                val assignee = if (!task.assignedPerson.isNullOrBlank()) " (${task.assignedPerson})" else ""
                val due = if (task.dueDate != null) {
                    " - Due: " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(task.dueDate))
                } else ""
                val taskLine = "$statusSymbol ${task.title}$assignee$due"
                val wrapped = wrapText(taskLine, 500, bodyPaint)
                for (wl in wrapped) {
                    canvas.drawText(wl, left + 10, y, bodyPaint)
                    y += 14f
                }
            }
            y += 16f
        }

        // Transcripts excerpt
        if (transcripts.isNotEmpty()) {
            canvas.drawText("Spoken Transcript Record (${transcripts.size} segments)", left, y, sectionPaint)
            y += 16f

            for (seg in transcripts.take(15)) {
                if (y > 800f) break // Avoid overflowing page 1
                val timeStr = formatMs(seg.startOffsetMs)
                val line = "[$timeStr] ${seg.text}"
                val wrapped = wrapText(line, 500, bodyPaint)
                for (wl in wrapped) {
                    if (y > 800f) break
                    canvas.drawText(wl, left + 10, y, bodyPaint)
                    y += 13f
                }
            }
        }

        // Footer
        canvas.drawLine(left, 815f, right, 815f, linePaint)
        canvas.drawText("Generated 100% on-device by Personal AI Assistant. Zero cloud cost.", left, 828f, headerPaint)

        pdfDocument.finishPage(page)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val sanitizedTitle = meeting.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportDir, "Meeting_${sanitizedTitle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }

    fun exportMeetingToMarkdown(
        context: Context,
        meeting: MeetingEntity,
        tasks: List<TaskEntity>,
        transcripts: List<TranscriptEntity>
    ): File {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(meeting.startTimestamp))
        val sb = StringBuilder()
        sb.append("# ${meeting.title}\n\n")
        sb.append("**Date:** $dateStr\n\n")

        sb.append("## Executive Summary\n")
        sb.append("${meeting.summary ?: "No summary"}\n\n")

        if (tasks.isNotEmpty()) {
            sb.append("## Action Items\n")
            for (t in tasks) {
                val check = if (t.status == "COMPLETED") "[x]" else "[ ]"
                val assignee = if (!t.assignedPerson.isNullOrBlank()) " (@${t.assignedPerson})" else ""
                val due = if (t.dueDate != null) {
                    " [Due: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(t.dueDate))}]"
                } else ""
                sb.append("- $check **${t.title}**$assignee$due\n")
            }
            sb.append("\n")
        }

        if (transcripts.isNotEmpty()) {
            sb.append("## Spoken Transcripts\n")
            for (tr in transcripts) {
                sb.append("- **[${formatMs(tr.startOffsetMs)}]** ${tr.text}\n")
            }
            sb.append("\n")
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val sanitizedTitle = meeting.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportDir, "Meeting_${sanitizedTitle}.md")
        outputFile.writeText(sb.toString())
        return outputFile
    }

    fun exportTasksToCsv(context: Context, tasks: List<TaskEntity>): File {
        val sb = StringBuilder()
        sb.append("ID,Title,AssignedPerson,DueDate,Status,CreatedDate\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (t in tasks) {
            val due = if (t.dueDate != null) dateFormat.format(Date(t.dueDate)) else ""
            val created = dateFormat.format(Date(t.createdAt))
            val cleanTitle = t.title.replace("\"", "\"\"")
            val cleanPerson = (t.assignedPerson ?: "").replace("\"", "\"\"")
            sb.append("${t.id},\"$cleanTitle\",\"$cleanPerson\",$due,${t.status},$created\n")
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "Tasks_Export_${System.currentTimeMillis()}.csv")
        outputFile.writeText(sb.toString())
        return outputFile
    }

    fun exportRecordingToJson(
        context: Context,
        recording: RecordingEntity,
        transcripts: List<TranscriptEntity>
    ): File {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": ${recording.id},\n")
        sb.append("  \"title\": \"${escapeJson(recording.title)}\",\n")
        sb.append("  \"startTimestamp\": ${recording.startTimestamp},\n")
        sb.append("  \"durationMs\": ${recording.durationMs},\n")
        sb.append("  \"status\": \"${recording.status}\",\n")
        sb.append("  \"segments\": [\n")
        for ((idx, tr) in transcripts.withIndex()) {
            sb.append("    {\n")
            sb.append("      \"index\": ${tr.segmentIndex},\n")
            sb.append("      \"startOffsetMs\": ${tr.startOffsetMs},\n")
            sb.append("      \"endOffsetMs\": ${tr.endOffsetMs},\n")
            sb.append("      \"text\": \"${escapeJson(tr.text)}\"\n")
            sb.append("    }${if (idx < transcripts.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "Recording_${recording.id}.json")
        outputFile.writeText(sb.toString())
        return outputFile
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share exported file"))
    }

    private fun wrapText(text: String, maxWidth: Int, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
