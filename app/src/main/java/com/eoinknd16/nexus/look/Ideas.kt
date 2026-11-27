package com.eoinknd16.nexus.look

/*
 * "Ideas": ready-made looks that restyle YOUR setup rather than replacing it.
 * Your tray, tiles, folders and apps all stay; colours, backdrop, fonts, tile
 * style and folder styles change, and a couple of blocks are added if you
 * don't have them. Trying one always saves what you had first.
 */

data class LookIdea(val name: String, val description: String, val apply: (Look) -> Look)

private fun Look.mapTiles(f: (TileConfig) -> TileConfig): Look {
    fun blocks(bs: List<HomeBlock>) = bs.map { b -> if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.map(f)) else b }
    return copy(home = home.copy(blocks = blocks(home.blocks), pages = home.pages.map { it.copy(blocks = blocks(it.blocks)) }))
}

/** Folders in order of appearance get these tile styles in turn (the last one repeats). */
private fun Look.styleFolders(vararg styles: FolderTileStyle): Look {
    var n = 0
    return mapTiles { t ->
        if (t.folderApps.isEmpty()) t else t.copy(folderTileStyle = styles[minOf(n++, styles.size - 1)])
    }
}

/** Adds [block] to the main page after the first block of type [after] (or at the top), if there isn't one of its kind already. */
private inline fun <reified B : HomeBlock> Look.ensureBlock(block: B, crossinline after: (HomeBlock) -> Boolean): Look {
    if (home.blocks.any { it is B }) return this
    val list = home.blocks.toMutableList()
    val i = list.indexOfFirst { after(it) }
    list.add(if (i >= 0) i + 1 else 0, block)
    return copy(home = home.copy(blocks = list))
}

val lookIdeas: List<LookIdea> = listOf(
    LookIdea(
        "Glass daily",
        "Frosted glass tiles that tip as you tilt, a calm starfield, your folders filling their tiles and a liquid battery under them.",
    ) { l ->
        l.copy(
            presetName = "Glass daily",
            palette = l.palette.copy(
                accent = "#7DD3FC", accent2 = "#A78BFA", warn = "#FBBF24", good = "#5DCAA5",
                background = "#0A0E19", deep = "#05070F", text = "#F2F4F8", muted = "#9AA3B5",
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Stars, scanlines = false, vignette = true, intensity = 0.8f),
            fonts = l.fonts.copy(clock = FontChoice.Thin, uppercaseLabels = false),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Glass" }.second),
        )
            .mapTiles { it.copy(skin = null, border = null) }
            .styleFolders(FolderTileStyle.Fill)
            .ensureBlock(HomeBlock.SearchBar()) { it is HomeBlock.Clock || it is HomeBlock.Glance }
            .ensureBlock(HomeBlock.Liquid(heightDp = 56f)) { it is HomeBlock.Tiles }
    },
    LookIdea(
        "Mono pro",
        "Near-black with one lime accent, monospace capitals, thin outlines, black-and-white icons, a physics folder and an Ask Nexus bar.",
    ) { l ->
        val outline = BorderOverride(style = BorderStyle.Solid, width = 0.5f, color = "#2C2C2A")
        l.copy(
            presetName = "Mono pro",
            palette = l.palette.copy(
                accent = "#C6F432", accent2 = "#C6F432", warn = "#C6F432", good = "#C6F432",
                background = "#0C0C0C", deep = "#050505", text = "#EDEDED", muted = "#888780",
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(label = FontChoice.Mono, body = FontChoice.Default, clock = FontChoice.Thin, uppercaseLabels = true),
            home = l.home.copy(
                tileSkin = TileSkin(
                    preset = "Mono pro", fill = SkinFill.Solid, color1 = "#0C0C0C", shape = SkinShape.Rounded, corner = 10f,
                    iconStyle = SkinIconStyle.Mono, font = SkinFont.Mono, case = SkinCase.Upper, letterSpacing = 1f,
                    labelWeight = 500, labelColor = "text", subColor = "accent", press = SkinPress.Sink,
                ),
            ),
        )
            .mapTiles { it.copy(skin = null, border = outline) }
            .styleFolders(FolderTileStyle.Physics, FolderTileStyle.Fill)
            .ensureBlock(HomeBlock.AskBar()) { it is HomeBlock.Clock || it is HomeBlock.Glance }
    },
)
