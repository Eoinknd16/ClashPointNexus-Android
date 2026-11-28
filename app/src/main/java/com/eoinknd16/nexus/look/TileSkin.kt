package com.eoinknd16.nexus.look

import kotlinx.serialization.Serializable

/*
 * Tile styles: everything about how a normal tile looks, from sleek and
 * minimal to loud and out-there. Every tile follows HomeLayout.tileSkin unless
 * it has its own (TileConfig.skin). Colours are palette slots ("accent",
 * "accent2", "warn", "good", "danger", "muted", "text"), #hex, or "" for the
 * tile's own colour.
 */

@Serializable
enum class SkinFill(val label: String) {
    Theme("Nexus panel"), Solid("Solid colour"), Gradient("Gradient"), Glass("Frosted glass"),
    Mesh("Mesh blobs"), AppColour("From the app's icon"), Outline("Outline only"), None("Transparent"),
}

@Serializable
enum class SkinPattern(val label: String) {
    None("None"), Dots("Dots"), Grid("Grid"), Diagonal("Diagonal lines"), Grain("Film grain"),
    Scanlines("Scanlines"), Carbon("Carbon fibre"), Hex("Hexagons"), Circuit("Circuit board"), Waves("Waves"),
}

@Serializable
enum class SkinShape(val label: String) {
    Theme("Same as everywhere"), Rounded("Rounded"), Squircle("Squircle"), Cut("Cut corners"),
    Pill("Pill / circle"), Leaf("Leaf"), Ticket("Ticket"), Square("Square"),
}

@Serializable
enum class SkinShadow(val label: String) {
    None("None"), Soft("Soft drop shadow"), Floating("Floating"), Hard("Hard offset (brutalist)"),
    Neumorphic("Neumorphic (soft 3D)"), Glow("Coloured glow"),
}

@Serializable
enum class SkinIconStyle(val label: String) {
    Plain("Plain"), Circle("Circle badge"), Squircle("Squircle badge"), Ring("Ring"), Glow("Glowing"),
    Tinted("Tinted to the colour"), Mono("Black & white"), Big("Oversized, off the edge"),
}

@Serializable
enum class SkinIconPos(val label: String) { Auto("From the layout"), TopStart("Top left"), TopEnd("Top right"), Center("Centre"), BottomEnd("Bottom right") }

@Serializable
enum class SkinFont(val label: String) {
    Theme("Same as everywhere"), Sans("Sans"), Serif("Serif"), Mono("Monospace"), Script("Handwritten"),
    Inter("Inter"), Manrope("Manrope"), SpaceGrotesk("Space Grotesk"), Sora("Sora"), JetBrainsMono("JetBrains Mono"),
    Outfit("Outfit"), Unbounded("Unbounded"), Syne("Syne"), Playfair("Playfair Display"),
}

@Serializable
enum class SkinCase(val label: String) { AsTyped("As typed"), Upper("UPPERCASE"), Lower("lowercase") }

@Serializable
enum class SkinAnim(val label: String) {
    None("None"), Shimmer("Shimmer sweep"), Breathe("Breathing glow"), Flow("Flowing colours"),
    Aurora("Aurora"), Pulse("Pulsing border"), Rainbow("Rainbow edge"),
}

@Serializable
enum class SkinPress(val label: String) { Squish("Squish"), Sink("Sink in"), Pop("Pop out"), Tilt("3D tip"), None("None") }

@Serializable
data class TileSkin(
    /** Name of the preset this came from (for the gallery's tick); edits keep it until you pick another. */
    val preset: String = "",
    // Surface
    val fill: SkinFill = SkinFill.Theme,
    val color1: String = "",
    val color2: String = "accent2",
    val color3: String = "",
    val angle: Float = 135f,
    val opacity: Float = 1f,
    val pattern: SkinPattern = SkinPattern.None,
    val patternOpacity: Float = 0.12f,
    val patternScale: Float = 1f,
    /** Glossy highlight across the top. */
    val shine: Boolean = false,
    /** Light along the inside of the edge. */
    val innerGlow: Float = 0f,
    // Shape and depth
    val shape: SkinShape = SkinShape.Theme,
    val corner: Float = 20f,
    val shadow: SkinShadow = SkinShadow.None,
    val shadowSize: Float = 12f,
    val shadowColor: String = "",
    /** Tips in 3D as you tilt the phone. */
    val tilt3d: Boolean = false,
    // Icon
    val iconStyle: SkinIconStyle = SkinIconStyle.Plain,
    val iconPos: SkinIconPos = SkinIconPos.Auto,
    val iconScale: Float = 1f,
    val iconColor: String = "",
    // Text
    val font: SkinFont = SkinFont.Theme,
    val case: SkinCase = SkinCase.AsTyped,
    val labelColor: String = "text",
    val subColor: String = "muted",
    val labelScale: Float = 1f,
    val labelWeight: Int = 600,
    val letterSpacing: Float = 0f,
    val textShadow: Boolean = false,
    val showLabel: Boolean = true,
    val showSublabel: Boolean = true,
    // Motion
    val anim: SkinAnim = SkinAnim.None,
    val animSpeed: Float = 1f,
    val press: SkinPress = SkinPress.Squish,
    // Layout (null / negative = the block's setting)
    val layout: TileLayout? = null,
    val padding: Float = -1f,
) {
    val animated: Boolean get() = anim != SkinAnim.None
}

/** The tile's own style, or everyone's default. */
fun TileConfig.effectiveSkin(home: HomeLayout): TileSkin = skin ?: home.tileSkin

/** One-tap looks for the gallery. */
val tileSkinPresets: List<Pair<String, TileSkin>> = listOf(
    "Nexus" to TileSkin(preset = "Nexus"),
    "Glass" to TileSkin(
        preset = "Glass", fill = SkinFill.Glass, shape = SkinShape.Rounded, corner = 24f, shine = true, innerGlow = 0.25f,
        pattern = SkinPattern.Grain, patternOpacity = 0.06f, shadow = SkinShadow.Soft, shadowSize = 14f, iconStyle = SkinIconStyle.Squircle,
        labelWeight = 500, tilt3d = true,
    ),
    "Minimal" to TileSkin(
        preset = "Minimal", fill = SkinFill.Solid, color1 = "#15171C", shape = SkinShape.Rounded, corner = 16f,
        labelWeight = 500, iconStyle = SkinIconStyle.Plain, press = SkinPress.Sink,
    ),
    "Neon" to TileSkin(
        preset = "Neon", fill = SkinFill.Solid, color1 = "#05060A", shape = SkinShape.Rounded, corner = 14f,
        shadow = SkinShadow.Glow, shadowSize = 16f, iconStyle = SkinIconStyle.Glow, anim = SkinAnim.Pulse,
        labelColor = "", case = SkinCase.Upper, letterSpacing = 2f, labelWeight = 700, textShadow = true,
    ),
    "Gradient pop" to TileSkin(
        preset = "Gradient pop", fill = SkinFill.Gradient, color1 = "", color2 = "accent2", angle = 135f, shape = SkinShape.Squircle,
        shine = true, shadow = SkinShadow.Floating, shadowSize = 16f, iconStyle = SkinIconStyle.Circle, labelWeight = 800, press = SkinPress.Pop,
    ),
    "Aurora" to TileSkin(
        preset = "Aurora", fill = SkinFill.Mesh, color1 = "", color2 = "accent2", color3 = "good", shape = SkinShape.Rounded, corner = 26f,
        anim = SkinAnim.Aurora, animSpeed = 0.6f, pattern = SkinPattern.Grain, patternOpacity = 0.08f, shine = true, labelWeight = 700,
    ),
    "Neumorphic" to TileSkin(
        preset = "Neumorphic", fill = SkinFill.Solid, color1 = "#1C1F26", shape = SkinShape.Rounded, corner = 22f,
        shadow = SkinShadow.Neumorphic, shadowSize = 14f, iconStyle = SkinIconStyle.Circle, iconColor = "", labelColor = "muted", press = SkinPress.Sink,
    ),
    "Brutalist" to TileSkin(
        preset = "Brutalist", fill = SkinFill.Solid, color1 = "warn", shape = SkinShape.Square, shadow = SkinShadow.Hard, shadowSize = 6f,
        shadowColor = "#000000", font = SkinFont.Mono, case = SkinCase.Upper, labelColor = "#000000", subColor = "#000000",
        labelWeight = 900, iconStyle = SkinIconStyle.Mono, press = SkinPress.Sink,
    ),
    "Terminal" to TileSkin(
        preset = "Terminal", fill = SkinFill.Solid, color1 = "#020A04", shape = SkinShape.Cut, corner = 10f, pattern = SkinPattern.Scanlines,
        patternOpacity = 0.18f, font = SkinFont.Mono, case = SkinCase.Upper, labelColor = "good", subColor = "good",
        iconStyle = SkinIconStyle.Tinted, iconColor = "good", innerGlow = 0.3f, textShadow = true, anim = SkinAnim.Breathe, animSpeed = 0.5f,
    ),
    "Holographic" to TileSkin(
        preset = "Holographic", fill = SkinFill.Gradient, color1 = "#6EE7F9", color2 = "#C084FC", color3 = "#F9A8D4", angle = 45f, opacity = 0.85f,
        shape = SkinShape.Rounded, corner = 22f, anim = SkinAnim.Flow, shine = true, tilt3d = true, labelColor = "#0B0B12", subColor = "#2A2A3A",
        iconStyle = SkinIconStyle.Circle, labelWeight = 800,
    ),
    "Carbon" to TileSkin(
        preset = "Carbon", fill = SkinFill.Solid, color1 = "#111214", pattern = SkinPattern.Carbon, patternOpacity = 0.22f,
        shape = SkinShape.Cut, corner = 14f, shine = true, shadow = SkinShadow.Soft, iconStyle = SkinIconStyle.Ring, case = SkinCase.Upper, letterSpacing = 1f,
    ),
    "Outline" to TileSkin(
        preset = "Outline", fill = SkinFill.Outline, shape = SkinShape.Rounded, corner = 18f, iconStyle = SkinIconStyle.Tinted, labelWeight = 500,
    ),
    "Bubble" to TileSkin(
        preset = "Bubble", fill = SkinFill.Gradient, color1 = "", color2 = "", angle = 90f, shape = SkinShape.Pill, shine = true,
        shadow = SkinShadow.Floating, iconStyle = SkinIconStyle.Plain, press = SkinPress.Pop, labelWeight = 700, layout = TileLayout.Centered,
    ),
    "App colours" to TileSkin(
        preset = "App colours", fill = SkinFill.AppColour, shape = SkinShape.Squircle, shine = true, shadow = SkinShadow.Soft,
        iconStyle = SkinIconStyle.Big, labelWeight = 800, textShadow = true, labelColor = "#FFFFFF", subColor = "#E0E0E0",
    ),
    "Circuit" to TileSkin(
        preset = "Circuit", fill = SkinFill.Solid, color1 = "#071018", pattern = SkinPattern.Circuit, patternOpacity = 0.25f,
        shape = SkinShape.Cut, corner = 12f, anim = SkinAnim.Shimmer, animSpeed = 0.7f, iconStyle = SkinIconStyle.Glow, font = SkinFont.Mono,
    ),
    "Rainbow" to TileSkin(
        preset = "Rainbow", fill = SkinFill.Solid, color1 = "#0A0A0F", shape = SkinShape.Rounded, corner = 20f, anim = SkinAnim.Rainbow,
        shadow = SkinShadow.Glow, shadowSize = 10f, iconStyle = SkinIconStyle.Plain,
    ),
)
