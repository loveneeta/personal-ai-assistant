package com.personalai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recordingId"]),
        Index(value = ["startOffsetMs"])
    ]
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val segmentIndex: Int,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
    val language: String = "en",
    val confidence: Float? = null,
    val reviewStatus: String = "AUTOMATIC" // "AUTOMATIC", "NEEDS_REVIEW", "VERIFIED"
)
