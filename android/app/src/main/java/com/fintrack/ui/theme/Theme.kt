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

/** Colors that aren't part of the Material scheme: money in / out and the category palette. */
@Immutable
data class MoneyColors(val credit: Color, val debit: Color, val categories: List<Color>)

private val LightMoney = MoneyColors(
    credit = Color(0xFF1B7A3A),
    debit = Color(0xFF181D19),
    categories = listOf(
        Color(0xFF2E7D4F), Color(0xFF3A6EA5), Color(0xFFB5651D), Color(0xFF8E4B9E),
        Color(0xFFC0392B), Color(0xFF00838F), Color(0xFF7A7A2E), Color(0xFF6D6D6D),
    ),
)

private val DarkMoney = MoneyColors(
    credit = Color(0xFF8AD79F),
    debit = Color(0xFFDFE4DD),
    categories = listOf(
        Color(0xFF7DCB9A), Color(0xFF8AB4E8), Color(0xFFF0A867), Color(0xFFD29BE0),
        Color(0xFFF08A7E), Color(0xFF6FD3DC), Color(0xFFCACA7A), Color(0xFFA8A8A8),
    ),
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
