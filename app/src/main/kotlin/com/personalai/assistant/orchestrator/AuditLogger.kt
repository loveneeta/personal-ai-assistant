package com.personalai.assistant.orchestrator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class AuditLogEntry(
    val id: String,
    val actionType: String,
    val recipient: String,
    val summary: String,
    val approvalLevel: String,
    val status: String, // "PENDING", "APPROVED", "REJECTED", "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Immutable Activity & Security Audit Logger.
 *
 * Maintains a persistent log of every agent action and user authorization event.
 */
class AuditLogger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AuditLogger"
        private const val PREFS_NAME = "jarvis_audit_log"
        private const val KEY_AUDIT_JSON = "audit_entries_json"
        private const val MAX_LOGS = 200

        @Volatile
        private var INSTANCE: AuditLogger? = null

        fun getInstance(context: Context): AuditLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuditLogger(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val logList = Collections.synchronizedList(mutableListOf<AuditLogEntry>())

    private val _logsFlow = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<AuditLogEntry>> = _logsFlow.asStateFlow()

    init {
        loadPersistedLogs()
    }

    fun logAction(
        id: String,
        actionType: String,
        recipient: String,
        summary: String,
        approvalLevel: String,
        status: String
    ) {
        val entry = AuditLogEntry(
            id = id,
            actionType = actionType,
            recipient = recipient,
            summary = summary,
            approvalLevel = approvalLevel,
            status = status
        )

        synchronized(logList) {
            logList.add(0, entry)
            if (logList.size > MAX_LOGS) {
                logList.removeAt(logList.lastIndex)
            }
        }

        updateFlowAndPersist()
        Log.i(TAG, "Audit: [$status] $actionType to $recipient - $summary")
    }

    private fun updateFlowAndPersist() {
        val copy: List<AuditLogEntry>
        synchronized(logList) {
            copy = logList.toList()
        }
        _logsFlow.value = copy

        try {
            val jsonArray = JSONArray()
            copy.take(100).forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("actionType", entry.actionType)
                    put("recipient", entry.recipient)
                    put("summary", entry.summary)
                    put("approvalLevel", entry.approvalLevel)
                    put("status", entry.status)
                    put("timestamp", entry.timestamp)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_AUDIT_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist audit log", e)
        }
    }

    private fun loadPersistedLogs() {
        val raw = prefs.getString(KEY_AUDIT_JSON, null) ?: return
        try {
            val array = JSONArray(raw)
            val loaded = mutableListOf<AuditLogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                loaded.add(
                    AuditLogEntry(
                        id = obj.getString("id"),
                        actionType = obj.getString("actionType"),
                        recipient = obj.getString("recipient"),
                        summary = obj.getString("summary"),
                        approvalLevel = obj.getString("approvalLevel"),
                        status = obj.getString("status"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            synchronized(logList) {
                logList.addAll(loaded)
            }
            _logsFlow.value = loaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audit logs", e)
        }
    }
}
