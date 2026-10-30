package com.personalai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.personalai.assistant.ui.theme.JarvisBorderCyan
import com.personalai.assistant.ui.theme.JarvisBorderViolet
import com.personalai.assistant.ui.theme.JarvisCosmicBlack
import com.personalai.assistant.ui.theme.JarvisCyan
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisNeonViolet
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisSurfaceDark
import com.personalai.assistant.ui.theme.JarvisSurfaceGlass
import com.personalai.assistant.ui.theme.JarvisTextMuted
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary

@Composable
fun JarvisVoiceEnrollmentDialog(
    isCurrentlyEnrolled: Boolean,
    isVerificationEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onEnrollSample: (passIndex: Int) -> Unit,
    onFinalizeEnrollment: () -> Boolean,
    onToggleVerification: (Boolean) -> Unit,
    onDeleteProfile: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(if (isCurrentlyEnrolled) 0 else 1) } // 0: Manage, 1..3: Recording passes, 4: Complete
    var isCapturingPass by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = JarvisSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(JarvisNeonViolet.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        .border(1.dp, JarvisNeonViolet.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = JarvisNeonViolet,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SPEAKER VOICE BIOMETRIC",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "100% ON-DEVICE ACOUSTIC TEMPLATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (currentStep == 0 && isCurrentlyEnrolled) {
                    // Profile Management Mode
                    JarvisGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = JarvisBorderViolet
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = JarvisStatusOnline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "PRIMARY PROFILE ENROLLED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisStatusOnline,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Your 16-D acoustic template is saved locally on your device. JARVIS verifies incoming speech to ensure only your voice executes personal protocols.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                    }

                    // Toggle verification on/off
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Require Voice Auth for Personal Actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextPrimary
                        )
                        androidx.compose.material3.Switch(
                            checked = isVerificationEnabled,
                            onCheckedChange = { onToggleVerification(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { currentStep = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceGlass, contentColor = JarvisCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retrain Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onDeleteProfile()
                                currentStep = 1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceGlass, contentColor = JarvisStatusError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisStatusError.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (currentStep in 1..3) {
                    // Step progress
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "COLLECTING SAMPLE $currentStep OF 3",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "${((currentStep - 1) * 33.3).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (currentStep - 1) / 3.0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = JarvisCyan,
                            trackColor = JarvisCosmicBlack
                        )
                    }

                    // Prompt to read
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = JarvisCosmicBlack,
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "PLEASE SPEAK OUT LOUD:",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                when (currentStep) {
                                    1 -> "\"Hey Jarvis, initialize my personal voice template.\""
                                    2 -> "\"Jarvis, verify audio identity and synchronize protocols.\""
                                    else -> "\"Hands-free assistant always ready on my command.\""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = JarvisIceBlue
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onEnrollSample(currentStep)
                            if (currentStep < 3) {
                                currentStep++
                            } else {
                                val success = onFinalizeEnrollment()
                                if (success) {
                                    currentStep = 4
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (currentStep < 3) "Capture Sample $currentStep" else "Finalize & Calibrate",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (currentStep == 4) {
                    // Completed!
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = JarvisStatusOnline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "ENROLLMENT CALIBRATED!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Your unique vocal acoustics are now saved. Hands-free commands will be matched against this template.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Privacy Note
                Text(
                    "🔒 Privacy Guarantee: Voice templates are strictly processed on-device. Audio samples are never uploaded or shared.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisCosmicBlack)
            ) {
                Text(if (currentStep == 4) "Done" else "Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
