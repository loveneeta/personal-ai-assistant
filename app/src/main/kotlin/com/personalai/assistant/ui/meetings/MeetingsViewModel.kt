package com.personalai.assistant.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetingsViewModel(private val repository: AppRepository) : ViewModel() {

    val meetings: StateFlow<List<MeetingEntity>> = repository.getAllMeetingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMeeting(id: Long) {
        viewModelScope.launch {
            repository.deleteMeeting(id)
        }
    }
}
