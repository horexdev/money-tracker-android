package dev.horex.moneytracker.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MoneyTrackerColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val surfaceMuted: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

internal val LightMoneyTrackerColors = MoneyTrackerColors(
    income = Color(0xFF15803D),
    incomeContainer = Color(0xFFDDF8E7),
    expense = Color(0xFFDC2626),
    expenseContainer = Color(0xFFFFE2E2),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFFF1D6),
    surfaceMuted = Color(0xFFF1F4F9),
    heroStart = Color(0xFF312E81),
    heroEnd = Color(0xFF4F46E5),
)

internal val DarkMoneyTrackerColors = MoneyTrackerColors(
    income = Color(0xFF4ADE80),
    incomeContainer = Color(0xFF123820),
    expense = Color(0xFFF87171),
    expenseContainer = Color(0xFF451A1A),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3F2C0A),
    surfaceMuted = Color(0xFF252A35),
    heroStart = Color(0xFF1E1B4B),
    heroEnd = Color(0xFF6366F1),
)

val LocalMoneyTrackerColors = staticCompositionLocalOf {
    LightMoneyTrackerColors
}
