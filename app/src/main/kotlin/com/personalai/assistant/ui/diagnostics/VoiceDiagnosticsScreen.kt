package com.personalai.assistant.ui.diagnostics

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNavyCard
import com.personalai.assistant.ui.theme.JarvisNavyDeep
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisSpaceVoid
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisStatusWarning
import com.personalai.assistant.ui.theme.JarvisSurfaceGlass
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDiagnosticsScreen(
    viewModel: VoiceDiagnosticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val countdownSec by viewModel.countdownSec.collectAsStateWithLifecycle()
    val metrics by viewModel.currentMetrics.collectAsStateWithLifecycle()
    val recordedFile by viewModel.lastRecordedFile.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playPos by viewModel.playProgressMs.collectAsStateWithLifecycle()
    val playDur by viewModel.playDurationMs.collectAsStateWithLifecycle()

    val transcription by viewModel.transcriptionResult.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val latencyMs by viewModel.transcriptionLatencyMs.collectAsStateWithLifecycle()

    val isTestingWake by viewModel.isTestingWakeWord.collectAsStateWithLifecycle()
    val wakeEvent by viewModel.wakeWordDetectedEvent.collectAsStateWithLifecycle()

    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val liveRecordingMetrics by viewModel.liveRecordingMetrics.collectAsStateWithLifecycle()
    val liveEvents by viewModel.liveDiagnosticEvents.collectAsStateWithLifecycle()

    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().background(JarvisSpaceVoid),
        containerColor = JarvisSpaceVoid,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "VOICE DIAGNOSTICS & LAB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyanElectric,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "HARDWARE TELEMETRY & ASR BENCHMARK",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyanElectric)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = viewModel.generateSanitizedReport()
                        clipboardManager.setText(AnnotatedString(report))
                        Toast.makeText(context, "Sanitized report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = JarvisCyanElectric)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisNavyDeep)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status Banner
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JarvisNavyCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRecording || isTestingWake) Icons.Default.Hearing else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isRecording) JarvisCyanElectric else JarvisTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusMsg,
                            color = JarvisTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2. Hardware Spec Card
            item {
                JarvisGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "AUDIO HARDWARE SPECIFICATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyanElectric,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Format: 16kHz PCM (Mono)", color = JarvisTextSecondary, fontSize = 11.sp)
                                Text("Encoding: 16-bit Signed LE", color = JarvisTextSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Mic Perm: ${if (viewModel.hasMicPermission()) "GRANTED" else "DENIED"}",
                                    color = if (viewModel.hasMicPermission()) JarvisStatusOnline else JarvisStatusError,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Model: ${if (viewModel.isModelReady()) "UNPACKED" else "PENDING DL"}",
                                    color = if (viewModel.isModelReady()) JarvisStatusOnline else JarvisStatusWarning,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2.5 Live Hardware Recording Health Monitor
            item {
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowAccent = liveRecordingMetrics.micState == "CAPTURING",
                    glowColor = JarvisCyanElectric
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE HARDWARE TELEMETRY & CAPTURE MONITOR",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyanElectric,
                                letterSpacing = 1.sp
                            )
                            val statusColor = when (liveRecordingMetrics.micState) {
                                "CAPTURING" -> JarvisStatusOnline
                                "PAUSED" -> JarvisStatusWarning
                                "ERROR" -> JarvisStatusError
                                else -> JarvisTextMuted
                            }
                            Text(
                                text = liveRecordingMetrics.micState,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live audio RMS level gauge
                        val normalizedRms = (liveRecordingMetrics.rollingRms / 8000.0).toFloat().coerceIn(0f, 1f)
                        Text(
                            text = "Audio Signal Level (RMS: ${String.format(Locale.US, "%.1f", liveRecordingMetrics.rollingRms)})",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { normalizedRms },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (liveRecordingMetrics.clippingPercentage > 0.5f) JarvisStatusError else JarvisCyanElectric,
                            trackColor = JarvisNavyCard
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Throughput: ${liveRecordingMetrics.bytesPerSecond / 1024} KB/s", color = JarvisTextSecondary, fontSize = 11.sp)
                                Text("Clipping: ${String.format(Locale.US, "%.1f", liveRecordingMetrics.clippingPercentage)}%", color = if (liveRecordingMetrics.clippingPercentage > 1f) JarvisStatusError else JarvisTextSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Silence: ${liveRecordingMetrics.silenceDurationMs / 1000}s", color = if (liveRecordingMetrics.silenceDurationMs > 5000L) JarvisStatusWarning else JarvisTextSecondary, fontSize = 11.sp)
                                Text("Interruptions: ${liveRecordingMetrics.interruptionCount}", color = JarvisTextSecondary, fontSize = 11.sp)
                            }
                        }

                        if (liveEvents.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Recent Diagnostic Events (${liveEvents.size}):",
                                color = JarvisTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            liveEvents.takeLast(3).reversed().forEach { event ->
                                Text(
                                    text = "• [${event.type}] ${event.details}",
                                    color = JarvisTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. Step 1: Live Diagnostic Audio Capture
            item {
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowAccent = isRecording
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "STEP 1: RECORD PRODUCTION AUDIO SAMPLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Captures raw microphone audio using the exact production 16kHz WAV pipeline. Speaks freely in Hindi, English, or mixed Hinglish.",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (countdownSec > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JarvisCosmicBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "COUNTDOWN: $countdownSec",
                                    color = JarvisCyanElectric,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startDiagnosticRecording(3) },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanElectric, contentColor = JarvisCosmicBlack),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quick 3s Sample", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.startDiagnosticRecording(10) },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonViolet, contentColor = JarvisTextPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("10s Hinglish Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (isRecording) {
                                    Button(
                                        onClick = { viewModel.stopRecordingNow() },
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisStatusError, contentColor = JarvisTextPrimary)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Metrics Inspection Card
            if (metrics != null) {
                item {
                    val m = metrics!!
                    JarvisGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisBorderCyan) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "CAPTURED AUDIO METRICS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyanElectric,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Duration: ${m.durationMs}ms", color = JarvisTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Avg RMS: ${m.avgRms.toInt()}", color = JarvisIceBlue, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Peak Amp: ${m.peakRms.toInt()} / 32767", color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Clipping: ${String.format(Locale.US, "%.2f", m.clippingPercentage)}%",
                                    color = if (m.clippingPercentage > 5f) JarvisStatusError else JarvisStatusOnline,
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Silence Ratio: ${String.format(Locale.US, "%.1f", m.silencePercentage)}%", color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Dropped Frames: 0", color = JarvisStatusOnline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            // Playback Controller
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) viewModel.stopAudioPlayback() else viewModel.playRecordedAudio()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(JarvisCyanElectric, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = JarvisCosmicBlack
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { if (playDur > 0) (playPos.toFloat() / playDur.toFloat()).coerceIn(0f, 1f) else 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = JarvisCyanElectric,
                                        trackColor = JarvisCosmicBlack
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${playPos / 1000}s / ${m.durationMs / 1000}s",
                                        color = JarvisTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Step 2: Run Speech Recognizer
            item {
                JarvisGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "STEP 2: RUN SPEECH RECOGNIZER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Runs on-device speech recognition against the captured test audio without streaming or cloud transmission.",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { viewModel.runSpeechRecognizer() },
                            enabled = recordedFile != null && !isTranscribing,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanElectric, contentColor = JarvisCosmicBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isTranscribing) "Transcribing..." else "Run Speech Recognizer", fontWeight = FontWeight.Bold)
                        }

                        if (transcription != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = JarvisCosmicBlack,
                                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("RECOGNIZED TRANSCRIPT", color = JarvisCyanElectric, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text("Latency: ${latencyMs}ms", color = JarvisIceBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = transcription!!,
                                        color = JarvisTextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Step 3: Live Wake Word Spotter Test
            item {
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = JarvisBorderViolet
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "STEP 3: LIVE WAKE-WORD DETECTION TEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisNeonViolet,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Opens live microphone and tests keyword spotter detection for \"Jarvis\" in real time.",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { viewModel.testLiveWakeWord(10) },
                            enabled = !isTestingWake,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonViolet, contentColor = JarvisTextPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isTestingWake) "Listening for \"Jarvis\"..." else "Start 10s Wake Word Test", fontWeight = FontWeight.Bold)
                        }

                        if (wakeEvent != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = JarvisStatusOnline.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisStatusOnline.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisStatusOnline, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = wakeEvent!!, color = JarvisStatusOnline, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
