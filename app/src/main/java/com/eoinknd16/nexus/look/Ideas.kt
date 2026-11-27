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

// ---- Finding your own tiles to build the layouts from ----------------------

private fun Look.allTiles(): List<TileConfig> =
    (home.blocks + home.pages.flatMap { it.blocks }).filterIsInstance<HomeBlock.Tiles>().flatMap { it.tiles }

private fun TileConfig.isTv() = action.kind == TileAction.Kind.Screen && action.arg == "tv" || label.trim().equals("tv", true)
private fun TileConfig.isSpotify() = (action.kind == TileAction.Kind.Screen && action.arg == "spotify") ||
    action.arg.contains("spotify", true) || label.contains("spotify", true)
private fun TileConfig.isPc() = action.kind == TileAction.Kind.PcHub || (action.kind == TileAction.Kind.Screen && action.arg == "pc") ||
    label.contains("remote", true) || label.contains("pc", true)
private val socialHints = listOf("whatsapp", "instagram", "snapchat", "discord", "reddit", "threads", "tiktok", "facebook", "twitter", "messenger", "telegram", "bereal")
private fun TileConfig.socialScore() = folderApps.count { k -> socialHints.any { k.contains(it, true) } }

/**
 * Rebuilds the main page as [main] (made from your own tiles), and moves every
 * tile and block that isn't part of the new layout onto a page called
 * "Everything else" so nothing is lost. Your tray isn't touched.
 */
private fun Look.rebuild(pageName: String, main: (picks: Picks, keep: (TileConfig) -> TileConfig) -> List<HomeBlock>): Look {
    val tiles = allTiles()
    val folders = tiles.filter { it.folderApps.isNotEmpty() }
    val tools = folders.maxByOrNull { it.folderApps.size - it.socialScore() * 3 }
    val social = folders.filter { it.id != tools?.id }.maxByOrNull { it.socialScore() * 100 + it.folderApps.size }
    val picks = Picks(
        tv = tiles.firstOrNull { it.isTv() } ?: TileConfig(label = "TV", sublabel = "Movies & shows", icon = "tv", color = "accent2", action = TileAction(TileAction.Kind.Screen, "tv")),
        spotify = tiles.firstOrNull { it.isSpotify() } ?: TileConfig(label = "Spotify", icon = "music", color = "good", action = TileAction(TileAction.Kind.Screen, "spotify")),
        pc = tiles.firstOrNull { it.isPc() } ?: TileConfig(label = "PC", sublabel = "Remote", icon = "computer", action = TileAction(TileAction.Kind.PcHub)),
        tools = tools,
        social = social,
    )
    val used = mutableSetOf<String>()
    val keep: (TileConfig) -> TileConfig = { t -> used += t.id; t }
    val clock = home.blocks.filterIsInstance<HomeBlock.Clock>().firstOrNull()
    val search = home.blocks.filterIsInstance<HomeBlock.SearchBar>().firstOrNull()
    val newMain = main(picks, keep)
    // Whatever's left over goes to "Everything else", in the style of your first tile block.
    val firstTiles = (home.blocks + home.pages.flatMap { it.blocks }).filterIsInstance<HomeBlock.Tiles>().firstOrNull()
    val leftoverTiles = home.blocks.filterIsInstance<HomeBlock.Tiles>().flatMap { it.tiles }.filter { it.id !in used }
    val leftoverBlocks = home.blocks.filter { b ->
        b !is HomeBlock.Tiles && b.id != clock?.id && b.id != search?.id && newMain.none { it.id == b.id }
    }
    val extra = buildList<HomeBlock> {
        if (leftoverTiles.isNotEmpty()) add((firstTiles ?: HomeBlock.Tiles()).copy(id = newId(), title = "", tiles = leftoverTiles))
        addAll(leftoverBlocks)
    }
    // Pages' tiles that we pulled onto the main page are removed from their old spot.
    val pages = home.pages.map { pg ->
        pg.copy(blocks = pg.blocks.map { b -> if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.filter { it.id !in used }) else b }
            .filter { b -> !(b is HomeBlock.Tiles && b.tiles.isEmpty()) })
    }
    val morePage = if (extra.isEmpty()) emptyList() else listOf(HomePage(name = "Everything else ($pageName)", blocks = extra))
    return copy(home = home.copy(blocks = newMain, pages = pages + morePage))
}

private fun Look.clockOr() = home.blocks.filterIsInstance<HomeBlock.Clock>().firstOrNull() ?: HomeBlock.Clock()
private fun Look.searchOr() = home.blocks.filterIsInstance<HomeBlock.SearchBar>().firstOrNull() ?: HomeBlock.SearchBar()
private fun Look.askOr() = home.blocks.filterIsInstance<HomeBlock.AskBar>().firstOrNull() ?: HomeBlock.AskBar(placeholder = "Ask Nexus…")
private fun Look.glanceOr() = home.blocks.filterIsInstance<HomeBlock.Glance>().firstOrNull() ?: HomeBlock.Glance()

private data class Picks(val tv: TileConfig, val spotify: TileConfig, val pc: TileConfig, val tools: TileConfig?, val social: TileConfig?)

val lookIdeas: List<LookIdea> = listOf(
    LookIdea(
        "Glass daily",
        "The Glass daily layout: big clock with date and weather, search, TV and Spotify as glass tiles, a wide folder with its apps filling it, and a liquid battery. Your tray stays; other tiles move to a page called Everything else.",
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
        ).rebuild("Glass daily") { p, keep ->
            val clock = l.home.blocks.filterIsInstance<HomeBlock.Clock>().firstOrNull() ?: HomeBlock.Clock()
            val search = l.home.blocks.filterIsInstance<HomeBlock.SearchBar>().firstOrNull() ?: HomeBlock.SearchBar()
            listOfNotNull(
                clock.copy(sizeSp = 64f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEE d MMM"),
                search,
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.85f, gapDp = 10f, iconSizeDp = 26f, labelScale = 0.95f, paddingDp = 14f,
                    showSublabels = false, tileLayout = TileLayout.Stack,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null),
                        keep(p.spotify).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 2, rowSpan = 1, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                HomeBlock.Liquid(heightDp = 48f, framed = true),
            )
        }
    },
    LookIdea(
        "Mono pro",
        "The Mono pro layout: clock with a lime date line, Ask Nexus bar, TV / PC / Spotify as thin list rows, then a physics folder and a fill folder side by side. Your tray stays; other tiles move to a page called Everything else.",
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
        ).rebuild("Mono pro") { p, keep ->
            val clock = l.home.blocks.filterIsInstance<HomeBlock.Clock>().firstOrNull() ?: HomeBlock.Clock()
            val ask = l.home.blocks.filterIsInstance<HomeBlock.AskBar>().firstOrNull() ?: HomeBlock.AskBar(placeholder = "Ask Nexus…")
            fun row(t: TileConfig) = keep(t).copy(skin = null, border = outline, colSpan = 1, rowSpan = 1)
            listOfNotNull(
                clock.copy(sizeSp = 66f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEE d MMM"),
                ask,
                HomeBlock.Tiles(
                    title = "", columns = 1, rowHeightDp = 46f, gapDp = 6f, iconSizeDp = 20f, labelScale = 0.9f, paddingDp = 10f,
                    showSublabels = true, tileLayout = TileLayout.Inline,
                    tiles = listOf(row(p.tv), row(p.pc), row(p.spotify)),
                ),
                listOfNotNull(p.social, p.tools).takeIf { it.isNotEmpty() }?.let { folders ->
                    HomeBlock.Tiles(
                        title = "", columns = 2, aspect = 1.3f, gapDp = 8f, iconSizeDp = 22f, labelScale = 0.85f, paddingDp = 10f,
                        showSublabels = false,
                        tiles = folders.mapIndexed { i, f ->
                            keep(f).copy(skin = null, border = outline, colSpan = 1, rowSpan = 1, folderTileStyle = if (i == 0) FolderTileStyle.Physics else FolderTileStyle.Fill)
                        },
                    )
                },
            )
        }
    },
    LookIdea(
        "Aurora media",
        "Watching and listening first: a compact clock, what you were playing to pick up again, posters picked from your watch history, aurora tiles for Spotify and your PC, and your folder as an orbit ring.",
    ) { l ->
        l.copy(
            presetName = "Aurora media",
            palette = l.palette.copy(
                accent = "#ED93B1", accent2 = "#AFA9EC", warn = "#FAC775", good = "#5DCAA5",
                background = "#110816", deep = "#0A0410", panel = "#2A1636", text = "#F6EEF9", muted = "#B4A6C4", panelOpacity = 0.85f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Nebula, scanlines = false, vignette = true, intensity = 0.7f),
            fonts = l.fonts.copy(clock = FontChoice.Medium, uppercaseLabels = false),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Aurora" }.second),
        ).rebuild("Aurora media") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 40f, align = Align.Start, showDate = false, showWeather = true, showPcStatus = false),
                HomeBlock.Resume(title = "", maxItems = 1),
                HomeBlock.Posters(title = "Picked for you", source = PosterSource.ForYou, count = 12, posterWidthDp = 72f),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.7f, gapDp = 10f, iconSizeDp = 26f, paddingDp = 14f, showSublabels = false,
                    tiles = listOfNotNull(
                        keep(p.spotify).copy(skin = null, border = null),
                        keep(p.pc).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 2, folderTileStyle = FolderTileStyle.Fill, folderStyle = FolderStyle.Ring, folderRingSize = 0f) },
                    ),
                ),
            )
        }
    },
    LookIdea(
        "App-colour bento",
        "Bright and playful on a light background: a bento grid where every tile takes its app's colour with an oversized icon, a tall physics folder, and search at the bottom.",
    ) { l ->
        l.copy(
            presetName = "App-colour bento",
            palette = l.palette.copy(
                accent = "#185FA5", accent2 = "#D85A30", warn = "#EF9F27", good = "#1D9E75",
                background = "#F1EFE8", deep = "#E6E3DA", panel = "#FFFFFF", text = "#2C2C2A", muted = "#5F5E5A", panelOpacity = 0.95f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(clock = FontChoice.Medium, uppercaseLabels = false),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "App colours" }.second.copy(shadow = SkinShadow.None, shape = SkinShape.Rounded, corner = 26f)),
        ).rebuild("App-colour bento") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 40f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEEE d MMMM"),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.6f, gapDp = 10f, iconSizeDp = 30f, paddingDp = 14f, showSublabels = true,
                    tiles = listOfNotNull(
                        keep(p.spotify).copy(skin = null, border = null, rowSpan = 2, color = "good"),
                        keep(p.tv).copy(skin = null, border = null, color = "#2C2C2A"),
                        keep(p.pc).copy(skin = null, border = null, color = "accent"),
                        p.social?.let { keep(it).copy(skin = null, border = null, rowSpan = 2, folderTileStyle = FolderTileStyle.Physics, color = "#FFFFFF") },
                        p.tools?.let { keep(it).copy(skin = null, border = null, rowSpan = 2, folderTileStyle = FolderTileStyle.Fill, color = "accent2") },
                    ),
                ),
                l.searchOr().copy(placeholder = "Search or ask"),
            )
        }
    },
    LookIdea(
        "Synthwave",
        "Neon pink and cyan on deep purple with the grid horizon, a big centred condensed clock, glowing neon tiles in three columns and your folders as physics boxes.",
    ) { l ->
        l.copy(
            presetName = "Synthwave",
            palette = l.palette.copy(
                accent = "#FF2A6D", accent2 = "#05D9E8", warn = "#F9C80E", good = "#01FF89",
                background = "#0B0221", deep = "#05010F", panel = "#1A0B3A", text = "#FDF0FF", muted = "#A98CC9", panelOpacity = 0.7f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Grid, scanlines = true, vignette = true, intensity = 1f),
            fonts = l.fonts.copy(clock = FontChoice.Condensed, label = FontChoice.Mono, uppercaseLabels = true),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Neon" }.second.copy(anim = SkinAnim.Pulse, animSpeed = 0.6f)),
        ).rebuild("Synthwave") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 84f, align = Align.Center, showDate = true, showWeather = true, showPcStatus = true),
                l.searchOr(),
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1f, gapDp = 12f, iconSizeDp = 32f, paddingDp = 12f, showSublabels = false,
                    tileLayout = TileLayout.Centered,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null, color = "accent"),
                        keep(p.spotify).copy(skin = null, border = null, color = "accent2"),
                        keep(p.pc).copy(skin = null, border = null, color = "warn"),
                        p.social?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Physics, color = "accent2") },
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 2, folderTileStyle = FolderTileStyle.Physics, color = "accent") },
                    ),
                ),
            )
        }
    },
    LookIdea(
        "Paper",
        "Calm and editorial: warm off-white, a serif clock, a framed at-a-glance card, soft white tiles with gentle shadows and one coral accent.",
    ) { l ->
        l.copy(
            presetName = "Paper",
            palette = l.palette.copy(
                accent = "#E4572E", accent2 = "#2E86AB", warn = "#F2A541", good = "#3BB273",
                background = "#FAF8F3", deep = "#F0ECE3", panel = "#FFFFFF", text = "#1E1D1A", muted = "#76716A", panelOpacity = 1f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.Serif, clock = FontChoice.Serif, label = FontChoice.Default, uppercaseLabels = false),
            home = l.home.copy(
                tileSkin = TileSkin(
                    preset = "Paper", fill = SkinFill.Solid, color1 = "#FFFFFF", shape = SkinShape.Rounded, corner = 20f,
                    shadow = SkinShadow.Soft, shadowSize = 6f, iconStyle = SkinIconStyle.Plain,
                    font = SkinFont.Serif, labelWeight = 600, labelColor = "text", subColor = "muted", press = SkinPress.Sink,
                ),
            ),
        ).rebuild("Paper") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 60f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEEE, d MMMM"),
                l.glanceOr().copy(framed = true, maxLines = 2),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.5f, gapDp = 12f, iconSizeDp = 28f, paddingDp = 16f, showSublabels = true,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null),
                        keep(p.spotify).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Fill) },
                        p.social?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                l.searchOr(),
            )
        }
    },
    LookIdea(
        "Terminal",
        "Green-on-black hacker console: monospace everything, scanlines, cut corners, a command bar for Ask Nexus, list rows for TV, PC and Spotify, and folders that rattle about.",
    ) { l ->
        l.copy(
            presetName = "Terminal",
            palette = l.palette.copy(
                accent = "#33FF66", accent2 = "#33FF66", warn = "#E6FF33", good = "#33FF66",
                background = "#020A04", deep = "#010502", panel = "#04140A", text = "#B8FFC8", muted = "#4C8F5D", panelOpacity = 0.9f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = true, vignette = true),
            fonts = l.fonts.copy(heading = FontChoice.Mono, body = FontChoice.Mono, label = FontChoice.Mono, clock = FontChoice.Mono, uppercaseLabels = true),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Terminal" }.second),
        ).rebuild("Terminal") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 56f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = true, dateFormat = "yyyy-MM-dd EEE"),
                l.askOr().copy(placeholder = "> run a command…"),
                HomeBlock.Tiles(
                    title = "", columns = 1, rowHeightDp = 44f, gapDp = 6f, iconSizeDp = 18f, labelScale = 0.9f, paddingDp = 10f,
                    showSublabels = true, tileLayout = TileLayout.Inline,
                    tiles = listOf(keep(p.tv).copy(skin = null, border = null), keep(p.pc).copy(skin = null, border = null), keep(p.spotify).copy(skin = null, border = null)),
                ),
                listOfNotNull(p.social, p.tools).takeIf { it.isNotEmpty() }?.let { folders ->
                    HomeBlock.Tiles(
                        title = "", columns = 2, aspect = 1.3f, gapDp = 8f, iconSizeDp = 20f, paddingDp = 10f, showSublabels = false,
                        tiles = folders.map { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Physics) },
                    )
                },
            )
        }
    },
)
