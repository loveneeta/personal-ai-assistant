package com.personalai.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalai.assistant.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: TranscriptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transcripts: List<TranscriptEntity>): List<Long>

    @Update
    suspend fun update(transcript: TranscriptEntity)

    @Query("UPDATE transcripts SET text = :newText, reviewStatus = :reviewStatus WHERE id = :id")
    suspend fun updateText(id: Long, newText: String, reviewStatus: String)

    @Query("SELECT * FROM transcripts WHERE recordingId = :recordingId ORDER BY segmentIndex ASC")
    fun getByRecordingIdFlow(recordingId: Long): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE recordingId = :recordingId ORDER BY segmentIndex ASC")
    suspend fun getByRecordingId(recordingId: Long): List<TranscriptEntity>

    @Query("""
        SELECT transcripts.* FROM transcripts
        JOIN transcripts_fts ON transcripts.id = transcripts_fts.rowid
        WHERE transcripts_fts MATCH :query
        ORDER BY transcripts.startOffsetMs ASC
    """)
    suspend fun searchTranscriptsFts(query: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE text LIKE '%' || :query || '%' ORDER BY startOffsetMs ASC")
    suspend fun searchTranscriptsLike(query: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts ORDER BY id ASC")
    suspend fun getAllTranscripts(): List<TranscriptEntity>

    @Query("DELETE FROM transcripts")
    suspend fun deleteAll()

    @Query("DELETE FROM transcripts WHERE recordingId = :recordingId")
    suspend fun deleteByRecordingId(recordingId: Long)
}
