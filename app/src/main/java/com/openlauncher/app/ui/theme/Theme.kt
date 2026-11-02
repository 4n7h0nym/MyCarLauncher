package com.openlauncher.app.ui.theme

import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.openlauncher.app.data.VisualTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
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
val LocalButtonColor = staticCompositionLocalOf { Color(0xFF4285F4) }

@Composable
fun OpenLauncherTheme(
    visualTheme: VisualTheme = VisualTheme.MATERIAL,
    albumAccent: Boolean = false,
    accent: Color        = AccentGoogleBlue,
    buttonColor: Color   = AccentGoogleBlue,
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
    val onAccent = contrastForeground(accent)
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
    val materialColors = if (isDayMode) lightColorScheme(
        primary = accent, onPrimary = onAccent,
        background = Color(0xFFF7F8FA), surface = Color.White, onSurface = Color(0xFF191C20),
        onBackground = Color(0xFF191C20), onSurfaceVariant = Color(0xFF454B55),
        surfaceContainerHigh = Color(0xFFE8ECF2), secondaryContainer = Color(0xFFE8ECF2), onSecondaryContainer = Color(0xFF191C20)
    ) else darkColorScheme(
        primary = accent, onPrimary = onAccent,
        background = if (visualTheme == VisualTheme.OLED) Color.Black else Color(0xFF101114),
        surface = if (visualTheme == VisualTheme.OLED) Color(0xFF111318) else Color(0xFF1C1F24),
        onSurface = Color(0xFFF3F4F7), onBackground = Color(0xFFF3F4F7),
        onSurfaceVariant = Color(0xFFC2C7D0), surfaceContainerHigh = Color(0xFF292D34),
        secondaryContainer = Color(0xFF292D34), onSecondaryContainer = Color(0xFFF3F4F7)
    )
    val resolved = when {
        visualTheme == VisualTheme.CUSTOM -> colorScheme
        visualTheme == VisualTheme.DYNAMIC && Build.VERSION.SDK_INT >= 31 -> {
            val system = if (isDayMode) dynamicLightColorScheme(LocalContext.current) else dynamicDarkColorScheme(LocalContext.current)
            if (albumAccent) system.copy(primary = accent, onPrimary = onAccent) else system
        }
        else -> materialColors
    }
    CompositionLocalProvider(
        LocalDayMode provides isDayMode,
        LocalButtonColor provides if (visualTheme == VisualTheme.CUSTOM) buttonColor else resolved.primary
    ) {
        MaterialTheme(
            colorScheme = resolved,
            typography  = launcherTypography(fontBold, textScale, appFont.toFontFamily()),
            content     = content
        )
    }
}

/** Choose the more legible foreground using WCAG contrast, including middle-luminance accents. */
fun contrastForeground(background: Color): Color =
    if (ColorUtils.calculateContrast(android.graphics.Color.BLACK, background.toArgb() or (0xFF shl 24)) >=
        ColorUtils.calculateContrast(android.graphics.Color.WHITE, background.toArgb() or (0xFF shl 24))) Color.Black else Color.White
