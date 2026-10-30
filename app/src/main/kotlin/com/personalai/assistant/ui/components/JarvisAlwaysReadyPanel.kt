package com.personalai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisElectricBlue
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisSurfaceDark
import com.personalai.assistant.ui.theme.JarvisSurfaceGlass
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import com.personalai.assistant.wakeword.JarvisAudioState

@Composable
fun JarvisAlwaysReadyPanel(
    currentState: JarvisAudioState,
    isSpeakerVerified: Boolean,
    isSpeakerEnrolled: Boolean,
    onToggleAlwaysReady: (Boolean) -> Unit,
    onPauseListening: () -> Unit,
    onResumeListening: () -> Unit,
    onEnrollVoiceClick: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isServiceActive = currentState != JarvisAudioState.IDLE_DISABLED &&
                          currentState != JarvisAudioState.STOPPED

    val panelBorderColor by animateColorAsState(
        targetValue = when (currentState) {
            JarvisAudioState.WAKE_DETECTED -> JarvisCyan
            JarvisAudioState.COMMAND_LISTENING -> JarvisCyan
            JarvisAudioState.VERIFYING_SPEAKER -> JarvisNeonViolet
            JarvisAudioState.PROCESSING -> JarvisNeonViolet
            JarvisAudioState.SPEAKING -> JarvisElectricBlue
            JarvisAudioState.ERROR -> JarvisStatusError
            JarvisAudioState.PAUSED -> JarvisTextMuted
            else -> JarvisBorderCyan.copy(alpha = 0.5f)
        },
        animationSpec = tween(400),
        label = "BorderColor"
    )

    JarvisGlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = panelBorderColor,
        glowAccent = currentState == JarvisAudioState.WAKE_DETECTED || currentState == JarvisAudioState.COMMAND_LISTENING
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row: Always Ready Switch & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isServiceActive) JarvisCyan.copy(alpha = 0.35f) else JarvisSurfaceDark,
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                if (isServiceActive) JarvisCyan else JarvisBorderCyan.copy(alpha = 0.4f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = if (isServiceActive) JarvisCyan else JarvisTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ALWAYS READY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "HANDS-FREE 'JARVIS' WAKE WORD",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isServiceActive) JarvisCyan else JarvisTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Switch(
                    checked = isServiceActive,
                    onCheckedChange = { onToggleAlwaysReady(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisCosmicBlack,
                        checkedTrackColor = JarvisCyan,
                        uncheckedThumbColor = JarvisTextMuted,
                        uncheckedTrackColor = JarvisSurfaceDark
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State Readout Telemetry Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = JarvisCosmicBlack.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, panelBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT SYSTEM STATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (currentState) {
                                JarvisAudioState.WAKE_LISTENING -> "WAITING FOR \"JARVIS\""
                                JarvisAudioState.WAKE_DETECTED -> "WAKE DETECTED // GREETING"
                                JarvisAudioState.VERIFYING_SPEAKER -> "VERIFYING VOICE IDENTITY..."
                                JarvisAudioState.COMMAND_LISTENING -> "LISTENING TO COMMAND (HINGLISH/ENG)"
                                JarvisAudioState.PROCESSING -> "QUERYING ON-DEVICE RAG MEMORY"
                                JarvisAudioState.SPEAKING -> "J.A.R.V.I.S. VOCAL RESPONSE"
                                JarvisAudioState.PAUSED -> "MICROPHONE PAUSED BY USER"
                                JarvisAudioState.PERMISSION_DENIED -> "RECORD AUDIO PERMISSION REQUIRED"
                                JarvisAudioState.ERROR -> "AUDIO CAPTURE ERROR"
                                else -> "OFFLINE // STANDBY"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (currentState) {
                                JarvisAudioState.WAKE_LISTENING -> JarvisIceBlue
                                JarvisAudioState.WAKE_DETECTED, JarvisAudioState.COMMAND_LISTENING -> JarvisCyan
                                JarvisAudioState.VERIFYING_SPEAKER, JarvisAudioState.PROCESSING -> JarvisNeonViolet
                                JarvisAudioState.SPEAKING -> JarvisElectricBlue
                                JarvisAudioState.ERROR, JarvisAudioState.PERMISSION_DENIED -> JarvisStatusError
                                else -> JarvisTextSecondary
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    // Status Pill
                    JarvisStatusBadge(
                        label = when (currentState) {
                            JarvisAudioState.WAKE_LISTENING -> "MIC ACTIVE"
                            JarvisAudioState.COMMAND_LISTENING -> "LISTENING"
                            JarvisAudioState.PROCESSING -> "COMPUTING"
                            JarvisAudioState.SPEAKING -> "TALKING"
                            JarvisAudioState.PAUSED -> "PAUSED"
                            JarvisAudioState.ERROR -> "FAULT"
                            else -> "STANDBY"
                        },
                        statusColor = when (currentState) {
                            JarvisAudioState.WAKE_LISTENING, JarvisAudioState.COMMAND_LISTENING -> JarvisStatusOnline
                            JarvisAudioState.PROCESSING, JarvisAudioState.SPEAKING -> JarvisNeonViolet
                            JarvisAudioState.PAUSED -> JarvisTextMuted
                            JarvisAudioState.ERROR -> JarvisStatusError
                            else -> JarvisTextMuted
                        },
                        isPulsing = isServiceActive && currentState != JarvisAudioState.PAUSED
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Badges Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Wake Word Engine
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JarvisSurfaceGlass,
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "WAKE ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        )
                        Text(
                            text = "Vosk Local ASR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = JarvisCyan,
                            fontSize = 10.sp
                        )
                    }
                }

                // Speaker Verification
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JarvisSurfaceGlass,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSpeakerVerified) JarvisNeonViolet.copy(alpha = 0.5f) else JarvisBorderCyan.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "SPEAKER AUTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        )
                        Text(
                            text = if (isSpeakerEnrolled) {
                                if (isSpeakerVerified) "Enabled (Enrolled)" else "Disabled (Enrolled)"
                            } else "Not Enrolled",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSpeakerEnrolled) JarvisNeonViolet else JarvisTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Quick Control Actions (when active)
            AnimatedVisibility(visible = isServiceActive) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentState == JarvisAudioState.PAUSED) {
                            Button(
                                onClick = onResumeListening,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume Mic", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onPauseListening,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceGlass, contentColor = JarvisIceBlue),
                                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause Mic", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onEnrollVoiceClick,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceGlass, contentColor = JarvisNeonViolet),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderViolet.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isSpeakerEnrolled) "Retrain Voice" else "Enroll Voice",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (onOpenDiagnostics != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenDiagnostics,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceGlass, contentColor = JarvisCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voice Diagnostics & Hardware Lab", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
