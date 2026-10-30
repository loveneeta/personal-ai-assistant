package com.personalai.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisIceBlue
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusListening
import com.personalai.assistant.ui.theme.JarvisVioletNeon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class JarvisOrbState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

@Composable
fun JarvisOrb(
    state: JarvisOrbState,
    soundLevel: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    reducedMotion: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "JarvisOrbTransition")

    // Ambient breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reducedMotion) 0 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingPulse"
    )

    // Continuous rotation angle for quantum rings
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reducedMotion) 0 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Fast rotation for processing
    val fastRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reducedMotion) 0 else 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastRotationAngle"
    )

    // Primary and accent colors derived from state
    val (coreColor, ringColor, glowColor) = when (state) {
        JarvisOrbState.IDLE -> Triple(
            JarvisCyanElectric.copy(alpha = 0.85f),
            JarvisIceBlue.copy(alpha = 0.7f),
            JarvisCyanElectric.copy(alpha = 0.25f)
        )
        JarvisOrbState.LISTENING -> Triple(
            JarvisStatusListening,
            JarvisCyanElectric,
            JarvisCyanElectric.copy(alpha = 0.55f)
        )
        JarvisOrbState.PROCESSING -> Triple(
            JarvisVioletNeon,
            JarvisIceBlue,
            JarvisVioletNeon.copy(alpha = 0.45f)
        )
        JarvisOrbState.SPEAKING -> Triple(
            JarvisIceBlue,
            JarvisCyanElectric,
            JarvisIceBlue.copy(alpha = 0.5f)
        )
        JarvisOrbState.ERROR -> Triple(
            JarvisStatusError,
            JarvisStatusError.copy(alpha = 0.8f),
            JarvisStatusError.copy(alpha = 0.35f)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = coreColor),
                role = Role.Button,
                onClickLabel = when (state) {
                    JarvisOrbState.LISTENING -> "Stop listening"
                    JarvisOrbState.ERROR -> "Retry voice input"
                    else -> "Activate JARVIS voice input"
                },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() / 2f

            // Dynamic scale factor based on audio sound level or breathing pulse
            val dynamicScale = when (state) {
                JarvisOrbState.LISTENING -> 1.0f + (soundLevel.coerceIn(0f, 1f) * 0.22f)
                JarvisOrbState.SPEAKING -> 1.0f + (soundLevel.coerceIn(0f, 1f) * 0.18f)
                JarvisOrbState.ERROR -> 1.0f
                else -> if (reducedMotion) 1.0f else breathingPulse
            }

            // 1. Outermost Ambient Halo Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius * dynamicScale
                ),
                radius = radius * dynamicScale,
                center = center
            )

            // 2. Outer Technical Segmented Ring
            val outerRingRadius = radius * 0.84f
            val baseAngle = if (state == JarvisOrbState.PROCESSING) fastRotationAngle else rotationAngle
            val segments = 8
            val sweep = 30f
            val gap = 15f
            for (i in 0 until segments) {
                val startAngle = baseAngle + (i * (sweep + gap))
                drawArc(
                    color = ringColor.copy(alpha = 0.45f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRingRadius, center.y - outerRingRadius),
                    size = androidx.compose.ui.geometry.Size(outerRingRadius * 2, outerRingRadius * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Middle Interlocking Hologram Rings
            val midRadius = radius * 0.65f
            val revAngle = -baseAngle * 1.2f
            drawArc(
                color = ringColor,
                startAngle = revAngle,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = if (state == JarvisOrbState.PROCESSING) JarvisVioletNeon else JarvisVioletNeon.copy(alpha = 0.8f),
                startAngle = revAngle + 180f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Reactive Waveform Spikes for LISTENING / SPEAKING
            if (state == JarvisOrbState.LISTENING || state == JarvisOrbState.SPEAKING) {
                val spikeCount = 16
                val amp = (soundLevel.coerceIn(0f, 1f) * radius * 0.28f).coerceAtLeast(4.dp.toPx())
                for (i in 0 until spikeCount) {
                    val angleRad = (i * (2 * PI / spikeCount) + (baseAngle * PI / 180f)).toFloat()
                    val r1 = radius * 0.45f
                    val r2 = r1 + amp * (0.6f + 0.4f * sin(i * 1.5f + baseAngle * 0.1f))
                    val p1 = Offset(center.x + r1 * cos(angleRad), center.y + r1 * sin(angleRad))
                    val p2 = Offset(center.x + r2 * cos(angleRad), center.y + r2 * sin(angleRad))
                    drawLine(
                        color = JarvisCyanElectric.copy(alpha = 0.8f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 5. Inner Core Ring
            val innerRadius = radius * 0.42f
            drawCircle(
                color = coreColor.copy(alpha = 0.6f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // 6. Central High-Intensity Plasma Core
            val coreSize = radius * 0.26f * (if (state == JarvisOrbState.LISTENING) 1.15f else 1.0f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor,
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreSize * 1.4f
                ),
                radius = coreSize,
                center = center
            )

            // 7. Micro Quantum Center Spark
            drawCircle(
                color = if (state == JarvisOrbState.ERROR) JarvisStatusError else JarvisVioletNeon,
                radius = 3.dp.toPx(),
                center = center
            )
        }
    }
}
