package com.personalai.assistant.ui.tasks

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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisDeepNavy
import com.personalai.assistant.ui.theme.JarvisElectricBlue
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
fun TasksScreen(
    viewModel: TasksViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskAssignee by remember { mutableStateOf("") }

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
                                Icons.Default.Assignment,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                "ACTION MATRIX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                "AI-EXTRACTED PROTOCOLS",
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
                actions = {
                    IconButton(onClick = { viewModel.shareCsv(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share CSV", tint = JarvisIceBlue)
                    }
                    IconButton(onClick = {
                        val file = viewModel.exportCsv(context)
                        Toast.makeText(context, "Exported: ${file.name}", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = JarvisCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisDeepNavy.copy(alpha = 0.95f),
                    titleContentColor = JarvisTextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = JarvisCyan,
                contentColor = JarvisCosmicBlack,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
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
                .padding(16.dp)
        ) {
            // Filter Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TaskFilter.ALL to "ALL",
                    TaskFilter.PENDING to "PENDING",
                    TaskFilter.COMPLETED to "RESOLVED"
                ).forEach { (filterType, label) ->
                    val isSelected = currentFilter == filterType
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) JarvisCyan.copy(alpha = 0.15f) else JarvisSurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) JarvisCyan else JarvisBorderCyan.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setFilter(filterType) }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) JarvisCyan else JarvisTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = JarvisCyan
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "NO PROTOCOLS FOUND",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Action items extracted from meetings or added manually will appear in this matrix.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRowCard(
                            task = task,
                            onToggle = { viewModel.toggleTaskStatus(task.id, task.status) },
                            onDelete = { viewModel.deleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = JarvisSurfaceDark,
            title = {
                Text(
                    "NEW PROTOCOL TASK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                ) {
                    Text("Add Protocol", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}

@Composable
fun TaskRowCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = task.status == "COMPLETED"

    JarvisGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (isCompleted) JarvisNeonViolet else JarvisCyan,
        borderColor = if (isCompleted) JarvisBorderViolet.copy(alpha = 0.5f) else JarvisBorderCyan.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onToggle() },
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
                    color = if (isCompleted) JarvisTextMuted else JarvisTextPrimary,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                if (!task.assignedPerson.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            task.assignedPerson,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisIceBlue,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                if (task.dueDate != null) {
                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = JarvisNeonViolet,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "DUE: $dateStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisNeonViolet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
