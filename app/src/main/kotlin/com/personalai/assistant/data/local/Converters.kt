package com.personalai.assistant.data.local

import androidx.room.TypeConverter
import com.personalai.assistant.data.local.entity.ProcessingStatus
import com.personalai.assistant.data.local.entity.RecordingStatus
import com.personalai.assistant.data.local.entity.TaskStatus

/**
 * Room TypeConverters translating type-safe domain Enums to persistent database Strings.
 * Guarantees zero schema breakage and full backward compatibility with existing SQLite tables.
 */
class Converters {

    @TypeConverter
    fun fromRecordingStatus(status: RecordingStatus?): String {
        return (status ?: RecordingStatus.COMPLETED).name
    }

    @TypeConverter
    fun toRecordingStatus(value: String?): RecordingStatus {
        return RecordingStatus.fromString(value)
    }

    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus?): String {
        return (status ?: ProcessingStatus.PENDING).name
    }

    @TypeConverter
    fun toProcessingStatus(value: String?): ProcessingStatus {
        return ProcessingStatus.fromString(value)
    }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus?): String {
        return (status ?: TaskStatus.PENDING).name
    }

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus {
        return TaskStatus.fromString(value)
    }
}
