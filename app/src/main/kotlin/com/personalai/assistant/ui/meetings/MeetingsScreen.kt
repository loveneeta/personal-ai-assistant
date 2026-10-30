package com.personalai.assistant.ui.meetings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.personalai.assistant.data.local.entity.MeetingEntity
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisDeepNavy
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsScreen(
    viewModel: MeetingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMeetingDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()

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
                                Icons.Default.Groups,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                "MEETING INTELLIGENCE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                "EXECUTIVE DEBRIEFS & LOGS",
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
            if (meetings.isEmpty()) {
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
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = JarvisNeonViolet
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "NO BRIEFINGS RECORDED",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Open any recording with speech and tap 'Generate Meeting Minutes' to automatically extract executive summaries, decisions, and action items.",
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
                    items(meetings, key = { it.id }) { meeting ->
                        MeetingCard(
                            meeting = meeting,
                            onClick = { onNavigateToMeetingDetails(meeting.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeetingCard(
    meeting: MeetingEntity,
    onClick: () -> Unit
) {
    JarvisGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        glowColor = JarvisCyan,
        borderColor = JarvisBorderCyan.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(JarvisCyan.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                        .border(1.dp, JarvisCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meeting.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )
                    val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                        .format(Date(meeting.startTimestamp))
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisIceBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = JarvisCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!meeting.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    meeting.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    color = JarvisTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
