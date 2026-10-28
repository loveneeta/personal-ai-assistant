package com.personalai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = TranscriptEntity::class)
@Entity(tableName = "transcripts_fts")
data class TranscriptFtsEntity(
    val text: String,
    val language: String
)
