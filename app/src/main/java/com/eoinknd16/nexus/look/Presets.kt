package com.eoinknd16.nexus.look

/**
 * Starting points. Applying a preset only changes appearance (colours, type,
 * shapes, backdrop, motion) -- your home layout, tiles, TV rows and player
 * settings are left alone.
 */
object Presets {
    data class Preset(val name: String, val description: String, val apply: (Look) -> Look)

    private fun Look.styled(
        name: String,
        palette: Palette,
        shape: ShapeStyle = ShapeStyle(),
        backdrop: Backdrop = Backdrop(),
        fonts: Fonts = Fonts(),
        motion: Motion = this.motion,
    ) = copy(presetName = name, palette = palette, shape = shape, backdrop = backdrop, fonts = fonts, motion = motion)

    val all = listOf(
        Preset("Nexus", "Cyan HUD over a grid horizon") { it.styled("Nexus", Palette()) },
        Preset("Synthwave", "Neon pink & purple, retro sun grid") {
            it.styled(
                "Synthwave",
                Palette(accent = "#FF4FD8", accent2 = "#7B5CFF", warn = "#FFC857", good = "#5CFFB0", background = "#0B0518", deep = "#140A28", panel = "#1A0D33", text = "#FBEAFF", muted = "#9C86B8"),
                backdrop = Backdrop(style = BackdropStyle.Grid, intensity = 1.15f, speed = 1.3f),
            )
        },
        Preset("Amber terminal", "Old-school CRT amber") {
            it.styled(
                "Amber terminal",
                Palette(accent = "#FFB000", accent2 = "#FF6A00", warn = "#FFD27A", good = "#C8FF6B", background = "#0A0700", deep = "#140E02", panel = "#1A1204", text = "#FFE7B3", muted = "#A0875A"),
                shape = ShapeStyle(corners = CornerStyle.Square, cornerSize = 0f, gradientBorder = false, glow = 0.5f),
                backdrop = Backdrop(style = BackdropStyle.Plain, scanlines = true),
                fonts = Fonts(heading = FontChoice.Mono, body = FontChoice.Mono, label = FontChoice.Mono, clock = FontChoice.Mono),
            )
        },
        Preset("Matrix", "Green rain-code") {
            it.styled(
                "Matrix",
                Palette(accent = "#39FF6A", accent2 = "#00B341", warn = "#E5FF5C", good = "#39FF6A", background = "#020803", deep = "#051207", panel = "#061A0A", text = "#D7FFE0", muted = "#5F9C6C"),
                backdrop = Backdrop(style = BackdropStyle.Stars, speed = 1.6f),
                fonts = Fonts(heading = FontChoice.Mono, label = FontChoice.Mono, clock = FontChoice.Mono),
            )
        },
        Preset("Ice", "Cold white-blue glass") {
            it.styled(
                "Ice",
                Palette(accent = "#BFEFFF", accent2 = "#6FA8FF", warn = "#FFE29A", good = "#A8FFE0", background = "#050A12", deep = "#0B1422", panel = "#13213A", text = "#F4FAFF", muted = "#8CA3BF", panelOpacity = 0.6f),
                shape = ShapeStyle(corners = CornerStyle.Round, cornerSize = 20f, brackets = false, glow = 0.2f),
                backdrop = Backdrop(style = BackdropStyle.Nebula, intensity = 0.9f, speed = 0.6f),
                fonts = Fonts(heading = FontChoice.Light, clock = FontChoice.Thin),
            )
        },
        Preset("Crimson", "Red alert") {
            it.styled(
                "Crimson",
                Palette(accent = "#FF3B3B", accent2 = "#FF8A3B", warn = "#FFD23B", good = "#7CFF6B", background = "#0B0303", deep = "#160606", panel = "#200909", text = "#FFE9E9", muted = "#A07A7A"),
                backdrop = Backdrop(style = BackdropStyle.Waves, intensity = 1f),
            )
        },
        Preset("Minimal", "Quiet: no glow, no animation") {
            it.styled(
                "Minimal",
                Palette(accent = "#FFFFFF", accent2 = "#9AA4B2", warn = "#F2C14E", good = "#8BD49C", background = "#000000", deep = "#0E0E10", panel = "#16161A", text = "#F5F5F7", muted = "#8A8A93", panelOpacity = 1f),
                shape = ShapeStyle(corners = CornerStyle.Round, cornerSize = 16f, brackets = false, glow = 0f, gradientBorder = false, borderWidth = 0f),
                backdrop = Backdrop(style = BackdropStyle.Plain, scanlines = false, vignette = false, animated = false),
                fonts = Fonts(label = FontChoice.Medium, clock = FontChoice.Light, uppercaseLabels = false),
                motion = Motion(animations = true, pressScale = 0.97f, haptics = true, pulse = false),
            )
        },
    )
}
