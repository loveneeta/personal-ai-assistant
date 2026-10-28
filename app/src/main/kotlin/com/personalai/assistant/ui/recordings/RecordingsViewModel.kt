package com.personalai.assistant.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.playback.AudioPlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingsViewModel(
    private val repository: AppRepository
) : ViewModel() {

    val allRecordings: StateFlow<List<RecordingEntity>> =
        repository.getAllRecordingsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioPlayer = AudioPlayer()

    fun play(filePath: String) {
        audioPlayer.play(filePath)
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun resume() {
        audioPlayer.resume()
    }

    fun stop() {
        audioPlayer.stop()
    }

    fun rename(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(id, newTitle)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteRecording(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
