package com.personalai.assistant.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalai.assistant.MainActivity
import com.personalai.assistant.R
import com.personalai.assistant.ai.DailySummarySynthesizer
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.repository.AppRepository
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class DailySummaryWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = AppRepository.getInstance(context)

        try {
            val allRecordings = repository.getAllRecordings()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val todayRecordings = allRecordings.filter { it.startTimestamp >= startOfDay }
            val allMeetings = repository.getAllMeetings()
            val todayMeetings = allMeetings.filter { it.startTimestamp >= startOfDay }
            val allTasks = repository.getAllTasks()
            val allNotes = repository.getAllNotes()
            val todayNotes = allNotes.filter { it.createdAt >= startOfDay }

            val report = DailySummarySynthesizer.synthesize(
                date = Date(),
                todayRecordings = todayRecordings,
                todayMeetings = todayMeetings,
                allTasks = allTasks,
                todayNotes = todayNotes
            )

            // Save report as a Note in Room
            repository.insertNote(
                NoteEntity(
                    title = report.title,
                    content = report.markdownContent,
                    isPinned = true
                )
            )

            // Show notification if there was activity today
            if (todayRecordings.isNotEmpty() || todayMeetings.isNotEmpty() || allTasks.isNotEmpty()) {
                showSummaryNotification(report.title, "${todayRecordings.size} recordings, ${report.pendingTasksCount} tasks pending")
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun showSummaryNotification(title: String, subtitle: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_summary_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily AI Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily end-of-day briefings and meeting summaries"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        private const val WORK_NAME = "DailySummaryWorker"

        fun schedule(context: Context, hourOfDay: Int = 20, minute: Int = 30) {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.DAY_OF_YEAR, 1)
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        }
    }
}
