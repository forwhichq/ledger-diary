package com.ledgerdiary.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 主色：克制的墨绿。记账软件要让人安心，不要花哨。
val Seed = Color(0xFF1F6F5C)
val ExpenseRed = Color(0xFFD9544D)
val IncomeGreen = Color(0xFF2E9E6B)
val MoodPalette = listOf(
    Color(0xFF8E8E93), // 未填
    Color(0xFF9AA5B1), // 1 很差
    Color(0xFFB8A99A), // 2 一般
    Color(0xFFE0C36B), // 3 还行
    Color(0xFF8FCFA8), // 4 不错
    Color(0xFF4FBF8B)  // 5 很好
)

private val LightScheme = lightColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAE0),
    onPrimaryContainer = Color(0xFF06301F),
    secondary = Color(0xFF4E6358),
    background = Color(0xFFF7F8F6),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFEEF1ED),
    onSurfaceVariant = Color(0xFF5C635D),
    outline = Color(0xFFBFC6C0),
    error = ExpenseRed
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FD3B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFF9CF0D3),
    secondary = Color(0xFFB5CCC0),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF191D1B),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF242927),
    onSurfaceVariant = Color(0xFFA8AEA9),
    outline = Color(0xFF424845),
    error = Color(0xFFFF8A80)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun LedgerDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
