package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 🌙 深色配置映射（Termius 风格）
private val DarkColorScheme = darkColorScheme(
    primary = TermiusDarkPrimary,
    secondary = TermiusDarkSecondary,
    tertiary = TermiusDarkTertiary,
    background = TermiusDarkBackground,
    surfaceContainer = TermiusDarkSurfaceContainer,
    onSurface = TermiusDarkOnSurface,
    onPrimary = TermiusDarkBackground,
    secondaryContainer = TermiusDarkSecondaryContainer,
    onSecondaryContainer = TermiusDarkOnSecondaryContainer
)

// ☀️ 浅色配置映射（Termius 风格）
private val LightColorScheme = lightColorScheme(
    primary = TermiusLightPrimary,
    secondary = TermiusLightSecondary,
    tertiary = TermiusLightTertiary,
    background = TermiusLightBackground,
    surfaceContainer = TermiusLightSurfaceContainer,
    onSurface = TermiusLightOnSurface,
    onPrimary = TermiusLightSurfaceContainer,
    secondaryContainer = TermiusLightSecondaryContainer,
    onSecondaryContainer = TermiusLightOnSecondaryContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 强制关闭系统动态壁纸取色，守住 Termius 的纯正血统
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
