package com.example.myapplication67.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier

enum class ThemeMode(val key: String, val title: String) {
    SYSTEM("system", "Тема устройства"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ThemeAccent(
    val key: String,
    val title: String,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightContainer: Color,
    val darkContainer: Color,
    val lightBackground: Color,
    val darkBackground: Color,
) {
    DEFAULT("default", "По умолчанию", Purple40, Purple80, Color(0xFFE9DFFF), Color(0xFF4F378B), Color(0xFFF7F1FF), Color(0xFF19151F)),
    GRAY("gray", "Серый", Color(0xFF5C626B), Color(0xFFC4C7CF), Color(0xFFE2E3E7), Color(0xFF40464F), Color(0xFFF6F6F7), Color(0xFF17191C)),
    BLUE("blue", "Синий", Color(0xFF1263C7), Color(0xFFA8C8FF), Color(0xFFD8E8FF), Color(0xFF234B83), Color(0xFFF3F7FF), Color(0xFF111821)),
    ORANGE("orange", "Оранжевый", Color(0xFFA14E14), Color(0xFFFFB786), Color(0xFFFFDDC7), Color(0xFF70380B), Color(0xFFFFF8F2), Color(0xFF241A14)),
    RED("red", "Красный", Color(0xFFB32637), Color(0xFFFFB2B8), Color(0xFFFFDADB), Color(0xFF7D2934), Color(0xFFFFF6F6), Color(0xFF241518)),
    GREEN("green", "Зелёный", Color(0xFF2D6A36), Color(0xFFA3D5A0), Color(0xFFD4EFCF), Color(0xFF315E39), Color(0xFFF4FAF3), Color(0xFF131E16)),
    BEIGE("beige", "Бежевый", Color(0xFF796247), Color(0xFFE0C5A5), Color(0xFFF0E3D1), Color(0xFF5B4630), Color(0xFFFCF8F0), Color(0xFF201B15)),
    LIGHT_PINK("light_pink", "Светло-розовый", Color(0xFFA74677), Color(0xFFFFC0D7), Color(0xFFFFD9E8), Color(0xFF7C3156), Color(0xFFFFF6FA), Color(0xFF21151B));

    companion object {
        fun fromKey(key: String?): ThemeAccent = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

val LocalAppBackgroundBrush = staticCompositionLocalOf<Brush> { SolidColor(Color.White) }

@Composable
fun MyApplication67Theme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: ThemeAccent = ThemeAccent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val onSurface = if (dark) Color(0xFFF0EDF3) else Color(0xFF211F24)
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = accent.darkPrimary,
            onPrimary = Color(0xFF24202A),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = Color.White,
            secondary = accent.darkPrimary,
            onSecondary = Color(0xFF24202A),
            secondaryContainer = accent.darkContainer,
            onSecondaryContainer = Color.White,
            tertiary = accent.darkPrimary,
            background = accent.darkBackground,
            onBackground = onSurface,
            surface = Color(0xFF211E25),
            onSurface = onSurface,
            surfaceVariant = accent.darkContainer,
            onSurfaceVariant = Color(0xFFD0CAD5),
            surfaceContainerLow = Color(0xFF29252E),
            surfaceContainer = Color(0xFF302B35),
        )
    } else {
        lightColorScheme(
            primary = accent.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = Color(0xFF211F24),
            secondary = accent.lightPrimary,
            onSecondary = Color.White,
            secondaryContainer = accent.lightContainer,
            onSecondaryContainer = Color(0xFF211F24),
            tertiary = accent.lightPrimary,
            background = accent.lightBackground,
            onBackground = onSurface,
            surface = Color.White,
            onSurface = onSurface,
            surfaceVariant = accent.lightContainer,
            onSurfaceVariant = Color(0xFF605D66),
            surfaceContainerLow = Color.White,
            surfaceContainer = accent.lightBackground,
        )
    }
    val backgroundBrush = if (dark) {
        Brush.verticalGradient(listOf(accent.darkBackground, colorScheme.surface))
    } else {
        Brush.verticalGradient(listOf(accent.lightBackground, Color.White))
    }

    CompositionLocalProvider(LocalAppBackgroundBrush provides backgroundBrush) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                Box(Modifier.fillMaxSize().background(backgroundBrush)) { content() }
            }
        }
    }
}
