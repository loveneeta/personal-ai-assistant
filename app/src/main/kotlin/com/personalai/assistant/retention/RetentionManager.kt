package com.personalai.assistant.retention

import android.content.Context
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class AudioRetentionPolicy(val label: String, val days: Int) {
    KEEP_FOREVER("Keep Audio Forever", -1),
    DAYS_7("Delete Audio After 7 Days", 7),
    DAYS_30("Delete Audio After 30 Days", 30),
    IMMEDIATE_POST_TRANSCRIPT("Delete Audio Once Transcribed", 0)
}

object RetentionManager {

    private const val PREFS_NAME = "retention_prefs"
    private const val KEY_POLICY = "audio_retention_policy"

    fun getPolicy(context: Context): AudioRetentionPolicy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_POLICY, AudioRetentionPolicy.KEEP_FOREVER.name)
        return try {
            AudioRetentionPolicy.valueOf(name ?: AudioRetentionPolicy.KEEP_FOREVER.name)
        } catch (_: Exception) {
            AudioRetentionPolicy.KEEP_FOREVER
        }
    }

    fun setPolicy(context: Context, policy: AudioRetentionPolicy) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_POLICY, policy.name).apply()
    }

    /**
     * Executes retention cleanup according to user policy.
     * Guaranteed rules:
     * 1. Never deletes audio currently being recorded or paused.
     * 2. Never deletes audio currently being transcribed or pending transcription.
     * 3. Reconciles database records when physical files are already missing from disk.
     * 4. Does not corrupt database records if physical deletion fails.
     * 5. Runs off the main thread on Dispatchers.IO.
     */
    suspend fun executeRetentionCleanup(context: Context, repository: AppRepository): Int = withContext(Dispatchers.IO) {
        val policy = getPolicy(context)
        if (policy == AudioRetentionPolicy.KEEP_FOREVER) return@withContext 0

        val now = System.currentTimeMillis()
        val recordings = repository.getAllRecordings()
        var cleanedCount = 0

        for (rec in recordings) {
            // Safety guard: Never delete audio currently capturing
            if (rec.status == "RECORDING" || rec.status == "PAUSED") {
                continue
            }

            // Safety guard: Never delete audio actively being transcribed or pending transcription
            if (rec.processingStatus == "PROCESSING" || rec.processingStatus == "PENDING") {
                continue
            }

            if (rec.filePath.isBlank()) continue
            val file = File(rec.filePath)

            // Missing file reconciliation: handle missing files without corrupting records
            if (!file.exists()) {
                repository.updateRecordingFilePath(rec.id, "")
                continue
            }

            var shouldDeleteAudio = false

            if (policy == AudioRetentionPolicy.IMMEDIATE_POST_TRANSCRIPT) {
                // Prune only when recording capture is finished AND transcription is completed
                if (rec.status == "COMPLETED" && rec.processingStatus == "COMPLETED") {
                    shouldDeleteAudio = true
                }
            } else if (policy.days > 0) {
                val ageMs = now - rec.startTimestamp
                val maxAgeMs = TimeUnit.DAYS.toMillis(policy.days.toLong())
                if (ageMs > maxAgeMs) {
                    // Safe to prune completed transcripts or failed/interrupted recordings older than window
                    if (rec.processingStatus == "COMPLETED" || rec.status == "INTERRUPTED" || rec.status == "ERROR" || rec.processingStatus == "FAILED") {
                        shouldDeleteAudio = true
                    }
                }
            }

            if (shouldDeleteAudio) {
                val deleted = repository.deleteAudioKeepTranscript(rec.id)
                if (deleted) cleanedCount++
            }
        }
        cleanedCount
    }
}
