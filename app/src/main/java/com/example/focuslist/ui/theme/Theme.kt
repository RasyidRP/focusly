package com.example.focuslist.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CustomColorScheme = darkColorScheme(
    primary = Dandelion,
    onPrimary = Black,
    primaryContainer = MediumGrey,
    onPrimaryContainer = Dandelion,

    secondary = Dandelion,
    onSecondary = Black,

    tertiary = Dandelion,
    onTertiary = Black,
    tertiaryContainer = LightGrey,
    onTertiaryContainer = White,

    background = Black,
    onBackground = White,

    surface = DarkGrey,
    onSurface = White,

    surfaceVariant = MediumGrey,
    onSurfaceVariant = White,

    error = Red,
    onError = Black
)

@Composable
fun FocusListTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = CustomColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}