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
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.family

/**
 * The app's colours/fonts, all read live from [LookState] -- so these are
 * getters, not constants, and any composable using them recomposes when the
 * user changes a colour in Customise. The original names (Cyan, Magenta...)
 * are kept as the semantic slots they became: Cyan = primary accent,
 * Magenta = secondary accent, Amber = warning, Lime = success.
 */
object Nx {
    private val p get() = LookState.palette

    val Void: Color get() = p.background
    val Deep: Color get() = p.deep
    val Panel: Color get() = p.panel
    val PanelSolid: Color get() = p.panelSolid
    val Cyan: Color get() = p.accent
    val CyanDim: Color get() = p.accent.copy(alpha = 0.33f)
    val Magenta: Color get() = p.accent2
    val Amber: Color get() = p.warn
    val Lime: Color get() = p.good
    val Danger: Color get() = p.danger
    val Text: Color get() = p.text
    val Muted: Color get() = p.muted
    val Line: Color get() = p.accent.copy(alpha = 0.2f)

    val Mono: FontFamily get() = LookState.look.fonts.label.family()
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val look = LookState.look
    val f = look.fonts
    val s = f.scale.coerceIn(0.7f, 1.6f)
    val heading = f.heading.family()
    val body = f.body.family()
    val label = f.label.family()

    val colors = darkColorScheme(
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
        surfaceContainer = Nx.PanelSolid,
        surfaceContainerHigh = Nx.PanelSolid,
        outline = Nx.Line,
        error = Nx.Danger,
    )

    val type = Typography(
        displaySmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Light, fontSize = (40 * s).sp, letterSpacing = 2.sp),
        headlineSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.SemiBold, fontSize = (22 * s).sp, letterSpacing = 0.5.sp),
        titleMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.SemiBold, fontSize = (16 * s).sp),
        bodyMedium = TextStyle(fontFamily = body, fontSize = (14 * s).sp, lineHeight = (20 * s).sp),
        bodyLarge = TextStyle(fontFamily = body, fontSize = (16 * s).sp, lineHeight = (22 * s).sp),
        labelSmall = TextStyle(fontFamily = label, fontSize = (11 * s).sp, letterSpacing = 2.sp),
        labelMedium = TextStyle(fontFamily = label, fontSize = (12 * s).sp, letterSpacing = 1.5.sp),
        labelLarge = TextStyle(fontFamily = label, fontSize = (14 * s).sp, letterSpacing = 1.sp),
    )

    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

/** Labels are uppercase in the HUD style unless the user turned that off. */
fun hud(text: String): String = if (LookState.look.fonts.uppercaseLabels) text.uppercase() else text
