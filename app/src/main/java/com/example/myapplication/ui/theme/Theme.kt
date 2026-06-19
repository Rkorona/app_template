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

// 🌙 深色配置映射
private val DarkColorScheme = darkColorScheme(
    primary = CoolDarkPrimary,
    secondary = CoolDarkSecondary,
    tertiary = CoolDarkTertiary,
    background = CoolDarkBackground,
    surfaceContainer = CoolDarkSurfaceContainer,
    onSurface = CoolDarkOnSurface,
    onPrimary = CoolDarkBackground
)

// ☀️ 浅色配置映射
private val LightColorScheme = lightColorScheme(
    primary = CoolLightPrimary,
    secondary = CoolLightSecondary,
    tertiary = CoolLightTertiary,
    background = CoolLightBackground,
    surfaceContainer = CoolLightSurfaceContainer,
    onSurface = CoolLightOnSurface,
    onPrimary = CoolLightBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, 
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
