package com.personalai.assistant.ui.recordings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.data.local.entity.TranscriptEntity
import com.personalai.assistant.transcription.TranscriptionProgress
import com.personalai.assistant.ui.home.formatMs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailsScreen(
    viewModel: RecordingDetailsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    onNavigateToMeeting: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val integrityStatus by viewModel.fileIntegrityStatus.collectAsStateWithLifecycle()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsStateWithLifecycle()
    val currentPosMs by viewModel.audioPlayer.currentPositionMs.collectAsStateWithLifecycle()
    val playerDurationMs by viewModel.audioPlayer.durationMs.collectAsStateWithLifecycle()
    val queueProgress by viewModel.queueProgress.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var segmentToEdit by remember { mutableStateOf<TranscriptEntity?>(null) }
    var editedSegmentText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = com.personalai.assistant.ui.theme.JarvisCosmicBlack,
        topBar = {
            TopAppBar(
                title = { 
                    androidx.compose.foundation.layout.Column {
                        Text(recording?.title ?: "Recording Dossier", color = com.personalai.assistant.ui.theme.JarvisTextPrimary, maxLines = 1)
                        Text(
                            "ACOUSTIC & TRANSCRIPTION TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = com.personalai.assistant.ui.theme.JarvisCyan,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = com.personalai.assistant.ui.theme.JarvisCyan)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shareJson(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share JSON", tint = com.personalai.assistant.ui.theme.JarvisIceBlue)
                    }
                    IconButton(onClick = {
                        renameText = recording?.title ?: ""
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = com.personalai.assistant.ui.theme.JarvisCyan)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.personalai.assistant.ui.theme.JarvisDeepNavy.copy(alpha = 0.95f),
                    titleContentColor = com.personalai.assistant.ui.theme.JarvisTextPrimary
                )
            )
        }
    ) { innerPadding ->
        val rec = recording
        if (rec == null) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Recording not found.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) { Text("Go Back") }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio Player or Google Voice Session Card
                val isGoogleVoiceSession = rec.filePath.startsWith("[")
                if (!isGoogleVoiceSession) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Audio Playback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val totalDuration = if (playerDurationMs > 0) playerDurationMs.toLong() else rec.durationMs
                            val sliderValue = if (totalDuration > 0) {
                                (currentPosMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = sliderValue,
                                onValueChange = { frac ->
                                    val target = (frac * totalDuration).toInt()
                                    viewModel.seekTo(target)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatMs(currentPosMs.toLong()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    formatMs(totalDuration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isPlaying) {
                                        viewModel.pause()
                                    } else {
                                        viewModel.play()
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Google Voice Dictation Session",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Dictated live with real-time word readout and whole-sentence completion. Full transcripts verified below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Transcription & STT Status Card (for WAV files)
                if (!isGoogleVoiceSession) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Whole-Sentence AI Transcription",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Multi-pass whole sentence reconstruction with intelligent pause boundary smoothing (no chopped fragments).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Retranscription Options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.reprocessTranscription("vosk-model-small-hi-0.22") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🇮🇳 Retranscribe (Hindi)")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.reprocessTranscription("vosk-model-small-en-in-0.4") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🌐 Hinglish / Indian English")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Live progress state
                            val prog = queueProgress
                            if (prog is TranscriptionProgress.Processing && prog.recordingId == rec.id) {
                                LinearProgressIndicator(
                                    progress = { prog.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    prog.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Status: ", style = MaterialTheme.typography.bodyMedium)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (rec.processingStatus) {
                                            "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
                                            "PROCESSING" -> MaterialTheme.colorScheme.tertiaryContainer
                                            "FAILED" -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            rec.processingStatus,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (rec.processingStatus == "FAILED") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        rec.errorDetails ?: "Transcription failed. Ensure a speech model is downloaded.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = onNavigateToModels) {
                                        Text("Manage Speech Models")
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Meeting Minutes Action Card
                if (transcripts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Meeting Intelligence",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Extract executive summary, decisions, and action items locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.generateMeetingMinutes { meetingId ->
                                        android.widget.Toast.makeText(context, "Meeting minutes synthesized!", android.widget.Toast.LENGTH_SHORT).show()
                                        onNavigateToMeeting(meetingId)
                                    }
                                }
                            ) {
                                Text("Minutes")
                            }
                        }
                    }
                }

                // Transcripts Segments Section
                if (transcripts.isNotEmpty()) {
                    Text(
                        "Complete Sentences Transcript (${transcripts.size} sentences)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        transcripts.forEach { segment ->
                            TranscriptSegmentCard(
                                segment = segment,
                                onSeekTo = { viewModel.seekTo(segment.startOffsetMs.toInt()) },
                                onEdit = {
                                    segmentToEdit = segment
                                    editedSegmentText = segment.text
                                }
                            )
                        }
                    }
                } else if (rec.processingStatus == "COMPLETED") {
                    Text(
                        "No spoken segments were extracted from this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // File Integrity Diagnostics Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (integrityStatus.startsWith("Valid") || integrityStatus.startsWith("Verified")) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (integrityStatus.startsWith("Valid") || integrityStatus.startsWith("Verified")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Session Verification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(integrityStatus, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Metadata Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Session Metadata",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm:ss a", Locale.getDefault())
                        MetadataRow("Recording ID", "#${rec.id}")
                        MetadataRow("Start Time", dateFmt.format(Date(rec.startTimestamp)))
                        val endTs = rec.endTimestamp
                        if (endTs != null) {
                            MetadataRow("End Time", dateFmt.format(Date(endTs)))
                        }
                        MetadataRow("Duration", formatMs(rec.durationMs))
                        if (!isGoogleVoiceSession) {
                            MetadataRow("File Size", "${rec.fileSizeBytes} bytes (${rec.fileSizeBytes / 1024} KB)")
                            MetadataRow("Audio Specs", "16kHz 16-bit Mono PCM WAV")
                        } else {
                            MetadataRow("Engine", "Android Google Speech Recognition (Voice Keyboard)")
                        }
                        MetadataRow("Status", rec.status)
                    }
                }
            }
        }

        // Edit Transcript Segment Dialog
        segmentToEdit?.let { seg ->
            AlertDialog(
                onDismissRequest = { segmentToEdit = null },
                title = { Text("Edit Sentence [${formatMs(seg.startOffsetMs)}]") },
                text = {
                    OutlinedTextField(
                        value = editedSegmentText,
                        onValueChange = { editedSegmentText = it },
                        label = { Text("Corrected Text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (editedSegmentText.isNotBlank()) {
                                viewModel.updateTranscript(seg.id, editedSegmentText.trim())
                            }
                            segmentToEdit = null
                        }
                    ) {
                        Text("Save Correction")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { segmentToEdit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Rename Dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Recording") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.rename(renameText.trim())
                            }
                            showRenameDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Recording?") },
                text = {
                    Text("Permanently delete this recording and its local audio file?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.delete { onNavigateBack() }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TranscriptSegmentCard(
    segment: TranscriptEntity,
    onSeekTo: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable timestamp pill that seeks player directly to audio position
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { onSeekTo() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Jump to audio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "${formatMs(segment.startOffsetMs)} - ${formatMs(segment.endOffsetMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            if (segment.language.startsWith("hi")) "🇮🇳 हिन्दी" else "🌐 ${segment.language.uppercase()}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (segment.reviewStatus == "VERIFIED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            segment.reviewStatus,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit text",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                segment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
