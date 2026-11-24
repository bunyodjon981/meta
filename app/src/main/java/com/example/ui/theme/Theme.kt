package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ForgeDarkColorScheme = darkColorScheme(
    primary = ForgeEmerald,
    onPrimary = Color(0xFF022C22),
    primaryContainer = ForgeEmeraldContainer,
    onPrimaryContainer = ForgeEmeraldBright,
    secondary = ForgeCyan,
    onSecondary = Color(0xFF042638),
    secondaryContainer = ForgeCyanContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = ForgeAmber,
    onTertiary = Color(0xFF2E1B02),
    tertiaryContainer = ForgeAmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = ForgeError,
    onError = Color(0xFF3A090E),
    errorContainer = ForgeErrorContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBackground,
    onBackground = Color(0xFFE6EDF5),
    surface = ObsidianSurface,
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = TerminalBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

private val ForgeLightColorScheme = lightColorScheme(
    primary = ForgeEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val ForgeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ApkForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ForgeDarkColorScheme else ForgeLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ForgeShapes,
        content = content
    )
}
