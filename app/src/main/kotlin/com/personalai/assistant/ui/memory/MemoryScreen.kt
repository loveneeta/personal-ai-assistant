package com.personalai.assistant.ui.memory

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.personalai.assistant.data.local.entity.NoteEntity
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
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecording: (Long) -> Unit,
    onNavigateToMeeting: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Search, 1: Notes
    var showAddNoteDialog by remember { mutableStateOf(false) }

    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNotePinned by remember { mutableStateOf(false) }

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
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                "KNOWLEDGE ARCHIVE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                "FULL-TEXT FTS4 RETRIEVAL",
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
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddNoteDialog = true },
                    containerColor = JarvisCyan,
                    contentColor = JarvisCosmicBlack,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
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
        ) {
            // Futuristic Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "GLOBAL SEARCH", 1 to "NOTES (${notes.size})").forEach { (tabIndex, tabTitle) ->
                    val isSelected = selectedTab == tabIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) JarvisCyan.copy(alpha = 0.15f) else JarvisSurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) JarvisCyan else JarvisBorderCyan.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = tabIndex }
                    ) {
                        Text(
                            text = tabTitle,
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

            if (selectedTab == 0) {
                // Search Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.onSearchQueryChange(it)
                        },
                        placeholder = {
                            Text(
                                "Search transcripts, meetings, tasks, notes...",
                                color = JarvisTextMuted,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = JarvisCyan)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.onSearchQueryChange("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = JarvisTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorderCyan,
                            focusedContainerColor = JarvisSurfaceDark,
                            unfocusedContainerColor = JarvisSurfaceDark
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (searchState.isSearching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = JarvisCyan
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "QUERYING LOCAL OFFLINE FTS4 INDEX...",
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else if (searchQuery.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = JarvisCyan
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        "LOCAL FULL-TEXT MEMORY",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = JarvisTextPrimary,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Type any query to search across all recorded transcripts, executive meeting minutes, and notes instantly on-device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        val total = searchState.transcripts.size + searchState.meetings.size + searchState.tasks.size + searchState.notes.size
                        if (total == 0) {
                            Text(
                                "NO MATCHES FOR \"$searchQuery\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(
                                "LOCATED $total MATCHES IN MEMORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(searchState.transcripts) { tr ->
                                    JarvisGlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToRecording(tr.recordingId) },
                                        glowColor = JarvisCyan,
                                        borderColor = JarvisBorderCyan.copy(alpha = 0.5f)
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp)) {
                                            Icon(
                                                Icons.Default.Hearing,
                                                contentDescription = null,
                                                tint = JarvisCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    "Recording #${tr.recordingId} • ${formatMs(tr.startOffsetMs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = JarvisIceBlue,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    tr.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = JarvisTextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                items(searchState.meetings) { m ->
                                    JarvisGlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToMeeting(m.id) },
                                        glowColor = JarvisNeonViolet,
                                        borderColor = JarvisBorderViolet.copy(alpha = 0.5f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Meeting: ${m.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = JarvisTextPrimary
                                            )
                                            if (!m.summary.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    m.summary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    color = JarvisTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                items(searchState.tasks) { t ->
                                    JarvisGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        glowColor = JarvisCyan,
                                        borderColor = JarvisBorderCyan.copy(alpha = 0.4f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Action Item: ${t.title}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = JarvisTextPrimary
                                            )
                                            Text(
                                                "Status: ${t.status}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = JarvisCyan,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                items(searchState.notes) { n ->
                                    JarvisGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        glowColor = JarvisIceBlue,
                                        borderColor = JarvisBorderCyan.copy(alpha = 0.4f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Note: ${n.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = JarvisTextPrimary
                                            )
                                            Text(
                                                n.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                color = JarvisTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Notes Tab
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = JarvisNeonViolet,
                            borderColor = JarvisBorderViolet
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Note,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = JarvisNeonViolet
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "NO PERSONAL NOTES RECORDED",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisTextPrimary,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Tap the + button below to log your first thought or memo.",
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
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onTogglePin = { viewModel.togglePin(note.id, note.isPinned) },
                                onDelete = { viewModel.deleteNote(note.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            containerColor = JarvisSurfaceDark,
            title = {
                Text(
                    "LOG PERSONAL NOTE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newNoteTitle,
                        onValueChange = { newNoteTitle = it },
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
                    OutlinedTextField(
                        value = newNoteContent,
                        onValueChange = { newNoteContent = it },
                        label = { Text("Content / Thoughts", color = JarvisTextSecondary) },
                        minLines = 3,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = newNotePinned,
                            onCheckedChange = { newNotePinned = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = JarvisCyan,
                                checkmarkColor = JarvisCosmicBlack,
                                uncheckedColor = JarvisBorderCyan
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pin note to top priority", color = JarvisTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNoteTitle.isNotBlank() || newNoteContent.isNotBlank()) {
                            viewModel.addNote(
                                title = newNoteTitle.ifBlank { "Untitled Note" },
                                content = newNoteContent,
                                isPinned = newNotePinned
                            )
                            newNoteTitle = ""
                            newNoteContent = ""
                            newNotePinned = false
                            showAddNoteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}

@Composable
fun NoteCard(
    note: NoteEntity,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    JarvisGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (note.isPinned) JarvisNeonViolet else JarvisCyan,
        borderColor = if (note.isPinned) JarvisBorderViolet.copy(alpha = 0.7f) else JarvisBorderCyan.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (note.isPinned) JarvisNeonViolet else JarvisTextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(note.updatedAt))
            Text(
                dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisIceBlue,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}
