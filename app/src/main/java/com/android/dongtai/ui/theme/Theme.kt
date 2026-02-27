package com.android.dongtai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.android.dongtai.utils.SettingsState

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Orange80,
    onSecondary = Orange40,
    secondaryContainer = Color(0xFF3D2E1A),
    onSecondaryContainer = Orange90,
    tertiary = Green80,
    onTertiary = Green50,
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE3E3E8),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE3E3E8),
    surfaceVariant = Color(0xFF1E2430),
    onSurfaceVariant = Color(0xFFBFC6D4),
    error = Red80,
    onError = Red50,
    errorContainer = Color(0xFF3B1010),
    onErrorContainer = Red80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue20,
    secondary = Orange50,
    onSecondary = Color.White,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange40,
    tertiary = Green50,
    onTertiary = Color.White,
    background = Color(0xFFF8F9FC),
    onBackground = Gray10,
    surface = Color.White,
    onSurface = Gray10,
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = Gray30,
    error = Red50,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red50
)

// 字体大小比例
val LocalFontScale = compositionLocalOf { 1.0f }

@Composable
fun DongtaiTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val themeMode = SettingsState.rememberThemeMode()
    val fontSize = SettingsState.rememberFontSize()
    
    val actualDarkTheme = when {
        darkTheme != null -> darkTheme
        themeMode == "dark" -> true
        themeMode == "light" -> false
        else -> isSystemInDarkTheme()
    }
    
    val fontScale = when (fontSize) {
        "small" -> 0.85f
        "normal" -> 1.0f
        "large" -> 1.15f
        "extra_large" -> 1.3f
        else -> 1.0f
    }
    
    val customTypography = remember(fontScale) {
        Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontSize = Typography.displayLarge.fontSize * fontScale),
            displayMedium = Typography.displayMedium.copy(fontSize = Typography.displayMedium.fontSize * fontScale),
            displaySmall = Typography.displaySmall.copy(fontSize = Typography.displaySmall.fontSize * fontScale),
            headlineLarge = Typography.headlineLarge.copy(fontSize = Typography.headlineLarge.fontSize * fontScale),
            headlineMedium = Typography.headlineMedium.copy(fontSize = Typography.headlineMedium.fontSize * fontScale),
            headlineSmall = Typography.headlineSmall.copy(fontSize = Typography.headlineSmall.fontSize * fontScale),
            titleLarge = Typography.titleLarge.copy(fontSize = Typography.titleLarge.fontSize * fontScale),
            titleMedium = Typography.titleMedium.copy(fontSize = Typography.titleMedium.fontSize * fontScale),
            titleSmall = Typography.titleSmall.copy(fontSize = Typography.titleSmall.fontSize * fontScale),
            bodyLarge = Typography.bodyLarge.copy(fontSize = Typography.bodyLarge.fontSize * fontScale),
            bodyMedium = Typography.bodyMedium.copy(fontSize = Typography.bodyMedium.fontSize * fontScale),
            bodySmall = Typography.bodySmall.copy(fontSize = Typography.bodySmall.fontSize * fontScale),
            labelLarge = Typography.labelLarge.copy(fontSize = Typography.labelLarge.fontSize * fontScale),
            labelMedium = Typography.labelMedium.copy(fontSize = Typography.labelMedium.fontSize * fontScale),
            labelSmall = Typography.labelSmall.copy(fontSize = Typography.labelSmall.fontSize * fontScale)
        )
    }
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        actualDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = customTypography,
            content = content
        )
    }
}
