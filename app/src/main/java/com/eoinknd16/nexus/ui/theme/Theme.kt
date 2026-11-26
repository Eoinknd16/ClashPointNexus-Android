package com.eoinknd16.nexus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Nx {
    val Void = Color(0xFF05070D)
    val Deep = Color(0xFF0A0F1C)
    val Panel = Color(0xCC0D1526)
    val PanelSolid = Color(0xFF0D1526)
    val Cyan = Color(0xFF3CF2FF)
    val CyanDim = Color(0x553CF2FF)
    val Magenta = Color(0xFFFF3CAC)
    val Amber = Color(0xFFFFB23C)
    val Lime = Color(0xFF7CFF6B)
    val Danger = Color(0xFFFF4D6D)
    val Text = Color(0xFFE6F7FF)
    val Muted = Color(0xFF7A8FA6)
    val Line = Color(0x333CF2FF)

    val Mono = FontFamily.Monospace
}

private val colors = darkColorScheme(
    primary = Nx.Cyan,
    onPrimary = Nx.Void,
    secondary = Nx.Magenta,
    onSecondary = Nx.Void,
    tertiary = Nx.Amber,
    background = Nx.Void,
    onBackground = Nx.Text,
    surface = Nx.PanelSolid,
    onSurface = Nx.Text,
    surfaceVariant = Nx.Deep,
    onSurfaceVariant = Nx.Muted,
    outline = Nx.Line,
    error = Nx.Danger,
)

private val type = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 40.sp, letterSpacing = 2.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.5.sp),
)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
