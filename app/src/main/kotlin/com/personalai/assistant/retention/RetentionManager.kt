package com.personalai.assistant.retention

import android.content.Context
import com.personalai.assistant.data.local.entity.ProcessingStatus
import com.personalai.assistant.data.local.entity.RecordingStatus
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
     * Executes automatic storage retention cleanup according to user policy.
     * Pruning rule: Deletes the physical .wav file from device storage, but
     * PRESERVES all associated transcripts, action items, and notes in the database.
     *
     * @return Number of audio files safely pruned.
     */
    suspend fun executeRetentionCleanup(context: Context, repository: AppRepository): Int = withContext(Dispatchers.IO) {
        val policy = getPolicy(context)
        if (policy == AudioRetentionPolicy.KEEP_FOREVER) return@withContext 0

        val now = System.currentTimeMillis()
        val recordings = repository.getAllRecordings()
        var cleanedCount = 0

        for (rec in recordings) {
            // Safety guard: Never delete audio currently capturing
            if (rec.status.isActive) {
                continue
            }

            // Safety guard: Never delete audio actively being transcribed or pending transcription
            if (rec.processingStatus.isProcessing || rec.processingStatus.isPending) {
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
                if (rec.status == RecordingStatus.COMPLETED && rec.processingStatus.isCompleted) {
                    shouldDeleteAudio = true
                }
            } else if (policy.days > 0) {
                val ageMs = now - rec.startTimestamp
                val maxAgeMs = TimeUnit.DAYS.toMillis(policy.days.toLong())
                if (ageMs > maxAgeMs) {
                    // Safe to prune completed transcripts or failed/interrupted recordings older than window
                    if (rec.processingStatus.isCompleted || rec.status == RecordingStatus.INTERRUPTED || rec.status == RecordingStatus.ERROR || rec.processingStatus.isFailed) {
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
