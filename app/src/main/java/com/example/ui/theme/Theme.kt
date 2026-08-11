package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.viewmodel.AppStyleTheme

// 1. 淺色風格
private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight
)

// 2. 深色風格
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark
)

// 3. 機械風格 (Cyberpunk Mechanical / Neon Cyan & Dark Metal)
private val MechanicalColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFF80DFEA),
    secondary = Color(0xFFFF007F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF880E4F),
    onSecondaryContainer = Color(0xFFFF80AB),
    background = Color(0xFF101418),
    surface = Color(0xFF1A2128),
    surfaceVariant = Color(0xFF263238),
    onSurface = Color(0xFFE0F7FA),
    onSurfaceVariant = Color(0xFFB0BEC5)
)

// 4. 可愛風格 (Kawaii Pastel Pink & Lavender)
private val CuteColorScheme = lightColorScheme(
    primary = Color(0xFFFF69B4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF0F5),
    onPrimaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFFAB47BC),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E5F5),
    onSecondaryContainer = Color(0xFF4A148C),
    background = Color(0xFFFFF5F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFCE4EC),
    onSurface = Color(0xFF4A154B),
    onSurfaceVariant = Color(0xFF880E4F)
)

// 5. 陽光風格 (Sunny Warm Orange & Gold)
private val SunnyColorScheme = lightColorScheme(
    primary = Color(0xFFF57C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF3E0),
    onPrimaryContainer = Color(0xFFE65100),
    secondary = Color(0xFF00ACC1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF006064),
    background = Color(0xFFFFFBEA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFFF8E1),
    onSurface = Color(0xFF3E2723),
    onSurfaceVariant = Color(0xFF6D4C41)
)

// 6. 學生風格 (Student Notebook Denim & Slate)
private val StudentColorScheme = lightColorScheme(
    primary = Color(0xFF1A365D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF2B6CB0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBF8FF),
    onSecondaryContainer = Color(0xFF1A365D),
    background = Color(0xFFF7FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurface = Color(0xFF2D3748),
    onSurfaceVariant = Color(0xFF4A5568)
)

// 7. 公文風格 (Official Seal Red & Parchment)
private val OfficialColorScheme = lightColorScheme(
    primary = Color(0xFF8B0000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEBEE),
    onPrimaryContainer = Color(0xFF5F0000),
    secondary = Color(0xFF37474F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF263238),
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0ECE1),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun MyMoneyKeepTheme(
    styleTheme: AppStyleTheme = AppStyleTheme.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (styleTheme) {
        AppStyleTheme.LIGHT -> LightColorScheme
        AppStyleTheme.DARK -> DarkColorScheme
        AppStyleTheme.MECHANICAL -> MechanicalColorScheme
        AppStyleTheme.CUTE -> CuteColorScheme
        AppStyleTheme.SUNNY -> SunnyColorScheme
        AppStyleTheme.STUDENT -> StudentColorScheme
        AppStyleTheme.OFFICIAL -> OfficialColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
