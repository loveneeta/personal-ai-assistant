package com.personalai.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.approval.ActionApprovalEngine
import com.personalai.assistant.approval.ui.ActionApprovalDialog
import com.personalai.assistant.ui.theme.PersonalAIAssistantTheme
import com.personalai.assistant.wakeword.JarvisAlwaysOnService

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      com.personalai.assistant.worker.DailySummaryWorker.schedule(this)
    } catch (e: Exception) {
      Log.e("MainActivity", "Failed to schedule DailySummaryWorker", e)
    }

    // Auto-restore Always Ready if enabled by user and mic permission is present
    try {
      val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
      if (hasMic && JarvisAlwaysOnService.isAlwaysReadyPersisted(this)) {
        JarvisAlwaysOnService.start(this)
      }
    } catch (e: Exception) {
      Log.e("MainActivity", "Failed to auto-restore Always Ready service", e)
    }

    enableEdgeToEdge()
    setContent {
      PersonalAIAssistantTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()

          // Universal Action Approval Dialog (Level 3 & 4 human verification gate)
          val currentApproval by ActionApprovalEngine.getInstance().currentApprovalRequest.collectAsStateWithLifecycle()
          currentApproval?.let { request ->
            ActionApprovalDialog(
              request = request,
              onApprove = { finalPayload ->
                ActionApprovalEngine.getInstance().approve(request.id, finalPayload)
              },
              onReject = {
                ActionApprovalEngine.getInstance().reject(request.id)
              }
            )
          }
        }
      }
    }
  }
}
