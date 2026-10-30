package com.personalai.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyanElectric,
    onPrimary = onPrimaryDark,
    primaryContainer = JarvisNavyCard,
    onPrimaryContainer = JarvisTextPrimary,

    secondary = JarvisIceBlue,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = JarvisTextPrimary,

    tertiary = JarvisVioletNeon,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = JarvisTextPrimary,

    background = JarvisSpaceVoid,
    onBackground = JarvisTextPrimary,

    surface = JarvisNavyDeep,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisNavyCard,
    onSurfaceVariant = JarvisTextSecondary,

    outline = JarvisGlassBorder,
    outlineVariant = outlineVariantDark
)

@Composable
fun PersonalAIAssistantTheme(
    darkTheme: Boolean = true, // Default to true for cinematic JARVIS UI experience
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // JARVIS is an intentional dark sci-fi aesthetic
    val colorScheme = JarvisDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = JarvisSpaceVoid.toArgb()
                window.navigationBarColor = JarvisSpaceVoid.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
