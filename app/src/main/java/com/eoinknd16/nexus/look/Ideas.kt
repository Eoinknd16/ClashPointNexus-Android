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
)
