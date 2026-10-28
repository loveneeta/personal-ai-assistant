package com.personalai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["startTimestamp"]),
        Index(value = ["status"]),
        Index(value = ["processingStatus"])
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val durationMs: Long = 0,
    val status: String = "COMPLETED", // "RECORDING", "PAUSED", "COMPLETED", "INTERRUPTED", "ERROR"
    val fileSizeBytes: Long = 0,
    val mimeType: String = "audio/wav",
    val processingStatus: String = "PENDING", // "PENDING", "PROCESSING", "COMPLETED", "FAILED"
    val errorDetails: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
