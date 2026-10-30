package com.personalai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisGlassBorder
import com.personalai.assistant.ui.theme.JarvisGlassElevated
import com.personalai.assistant.ui.theme.JarvisGlassSurface
import com.personalai.assistant.ui.theme.JarvisNavyCard

@Composable
fun JarvisGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = JarvisGlassBorder,
    glowAccent: Boolean = false,
    glowColor: Color = JarvisCyanElectric,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val finalBorderColor = if (glowAccent) glowColor.copy(alpha = 0.45f) else borderColor

    val bgBrush = Brush.verticalGradient(
        colors = if (glowAccent) listOf(
            JarvisGlassElevated,
            JarvisNavyCard.copy(alpha = 0.9f)
        ) else listOf(
            JarvisGlassSurface,
            JarvisNavyCard.copy(alpha = 0.8f)
        )
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    val interactionSource = remember { MutableInteractionSource() }
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        indication = ripple(color = JarvisCyanElectric),
                        interactionSource = interactionSource,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(borderWidth, finalBorderColor)
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush)
                .padding(16.dp),
            content = content
        )
    }
}
