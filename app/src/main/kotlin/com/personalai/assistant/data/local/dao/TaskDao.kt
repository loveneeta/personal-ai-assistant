package com.personalai.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalai.assistant.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' ORDER BY dueDate ASC, createdAt DESC")
    fun getPendingTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE sourceRecordingId = :recordingId")
    fun getTasksForRecordingFlow(recordingId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE sourceMeetingId = :meetingId")
    fun getTasksForMeetingFlow(meetingId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE sourceMeetingId = :meetingId ORDER BY createdAt ASC")
    suspend fun getTasksForMeeting(meetingId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR assignedPerson LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun searchTasks(query: String): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(task: TaskEntity)
}
