package com.eyecare

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// Фирменная палитра (глаз/забота о зрении): синий акцент + бирюзовый вторичный.
// Явные светлая и тёмная схемы вместо dynamic color — предсказуемый контраст и читаемость.

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF), // iOS systemBlue (light)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF1E60C4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E6FF),
    onSecondaryContainer = Color(0xFF001A41),
    tertiary = Color(0xFF00A0B0),
    background = Color(0xFFEEF1F6),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF), // iOS systemBlue (dark)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00458C),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF9EC5FF),
    onSecondary = Color(0xFF00315F),
    secondaryContainer = Color(0xFF10406F),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = Color(0xFF4DD6E6),
    background = Color(0xFF15181E),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF262C37),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF333945),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// Inter (OFL) — свободный шрифт, визуально близкий к SF Pro (SF Pro встраивать нельзя по лицензии).
// Вариативный файл: задаём нужные начертания через ось веса (wght). Требует API 26+ (minSdk = 26).
@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Inter),
        displayMedium = displayMedium.copy(fontFamily = Inter),
        displaySmall = displaySmall.copy(fontFamily = Inter),
        headlineLarge = headlineLarge.copy(fontFamily = Inter),
        headlineMedium = headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter),
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter),
        bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}

/** Выбор темы пользователем. По умолчанию — как в системе. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Тема приложения. Явные светлая/тёмная схемы с высоким контрастом. Режим ([themeMode])
 * выбирается пользователем: [ThemeMode.SYSTEM] следует системной теме, иначе форсируется.
 */
@Composable
fun EyeCareTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography) {
        // Цвет контента по умолчанию на фоне экрана (иначе Text/Icon вне Surface — чёрные
        // и не видны в тёмной теме). Внутри GlassCard переопределяется на onSurface.
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}
