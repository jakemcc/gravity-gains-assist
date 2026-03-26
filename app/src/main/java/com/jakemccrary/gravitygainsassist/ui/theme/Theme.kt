package com.jakemccrary.gravitygainsassist.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MossGreen,
    onPrimary = MossGreenDeep,
    secondary = MossGreenMuted,
    onSecondary = Night,
    tertiary = AlertSoft,
    background = Night,
    onBackground = Mist,
    surface = NightSoft,
    onSurface = Mist,
    surfaceVariant = ForestSurfaceRaised,
    onSurfaceVariant = MistMuted,
    outline = OutlineSoft,
    error = ErrorSoft,
    onError = Night,
)

private val LightColorScheme = lightColorScheme(
    primary = MossGreenDeep,
    onPrimary = Mist,
    secondary = MossGreenMuted,
    tertiary = AlertSoft,
    background = Mist,
    onBackground = Night,
    surface = Color.White,
    onSurface = Night,
    surfaceVariant = Color(0xFFE7ECDC),
    onSurfaceVariant = Color(0xFF4B574B),
    outline = Color(0xFFCAD4C0),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun GravityGainsAssistTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
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
