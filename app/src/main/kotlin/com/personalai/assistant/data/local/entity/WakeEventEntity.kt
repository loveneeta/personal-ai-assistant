package com.personalai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wake_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["device"])
    ]
)
data class WakeEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val device: String = "ANDROID", // "ANDROID" | "WINDOWS"
    val wakeWord: String = "Jarvis",
    val speakerVerified: Boolean = false,
    val recognizedCommand: String? = null,
    val responseSummary: String? = null,
    val status: String = "COMPLETED" // "COMPLETED" | "TIMEOUT" | "REJECTED_SPEAKER" | "CANCELLED"
)
