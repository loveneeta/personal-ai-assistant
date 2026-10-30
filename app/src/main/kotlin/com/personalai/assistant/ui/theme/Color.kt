package com.personalai.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// JARVIS SCI-FI DESIGN TOKENS
// ==========================================

// Cosmic & Space Backgrounds
val JarvisSpaceVoid = Color(0xFF04070D)
val JarvisSpaceBlack = Color(0xFF070B12)
val JarvisNavyDeep = Color(0xFF0B1220)
val JarvisNavyCard = Color(0xFF0F1A2E)
val JarvisGlassSurface = Color(0xCC0E182A)
val JarvisGlassElevated = Color(0xDD132038)
val JarvisGlassBorder = Color(0xFF1E2E4A)
val JarvisGlassBorderGlow = Color(0x4000F0FF)

// Primary Hologram Accents: Electric Cyan & Ice Blue
val JarvisCyanElectric = Color(0xFF00F0FF)
val JarvisCyanVibrant = Color(0xFF00E5FF)
val JarvisIceBlue = Color(0xFF38BDF8)
val JarvisIceBlueLight = Color(0xFFBAE6FD)
val JarvisCyanGlow = Color(0x3300F0FF)
val JarvisCyanDim = Color(0xFF0284C7)

// Secondary Hologram Accents: Neon Violet & Cyber Indigo
val JarvisVioletNeon = Color(0xFFA855F7)
val JarvisVioletVibrant = Color(0xFF8B5CF6)
val JarvisIndigoCyber = Color(0xFF6366F1)
val JarvisPurpleGlow = Color(0x33A855F7)

// Status & Alert Indicators
val JarvisStatusOnline = Color(0xFF10B981)  // Emerald Green
val JarvisStatusWarning = Color(0xFFF59E0B) // Amber
val JarvisStatusError = Color(0xFFEF4444)   // Crimson Alert
val JarvisStatusListening = Color(0xFF00F0FF) // Cyan Pulse

// High Contrast Typography Colors
val JarvisTextPrimary = Color(0xFFF0F9FF)   // Pure crisp ice white
val JarvisTextSecondary = Color(0xFF94A3B8) // Cool slate grey
val JarvisTextMuted = Color(0xFF64748B)     // Dim ambient text
val JarvisTextAccent = Color(0xFF38BDF8)    // Cyan/blue highlighted text

// Material 3 Color Mappings
val primaryDark = JarvisCyanElectric
val onPrimaryDark = Color(0xFF021B24)
val primaryContainerDark = Color(0xFF082F49)
val onPrimaryContainerDark = JarvisTextPrimary

val secondaryDark = JarvisIceBlue
val onSecondaryDark = Color(0xFF032238)
val secondaryContainerDark = Color(0xFF0C2B4E)
val onSecondaryContainerDark = JarvisTextPrimary

val tertiaryDark = JarvisVioletNeon
val onTertiaryDark = Color(0xFF2E0854)
val tertiaryContainerDark = Color(0xFF3B1366)
val onTertiaryContainerDark = JarvisTextPrimary

val backgroundDark = JarvisSpaceBlack
val onBackgroundDark = JarvisTextPrimary

val surfaceDark = JarvisNavyDeep
val onSurfaceDark = JarvisTextPrimary
val surfaceVariantDark = JarvisNavyCard
val onSurfaceVariantDark = JarvisTextSecondary

val outlineDark = JarvisGlassBorder
val outlineVariantDark = Color(0xFF16253D)

// Fallback Light Colors (harmonized dark-dominant sci-fi palette)
val primaryLight = JarvisCyanDim
val primaryContainerLight = Color(0xFFE0F2FE)
val secondaryLight = Color(0xFF0369A1)
val tertiaryLight = JarvisVioletVibrant

// Aliases for JARVIS Sci-Fi Component Styling
val JarvisCosmicBlack = JarvisSpaceBlack
val JarvisDeepNavy = JarvisNavyDeep
val JarvisCyan = JarvisCyanElectric
val JarvisElectricBlue = JarvisIceBlue
val JarvisNeonViolet = JarvisVioletNeon
val JarvisSurfaceDark = JarvisNavyCard
val JarvisSurfaceGlass = JarvisGlassSurface
val JarvisBorderCyan = JarvisGlassBorder
val JarvisBorderViolet = Color(0x66A855F7)
val JarvisGlowCyan = JarvisCyanGlow

