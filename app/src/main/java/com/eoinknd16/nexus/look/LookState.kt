package com.eoinknd16.nexus.look

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Parses #RGB, #RRGGBB or #AARRGGBB; falls back to [fallback] on anything else. */
fun parseColor(hex: String, fallback: Color = Color.Magenta): Color {
    val h = hex.trim().removePrefix("#")
    val v = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("").toLongOrNull(16)?.or(0xFF000000)
        6 -> h.toLongOrNull(16)?.or(0xFF000000)
        8 -> h.toLongOrNull(16)
        else -> null
    } ?: return fallback
    return Color(v.toInt())
}

fun Color.toHex(withAlpha: Boolean = alpha < 1f): String {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return if (withAlpha) "#%02X%02X%02X%02X".format(a, r, g, b) else "#%02X%02X%02X".format(r, g, b)
}

/** [Palette] with every colour parsed once, instead of on every read. */
class ResolvedPalette(p: Palette) {
    val accent = parseColor(p.accent)
    val accent2 = parseColor(p.accent2)
    val warn = parseColor(p.warn)
    val good = parseColor(p.good)
    val danger = parseColor(p.danger)
    val background = parseColor(p.background)
    val deep = parseColor(p.deep)
    val panelSolid = parseColor(p.panel)
    val panel = panelSolid.copy(alpha = p.panelOpacity.coerceIn(0f, 1f))
    val text = parseColor(p.text)
    val muted = parseColor(p.muted)

    /** Palette slot name ("accent", "accent2", ...) or a #hex colour. */
    fun slot(name: String): Color = when (name) {
        "accent" -> accent
        "accent2" -> accent2
        "warn" -> warn
        "good" -> good
        "danger" -> danger
        "muted" -> muted
        "text" -> text
        else -> parseColor(name, accent)
    }
}

fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.Default -> FontFamily.Default
    FontChoice.Light -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
    FontChoice.Thin -> FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))
    FontChoice.Medium -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
    FontChoice.Condensed -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    FontChoice.Serif -> FontFamily.Serif
    FontChoice.Mono -> FontFamily.Monospace
    FontChoice.Casual -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
    FontChoice.Cursive -> FontFamily.Cursive
    FontChoice.SmallCaps -> FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.NORMAL))
}

/**
 * The live [Look], readable from anywhere in the UI. Backed by Compose
 * snapshot state, so any composable that reads it recomposes when it changes
 * -- that's what makes every customisation apply instantly, everywhere,
 * without threading a parameter through every screen.
 */
object LookState {
    var look by mutableStateOf(Look())
        private set
    var palette by mutableStateOf(ResolvedPalette(Palette()))
        private set

    fun apply(newLook: Look) {
        if (newLook.palette != look.palette) palette = ResolvedPalette(newLook.palette)
        look = newLook
    }
}
