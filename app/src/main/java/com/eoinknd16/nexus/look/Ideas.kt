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
                followWallpaper = false,
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
                followWallpaper = false,
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
                followWallpaper = false,
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
                followWallpaper = false,
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
                followWallpaper = false,
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
                followWallpaper = false,
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
                followWallpaper = false,
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

    // ---- Device-native looks (your wallpaper, its colours, modern type, live rings) ----
    LookIdea(
        "Pixel You",
        "Like a Pixel home screen but more: your own wallpaper behind everything, colours pulled from it (and following it when you change it), Outfit type, a huge thin clock, nested device rings, frosted squircle tiles and a folder whose icons fill it.",
    ) { l ->
        l.copy(
            presetName = "Pixel You",
            palette = l.palette.copy(followWallpaper = true, panelOpacity = 0.45f),
            backdrop = l.backdrop.copy(style = BackdropStyle.Wallpaper, wallpaperDim = 0.25f, wallpaperBlur = 0f, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.Outfit, body = FontChoice.Outfit, label = FontChoice.Outfit, clock = FontChoice.Outfit, uppercaseLabels = false),
            shape = l.shape.copy(corners = CornerStyle.Round, cornerSize = 26f, borderStyle = BorderStyle.None, brackets = false, glow = 0f),
            home = l.home.copy(tileSkin = TileSkin(
                preset = "Pixel You", fill = SkinFill.Glass, opacity = 0.75f, shape = SkinShape.Squircle, corner = 28f,
                iconStyle = SkinIconStyle.Circle, font = SkinFont.Outfit, labelWeight = 600, shadow = SkinShadow.Soft, shadowSize = 10f, press = SkinPress.Squish,
            )),
        ).rebuild("Pixel You") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 88f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEEE, d MMMM"),
                HomeBlock.Rings(style = RingStyle.Nested, rings = listOf(RingKind.Battery, RingKind.Day, RingKind.Storage), sizeDp = 64f, framed = true),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.6f, gapDp = 12f, iconSizeDp = 28f, paddingDp = 14f, showSublabels = false, tileLayout = TileLayout.Stack,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null), keep(p.spotify).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 2, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                l.searchOr(),
            )
        }
    },
    LookIdea(
        "Frosted",
        "One UI-style frosted glass: your wallpaper blurred behind the whole screen (Android 12+), Manrope type, soft white glass tiles, battery / storage / memory rings and a glance line.",
    ) { l ->
        l.copy(
            presetName = "Frosted",
            palette = l.palette.copy(followWallpaper = true, panelOpacity = 0.3f),
            backdrop = l.backdrop.copy(style = BackdropStyle.Wallpaper, wallpaperDim = 0.15f, wallpaperBlur = 36f, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.Manrope, body = FontChoice.Manrope, label = FontChoice.Manrope, clock = FontChoice.Manrope, uppercaseLabels = false),
            shape = l.shape.copy(corners = CornerStyle.Round, cornerSize = 24f, borderStyle = BorderStyle.Solid, borderWidth = 0.6f, borderColor = "#33FFFFFF", brackets = false, glow = 0f),
            home = l.home.copy(tileSkin = TileSkin(
                preset = "Frosted", fill = SkinFill.Glass, opacity = 0.55f, shape = SkinShape.Rounded, corner = 24f, shine = true, innerGlow = 0.35f,
                iconStyle = SkinIconStyle.Squircle, font = SkinFont.Manrope, labelWeight = 600, tilt3d = true, press = SkinPress.Tilt,
            )),
        ).rebuild("Frosted") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 72f, align = Align.Center, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEE d MMM"),
                l.glanceOr(),
                HomeBlock.Rings(style = RingStyle.Separate, rings = listOf(RingKind.Battery, RingKind.Storage, RingKind.Memory), sizeDp = 70f, thickness = 6f),
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1f, gapDp = 10f, iconSizeDp = 30f, paddingDp = 10f, showSublabels = false, tileLayout = TileLayout.Centered,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null), keep(p.spotify).copy(skin = null, border = null), keep(p.pc).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 2, folderTileStyle = FolderTileStyle.Physics) },
                        p.social?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                l.askOr(),
            )
        }
    },
    LookIdea(
        "Bento grotesk",
        "A bold bento grid in Space Grotesk: a wide TV card, tall Spotify card, PC and folders in odd sizes, slim progress bars for the day, week and battery, on matte charcoal.",
    ) { l ->
        l.copy(
            presetName = "Bento grotesk",
            palette = l.palette.copy(
                followWallpaper = false, accent = "#FF6B3D", accent2 = "#4D7CFF", warn = "#FFC83D", good = "#3DDC97",
                background = "#111214", deep = "#0A0A0B", panel = "#1B1C20", text = "#F4F4F2", muted = "#8D8E93", panelOpacity = 1f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.SpaceGrotesk, body = FontChoice.SpaceGrotesk, label = FontChoice.SpaceGrotesk, clock = FontChoice.SpaceGrotesk, uppercaseLabels = false),
            shape = l.shape.copy(corners = CornerStyle.Round, cornerSize = 22f, borderStyle = BorderStyle.None, brackets = false, glow = 0f),
            home = l.home.copy(tileSkin = TileSkin(
                preset = "Bento grotesk", fill = SkinFill.Solid, color1 = "#1B1C20", shape = SkinShape.Rounded, corner = 22f,
                iconStyle = SkinIconStyle.Tinted, iconPos = SkinIconPos.TopStart, font = SkinFont.SpaceGrotesk, labelWeight = 700, labelScale = 1.1f, press = SkinPress.Sink,
            )),
        ).rebuild("Bento grotesk") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 70f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = true, dateFormat = "EEE d MMM"),
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1f, gapDp = 8f, iconSizeDp = 26f, paddingDp = 14f, showSublabels = true, tileLayout = TileLayout.Stack,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null, colSpan = 2, color = "accent"),
                        keep(p.spotify).copy(skin = null, border = null, rowSpan = 2, color = "good"),
                        keep(p.pc).copy(skin = null, border = null, color = "accent2"),
                        p.tools?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Fill) },
                        p.social?.let { keep(it).copy(skin = null, border = null, colSpan = 3, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                HomeBlock.Rings(style = RingStyle.Bars, rings = listOf(RingKind.Day, RingKind.Week, RingKind.Battery), thickness = 8f, framed = true),
                l.searchOr(),
            )
        }
    },
    LookIdea(
        "Instrument panel",
        "A cockpit dashboard: JetBrains Mono, carbon-fibre tiles, 270° gauges for battery, storage, memory and the day, a command bar and folders that rattle about, on a slow grid horizon.",
    ) { l ->
        l.copy(
            presetName = "Instrument panel",
            palette = l.palette.copy(
                followWallpaper = false, accent = "#FFB020", accent2 = "#40E0FF", warn = "#FFB020", good = "#7CFF6B",
                background = "#07090C", deep = "#030405", panel = "#0F1318", text = "#E8ECEF", muted = "#6C7682", panelOpacity = 0.92f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Grid, intensity = 0.55f, speed = 0.4f, scanlines = false, vignette = true),
            fonts = l.fonts.copy(heading = FontChoice.JetBrainsMono, body = FontChoice.Inter, label = FontChoice.JetBrainsMono, clock = FontChoice.JetBrainsMono, uppercaseLabels = true),
            shape = l.shape.copy(corners = CornerStyle.Cut, cornerSize = 10f, borderStyle = BorderStyle.Solid, borderWidth = 0.8f, borderColor = "accent", brackets = true, glow = 0.2f),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Carbon" }.second.copy(preset = "Instrument panel", font = SkinFont.JetBrainsMono, case = SkinCase.Upper, letterSpacing = 1.5f)),
        ).rebuild("Instrument panel") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 58f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = true, dateFormat = "EEE dd.MM"),
                HomeBlock.Rings(style = RingStyle.Arcs, rings = listOf(RingKind.Battery, RingKind.Storage, RingKind.Memory, RingKind.Day), sizeDp = 70f, thickness = 6f),
                l.askOr().copy(placeholder = "> command"),
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1.1f, gapDp = 8f, iconSizeDp = 22f, paddingDp = 10f, showSublabels = true,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null), keep(p.spotify).copy(skin = null, border = null), keep(p.pc).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, colSpan = 3, folderTileStyle = FolderTileStyle.Physics) },
                    ),
                ),
            )
        }
    },
    LookIdea(
        "Editorial",
        "Magazine-style: a Playfair Display clock and headings, Inter text, warm ink-black with cream type and a single red accent, hairline tiles with no icons' badges, posters picked for you and a quiet year-progress bar.",
    ) { l ->
        val hair = BorderOverride(style = BorderStyle.Solid, width = 0.5f, color = "#3A3630")
        l.copy(
            presetName = "Editorial",
            palette = l.palette.copy(
                followWallpaper = false, accent = "#E5484D", accent2 = "#E5484D", warn = "#D6A35C", good = "#9DB89A",
                background = "#14120F", deep = "#0C0B09", panel = "#1A1814", text = "#F3EBDD", muted = "#9A9285", panelOpacity = 1f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.Playfair, body = FontChoice.Inter, label = FontChoice.Inter, clock = FontChoice.Playfair, uppercaseLabels = true),
            shape = l.shape.copy(corners = CornerStyle.Square, cornerSize = 0f, borderStyle = BorderStyle.Solid, borderWidth = 0.5f, borderColor = "#3A3630", brackets = false, glow = 0f),
            home = l.home.copy(tileSkin = TileSkin(
                preset = "Editorial", fill = SkinFill.None, shape = SkinShape.Square, corner = 0f, iconStyle = SkinIconStyle.Mono,
                iconPos = SkinIconPos.TopEnd, font = SkinFont.Playfair, labelWeight = 600, labelScale = 1.2f, subColor = "accent", press = SkinPress.Sink,
            )),
        ).rebuild("Editorial") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 80f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEEE d MMMM"),
                HomeBlock.Rings(style = RingStyle.Bars, rings = listOf(RingKind.Year), thickness = 3f, glow = false, framed = false),
                HomeBlock.Posters(title = "Picked for you", source = PosterSource.ForYou),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 2.2f, gapDp = 0f, iconSizeDp = 20f, paddingDp = 14f, showSublabels = true, tileLayout = TileLayout.Stack,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = hair), keep(p.spotify).copy(skin = null, border = hair), keep(p.pc).copy(skin = null, border = hair),
                        p.tools?.let { keep(it).copy(skin = null, border = hair, folderTileStyle = FolderTileStyle.Fill) },
                    ),
                ),
                l.searchOr(),
            )
        }
    },
    LookIdea(
        "Unbounded night",
        "Big, wide Unbounded type over a slow nebula, aurora mesh tiles that tip in 3D as you tilt the phone, glowing rings and an orbit-ring folder.",
    ) { l ->
        l.copy(
            presetName = "Unbounded night",
            palette = l.palette.copy(
                followWallpaper = false, accent = "#8B5CFF", accent2 = "#FF5CC8", warn = "#FFB547", good = "#3DFFC5",
                background = "#07061A", deep = "#030210", panel = "#120F2E", text = "#F5F3FF", muted = "#9C97C9", panelOpacity = 0.6f,
            ),
            backdrop = l.backdrop.copy(style = BackdropStyle.Nebula, intensity = 0.9f, speed = 0.6f, scanlines = false, vignette = true),
            fonts = l.fonts.copy(heading = FontChoice.Unbounded, body = FontChoice.Sora, label = FontChoice.Sora, clock = FontChoice.Unbounded, uppercaseLabels = true),
            shape = l.shape.copy(corners = CornerStyle.Round, cornerSize = 26f, borderStyle = BorderStyle.Gradient, brackets = false, glow = 0.4f),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Aurora" }.second.copy(preset = "Unbounded night", font = SkinFont.Unbounded, tilt3d = true, shape = SkinShape.Squircle, corner = 30f)),
        ).rebuild("Unbounded night") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 64f, align = Align.Center, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEE d MMM"),
                HomeBlock.Rings(style = RingStyle.Separate, rings = listOf(RingKind.Battery, RingKind.Day, RingKind.Week), sizeDp = 68f, glow = true, framed = false),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.25f, gapDp = 12f, iconSizeDp = 30f, paddingDp = 14f, showSublabels = false,
                    tiles = listOfNotNull(
                        keep(p.tv).copy(skin = null, border = null), keep(p.spotify).copy(skin = null, border = null),
                        keep(p.pc).copy(skin = null, border = null),
                        p.tools?.let { keep(it).copy(skin = null, border = null, folderTileStyle = FolderTileStyle.Physics, folderStyle = FolderStyle.Ring, folderRingSize = 0f) },
                    ),
                ),
                l.askOr(),
            )
        }
    },

    // ---- Layout templates built on the newer blocks and block styles ----
    LookIdea(
        "Daily planner",
        "Your day at a glance: clock, quick toggles, the next few calendar events as a timeline, a sticky note, a countdown and your tiles as a sideways carousel. Every block sits on its own glass card.",
    ) { l ->
        val toggles = HomeBlock.Toggles(style = ToggleStyle.Circles, sizeDp = 48f)
        val agenda = HomeBlock.Agenda(style = AgendaStyle.Timeline, days = 3, maxItems = 4, framed = false)
        val note = HomeBlock.Note(text = "Tap to write today's focus", framed = false)
        val count = HomeBlock.Countdown(title = "Christmas", target = "2026-12-25 00:00", style = CountdownStyle.Compact, framed = false, startedAt = System.currentTimeMillis())
        val glass = blockStylePresets.first { it.first == "Glass" }.second
        l.copy(
            presetName = "Daily planner",
            fonts = l.fonts.copy(heading = FontChoice.Inter, body = FontChoice.Inter, label = FontChoice.Inter, clock = FontChoice.Inter, uppercaseLabels = false),
        ).rebuild("Daily planner") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 60f, align = Align.Start, showDate = true, showWeather = true, showPcStatus = false, dateFormat = "EEEE d MMM"),
                toggles, agenda, note, count,
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1f, gapDp = 10f, iconSizeDp = 28f, paddingDp = 12f, showSublabels = false, arrange = TilesArrange.Carousel,
                    tiles = listOfNotNull(keep(p.tv), keep(p.spotify), keep(p.pc), p.tools?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Fill) }, p.social?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Fill) }).map { it.copy(skin = null, border = null) },
                ),
            )
        }.let { r -> r.copy(home = r.home.copy(blockStyles = r.home.blockStyles + listOf(toggles.id, agenda.id, note.id, count.id).associateWith { glass })) }
    },
    LookIdea(
        "Control centre",
        "Like the phone's quick settings, on your home screen: big square toggles, gauges for battery, storage and memory, a hero TV tile with the rest in a mosaic, all on soft cards.",
    ) { l ->
        val toggles = HomeBlock.Toggles(style = ToggleStyle.Squares, sizeDp = 58f, items = listOf(ToggleKind.Torch, ToggleKind.Wifi, ToggleKind.Bluetooth, ToggleKind.Dnd, ToggleKind.Silent, ToggleKind.Volume, ToggleKind.Brightness, ToggleKind.Camera))
        val rings = HomeBlock.Rings(style = RingStyle.Arcs, rings = listOf(RingKind.Battery, RingKind.Storage, RingKind.Memory), framed = false)
        val card = blockStylePresets.first { it.first == "Card" }.second
        l.copy(presetName = "Control centre").rebuild("Control centre") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 52f, align = Align.Start, showDate = true, showWeather = true, dateFormat = "EEE d MMM"),
                toggles, rings,
                HomeBlock.Tiles(
                    title = "", columns = 3, aspect = 1f, gapDp = 10f, iconSizeDp = 28f, paddingDp = 12f, showSublabels = true, arrange = TilesArrange.Hero,
                    tiles = listOfNotNull(keep(p.tv), keep(p.spotify), keep(p.pc), p.tools?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Fill) }).map { it.copy(skin = null, border = null) },
                ),
                l.searchOr(),
            )
        }.let { r -> r.copy(home = r.home.copy(blockStyles = r.home.blockStyles + listOf(toggles.id, rings.id).associateWith { card })) }
    },
    LookIdea(
        "Photo frame",
        "A big photo of your choice that slowly drifts, a light clock under it, a slim year bar, your tiles in a staggered grid and a frosted search bar. Tap the photo spot to pick one.",
    ) { l ->
        val photo = HomeBlock.Photo(heightDp = 260f, caption = "", drift = true, fade = true)
        val search = l.searchOr()
        l.copy(
            presetName = "Photo frame",
            fonts = l.fonts.copy(clock = FontChoice.Manrope, heading = FontChoice.Manrope, body = FontChoice.Manrope, uppercaseLabels = false),
        ).rebuild("Photo frame") { p, keep ->
            listOfNotNull(
                photo,
                l.clockOr().copy(sizeSp = 56f, align = Align.Start, showDate = true, showWeather = true, dateFormat = "EEE d MMM"),
                HomeBlock.Rings(style = RingStyle.Bars, rings = listOf(RingKind.Year), thickness = 3f, glow = false, framed = false),
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.4f, gapDp = 10f, iconSizeDp = 26f, paddingDp = 12f, showSublabels = false, arrange = TilesArrange.Staggered,
                    tiles = listOfNotNull(keep(p.tv), keep(p.spotify), keep(p.pc), p.tools?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Fill) }).map { it.copy(skin = null, border = null) },
                ),
                search,
            )
        }.let { r -> r.copy(home = r.home.copy(blockStyles = r.home.blockStyles + (search.id to blockStylePresets.first { it.first == "Glass" }.second))) }
    },
    LookIdea(
        "Sticker board",
        "Playful collage: paper-style tiles with hard shadows, a handwritten note and a countdown stuck on at slight angles, Syne type and warm colours.",
    ) { l ->
        val note = HomeBlock.Note(text = "don't forget!", handwritten = true, sizeSp = 22f, framed = false)
        val count = HomeBlock.Countdown(title = "Christmas", style = CountdownStyle.Big, framed = false, startedAt = System.currentTimeMillis())
        val sticker = blockStylePresets.first { it.first == "Sticker" }.second
        l.copy(
            presetName = "Sticker board",
            palette = l.palette.copy(followWallpaper = false, accent = "#E5484D", accent2 = "#3E63DD", warn = "#F5A524", good = "#30A46C",
                background = "#22201C", deep = "#161512", panel = "#2C2A25", text = "#F3EBDD", muted = "#A39E93", panelOpacity = 1f),
            backdrop = l.backdrop.copy(style = BackdropStyle.Plain, scanlines = false, vignette = false),
            fonts = l.fonts.copy(heading = FontChoice.Syne, body = FontChoice.Inter, label = FontChoice.Syne, clock = FontChoice.Syne, uppercaseLabels = false),
            home = l.home.copy(tileSkin = tileSkinPresets.first { it.first == "Paper" }.second),
        ).rebuild("Sticker board") { p, keep ->
            listOfNotNull(
                l.clockOr().copy(sizeSp = 64f, align = Align.Start, showDate = true, showWeather = true, dateFormat = "EEE d MMM"),
                note, count,
                HomeBlock.Tiles(
                    title = "", columns = 2, aspect = 1.3f, gapDp = 14f, iconSizeDp = 26f, paddingDp = 12f, showSublabels = false, arrange = TilesArrange.Mosaic,
                    tiles = listOfNotNull(keep(p.tv), keep(p.spotify), keep(p.pc), p.tools?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Physics) }, p.social?.let { keep(it).copy(folderTileStyle = FolderTileStyle.Fill) }).map { it.copy(skin = null, border = null) },
                ),
            )
        }.let { r -> r.copy(home = r.home.copy(blockStyles = r.home.blockStyles + (note.id to sticker) + (count.id to sticker.copy(rotate = 1.5f, color1 = "#FFE9A8")))) }
    },
)
