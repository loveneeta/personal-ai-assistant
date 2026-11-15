package com.personalai.assistant.integrations.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult
import com.personalai.assistant.permissions.JarvisPermissionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarEventRecord(
    val id: Long,
    val title: String,
    val description: String?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val location: String?
)

/**
 * Executive Calendar & Scheduling Agent.
 *
 * Reads upcoming schedule, detects scheduling overlaps/conflicts, and stages
 * calendar bookings with Level 2 Approval.
 */
class CalendarAgent(private val context: Context) {

    companion object {
        private const val TAG = "CalendarAgent"
    }

    /**
     * Reads events scheduled for the next [daysAhead] days.
     */
    fun getUpcomingEvents(daysAhead: Int = 7): List<CalendarEventRecord> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot read calendar: READ_CALENDAR permission not granted.")
            return emptyList()
        }

        JarvisPermissionManager.getInstance(context).recordPermissionAccess("calendar")
        val events = mutableListOf<CalendarEventRecord>()

        try {
            val now = System.currentTimeMillis()
            val endRange = now + (daysAhead * 24L * 60L * 60L * 1000L)

            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(now.toString(), endRange.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val title = if (titleIdx != -1) c.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                    val desc = if (descIdx != -1) c.getString(descIdx) else null
                    val start = if (startIdx != -1) c.getLong(startIdx) else 0L
                    val end = if (endIdx != -1) c.getLong(endIdx) else start + 3600000L
                    val loc = if (locIdx != -1) c.getString(locIdx) else null

                    events.add(CalendarEventRecord(id, title, desc, start, end, loc))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar events", e)
        }

        return events
    }

    /**
     * Checks if a proposed time window overlaps with an existing calendar event.
     */
    fun findConflict(startEpochMs: Long, durationMs: Long = 3600000L): CalendarEventRecord? {
        val endEpochMs = startEpochMs + durationMs
        val upcoming = getUpcomingEvents(3)
        return upcoming.firstOrNull { event ->
            // Check for time overlap
            startEpochMs < event.endEpochMs && endEpochMs > event.startEpochMs
        }
    }

    /**
     * Stages a calendar event for human confirmation.
     */
    fun stageEventCreation(
        title: String,
        startEpochMs: Long,
        durationMinutes: Int = 60,
        sourceCommand: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val conflict = findConflict(startEpochMs, durationMinutes * 60_000L)
        val formattedDate = SimpleDateFormat("EEEE, MMM d 'at' hh:mm a", Locale.getDefault()).format(Date(startEpochMs))

        val warning = if (conflict != null) {
            " [WARNING: Overlaps with '${conflict.title}']"
        } else ""

        val request = ApprovalRequest(
            level = ApprovalLevel.LEVEL_2_REVERSIBLE,
            actionType = "MODIFY_CALENDAR",
            recipient = "Android Calendar",
            displayTitle = "Create Calendar Event",
            messagePayload = "Schedule '$title' on $formattedDate (Duration: $durationMinutes min)$warning",
            sourceInstruction = sourceCommand
        )

        ActionApprovalEngine.getInstance().submitRequest(request) { result ->
            when (result) {
                is ApprovalResult.Approved -> {
                    val success = executeInsertCalendarEvent(title, startEpochMs, durationMinutes)
                    if (success) {
                        onComplete(true, "Event '$title' successfully scheduled for $formattedDate.")
                    } else {
                        onComplete(false, "Failed to write event to calendar.")
                    }
                }
                is ApprovalResult.Rejected -> {
                    onComplete(false, "Event creation cancelled.")
                }
                is ApprovalResult.Expired -> {
                    onComplete(false, "Calendar event approval timed out.")
                }
                is ApprovalResult.Cancelled -> {
                    onComplete(false, "Event creation aborted.")
                }
            }
        }
    }

    private fun executeInsertCalendarEvent(title: String, startEpochMs: Long, durationMinutes: Int): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startEpochMs + (durationMinutes * 60_000L))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting calendar event", e)
            false
        }
    }
}
