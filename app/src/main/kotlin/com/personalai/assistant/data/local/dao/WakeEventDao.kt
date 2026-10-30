package com.personalai.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalai.assistant.data.local.entity.WakeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WakeEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WakeEventEntity): Long

    @Query("SELECT * FROM wake_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentWakeEventsFlow(limit: Int = 20): Flow<List<WakeEventEntity>>

    @Query("SELECT * FROM wake_events ORDER BY timestamp DESC")
    suspend fun getAllWakeEvents(): List<WakeEventEntity>

    @Query("SELECT COUNT(*) FROM wake_events")
    suspend fun getCount(): Int

    @Query("DELETE FROM wake_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wake_events")
    suspend fun clearAll()
}
