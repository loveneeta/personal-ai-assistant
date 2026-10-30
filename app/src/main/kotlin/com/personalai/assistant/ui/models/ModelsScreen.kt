package com.personalai.assistant.ui.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.transcription.models.ModelDownloadStage
import com.personalai.assistant.transcription.models.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val activeId by viewModel.activeModelId.collectAsStateWithLifecycle()
    val downloadingId by viewModel.downloadingModelId.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val stage by viewModel.downloadStage.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = com.personalai.assistant.ui.theme.JarvisCosmicBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ON-DEVICE NEURAL MODELS", color = com.personalai.assistant.ui.theme.JarvisTextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "OFFLINE ASR & ACOUSTIC PACKS",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.personalai.assistant.ui.theme.JarvisDeepNavy.copy(alpha = 0.95f),
                    titleContentColor = com.personalai.assistant.ui.theme.JarvisTextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "100% On-Device AI • Zero Cloud Cost",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Speech-to-text transcription runs directly on your device CPU. Models are downloaded once from verified open repositories and never transmit speech audio to external servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (statusMsg != null) {
                item {
                    val isError = statusMsg!!.contains("failed", ignoreCase = true) || statusMsg!!.contains("Error", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                statusMsg!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            items(models, key = { it.id }) { model ->
                val isThisDownloading = downloadingId == model.id
                val isThisActive = model.id == activeId && model.isDownloaded

                ModelCard(
                    model = model,
                    isActive = isThisActive,
                    isDownloading = isThisDownloading,
                    downloadProgress = if (isThisDownloading) progress else 0f,
                    downloadStage = if (isThisDownloading) stage else ModelDownloadStage.Idle,
                    onDownload = { viewModel.download(model.id) },
                    onCancel = { viewModel.cancelDownload() },
                    onActivate = { viewModel.selectActive(model.id) },
                    onDelete = { viewModel.delete(model.id) }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelInfo,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStage: ModelDownloadStage,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        model.language,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.width(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Download: ${model.sizeFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Runtime: ${model.approxRamFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                when (downloadStage) {
                    is ModelDownloadStage.Extracting, is ModelDownloadStage.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val stageText = when (downloadStage) {
                    is ModelDownloadStage.Connecting -> "Connecting to server..."
                    is ModelDownloadStage.Extracting -> "Extracting model files safely..."
                    is ModelDownloadStage.Verifying -> "Verifying acoustic model integrity..."
                    is ModelDownloadStage.Completed -> "Installation verified!"
                    else -> "Downloading: ${(downloadProgress * 100).toInt()}%"
                }
                Text(
                    stageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloading) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                } else if (!model.isDownloaded) {
                    Button(
                        onClick = onDownload,
                        enabled = true
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Model")
                    }
                } else {
                    if (!isActive) {
                        FilledTonalButton(onClick = onActivate) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set as Active")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
