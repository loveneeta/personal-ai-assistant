package com.personalai.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalai.assistant.data.local.dao.ChatMessageDao
import com.personalai.assistant.data.local.dao.MeetingDao
import com.personalai.assistant.data.local.dao.NoteDao
import com.personalai.assistant.data.local.dao.RecordingDao
import com.personalai.assistant.data.local.dao.TaskDao
import com.personalai.assistant.data.local.dao.TranscriptDao
import com.personalai.assistant.data.local.dao.WakeEventDao
import com.personalai.assistant.data.local.entity.ChatMessageEntity
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
        WakeEventEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun meetingDao(): MeetingDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun wakeEventDao(): WakeEventDao
    abstract fun chatMessageDao(): ChatMessageDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `isUser` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transcripts` ADD COLUMN `originalText` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transcripts` ADD COLUMN `translatedText` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `transcripts` ADD COLUMN `detectedLanguage` TEXT NOT NULL DEFAULT 'en'")

                db.execSQL("ALTER TABLE `wake_events` ADD COLUMN `originalCommand` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `wake_events` ADD COLUMN `translatedCommand` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `wake_events` ADD COLUMN `detectedLanguage` TEXT NOT NULL DEFAULT 'en'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_ai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
