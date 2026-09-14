package com.example.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LabDarkColorScheme = darkColorScheme(
    primary = LabPrimary,
    onPrimary = LabOnPrimary,
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = LabSecondary,
    onSecondary = LabOnSecondary,
    secondaryContainer = Color(0xFF1B3D00),
    onSecondaryContainer = Color(0xFFB8FF7A),
    tertiary = LabTertiary,
    onTertiary = LabOnTertiary,
    tertiaryContainer = Color(0xFF3F3300),
    onTertiaryContainer = Color(0xFFFFEB80),
    background = LabBackground,
    onBackground = LabTextPrimary,
    surface = LabSurface,
    onSurface = LabTextPrimary,
    surfaceVariant = LabSurfaceVariant,
    onSurfaceVariant = LabTextSecondary,
    surfaceContainer = LabSurfaceElevated,
    surfaceContainerHigh = LabSurfaceHighlight,
    surfaceContainerHighest = Color(0xFF223550),
    surfaceContainerLow = Color(0xFF0A121E),
    surfaceContainerLowest = Color(0xFF05080E),
    outline = LabBorder,
    outlineVariant = Color(0xFF1B293D),
    error = LabError,
    onError = LabOnError,
    errorContainer = Color(0xFF4C0000),
    onErrorContainer = Color(0xFFFF8585)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val techRippleConfig = RippleConfiguration(
        color = LabPrimary,
        rippleAlpha = RippleAlpha(
            draggedAlpha = 0.30f,
            focusedAlpha = 0.35f,
            hoveredAlpha = 0.20f,
            pressedAlpha = 0.45f
        )
    )

    MaterialTheme(
        colorScheme = LabDarkColorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides techRippleConfig,
            content = content
        )
    }
}

