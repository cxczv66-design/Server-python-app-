package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = TailscaleBlue,
    onSecondary = TailscaleOnSecondary,
    secondaryContainer = TailscaleContainer,
    onSecondaryContainer = TailscaleOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianSurfaceHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldContainer,
    onPrimary = LightSurface,
    primaryContainer = EmeraldOnContainer,
    onPrimaryContainer = EmeraldOnPrimary,
    secondary = TailscaleContainer,
    onSecondary = LightSurface,
    secondaryContainer = TailscaleOnContainer,
    onSecondaryContainer = TailscaleOnSecondary,
    tertiary = AmberContainer,
    onTertiary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun ScriptBridgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
