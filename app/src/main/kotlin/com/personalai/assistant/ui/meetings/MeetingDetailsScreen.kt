package com.personalai.assistant.ui.meetings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.data.local.entity.TaskEntity
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
fun MeetingDetailsScreen(
    viewModel: MeetingDetailsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meeting by viewModel.meeting.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskAssignee by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = JarvisCosmicBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            meeting?.title ?: "MEETING DOSSIER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            "DEBRIEF & ACTION LOG",
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
                actions = {
                    IconButton(onClick = { viewModel.shareMeeting(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = JarvisIceBlue)
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
                    containerColor = JarvisDeepNavy.copy(alpha = 0.95f),
                    titleContentColor = JarvisTextPrimary
                )
            )
        }
    ) { innerPadding ->
        val m = meeting
        if (m == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "INITIALIZING DOSSIER...",
                    color = JarvisCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Date Card
                item {
                    JarvisGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = JarvisCyan,
                        borderColor = JarvisBorderCyan
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                m.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault())
                                .format(Date(m.startTimestamp))
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisIceBlue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = JarvisSurfaceGlass,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan),
                                    modifier = Modifier.clickable {
                                        val f = viewModel.exportPdf(context)
                                        if (f != null) {
                                            Toast.makeText(context, "Exported PDF to ${f.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = JarvisCyan
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "EXPORT PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = JarvisCyan,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = JarvisSurfaceGlass,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan),
                                    modifier = Modifier.clickable {
                                        val f = viewModel.exportMarkdown(context)
                                        if (f != null) {
                                            Toast.makeText(context, "Exported Markdown to ${f.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        "MARKDOWN",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = JarvisIceBlue,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Executive Summary Card
                item {
                    JarvisGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = JarvisNeonViolet,
                        borderColor = JarvisBorderViolet.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "EXECUTIVE SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                m.summary ?: "No executive summary available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Action Items Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ASSIGNED PROTOCOLS (${tasks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisIceBlue,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { showAddTaskDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task", tint = JarvisCyan)
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    item {
                        Text(
                            "No action items assigned for this meeting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        JarvisGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = JarvisCyan,
                            borderColor = JarvisBorderCyan.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.status == "COMPLETED",
                                    onCheckedChange = { viewModel.toggleTaskStatus(task.id, task.status) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = JarvisCyan,
                                        checkmarkColor = JarvisCosmicBlack,
                                        uncheckedColor = JarvisBorderCyan
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (task.status == "COMPLETED") JarvisTextMuted else JarvisTextPrimary
                                    )
                                    if (!task.assignedPerson.isNullOrBlank()) {
                                        Text(
                                            "Assigned: ${task.assignedPerson}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisCyan,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Spoken Transcript Section
                if (transcripts.isNotEmpty()) {
                    item {
                        Text(
                            "AUDIO TRANSCRIPTION TELEMETRY (${transcripts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisIceBlue,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(transcripts) { tr ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisSurfaceGlass,
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    formatMs(tr.startOffsetMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    tr.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextPrimary,
                                    modifier = Modifier.weight(1f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            containerColor = JarvisSurfaceDark,
            title = {
                Text(
                    "ADD ACTION PROTOCOL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        label = { Text("Task Description", color = JarvisTextSecondary) },
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
                    OutlinedTextField(
                        value = newTaskAssignee,
                        onValueChange = { newTaskAssignee = it },
                        label = { Text("Assignee (Optional)", color = JarvisTextSecondary) },
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
                        if (newTaskTitle.isNotBlank()) {
                            viewModel.addTask(newTaskTitle.trim(), newTaskAssignee.trim())
                            newTaskTitle = ""
                            newTaskAssignee = ""
                            showAddTaskDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = JarvisSurfaceDark,
            title = {
                Text(
                    "PURGE MEETING MINUTES?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    "This will remove this meeting record and its generated minutes. Linked recordings will remain intact.",
                    color = JarvisTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMeeting {
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}
