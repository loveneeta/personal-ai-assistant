package com.personalai.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.personalai.assistant.ai.ChatMessage
import com.personalai.assistant.ai.Citation
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.components.JarvisStatusBadge
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisDeepNavy
import com.personalai.assistant.ui.theme.JarvisElectricBlue
import com.personalai.assistant.ui.theme.JarvisGlowCyan
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisSurfaceDark
import com.personalai.assistant.ui.theme.JarvisSurfaceGlass
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecording: (Long) -> Unit,
    onNavigateToMeeting: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var inputPrompt by remember { mutableStateOf("") }

    val promptSuggestions = listOf(
        "What are my pending tasks?",
        "Summarize recent meetings",
        "What decisions were made?",
        "What did we discuss today?"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
                                "J.A.R.V.I.S.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                "NEURAL KNOWLEDGE QUERY",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        JarvisStatusBadge(label = "RAG ONLINE", isPulsing = true)
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
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = "Clear History",
                            tint = JarvisTextSecondary
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
            // Chat message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        JarvisGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            glowColor = JarvisNeonViolet,
                            borderColor = JarvisBorderViolet
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "NEURAL CORE READY",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisTextPrimary,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Ask anything about past meetings, extracted action items, voice recordings, or transcripts. Everything is queried locally on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        onCitationClick = { citation ->
                            when (citation.type) {
                                "RECORDING" -> onNavigateToRecording(citation.referenceId)
                                "MEETING" -> onNavigateToMeeting(citation.referenceId)
                            }
                        }
                    )
                }

                if (isLoading) {
                    item {
                        JarvisGlassCard(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            glowColor = JarvisCyan,
                            borderColor = JarvisBorderCyan
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = JarvisCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "QUERYING LOCAL NEURAL INDEX...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            // Quick suggestion chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(promptSuggestions) { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JarvisSurfaceGlass,
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable {
                            viewModel.sendMessage(suggestion)
                        }
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 12.sp,
                            color = JarvisIceBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Futuristic Input Bar
            Surface(
                color = JarvisSurfaceDark.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = {
                            Text(
                                "Ask J.A.R.V.I.S. about meetings, tasks...",
                                color = JarvisTextMuted,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorderCyan.copy(alpha = 0.5f),
                            focusedContainerColor = JarvisCosmicBlack.copy(alpha = 0.7f),
                            unfocusedContainerColor = JarvisCosmicBlack.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            if (inputPrompt.isNotBlank()) {
                                viewModel.sendMessage(inputPrompt)
                                inputPrompt = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(JarvisCyan, JarvisElectricBlue)
                                )
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = JarvisCosmicBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onCitationClick: (Citation) -> Unit
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                ),
                color = JarvisSurfaceGlass,
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "COMMAND",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextPrimary
                    )
                }
            }
        } else {
            JarvisGlassCard(
                modifier = Modifier.fillMaxWidth(0.92f),
                glowColor = JarvisCyan,
                borderColor = JarvisBorderCyan.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(JarvisCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "J.A.R.V.I.S. INTELLIGENCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisIceBlue,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextPrimary,
                        lineHeight = 20.sp
                    )

                    if (message.citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "KNOWLEDGE CITATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.citations.forEach { cit ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = JarvisDeepNavy.copy(alpha = 0.8f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCitationClick(cit) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = JarvisCyan
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "[${cit.type}] ${cit.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = JarvisIceBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
