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
/** The phone's Material You colours (from its wallpaper), read on Android 12+. */
object WallpaperColours {
    data class Set(val accent: String, val accent2: String, val warn: String, val background: String, val deep: String, val panel: String, val text: String, val muted: String)
    var current: Set? = null
        private set
    var stamp = 0
        private set

    fun load(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        fun c(id: Int) = String.format("#%06X", 0xFFFFFF and context.getColor(id))
        val next = Set(
            accent = c(android.R.color.system_accent1_200), accent2 = c(android.R.color.system_accent3_200),
            warn = c(android.R.color.system_accent2_200), background = c(android.R.color.system_neutral1_900),
            deep = c(android.R.color.system_neutral1_1000), panel = c(android.R.color.system_neutral2_800),
            text = c(android.R.color.system_neutral1_50), muted = c(android.R.color.system_neutral2_300),
        )
        if (next != current) { current = next; stamp++ }
    }
}

class ResolvedPalette(p: Palette) {
    /** Which wallpaper colours this was built from (so a wallpaper change rebuilds it). */
    var dynamicStamp = 0
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
    FontChoice.Inter -> BundledFonts.inter
    FontChoice.Manrope -> BundledFonts.manrope
    FontChoice.SpaceGrotesk -> BundledFonts.spaceGrotesk
    FontChoice.Sora -> BundledFonts.sora
    FontChoice.JetBrainsMono -> BundledFonts.jetbrains
    FontChoice.Outfit -> BundledFonts.outfit
    FontChoice.Unbounded -> BundledFonts.unbounded
    FontChoice.Syne -> BundledFonts.syne
    FontChoice.Playfair -> BundledFonts.playfair
}

/** Typefaces shipped with Nexus (res/font), four weights each: light for clocks, regular, semibold, heavy. */
object BundledFonts {
    private fun fam(vararg f: Pair<Int, Int>) = FontFamily(f.map { (res, w) -> androidx.compose.ui.text.font.Font(res, androidx.compose.ui.text.font.FontWeight(w)) })
    val inter by lazy { fam(com.eoinknd16.nexus.R.font.inter_300light to 300, com.eoinknd16.nexus.R.font.inter_400regular to 400, com.eoinknd16.nexus.R.font.inter_600semibold to 600, com.eoinknd16.nexus.R.font.inter_800extrabold to 800) }
    val manrope by lazy { fam(com.eoinknd16.nexus.R.font.manrope_200extralight to 200, com.eoinknd16.nexus.R.font.manrope_400regular to 400, com.eoinknd16.nexus.R.font.manrope_600semibold to 600, com.eoinknd16.nexus.R.font.manrope_800extrabold to 800) }
    val spaceGrotesk by lazy { fam(com.eoinknd16.nexus.R.font.space_grotesk_300light to 300, com.eoinknd16.nexus.R.font.space_grotesk_400regular to 400, com.eoinknd16.nexus.R.font.space_grotesk_600semibold to 600, com.eoinknd16.nexus.R.font.space_grotesk_700bold to 700) }
    val sora by lazy { fam(com.eoinknd16.nexus.R.font.sora_200extralight to 200, com.eoinknd16.nexus.R.font.sora_400regular to 400, com.eoinknd16.nexus.R.font.sora_600semibold to 600, com.eoinknd16.nexus.R.font.sora_800extrabold to 800) }
    val jetbrains by lazy { fam(com.eoinknd16.nexus.R.font.jetbrains_mono_200extralight to 200, com.eoinknd16.nexus.R.font.jetbrains_mono_400regular to 400, com.eoinknd16.nexus.R.font.jetbrains_mono_600semibold to 600, com.eoinknd16.nexus.R.font.jetbrains_mono_800extrabold to 800) }
    val outfit by lazy { fam(com.eoinknd16.nexus.R.font.outfit_200extralight to 200, com.eoinknd16.nexus.R.font.outfit_400regular to 400, com.eoinknd16.nexus.R.font.outfit_600semibold to 600, com.eoinknd16.nexus.R.font.outfit_800extrabold to 800) }
    val unbounded by lazy { fam(com.eoinknd16.nexus.R.font.unbounded_300light to 300, com.eoinknd16.nexus.R.font.unbounded_400regular to 400, com.eoinknd16.nexus.R.font.unbounded_600semibold to 600, com.eoinknd16.nexus.R.font.unbounded_800extrabold to 800) }
    val syne by lazy { fam(com.eoinknd16.nexus.R.font.syne_400regular to 400, com.eoinknd16.nexus.R.font.syne_600semibold to 600, com.eoinknd16.nexus.R.font.syne_700bold to 700, com.eoinknd16.nexus.R.font.syne_800extrabold to 800) }
    val playfair by lazy { fam(com.eoinknd16.nexus.R.font.playfair_display_400regular to 400, com.eoinknd16.nexus.R.font.playfair_display_600semibold to 600, com.eoinknd16.nexus.R.font.playfair_display_800extrabold to 800) }
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
        if (newLook.palette != look.palette || palette.dynamicStamp != WallpaperColours.stamp) palette = resolve(newLook.palette)
        look = newLook
    }

    /** The wallpaper changed: re-match the colours if the palette follows it. */
    fun wallpaperChanged() {
        if (look.palette.followWallpaper) palette = resolve(look.palette)
    }

    private fun resolve(p: Palette): ResolvedPalette {
        val w = WallpaperColours.current
        return if (p.followWallpaper && w != null) ResolvedPalette(
            p.copy(accent = w.accent, accent2 = w.accent2, warn = w.warn, background = w.background, deep = w.deep, panel = w.panel, text = w.text, muted = w.muted),
        ).also { it.dynamicStamp = WallpaperColours.stamp } else ResolvedPalette(p)
    }
}
