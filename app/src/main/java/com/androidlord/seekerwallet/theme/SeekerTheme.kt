package com.androidlord.seekerwallet.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemePreset(val label: String) {
    SAND("Sand"),
    TEAL("Teal"),
    GRAPHITE("Graphite"),
}

data class SeekerPalette(
    val backdropTop: Color,
    val backdropBottom: Color,
    val heroCard: Color,
    val heroBadge: Color,
    val heroText: Color,
)

val LocalSeekerPalette = staticCompositionLocalOf {
    SeekerPalette(
        backdropTop = Color(0xFFF8E5C8),
        backdropBottom = Color(0xFFF2F0E8),
        heroCard = Color(0xFF1D2A30),
        heroBadge = Color(0xFFC96B2C),
        heroText = Color(0xFFF9F5EE),
    )
}

@Composable
fun SeekerTheme(
    themePreset: ThemePreset,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = colorScheme(themePreset, useDarkTheme)
    val palette = palette(themePreset)
    CompositionLocalProvider(LocalSeekerPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SeekerTypography,
            content = content,
        )
    }
}

private fun colorScheme(preset: ThemePreset, dark: Boolean): ColorScheme {
    return when (preset) {
        ThemePreset.SAND -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFF0A55A),
                onPrimary = Color(0xFF1D120C),
                secondary = Color(0xFF9BD7C6),
                background = Color(0xFF181715),
                surface = Color(0xFF24211D),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFBE5E28),
                onPrimary = Color(0xFFFFFBF7),
                secondary = Color(0xFF1E7366),
                background = Color(0xFFF6E7D7),
                surface = Color(0xFFFFFBF7),
            )
        }
        ThemePreset.TEAL -> if (dark) {
            darkColorScheme(
                primary = Color(0xFF14F195),
                onPrimary = Color(0xFF051713),
                secondary = Color(0xFF9945FF),
                background = Color(0xFF060B18),
                surface = Color(0xFF111A2B),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF14F195),
                onPrimary = Color(0xFF04150F),
                secondary = Color(0xFF7D4CFF),
                background = Color(0xFF0A1120),
                surface = Color(0xFF141E31),
            )
        }
        ThemePreset.GRAPHITE -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFE7D9A0),
                onPrimary = Color(0xFF29230E),
                secondary = Color(0xFFBCC7BD),
                background = Color(0xFF111415),
                surface = Color(0xFF1E2426),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF5E6130),
                onPrimary = Color(0xFFFFFDF6),
                secondary = Color(0xFF4C5E64),
                background = Color(0xFFEAE8DE),
                surface = Color(0xFFFAFBF8),
            )
        }
    }
}

private fun palette(preset: ThemePreset): SeekerPalette {
    return when (preset) {
        ThemePreset.SAND -> SeekerPalette(
            backdropTop = Color(0xFFFFE3C4),
            backdropBottom = Color(0xFFF2EFE5),
            heroCard = Color(0xFF1F2A33),
            heroBadge = Color(0xFFDA7A3B),
            heroText = Color(0xFFF9F4EF),
        )
        ThemePreset.TEAL -> SeekerPalette(
            backdropTop = Color(0xFF070B17),
            backdropBottom = Color(0xFF103B3B),
            heroCard = Color(0xFF151B2E),
            heroBadge = Color(0xFF14F195),
            heroText = Color(0xFFE7FFF7),
        )
        ThemePreset.GRAPHITE -> SeekerPalette(
            backdropTop = Color(0xFFE4E1D4),
            backdropBottom = Color(0xFFE9ECE7),
            heroCard = Color(0xFF20292C),
            heroBadge = Color(0xFF95A369),
            heroText = Color(0xFFF6F7F2),
        )
    }
}
