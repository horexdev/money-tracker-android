package dev.horex.moneytracker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class MoneyTrackerThemeMode {
    Light,
    Dark,
    System,
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E6FF),
    onPrimaryContainer = Color(0xFF181256),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF241400),
    tertiaryContainer = Color(0xFFFFE8B5),
    onTertiaryContainer = Color(0xFF4A2A00),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF171821),
    surface = Color.White,
    onSurface = Color(0xFF171821),
    surfaceVariant = Color(0xFFE3E7EF),
    onSurfaceVariant = Color(0xFF515766),
    outline = Color(0xFF737B8C),
    outlineVariant = Color(0xFFD8DDE7),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1C1A57),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE7E6FF),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3B2600),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFFE8B5),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE7EAF2),
    surface = Color(0xFF1A1D27),
    onSurface = Color(0xFFE7EAF2),
    surfaceVariant = Color(0xFF2D3340),
    onSurfaceVariant = Color(0xFFB9C0CE),
    outline = Color(0xFF8D96A8),
    outlineVariant = Color(0xFF3A4150),
)

@Composable
fun MoneyTrackerTheme(
    themeMode: MoneyTrackerThemeMode = MoneyTrackerThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        MoneyTrackerThemeMode.Light -> false
        MoneyTrackerThemeMode.Dark -> true
        MoneyTrackerThemeMode.System -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(
        LocalMoneyTrackerColors provides if (darkTheme) {
            DarkMoneyTrackerColors
        } else {
            LightMoneyTrackerColors
        },
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = MoneyTrackerTypography,
            shapes = MoneyTrackerShapes,
            content = content,
        )
    }
}

object MoneyTrackerThemeTokens {
    val colors: MoneyTrackerColors
        @Composable
        get() = LocalMoneyTrackerColors.current
}
