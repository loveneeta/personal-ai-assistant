package com.personalai.assistant.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.recording.RecordingState
import com.personalai.assistant.ui.components.JarvisBottomNavigationBar
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.components.JarvisNavDestination
import com.personalai.assistant.ui.components.JarvisNavigationRailBar
import com.personalai.assistant.ui.components.JarvisOrb
import com.personalai.assistant.ui.components.JarvisOrbState
import com.personalai.assistant.ui.components.JarvisAlwaysReadyPanel
import com.personalai.assistant.ui.components.JarvisVoiceEnrollmentDialog
import com.personalai.assistant.wakeword.JarvisAudioState
import com.personalai.assistant.ui.components.JarvisStatusBadge
import com.personalai.assistant.ui.components.JarvisTopAppBar
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisGlassBorder
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNavyCard
import com.personalai.assistant.ui.theme.JarvisNavyDeep
import com.personalai.assistant.ui.theme.JarvisSpaceBlack
import com.personalai.assistant.ui.theme.JarvisSpaceVoid
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusListening
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisStatusWarning
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import com.personalai.assistant.ui.theme.JarvisVioletNeon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToRecordings: () -> Unit,
    onNavigateToRecordingDetails: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMeetings: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val todayRecordings by viewModel.todayRecordings.collectAsStateWithLifecycle()
    val recentRecordings by viewModel.recentRecordings.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeModelId.collectAsStateWithLifecycle()

    // Live Google Voice Typing states
    val isGoogleVoiceListening by viewModel.isGoogleVoiceListening.collectAsStateWithLifecycle()
    val googleLiveHypothesis by viewModel.googleVoiceLiveHypothesis.collectAsStateWithLifecycle()
    val googleCompletedSentences by viewModel.googleVoiceCompletedSentences.collectAsStateWithLifecycle()
    val googleSoundLevel by viewModel.googleVoiceSoundLevel.collectAsStateWithLifecycle()
    val googleVoiceError by viewModel.googleVoiceError.collectAsStateWithLifecycle()

    val alwaysOnState by viewModel.alwaysOnState.collectAsStateWithLifecycle()
    val alwaysOnSoundLevel by viewModel.alwaysOnSoundLevel.collectAsStateWithLifecycle()
    var showEnrollmentDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Live Voice Matrix, 1: High-Fi Audio WAV
    var googleVoiceLang by remember { mutableStateOf("hi-en") } // Default to Hinglish
    var customTitle by remember { mutableStateOf("") }
    var globalCommandInput by remember { mutableStateOf("") }
    var isSidebarExpanded by remember { mutableStateOf(false) }

    // Dialogue for editing recognized sentence before final save
    var editingSentenceIndex by remember { mutableIntStateOf(-1) }
    var editingSentenceText by remember { mutableStateOf("") }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Determine current JARVIS Voice Orb state
    val orbState = when {
        alwaysOnState == JarvisAudioState.WAKE_DETECTED || alwaysOnState == JarvisAudioState.COMMAND_LISTENING || isGoogleVoiceListening || recordingState is RecordingState.Recording -> JarvisOrbState.LISTENING
        alwaysOnState == JarvisAudioState.PROCESSING || alwaysOnState == JarvisAudioState.VERIFYING_SPEAKER || recordingState is RecordingState.Finalizing -> JarvisOrbState.PROCESSING
        alwaysOnState == JarvisAudioState.SPEAKING -> JarvisOrbState.SPEAKING
        alwaysOnState == JarvisAudioState.ERROR || googleVoiceError != null || recordingState is RecordingState.Error -> JarvisOrbState.ERROR
        else -> JarvisOrbState.IDLE
    }
    val activeSoundLevel = kotlin.math.max(googleSoundLevel, alwaysOnSoundLevel)

    // Context-aware greeting based on local time
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "GOOD MORNING, COMMANDER"
            in 12..16 -> "GOOD AFTERNOON, COMMANDER"
            in 17..21 -> "GOOD EVENING, COMMANDER"
            else -> "SYSTEMS ONLINE, COMMANDER"
        }
    }

    // Responsive screen layout handling
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(JarvisSpaceVoid)) {
        val isWideScreen = maxWidth >= 840.dp
        val isTablet = maxWidth >= 600.dp && maxWidth < 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive Rail / Sidebar for Tablet and Desktop
            if (isWideScreen || isTablet) {
                JarvisNavigationRailBar(
                    currentDestination = JarvisNavDestination.HOME,
                    onNavigate = { dest ->
                        when (dest) {
                            JarvisNavDestination.HOME -> {}
                            JarvisNavDestination.CHAT -> onNavigateToChat()
                            JarvisNavDestination.MEETINGS -> onNavigateToMeetings()
                            JarvisNavDestination.TASKS -> onNavigateToTasks()
                            JarvisNavDestination.MEMORY -> onNavigateToMemory()
                            JarvisNavDestination.SETTINGS -> onNavigateToSettings()
                        }
                    },
                    isExpanded = isSidebarExpanded && isWideScreen,
                    onToggleExpand = if (isWideScreen) { { isSidebarExpanded = !isSidebarExpanded } } else null
                )
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = JarvisSpaceVoid,
                topBar = {
                    JarvisTopAppBar(
                        title = "J.A.R.V.I.S.",
                        subtitle = "LOCAL QUANTUM CORE // ₹0 PRIVACY GUARANTEE",
                        onNavigateToSettings = onNavigateToSettings,
                        onToggleSidebar = if (isWideScreen) { { isSidebarExpanded = !isSidebarExpanded } } else null,
                        isSidebarOpen = isSidebarExpanded
                    )
                },
                bottomBar = {
                    if (!isWideScreen && !isTablet) {
                        JarvisBottomNavigationBar(
                            currentDestination = JarvisNavDestination.HOME,
                            onNavigate = { dest ->
                                when (dest) {
                                    JarvisNavDestination.HOME -> {}
                                    JarvisNavDestination.CHAT -> onNavigateToChat()
                                    JarvisNavDestination.MEETINGS -> onNavigateToMeetings()
                                    JarvisNavDestination.TASKS -> onNavigateToTasks()
                                    JarvisNavDestination.MEMORY -> onNavigateToMemory()
                                    JarvisNavDestination.SETTINGS -> onNavigateToSettings()
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = if (isWideScreen) 32.dp else 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Context-Aware Greeting & Assistant Status Header
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusSubtitle = when {
                                    !hasMicPermission -> "Microphone permission required for voice protocols."
                                    activeModelId.isBlank() -> "No offline speech model active. Tap Settings to configure."
                                    alwaysOnState == JarvisAudioState.WAKE_LISTENING -> "Always Ready active // Keyword spotter listening for 'Jarvis'"
                                    alwaysOnState == JarvisAudioState.COMMAND_LISTENING -> "Capturing speech command (Hinglish/English)..."
                                    alwaysOnState == JarvisAudioState.PROCESSING -> "Processing command through local neural core..."
                                    alwaysOnState == JarvisAudioState.SPEAKING -> "J.A.R.V.I.S. vocal response active."
                                    alwaysOnState == JarvisAudioState.ERROR -> "Voice engine fault. Check microphone permissions."
                                    isGoogleVoiceListening -> "Live Voice Matrix dictation active."
                                    recordingState is RecordingState.Recording -> "High-Fi PCM WAV recording in progress."
                                    else -> "Local quantum core standby // Ready for instructions."
                                }
                                val statusBadgeLabel = when {
                                    !hasMicPermission || alwaysOnState == JarvisAudioState.ERROR -> "ATTENTION"
                                    alwaysOnState == JarvisAudioState.WAKE_LISTENING -> "LISTENING"
                                    alwaysOnState == JarvisAudioState.COMMAND_LISTENING -> "COMMAND"
                                    alwaysOnState == JarvisAudioState.PROCESSING -> "COMPUTING"
                                    alwaysOnState == JarvisAudioState.SPEAKING -> "TALKING"
                                    isGoogleVoiceListening || recordingState is RecordingState.Recording -> "RECORDING"
                                    else -> "STANDBY"
                                }
                                val statusBadgeColor = when {
                                    !hasMicPermission || alwaysOnState == JarvisAudioState.ERROR -> JarvisStatusError
                                    alwaysOnState == JarvisAudioState.WAKE_LISTENING || isGoogleVoiceListening || recordingState is RecordingState.Recording -> JarvisStatusListening
                                    alwaysOnState == JarvisAudioState.COMMAND_LISTENING || alwaysOnState == JarvisAudioState.SPEAKING -> JarvisCyanElectric
                                    alwaysOnState == JarvisAudioState.PROCESSING -> JarvisVioletNeon
                                    else -> JarvisStatusOnline
                                }

                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = greeting,
                                        color = JarvisCyanElectric,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = statusSubtitle,
                                        color = JarvisTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                JarvisStatusBadge(
                                    label = statusBadgeLabel,
                                    statusColor = statusBadgeColor,
                                    isPulsing = alwaysOnState == JarvisAudioState.WAKE_LISTENING || isGoogleVoiceListening || recordingState is RecordingState.Recording
                                )
                            }
                        }
                    }

                    // 2. Global AI Command & Prompt Input Bar
                    item {
                        JarvisGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowAccent = globalCommandInput.isNotBlank()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = JarvisCyanElectric,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = globalCommandInput,
                                    onValueChange = { globalCommandInput = it },
                                    placeholder = {
                                        Text(
                                            "Ask JARVIS or query memory (Ctrl+K)...",
                                            color = JarvisTextMuted,
                                            fontSize = 14.sp
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = JarvisTextPrimary,
                                        unfocusedTextColor = JarvisTextPrimary
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        if (globalCommandInput.isNotBlank()) {
                                            onNavigateToChat()
                                        } else {
                                            onNavigateToChat()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(JarvisCyanElectric.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Submit Command",
                                        tint = JarvisCyanElectric,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Permission Alert (if microphone not granted)
                    if (!hasMicPermission) {
                        item {
                            JarvisGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = JarvisStatusError
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = JarvisStatusError
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "AUDIO HARDWARE ACCESS REQUIRED",
                                            color = JarvisStatusError,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.0.sp,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Voice input, meeting transcription, and daily synthesis operate 100% on-device. Please authorize microphone access to activate the voice orb.",
                                        color = JarvisTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(perms.toTypedArray())
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisStatusError)
                                    ) {
                                        Text("Authorize Audio Hardware", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 3.5. Hands-Free Always-Ready Panel
                    item {
                        JarvisAlwaysReadyPanel(
                            currentState = alwaysOnState,
                            isSpeakerVerified = viewModel.isSpeakerVerificationEnabled(context),
                            isSpeakerEnrolled = viewModel.isSpeakerEnrolled(context),
                            onToggleAlwaysReady = { enabled ->
                                if (!hasMicPermission) {
                                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(perms.toTypedArray())
                                } else {
                                    if (enabled) {
                                        viewModel.startAlwaysOn(context)
                                    } else {
                                        viewModel.stopAlwaysOn(context)
                                    }
                                }
                            },
                            onPauseListening = { viewModel.pauseAlwaysOn(context) },
                            onResumeListening = { viewModel.resumeAlwaysOn(context) },
                            onEnrollVoiceClick = { showEnrollmentDialog = true },
                            onOpenDiagnostics = onNavigateToDiagnostics
                        )
                    }

                    // 4. Primary Animated JARVIS Voice Orb & Matrix Controller
                    item {
                        JarvisGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowAccent = orbState == JarvisOrbState.LISTENING
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Mode Selector: Live Voice Matrix vs Offline PCM WAV
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = JarvisSpaceBlack,
                                    contentColor = JarvisCyanElectric,
                                    indicator = { tabPositions ->
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = JarvisCyanElectric,
                                            height = 2.dp
                                        )
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, JarvisGlassBorder, RoundedCornerShape(10.dp))
                                ) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Live Voice Matrix", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Offline PCM WAV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // The Central JARVIS Interactive Voice Orb
                                JarvisOrb(
                                    state = orbState,
                                    soundLevel = activeSoundLevel,
                                    size = if (isWideScreen) 160.dp else 140.dp,
                                    onClick = {
                                        if (!hasMicPermission) {
                                            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(perms.toTypedArray())
                                        } else {
                                            if (selectedTab == 0) {
                                                if (isGoogleVoiceListening) {
                                                    viewModel.stopGoogleVoiceListening()
                                                } else {
                                                    viewModel.startGoogleVoiceListening(googleVoiceLang)
                                                }
                                            } else {
                                                when (recordingState) {
                                                    is RecordingState.Recording -> viewModel.stopRecording(context)
                                                    is RecordingState.Paused -> viewModel.resumeRecording(context)
                                                    else -> {
                                                        val title = customTitle.ifBlank { null }
                                                        viewModel.startRecording(context, title)
                                                        customTitle = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Orb Subtitle & State indicator
                                val orbStatusText = when (orbState) {
                                    JarvisOrbState.LISTENING -> "LIVE AUDIO CAPTURE ACTIVE // SPEAK NOW"
                                    JarvisOrbState.PROCESSING -> "QUANTUM SYNTHESIS IN PROGRESS..."
                                    JarvisOrbState.ERROR -> "AUDIO BUS ANOMALY DETECTED"
                                    JarvisOrbState.SPEAKING -> "TRANSMITTING RESPONSE"
                                    JarvisOrbState.IDLE -> "JARVIS CORE STANDBY // TAP ORB TO ENGAGE"
                                }
                                Text(
                                    text = orbStatusText,
                                    color = when (orbState) {
                                        JarvisOrbState.LISTENING -> JarvisCyanElectric
                                        JarvisOrbState.ERROR -> JarvisStatusError
                                        JarvisOrbState.PROCESSING -> JarvisVioletNeon
                                        else -> JarvisTextSecondary
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.0.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // MODE 0: Live Voice Matrix Controls
                                if (selectedTab == 0) {
                                    if (!isGoogleVoiceListening) {
                                        // Language Code-Switching Selector (Hinglish fidelity)
                                        Text(
                                            "Acoustic Language Matrix:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = JarvisTextSecondary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilterChip(
                                                selected = googleVoiceLang == "hi-en",
                                                onClick = { googleVoiceLang = "hi-en" },
                                                label = { Text("🇮🇳 Hinglish (Auto)", fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = JarvisNavyCard,
                                                    selectedLabelColor = JarvisCyanElectric
                                                )
                                            )
                                            FilterChip(
                                                selected = googleVoiceLang == "en-IN",
                                                onClick = { googleVoiceLang = "en-IN" },
                                                label = { Text("🌐 English (IN)", fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = JarvisNavyCard,
                                                    selectedLabelColor = JarvisCyanElectric
                                                )
                                            )
                                            FilterChip(
                                                selected = googleVoiceLang == "hi-IN",
                                                onClick = { googleVoiceLang = "hi-IN" },
                                                label = { Text("हिन्दी", fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = JarvisNavyCard,
                                                    selectedLabelColor = JarvisCyanElectric
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = customTitle,
                                            onValueChange = { customTitle = it },
                                            label = { Text("Session Protocol / Title", fontSize = 12.sp) },
                                            placeholder = { Text("e.g. Core Architecture Briefing", color = JarvisTextMuted) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = JarvisCyanElectric,
                                                unfocusedBorderColor = JarvisGlassBorder
                                            )
                                        )
                                    } else {
                                        // Active Listening readout & Live Hypothesis
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { googleSoundLevel },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = JarvisCyanElectric,
                                                trackColor = JarvisNavyCard
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Live Speech readout box
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(JarvisNavyDeep)
                                                    .border(1.dp, JarvisCyanElectric.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                                    .padding(14.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        "REAL-TIME NEURAL READOUT:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = JarvisCyanElectric,
                                                        letterSpacing = 1.0.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = if (googleLiveHypothesis.isNotBlank()) "\"${googleLiveHypothesis}\"" else "Listening to frequency buffer...",
                                                        color = if (googleLiveHypothesis.isNotBlank()) JarvisTextPrimary else JarvisTextMuted,
                                                        fontSize = 14.sp,
                                                        fontStyle = if (googleLiveHypothesis.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            // Completed sentences stream
                                            if (googleCompletedSentences.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    "Committed Transcripts (${googleCompletedSentences.size}):",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = JarvisTextSecondary,
                                                    modifier = Modifier.align(Alignment.Start)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    googleCompletedSentences.takeLast(3).forEachIndexed { idx, sentence ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(JarvisNavyDeep)
                                                                .border(0.8.dp, JarvisGlassBorder, RoundedCornerShape(6.dp))
                                                                .padding(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = JarvisStatusOnline,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = sentence,
                                                                color = JarvisTextPrimary,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            // Quick edit icon to correct transcription errors faithfully
                                                            IconButton(
                                                                onClick = {
                                                                    editingSentenceIndex = idx
                                                                    editingSentenceText = sentence
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Edit,
                                                                    contentDescription = "Edit transcription",
                                                                    tint = JarvisTextSecondary,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.stopGoogleVoiceListening() },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGlassBorder)
                                                ) {
                                                    Text("Disengage", color = JarvisTextSecondary)
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.saveGoogleVoiceSession(
                                                            title = customTitle.ifBlank { null },
                                                            languageCode = googleVoiceLang
                                                        ) { recId ->
                                                            customTitle = ""
                                                            Toast.makeText(context, "Session archived to Neural Database", Toast.LENGTH_SHORT).show()
                                                            onNavigateToRecordingDetails(recId)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanElectric)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = JarvisSpaceBlack, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Commit & Synthesize", color = JarvisSpaceBlack, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // MODE 1: Offline PCM WAV Recording controls
                                    when (val state = recordingState) {
                                        is RecordingState.Idle, is RecordingState.Completed, is RecordingState.Error -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilterChip(
                                                    selected = activeModelId.contains("hi"),
                                                    onClick = { viewModel.setActiveModel("vosk-model-small-hi-0.22") },
                                                    label = { Text("🇮🇳 Hindi", fontSize = 11.sp) }
                                                )
                                                FilterChip(
                                                    selected = activeModelId.contains("en-in"),
                                                    onClick = { viewModel.setActiveModel("vosk-model-small-en-in-0.4") },
                                                    label = { Text("🌐 Indian English", fontSize = 11.sp) }
                                                )
                                                FilterChip(
                                                    selected = activeModelId.contains("en-us"),
                                                    onClick = { viewModel.setActiveModel("vosk-model-small-en-us-0.15") },
                                                    label = { Text("US", fontSize = 11.sp) }
                                                )
                                            }
                                        }

                                        is RecordingState.Recording -> {
                                            Text(
                                                text = formatMs(state.durationMs),
                                                fontSize = 42.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = JarvisCyanElectric
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                OutlinedButton(
                                                    onClick = { viewModel.pauseRecording(context) },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGlassBorder)
                                                ) {
                                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = JarvisIceBlue)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Pause", color = JarvisIceBlue)
                                                }
                                                Button(
                                                    onClick = { viewModel.stopRecording(context) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisStatusError)
                                                ) {
                                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Finalize", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        is RecordingState.Paused -> {
                                            Text(
                                                text = formatMs(state.durationMs),
                                                fontSize = 42.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = JarvisStatusWarning
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Button(
                                                    onClick = { viewModel.resumeRecording(context) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanElectric)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = JarvisSpaceBlack)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Resume", color = JarvisSpaceBlack)
                                                }
                                                OutlinedButton(
                                                    onClick = { viewModel.stopRecording(context) },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGlassBorder)
                                                ) {
                                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = JarvisStatusError)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Stop", color = JarvisStatusError)
                                                }
                                            }
                                        }

                                        is RecordingState.Finalizing -> {
                                            Text("Encoding Linear PCM WAV to Sandboxed Storage...", color = JarvisCyanElectric, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. System Intelligence & Telemetry Cards (Today's metrics)
                    item {
                        val totalDurationMs = todayRecordings.sumOf { it.durationMs }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            JarvisGlassCard(modifier = Modifier.weight(1f)) {
                                Column {
                                    Text(
                                        "RECORDINGS TODAY",
                                        color = JarvisTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.0.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${todayRecordings.size}",
                                        color = JarvisCyanElectric,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            JarvisGlassCard(modifier = Modifier.weight(1f)) {
                                Column {
                                    Text(
                                        "AUDIO CAPTURED",
                                        color = JarvisTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.0.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatMs(totalDurationMs),
                                        color = JarvisIceBlue,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // 6. Futuristic Quick Action Holographic Cards (Phase 3 requirements)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "INTELLIGENCE MATRIX & WORKSPACE",
                                color = JarvisTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )

                            // Row 1: Ask JARVIS & Meeting Intelligence
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                JarvisGlassCard(
                                    modifier = Modifier.weight(1f),
                                    glowAccent = true,
                                    onClick = onNavigateToChat,
                                    onClickLabel = "Open JARVIS Neural Chat"
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = JarvisCyanElectric, modifier = Modifier.size(20.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Ask J.A.R.V.I.S.", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Source-Grounded Memory", color = JarvisCyanElectric.copy(alpha = 0.8f), fontSize = 11.sp)
                                    }
                                }

                                JarvisGlassCard(
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToMeetings,
                                    onClickLabel = "Open Meetings and Minutes"
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Hearing, contentDescription = null, tint = JarvisIceBlue, modifier = Modifier.size(20.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Meeting Minutes", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("NLP Extracts & PDF", color = JarvisTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Row 2: Action Items & Sub-millisecond Memory
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                JarvisGlassCard(
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToTasks,
                                    onClickLabel = "Open Action Items"
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisVioletNeon, modifier = Modifier.size(20.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Action Protocols", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Tasks & Deadlines", color = JarvisTextSecondary, fontSize = 11.sp)
                                    }
                                }

                                JarvisGlassCard(
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToMemory,
                                    onClickLabel = "Open Memory and Notes"
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisCyanElectric, modifier = Modifier.size(20.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JarvisTextMuted, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Neural Memory", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("FTS4 Search & Notes", color = JarvisTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 7. Recent Recordings Feed
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "COMMITTED INTELLIGENCE SESSIONS",
                                color = JarvisTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp
                            )
                            Text(
                                "VIEW ARCHIVE",
                                color = JarvisCyanElectric,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.clickable { onNavigateToRecordings() }
                            )
                        }
                    }

                    if (recentRecordings.isEmpty()) {
                        item {
                            JarvisGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "No telemetry or recordings captured yet. Activate the central Voice Orb to initiate your first neural session.",
                                    color = JarvisTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        items(recentRecordings) { recording ->
                            RecordingItemCard(
                                recording = recording,
                                onClick = { onNavigateToRecordingDetails(recording.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to edit recognized transcript faithfully
    if (editingSentenceIndex >= 0) {
        AlertDialog(
            onDismissRequest = { editingSentenceIndex = -1 },
            title = {
                Text("Edit Transcript Segment", color = JarvisCyanElectric, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = editingSentenceText,
                    onValueChange = { editingSentenceText = it },
                    label = { Text("Faithful Speech Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingSentenceIndex = -1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanElectric)
                ) {
                    Text("Apply Correction", color = JarvisSpaceBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSentenceIndex = -1 }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisNavyCard
        )
    }

    if (showEnrollmentDialog) {
        JarvisVoiceEnrollmentDialog(
            isCurrentlyEnrolled = viewModel.isSpeakerEnrolled(context),
            isVerificationEnabled = viewModel.isSpeakerVerificationEnabled(context),
            onDismissRequest = { showEnrollmentDialog = false },
            onEnrollSample = { passIndex ->
                viewModel.enrollVoiceSample(context) { success, msg ->
                    if (!success && msg != null) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFinalizeEnrollment = {
                viewModel.finalizeVoiceEnrollment(context)
            },
            onToggleVerification = { enabled ->
                viewModel.toggleSpeakerVerification(context, enabled)
            },
            onDeleteProfile = {
                viewModel.deleteSpeakerProfile(context)
            }
        )
    }
}

@Composable
fun RecordingItemCard(
    recording: RecordingEntity,
    onClick: () -> Unit
) {
    JarvisGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = "Open recording: ${recording.title}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JarvisNavyDeep)
                    .border(1.2.dp, JarvisCyanElectric.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (recording.filePath.contains("Live Google Voice")) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                    contentDescription = null,
                    tint = JarvisCyanElectric,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                    .format(Date(recording.startTimestamp))
                Text(
                    text = dateStr,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMs(recording.durationMs),
                    color = JarvisIceBlue,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (recording.status) {
                        "COMPLETED" -> JarvisCyanElectric.copy(alpha = 0.15f)
                        "INTERRUPTED" -> JarvisStatusWarning.copy(alpha = 0.15f)
                        "ERROR" -> JarvisStatusError.copy(alpha = 0.15f)
                        else -> JarvisGlassBorder
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        when (recording.status) {
                            "COMPLETED" -> JarvisCyanElectric.copy(alpha = 0.6f)
                            "INTERRUPTED" -> JarvisStatusWarning.copy(alpha = 0.6f)
                            "ERROR" -> JarvisStatusError.copy(alpha = 0.6f)
                            else -> JarvisGlassBorder
                        }
                    )
                ) {
                    Text(
                        text = recording.status,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = when (recording.status) {
                            "COMPLETED" -> JarvisCyanElectric
                            "INTERRUPTED" -> JarvisStatusWarning
                            "ERROR" -> JarvisStatusError
                            else -> JarvisTextSecondary
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
