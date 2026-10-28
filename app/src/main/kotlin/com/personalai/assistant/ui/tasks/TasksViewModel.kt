package com.personalai.assistant.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.data.local.entity.TaskEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.export.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class TaskFilter {
    ALL, PENDING, COMPLETED
}

class TasksViewModel(private val repository: AppRepository) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    private val allTasksFlow = repository.getAllTasksFlow()

    val filteredTasks: StateFlow<List<TaskEntity>> = combine(allTasksFlow, _filter) { tasks, filter ->
        when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.PENDING -> tasks.filter { it.status != "COMPLETED" }
            TaskFilter.COMPLETED -> tasks.filter { it.status == "COMPLETED" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun toggleTaskStatus(taskId: Long, currentStatus: String) {
        viewModelScope.launch {
            val newStatus = if (currentStatus == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateTaskStatus(taskId, newStatus)
        }
    }

    fun addTask(title: String, assignee: String?) {
        viewModelScope.launch {
            repository.insertTask(
                TaskEntity(
                    title = title,
                    assignedPerson = assignee?.ifBlank { null }
                )
            )
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    fun exportCsv(context: Context): File {
        return ExportManager.exportTasksToCsv(context, filteredTasks.value)
    }

    fun shareCsv(context: Context) {
        val file = exportCsv(context)
        ExportManager.shareFile(context, file, "text/csv")
    }
}
