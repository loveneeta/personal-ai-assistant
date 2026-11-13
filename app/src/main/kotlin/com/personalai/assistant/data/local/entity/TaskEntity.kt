package com.personalai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["sourceMeetingId"]),
        Index(value = ["sourceRecordingId"]),
        Index(value = ["status"]),
        Index(value = ["dueDate"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val sourceMeetingId: Long? = null,
    val sourceRecordingId: Long? = null,
    val dueDate: Long? = null,
    val assignedPerson: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
