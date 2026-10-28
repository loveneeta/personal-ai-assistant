package com.personalai.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.personalai.assistant.data.local.dao.MeetingDao
import com.personalai.assistant.data.local.dao.NoteDao
import com.personalai.assistant.data.local.dao.RecordingDao
import com.personalai.assistant.data.local.dao.TaskDao
import com.personalai.assistant.data.local.dao.TranscriptDao
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.local.entity.TranscriptFtsEntity

@Database(
    entities = [
        RecordingEntity::class,
        TranscriptEntity::class,
        TranscriptFtsEntity::class,
        MeetingEntity::class,
        TaskEntity::class,
        NoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun meetingDao(): MeetingDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_ai.db"
                )
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
