package com.personalai.assistant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data object RecordingsNavKey : NavKey

@Serializable
data class RecordingDetailsNavKey(val recordingId: Long) : NavKey

@Serializable
data object SettingsNavKey : NavKey

@Serializable
data object ModelsNavKey : NavKey

@Serializable
data object MeetingsNavKey : NavKey

@Serializable
data class MeetingDetailsNavKey(val meetingId: Long) : NavKey

@Serializable
data object TasksNavKey : NavKey

@Serializable
data object MemoryNavKey : NavKey

@Serializable
data object ChatNavKey : NavKey
