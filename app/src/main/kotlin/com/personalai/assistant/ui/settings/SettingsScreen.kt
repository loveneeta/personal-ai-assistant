package com.personalai.assistant.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.retention.AudioRetentionPolicy
import com.personalai.assistant.ui.components.JarvisGlassCard
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
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val recoveryStatus by viewModel.recoveryStatus.collectAsStateWithLifecycle()
    val retentionPolicy by viewModel.retentionPolicy.collectAsStateWithLifecycle()
    val opStatus by viewModel.operationStatus.collectAsStateWithLifecycle()

    val wakeEvents by viewModel.wakeEvents.collectAsStateWithLifecycle()
    val isSpeakerEnrolled by viewModel.isSpeakerEnrolled.collectAsStateWithLifecycle()
    val isSpeakerVerificationEnabled by viewModel.isSpeakerVerificationEnabled.collectAsStateWithLifecycle()

    var wakeWordText by remember { mutableStateOf(viewModel.wakeWord.value) }
    var wakeSensitivity by remember { mutableFloatStateOf(viewModel.wakeSensitivity.value) }
    var timeoutSeconds by remember { mutableIntStateOf(viewModel.commandTimeoutSeconds.value) }
    var rawAudioRetention by remember { mutableStateOf(viewModel.rawAudioRetention.value) }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = JarvisCosmicBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SYSTEM CONTROL & PRIVACY",
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "TELEMETRY, RETENTION & WAKE PROTOCOLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Operation Status Banner
            if (opStatus != null) {
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = JarvisCyan
                ) {
                    Text(
                        opStatus!!,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 1. Hands-Free Wake-Word & Speaker Auth Protocol Card
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Hearing,
                            contentDescription = null,
                            tint = JarvisCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Hands-Free Wake Word Protocol",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Configure the on-device acoustic keyword detector. Runs locally on device CPU with zero cloud transmission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Configured Wake Word
                    OutlinedTextField(
                        value = wakeWordText,
                        onValueChange = {
                            wakeWordText = it
                            viewModel.wakeWord.value = it
                        },
                        label = { Text("Configured Wake Word", color = JarvisTextSecondary) },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sensitivity Slider
                    Text(
                        "Wake Sensitivity: ${(wakeSensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisIceBlue,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = wakeSensitivity,
                        onValueChange = {
                            wakeSensitivity = it
                            viewModel.wakeSensitivity.value = it
                        },
                        valueRange = 0.2f..0.95f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Command Timeout Slider
                    Text(
                        "Command Silence Timeout: ${timeoutSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisIceBlue,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = timeoutSeconds.toFloat(),
                        onValueChange = {
                            timeoutSeconds = it.toInt()
                            viewModel.commandTimeoutSeconds.value = it.toInt()
                        },
                        valueRange = 3f..15f,
                        steps = 11
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Speaker Biometric Verification Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Primary Speaker Verification",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = JarvisTextPrimary
                            )
                            Text(
                                if (isSpeakerEnrolled) "Template active. Voice matched before personal queries."
                                else "Profile not enrolled. Tap to record voice passes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSpeakerEnrolled) JarvisNeonViolet else JarvisTextSecondary
                            )
                        }
                        Switch(
                            checked = isSpeakerVerificationEnabled && isSpeakerEnrolled,
                            onCheckedChange = {
                                if (isSpeakerEnrolled) {
                                    viewModel.toggleSpeakerVerification(it)
                                } else {
                                    Toast.makeText(context, "Please enroll your voice profile first.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisCosmicBlack,
                                checkedTrackColor = JarvisNeonViolet,
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisSurfaceDark
                            )
                        )
                    }

                    if (isSpeakerEnrolled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteSpeakerProfile()
                                Toast.makeText(context, "Voice profile deleted.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Voice Profile")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Raw Audio Retention Toggle (Privacy-by-Default)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Retain Raw Audio of Voice Commands",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = JarvisTextPrimary
                            )
                            Text(
                                "Off by default. When disabled, only the text command is stored in memory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                        Switch(
                            checked = rawAudioRetention,
                            onCheckedChange = {
                                rawAudioRetention = it
                                viewModel.rawAudioRetention.value = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisCosmicBlack,
                                checkedTrackColor = JarvisCyan,
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisSurfaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wake History Audit Log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Logged Wake Events: ${wakeEvents.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = JarvisIceBlue
                        )
                        if (wakeEvents.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearWakeEvents() }) {
                                Text("Purge Log", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 2. Speech Models Management Card
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = JarvisCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "On-Device Speech Models",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Download and manage offline models for Indian English, Hindi, and Universal English. Zero cloud calls, 100% free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToModels,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                    ) {
                        Text("Manage Speech Models", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Daily AI Summary Card
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderViolet
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = JarvisNeonViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Daily Personal AI Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Automatically scheduled daily at 8:30 PM via Android WorkManager. Aggregates your recordings, transcripts, decisions, and tasks into an end-of-day briefing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            viewModel.generateDailySummaryNow {
                                Toast.makeText(context, "Daily summary generated and saved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Generate Today's Summary Now")
                    }
                }
            }

            // 4. Audio Storage & Retention Policies
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Storage & Data Retention",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Saved Audio: ${stats.fileCount} files (${stats.formattedSize})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = JarvisIceBlue,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Audio Retention Policy:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )

                    AudioRetentionPolicy.values().forEach { policy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = retentionPolicy == policy,
                                onClick = { viewModel.setRetentionPolicy(policy) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(policy.label, style = MaterialTheme.typography.bodySmall, color = JarvisTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.runRetentionCleanup() }) {
                            Text("Run Retention Cleanup")
                        }
                        OutlinedButton(onClick = { viewModel.refreshStorageStats() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            }

            // 5. Encrypted Hardware Backup Card (AES-256)
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderViolet
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = JarvisNeonViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Hardware-Backed Encrypted Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Export a full database snapshot encrypted with AES-256-GCM. Requires your personal passphrase to decrypt on any device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showBackupDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                        ) {
                            Text("Create Backup", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { showRestoreDialog = true }) {
                            Text("Restore")
                        }
                        IconButton(onClick = { viewModel.shareLatestBackup() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Backup", tint = JarvisIceBlue)
                        }
                    }
                }
            }

            // 6. Crash Recovery Diagnostics
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisBorderCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Audio File Integrity & Crash Recovery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scans app storage for incomplete WAV files from unexpected shutdowns, repairs RIFF headers in-place, and syncs recording metadata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )

                    if (recoveryStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            recoveryStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { viewModel.triggerRecovery() }) {
                        Text("Scan & Repair Audio Sessions")
                    }
                }
            }

            // 7. Danger Zone: Wipe All Data
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Permanently purge all audio files, transcripts, meeting minutes, tasks, and notes from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showClearAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All Local Data")
                    }
                }
            }
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            containerColor = JarvisSurfaceDark,
            title = { Text("Create Encrypted Backup", color = JarvisTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a secure passphrase to encrypt your backup with AES-256-GCM:", color = JarvisTextSecondary)
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase (min 6 chars)", color = JarvisTextSecondary) },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassphrase.length >= 6) {
                            viewModel.createEncryptedBackup(backupPassphrase) {
                                showBackupDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                ) {
                    Text("Encrypt & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = JarvisSurfaceDark,
            title = { Text("Restore From Backup", color = JarvisTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when creating the backup:", color = JarvisTextSecondary)
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase", color = JarvisTextSecondary) },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassphrase.isNotEmpty()) {
                            viewModel.restoreEncryptedBackup(backupPassphrase) {
                                showRestoreDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                ) {
                    Text("Decrypt & Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = JarvisSurfaceDark,
            title = { Text("Permanently Delete All Data?", color = JarvisTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete ALL audio files, transcripts, meeting minutes, and personal notes. This action cannot be undone.", color = JarvisTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData {
                            showClearAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}
