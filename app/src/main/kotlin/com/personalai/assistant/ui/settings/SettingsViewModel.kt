package com.personalai.assistant.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.assistant.ai.DailySummarySynthesizer
import com.personalai.assistant.backup.BackupManager
import com.personalai.assistant.backup.BackupResult
import com.personalai.assistant.data.local.entity.NoteEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.export.ExportManager
import com.personalai.assistant.retention.AudioRetentionPolicy
import com.personalai.assistant.retention.RetentionManager
import com.personalai.assistant.worker.DailySummaryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StorageStats(
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0,
    val formattedSize: String = "0 MB"
)

class SettingsViewModel(
    private val repository: AppRepository,
    private val context: Context
) : ViewModel() {

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _recoveryStatus = MutableStateFlow<String?>(null)
    val recoveryStatus: StateFlow<String?> = _recoveryStatus.asStateFlow()

    private val _retentionPolicy = MutableStateFlow(RetentionManager.getPolicy(context))
    val retentionPolicy: StateFlow<AudioRetentionPolicy> = _retentionPolicy.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    init {
        refreshStorageStats()
        // Ensure daily summary is scheduled
        DailySummaryWorker.schedule(context)
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "recordings")
            val files = dir.listFiles() ?: emptyArray()
            val totalBytes = files.sumOf { it.length() }
            val formatted = formatSize(totalBytes)

            withContext(Dispatchers.Main) {
                _storageStats.value = StorageStats(
                    fileCount = files.size,
                    totalSizeBytes = totalBytes,
                    formattedSize = formatted
                )
            }
        }
    }

    fun setRetentionPolicy(policy: AudioRetentionPolicy) {
        RetentionManager.setPolicy(context, policy)
        _retentionPolicy.value = policy
    }

    fun runRetentionCleanup() {
        viewModelScope.launch {
            val count = RetentionManager.executeRetentionCleanup(context, repository)
            _operationStatus.value = if (count > 0) {
                "Cleaned up $count expired audio file(s)."
            } else {
                "Retention cleanup complete. No eligible audio files needed deletion."
            }
            refreshStorageStats()
        }
    }

    fun generateDailySummaryNow(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val allRecordings = repository.getAllRecordings()
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis
                val todayRecordings = allRecordings.filter { it.startTimestamp >= startOfDay }
                val allMeetings = repository.getAllMeetings()
                val todayMeetings = allMeetings.filter { it.startTimestamp >= startOfDay }
                val allTasks = repository.getAllTasks()
                val allNotes = repository.getAllNotes()
                val todayNotes = allNotes.filter { it.createdAt >= startOfDay }

                val report = DailySummarySynthesizer.synthesize(
                    date = Date(),
                    todayRecordings = todayRecordings,
                    todayMeetings = todayMeetings,
                    allTasks = allTasks,
                    todayNotes = todayNotes
                )

                repository.insertNote(
                    NoteEntity(
                        title = report.title,
                        content = report.markdownContent,
                        isPinned = true
                    )
                )

                _operationStatus.value = "Daily summary synthesized and saved to Notes!"
                onComplete(report.markdownContent)
            } catch (e: Exception) {
                _operationStatus.value = "Failed to generate daily summary: ${e.message}"
            }
        }
    }

    fun createEncryptedBackup(passphrase: String, onComplete: (BackupResult) -> Unit) {
        viewModelScope.launch {
            val result = BackupManager.createEncryptedBackup(context, repository, passphrase)
            _operationStatus.value = result.message
            onComplete(result)
        }
    }

    fun restoreEncryptedBackup(passphrase: String, onComplete: (BackupResult) -> Unit) {
        viewModelScope.launch {
            val backupDir = File(context.filesDir, "backups")
            val files = backupDir.listFiles { _, name -> name.endsWith(".enc") }
            val latest = files?.maxByOrNull { it.lastModified() }
            if (latest == null) {
                val res = BackupResult(false, "No backup file found in storage.")
                _operationStatus.value = res.message
                onComplete(res)
                return@launch
            }

            val result = BackupManager.restoreEncryptedBackup(context, repository, latest, passphrase)
            _operationStatus.value = result.message
            refreshStorageStats()
            onComplete(result)
        }
    }

    fun shareLatestBackup() {
        val backupDir = File(context.filesDir, "backups")
        val files = backupDir.listFiles { _, name -> name.endsWith(".enc") }
        val latest = files?.maxByOrNull { it.lastModified() }
        if (latest != null) {
            ExportManager.shareFile(context, latest, "application/octet-stream")
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllData()
            refreshStorageStats()
            _operationStatus.value = "All local data has been permanently cleared."
            onComplete()
        }
    }

    fun triggerRecovery() {
        viewModelScope.launch {
            val recovered = repository.reconcileIncompleteRecordings()
            _recoveryStatus.value = if (recovered.isEmpty()) {
                "All audio recordings are intact. No damaged sessions found."
            } else {
                "Recovered ${recovered.size} interrupted audio recording(s)."
            }
            refreshStorageStats()
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
