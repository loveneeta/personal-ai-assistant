package com.personalai.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalai.assistant.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity): Long

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: Long): MeetingEntity?

    @Query("SELECT * FROM meetings ORDER BY startTimestamp DESC")
    fun getAllMeetingsFlow(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE sourceRecordingId = :recordingId")
    fun getMeetingsForRecordingFlow(recordingId: Long): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings ORDER BY startTimestamp DESC")
    suspend fun getAllMeetings(): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY startTimestamp DESC")
    suspend fun searchMeetings(query: String): List<MeetingEntity>

    @Query("DELETE FROM meetings")
    suspend fun deleteAll()

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(meeting: MeetingEntity)
}
