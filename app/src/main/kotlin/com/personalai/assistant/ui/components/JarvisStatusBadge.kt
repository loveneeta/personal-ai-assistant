package com.personalai.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalai.assistant.ui.theme.JarvisGlassBorder
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisTextPrimary

@Composable
fun JarvisStatusBadge(
    label: String,
    statusColor: Color = JarvisStatusOnline,
    isPulsing: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BadgePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BadgeAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B1424).copy(alpha = 0.85f))
            .border(1.dp, JarvisGlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isPulsing) statusColor.copy(alpha = alpha) else statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            color = JarvisTextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
