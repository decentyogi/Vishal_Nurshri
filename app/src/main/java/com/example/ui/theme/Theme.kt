package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3B9),
    onPrimary = Color(0xFF5E0B19),
    primaryContainer = MaroonPrimaryDark,
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = GoldAccentLight,
    onSecondary = Color(0xFF432C00),
    secondaryContainer = Color(0xFF5E4200),
    onSecondaryContainer = Color(0xFFFFDEA3),
    background = DarkBackground,
    onBackground = Color(0xFFECE0E1),
    surface = DarkSurface,
    onSurface = Color(0xFFECE0E1),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD4C2C4)
)

private val LightColorScheme = lightColorScheme(
    primary = MaroonPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = MaroonPrimaryDark,
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA3),
    onSecondaryContainer = Color(0xFF281900),
    background = CreamBackground,
    onBackground = InkTextPrimary,
    surface = CreamSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkTextSecondary
)

@Composable
fun CashMemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded maroon identity
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

// For compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CashMemoTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
