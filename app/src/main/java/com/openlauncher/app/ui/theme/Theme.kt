package com.openlauncher.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.openlauncher.app.data.AppFont

val LocalDayMode = staticCompositionLocalOf { false }

@Composable
fun OpenLauncherTheme(
    accent: Color        = AccentGoogleBlue,
    background: Color    = Black,
    textColor: Color     = Color.White,
    surfaceColor: Color  = CardSurface,
    borderColor: Color   = DividerGray,
    textMutedColor: Color = TextMuted,
    fontBold: Boolean    = false,
    textScale: Float     = 1.0f,
    appFont: AppFont     = AppFont.JETBRAINS_MONO,
    isDayMode: Boolean   = false,
    useCustomBg: Boolean = false,
    content: @Composable () -> Unit
) {
    // Contrast-aware: the accent is user-chosen and can be any brightness,
    // so a fixed onPrimary (white) goes invisible on light accents
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val colorScheme = if (isDayMode) lightColorScheme(
        primary          = accent,
        onPrimary        = onAccent,
        secondary        = accent.copy(alpha = 0.7f),
        onSecondary      = onAccent,
        tertiary         = accent.copy(alpha = 0.5f),
        background       = if (useCustomBg) background else Color(0xFFF8F9FA),
        surface          = surfaceColor,
        onBackground     = textColor,
        onSurface        = textColor,
        surfaceVariant   = Color(0xFFF1F3F4),
        onSurfaceVariant = textMutedColor,
        outline          = borderColor
    ) else darkColorScheme(
        primary          = accent,
        onPrimary        = onAccent,
        secondary        = accent.copy(alpha = 0.7f),
        onSecondary      = onAccent,
        tertiary         = accent.copy(alpha = 0.5f),
        background       = background,
        surface          = surfaceColor,
        onBackground     = textColor,
        onSurface        = textColor,
        surfaceVariant   = Color(0xFF28282D),
        onSurfaceVariant = textMutedColor,
        outline          = borderColor
    )
    CompositionLocalProvider(LocalDayMode provides isDayMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = launcherTypography(fontBold, textScale, appFont.toFontFamily()),
            content     = content
        )
    }
}
