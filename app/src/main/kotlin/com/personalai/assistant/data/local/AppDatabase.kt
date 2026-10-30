package com.personalai.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalai.assistant.data.local.dao.MeetingDao
import com.personalai.assistant.data.local.dao.NoteDao
import com.personalai.assistant.data.local.dao.RecordingDao
import com.personalai.assistant.data.local.dao.TaskDao
import com.personalai.assistant.data.local.dao.TranscriptDao
import com.personalai.assistant.data.local.dao.WakeEventDao
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.local.entity.TranscriptFtsEntity
import com.personalai.assistant.data.local.entity.WakeEventEntity

@Database(
    entities = [
        RecordingEntity::class,
        TranscriptEntity::class,
        TranscriptFtsEntity::class,
        MeetingEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        WakeEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun meetingDao(): MeetingDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun wakeEventDao(): WakeEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wake_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `device` TEXT NOT NULL,
                        `wakeWord` TEXT NOT NULL,
                        `speakerVerified` INTEGER NOT NULL,
                        `recognizedCommand` TEXT,
                        `responseSummary` TEXT,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wake_events_timestamp` ON `wake_events` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wake_events_device` ON `wake_events` (`device`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
