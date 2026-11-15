package com.personalai.assistant.integrations.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ApprovalLevel
import com.personalai.assistant.approval.ApprovalRequest
import com.personalai.assistant.approval.ApprovalResult
import java.io.BufferedReader
import java.io.InputStreamReader

data class ScannedDocument(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val textSnippet: String
)

/**
 * Executive Files & Documents Agent.
 *
 * Scans permitted Storage Access Framework document URIs and extracts text
 * into local memory. All destructive file deletions require Level 4 High-Risk confirmation.
 */
class FileAgent(private val context: Context) {

    companion object {
        private const val TAG = "FileAgent"
    }

    /**
     * Reads text content from a permitted document URI (TXT, CSV, MD, JSON).
     */
    fun readDocumentContent(uri: Uri): ScannedDocument? {
        return try {
            var fileName = "Document"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: "Document"
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    var linesRead = 0
                    while (reader.readLine().also { line = it } != null && linesRead < 300) {
                        sb.append(line).append("\n")
                        linesRead++
                    }
                }
            }

            ScannedDocument(
                uri = uri,
                name = fileName,
                sizeBytes = fileSize,
                textSnippet = sb.toString().trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading document at $uri", e)
            null
        }
    }

    /**
     * Stages a file deletion with Level 4 High-Risk Confirmation.
     */
    fun stageFileDeletion(
        fileName: String,
        fileUri: Uri,
        sourceCommand: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val request = ApprovalRequest(
            level = ApprovalLevel.LEVEL_4_HIGH_RISK,
            actionType = "DELETE_FILE",
            recipient = fileName,
            displayTitle = "PERMANENT FILE DELETION",
            messagePayload = "Permanently delete file '$fileName'. This action CANNOT be undone.",
            sourceInstruction = sourceCommand
        )

        ActionApprovalEngine.getInstance().submitRequest(request) { result ->
            when (result) {
                is ApprovalResult.Approved -> {
                    try {
                        val deleted = android.provider.DocumentsContract.deleteDocument(context.contentResolver, fileUri)
                        if (deleted) {
                            onComplete(true, "File '$fileName' has been deleted.")
                        } else {
                            onComplete(false, "System was unable to delete '$fileName'.")
                        }
                    } catch (e: Exception) {
                        onComplete(false, "Deletion failed: ${e.message}")
                    }
                }
                is ApprovalResult.Rejected -> {
                    onComplete(false, "File deletion cancelled by user.")
                }
                is ApprovalResult.Expired -> {
                    onComplete(false, "Deletion request expired.")
                }
                is ApprovalResult.Cancelled -> {
                    onComplete(false, "Deletion aborted.")
                }
            }
        }
    }
}
