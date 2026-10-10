package com.fintrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6C3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFD6),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6354),
    secondaryContainer = Color(0xFFD2E8D5),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF3A6472),
    tertiaryContainer = Color(0xFFBEEAFA),
    background = Color(0xFFF7FAF5),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF7FAF5),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5EF),
    surfaceContainer = Color(0xFFEBEFE9),
    surfaceContainerHigh = Color(0xFFE6EAE3),
    surfaceContainerHighest = Color(0xFFE0E4DE),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD79F),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA6F4B9),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF384B3D),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFA2CDDD),
    tertiaryContainer = Color(0xFF204C59),
    background = Color(0xFF101511),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF101511),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF181D19),
    surfaceContainer = Color(0xFF1C211D),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF313631),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

/**
 * Colors that aren't part of the Material scheme: money in / out and category colors.
 * The default categories each get their own color; any category you add gets one from [palette].
 */
@Immutable
data class MoneyColors(
    val credit: Color,
    val debit: Color,
    val named: Map<String, Color>,
    val palette: List<Color>,
    val uncategorized: Color,
)

private fun named(vararg colors: Long) = DEFAULT_CATEGORY_ORDER.zip(colors.map { Color(it) }).toMap()

/** Same order as the PC's default categories. */
private val DEFAULT_CATEGORY_ORDER = listOf(
    "Food & Dining", "Groceries", "Shopping", "Transport", "Fuel", "Bills & Utilities", "Rent",
    "Entertainment", "Health", "Travel", "Education", "Transfers", "Investments", "Other",
)

private val LightMoney = MoneyColors(
    credit = Color(0xFF1B7A3A),
    debit = Color(0xFF181D19),
    named = named(
        0xFFD9480F, 0xFF2B8A3E, 0xFFAE3EC9, 0xFF1971C2, 0xFFE67700, 0xFF0C8599, 0xFF5F3DC4,
        0xFFC2255C, 0xFFE03131, 0xFF1098AD, 0xFF6741D9, 0xFF495057, 0xFF2F9E44, 0xFF868E96,
    ),
    palette = listOf(Color(0xFF3B5BDB), Color(0xFFF08C00), Color(0xFF099268), Color(0xFFA61E4D), Color(0xFF5C940D)),
    uncategorized = Color(0xFF9AA19A),
)

private val DarkMoney = MoneyColors(
    credit = Color(0xFF8AD79F),
    debit = Color(0xFFDFE4DD),
    named = named(
        0xFFFF8A5B, 0xFF69DB7C, 0xFFE599F7, 0xFF74C0FC, 0xFFFFC078, 0xFF66D9E8, 0xFFB197FC,
        0xFFF783AC, 0xFFFF8787, 0xFF3BC9DB, 0xFF9775FA, 0xFFADB5BD, 0xFF8CE99A, 0xFFCED4DA,
    ),
    palette = listOf(Color(0xFF91A7FF), Color(0xFFFFD43B), Color(0xFF63E6BE), Color(0xFFFAA2C1), Color(0xFFC0EB75)),
    uncategorized = Color(0xFF7D857D),
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular figures so amounts line up in lists. */
val AmountStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FinTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides if (darkTheme) DarkMoney else LightMoney) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
