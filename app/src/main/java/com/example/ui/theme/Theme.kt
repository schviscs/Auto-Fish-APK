package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ImmersiveColorScheme = darkColorScheme(
    primary = ImmersivePrimary,
    onPrimary = ImmersivePrimaryDark,
    background = ImmersiveBackground,
    onBackground = ImmersiveText,
    surface = ImmersiveSurface,
    onSurface = ImmersiveText,
    surfaceVariant = ImmersiveSurface,
    onSurfaceVariant = ImmersiveTextSecondary,
    surfaceTint = ImmersiveSurface,
    primaryContainer = ImmersiveBackground,
    onPrimaryContainer = ImmersiveText,
    tertiaryContainer = ImmersiveSurface,
    onTertiaryContainer = ImmersivePrimary
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = ImmersiveColorScheme, typography = Typography, content = content)
}
