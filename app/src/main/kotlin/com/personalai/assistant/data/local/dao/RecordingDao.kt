package com.personalai.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalai.assistant.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings ORDER BY startTimestamp DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY startTimestamp DESC LIMIT :limit")
    fun getRecentRecordingsFlow(limit: Int): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE startTimestamp >= :startOfDayTimestamp ORDER BY startTimestamp DESC")
    fun getTodayRecordingsFlow(startOfDayTimestamp: Long): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE status IN ('RECORDING', 'PAUSED')")
    suspend fun getIncompleteRecordings(): List<RecordingEntity>

    @Query("UPDATE recordings SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, newTitle: String, updatedAt: Long)

    @Query("UPDATE recordings SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE recordings SET durationMs = :durationMs, fileSizeBytes = :fileSizeBytes, endTimestamp = :endTimestamp, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun finalizeRecording(
        id: Long,
        durationMs: Long,
        fileSizeBytes: Long,
        endTimestamp: Long,
        status: String,
        updatedAt: Long
    )

    @Query("SELECT * FROM recordings ORDER BY startTimestamp DESC")
    suspend fun getAllRecordings(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :query || '%' ORDER BY startTimestamp DESC")
    suspend fun searchRecordings(query: String): List<RecordingEntity>

    @Query("UPDATE recordings SET filePath = :newPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFilePath(id: Long, newPath: String, updatedAt: Long)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(recording: RecordingEntity)
}
