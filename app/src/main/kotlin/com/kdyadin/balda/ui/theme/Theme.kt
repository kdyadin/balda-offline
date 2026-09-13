package com.kdyadin.balda.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kdyadin.balda.data.ThemeMode

/** Цвета игрового поля, не входящие в Material-схему. */
data class BoardColors(
    val emptyCell: Color,
    val filledCell: Color,
    val startWordCell: Color,
    val selectedCell: Color,
    val pathCell: Color,
    val pendingCell: Color,
    val hintCell: Color,
    val cellText: Color,
    val pathText: Color,
    val gridLine: Color,
)

val LocalBoardColors = staticCompositionLocalOf<BoardColors> { error("BoardColors not provided") }

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4E5D3),
    onPrimaryContainer = Color(0xFF0B2A21),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDBF),
    onSecondaryContainer = Color(0xFF2E1A05),
    tertiary = Color(0xFF5B4E8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3DEFF),
    onTertiaryContainer = Color(0xFF181040),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF7F1),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFBF7F1),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFE7E1D8),
    onSurfaceVariant = Color(0xFF49453F),
    outline = Color(0xFF7A766F),
    outlineVariant = Color(0xFFCBC5BB),
    surfaceContainer = Color(0xFFF1ECE5),
    surfaceContainerHigh = Color(0xFFEBE6DF),
    surfaceContainerHighest = Color(0xFFE5E0D9),
    surfaceContainerLow = Color(0xFFF6F1EB),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF98C9B7),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF17463A),
    onPrimaryContainer = Color(0xFFB4E5D3),
    secondary = Color(0xFFE9BE93),
    onSecondary = Color(0xFF462A0C),
    secondaryContainer = Color(0xFF62401F),
    onSecondaryContainer = Color(0xFFFFDDBF),
    tertiary = Color(0xFFC6BFFF),
    onTertiary = Color(0xFF2C245B),
    tertiaryContainer = Color(0xFF433973),
    onTertiaryContainer = Color(0xFFE3DEFF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF15201C),
    onBackground = Color(0xFFE5E2DD),
    surface = Color(0xFF15201C),
    onSurface = Color(0xFFE5E2DD),
    surfaceVariant = Color(0xFF3C4642),
    onSurfaceVariant = Color(0xFFC8C5BE),
    outline = Color(0xFF8C9590),
    outlineVariant = Color(0xFF3F4945),
    surfaceContainer = Color(0xFF1E2925),
    surfaceContainerHigh = Color(0xFF28332F),
    surfaceContainerHighest = Color(0xFF323E39),
    surfaceContainerLow = Color(0xFF1A2521),
)

private val LightBoard = BoardColors(
    emptyCell = Color(0xFFF3EEE6),
    filledCell = Color(0xFFFFFFFF),
    startWordCell = Color(0xFFFFF3DD),
    selectedCell = Color(0xFFD3EDE3),
    pathCell = Color(0xFF2F5D50),
    pendingCell = Color(0xFFFFE0A3),
    hintCell = Color(0xFFE3DEFF),
    cellText = Color(0xFF1D1B18),
    pathText = Color(0xFFFFFFFF),
    gridLine = Color(0xFFCBC5BB),
)

private val DarkBoard = BoardColors(
    emptyCell = Color(0xFF243029),
    filledCell = Color(0xFF34403A),
    startWordCell = Color(0xFF4A3F2A),
    selectedCell = Color(0xFF2E5A4C),
    pathCell = Color(0xFF98C9B7),
    pendingCell = Color(0xFF7A5C22),
    hintCell = Color(0xFF433973),
    cellText = Color(0xFFF2EFE9),
    pathText = Color(0xFF002E22),
    gridLine = Color(0xFF3F4945),
)

val BaldaTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
    )
}

@Composable
fun BaldaTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val board = if (dark) DarkBoard else LightBoard
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBoardColors provides board) {
        MaterialTheme(colorScheme = scheme, typography = BaldaTypography, content = content)
    }
}
