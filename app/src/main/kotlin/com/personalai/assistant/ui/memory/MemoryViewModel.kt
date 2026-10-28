package com.personalai.assistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GlobalSearchResults(
    val query: String,
    val transcripts: List<TranscriptEntity> = emptyList(),
    val recordings: List<RecordingEntity> = emptyList(),
    val meetings: List<MeetingEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val isSearching: Boolean = false
)

class MemoryViewModel(private val repository: AppRepository) : ViewModel() {

    val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchState = MutableStateFlow(GlobalSearchResults(""))
    val searchState: StateFlow<GlobalSearchResults> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchQueryChange(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()

        if (trimmed.isEmpty()) {
            _searchState.value = GlobalSearchResults("")
            return
        }

        _searchState.value = _searchState.value.copy(query = query, isSearching = true)

        searchJob = viewModelScope.launch {
            delay(250) // Debounce
            val tr = repository.searchTranscripts(trimmed)
            val rec = repository.searchRecordings(trimmed)
            val meet = repository.searchMeetings(trimmed)
            val tsk = repository.searchTasks(trimmed)
            val nt = repository.searchNotes(trimmed)

            _searchState.value = GlobalSearchResults(
                query = query,
                transcripts = tr,
                recordings = rec,
                meetings = meet,
                tasks = tsk,
                notes = nt,
                isSearching = false
            )
        }
    }

    fun addNote(title: String, content: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    isPinned = isPinned
                )
            )
        }
    }

    fun togglePin(id: Long, currentPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinNote(id, !currentPinned)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
}
