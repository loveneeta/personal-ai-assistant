package com.personalai.assistant.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.home.formatMs
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisDeepNavy
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisSurfaceDark
import com.personalai.assistant.ui.theme.JarvisSurfaceGlass
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    viewModel: RecordingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val recordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsStateWithLifecycle()
    val currentPlayingPath by viewModel.audioPlayer.currentPlayingPath.collectAsStateWithLifecycle()

    var recordingToRename by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var recordingToDelete by remember { mutableStateOf<RecordingEntity?>(null) }

    Scaffold(
        containerColor = JarvisCosmicBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(JarvisCyan.copy(alpha = 0.35f), Color.Transparent)
                                    )
                                )
                                .border(1.dp, JarvisCyan.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                "ACOUSTIC ARCHIVES (${recordings.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "16KHZ LINEAR PCM AUDIT LOGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisDeepNavy.copy(alpha = 0.95f),
                    titleContentColor = JarvisTextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JarvisDeepNavy.copy(alpha = 0.4f),
                            JarvisCosmicBlack
                        )
                    )
                )
        ) {
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = JarvisCyan,
                        borderColor = JarvisBorderCyan
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = JarvisCyan
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "NO AUDIO STREAMS SAVED",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Voice recordings and captured telemetry will appear in this acoustic archive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recordings, key = { it.id }) { recording ->
                        val isThisPlaying = isPlaying && currentPlayingPath == recording.filePath
                        RecordingFullCard(
                            recording = recording,
                            isPlaying = isThisPlaying,
                            onPlayPause = {
                                if (isThisPlaying) {
                                    viewModel.pause()
                                } else {
                                    viewModel.play(recording.filePath)
                                }
                            },
                            onClick = { onNavigateToDetails(recording.id) },
                            onRename = {
                                recordingToRename = recording
                                renameText = recording.title
                            },
                            onDelete = {
                                recordingToDelete = recording
                            }
                        )
                    }
                }
            }
        }

        // Rename Dialog
        recordingToRename?.let { rec ->
            AlertDialog(
                onDismissRequest = { recordingToRename = null },
                containerColor = JarvisSurfaceDark,
                title = {
                    Text(
                        "RENAME AUDIO ARCHIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary,
                        letterSpacing = 1.sp
                    )
                },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Title", color = JarvisTextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorderCyan,
                            focusedContainerColor = JarvisCosmicBlack,
                            unfocusedContainerColor = JarvisCosmicBlack
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.rename(rec.id, renameText.trim())
                            }
                            recordingToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordingToRename = null }) {
                        Text("Cancel", color = JarvisTextSecondary)
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        recordingToDelete?.let { rec ->
            AlertDialog(
                onDismissRequest = { recordingToDelete = null },
                containerColor = JarvisSurfaceDark,
                title = {
                    Text(
                        "DELETE AUDIO FILE?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary,
                        letterSpacing = 1.sp
                    )
                },
                text = {
                    Text(
                        "This will permanently purge '${rec.title}' and its audio file from your device.",
                        color = JarvisTextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.delete(rec.id)
                            recordingToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Purge")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordingToDelete = null }) {
                        Text("Cancel", color = JarvisTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun RecordingFullCard(
    recording: RecordingEntity,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    JarvisGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        glowColor = JarvisCyan,
        borderColor = JarvisBorderCyan.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recording.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                        .format(Date(recording.startTimestamp))
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisIceBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (recording.status) {
                        "COMPLETED" -> JarvisCyan.copy(alpha = 0.15f)
                        "INTERRUPTED" -> JarvisNeonViolet.copy(alpha = 0.15f)
                        "ERROR" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else -> JarvisSurfaceDark
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (recording.status) {
                            "COMPLETED" -> JarvisCyan.copy(alpha = 0.7f)
                            "INTERRUPTED" -> JarvisNeonViolet.copy(alpha = 0.7f)
                            "ERROR" -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else -> JarvisBorderCyan
                        }
                    )
                ) {
                    Text(
                        recording.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (recording.status) {
                            "COMPLETED" -> JarvisCyan
                            "INTERRUPTED" -> JarvisNeonViolet
                            "ERROR" -> MaterialTheme.colorScheme.error
                            else -> JarvisTextSecondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatMs(recording.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val sizeKb = recording.fileSizeBytes / 1024
                    Text(
                        "${sizeKb} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(JarvisCyan.copy(alpha = 0.15f))
                            .border(1.dp, JarvisCyan.copy(alpha = 0.5f), CircleShape)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = JarvisIceBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
