package com.personalai.assistant.retention

import android.content.Context
import com.personalai.assistant.data.repository.AppRepository
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
        } catch (e: Exception) {
            AudioRetentionPolicy.KEEP_FOREVER
        }
    }

    fun setPolicy(context: Context, policy: AudioRetentionPolicy) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_POLICY, policy.name).apply()
    }

    suspend fun executeRetentionCleanup(context: Context, repository: AppRepository): Int {
        val policy = getPolicy(context)
        if (policy == AudioRetentionPolicy.KEEP_FOREVER) return 0

        val now = System.currentTimeMillis()
        val recordings = repository.getAllRecordings()
        var cleanedCount = 0

        for (rec in recordings) {
            if (rec.filePath.isBlank()) continue
            val file = File(rec.filePath)
            if (!file.exists()) continue

            var shouldDeleteAudio = false

            if (policy == AudioRetentionPolicy.IMMEDIATE_POST_TRANSCRIPT) {
                if (rec.processingStatus == "COMPLETED") {
                    shouldDeleteAudio = true
                }
            } else if (policy.days > 0) {
                val ageMs = now - rec.startTimestamp
                val maxAgeMs = TimeUnit.DAYS.toMillis(policy.days.toLong())
                if (ageMs > maxAgeMs) {
                    shouldDeleteAudio = true
                }
            }

            if (shouldDeleteAudio) {
                val deleted = repository.deleteAudioKeepTranscript(rec.id)
                if (deleted) cleanedCount++
            }
        }
        return cleanedCount
    }
}
