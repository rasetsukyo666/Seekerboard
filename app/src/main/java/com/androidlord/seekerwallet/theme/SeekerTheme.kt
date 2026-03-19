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
                primary = Color(0xFF6FE4CA),
                onPrimary = Color(0xFF07221E),
                secondary = Color(0xFF7DD3FC),
                background = Color(0xFF081311),
                surface = Color(0xFF10201D),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF0E7666),
                onPrimary = Color(0xFFF2FFFC),
                secondary = Color(0xFF145B78),
                background = Color(0xFFE4FBF5),
                surface = Color(0xFFF5FFFD),
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
            backdropTop = Color(0xFFD9FFF4),
            backdropBottom = Color(0xFFE5F6F8),
            heroCard = Color(0xFF102826),
            heroBadge = Color(0xFF47C8B0),
            heroText = Color(0xFFF2FFFB),
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
