package com.khanhan.pingpilot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DE3FF),
    onPrimary = Color(0xFF002B35),
    secondary = Color(0xFFA9C7FF),
    background = Color(0xFF070B14),
    surface = Color(0xFF101725),
    surfaceVariant = Color(0xFF182235),
    onBackground = Color(0xFFEAF1FF),
    onSurface = Color(0xFFEAF1FF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00677B),
    secondary = Color(0xFF3B5F91),
    background = Color(0xFFF5F8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EEF9)
)

@Composable
fun PingPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
