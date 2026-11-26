package com.eoinknd16.nexus.look

/** Pure edits on the look tree, used by the home edit mode and Customise. */

fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val j = index + delta
    if (index !in indices || j !in indices) return this
    return toMutableList().apply { add(j, removeAt(index)) }
}

fun Look.updateBlock(id: String, transform: (HomeBlock) -> HomeBlock): Look =
    copy(home = home.copy(blocks = home.blocks.map { if (it.id == id) transform(it) else it }))

fun Look.moveBlock(id: String, delta: Int): Look =
    copy(home = home.copy(blocks = home.blocks.moved(home.blocks.indexOfFirst { it.id == id }, delta)))

fun Look.removeBlock(id: String): Look = copy(home = home.copy(blocks = home.blocks.filterNot { it.id == id }))

fun Look.addBlock(block: HomeBlock, afterId: String? = null): Look {
    val list = home.blocks.toMutableList()
    val i = afterId?.let { a -> list.indexOfFirst { it.id == a } } ?: -1
    if (i >= 0) list.add(i + 1, block) else list.add(block)
    return copy(home = home.copy(blocks = list))
}

fun Look.updateDock(transform: (DockConfig) -> DockConfig): Look = copy(home = home.copy(dock = transform(home.dock)))

/** Replace/insert a tile wherever it lives: a Tiles block (by block id) or the dock (blockId == null). */
fun Look.upsertTile(blockId: String?, tile: TileConfig): Look {
    fun List<TileConfig>.upsert() = if (any { it.id == tile.id }) map { if (it.id == tile.id) tile else it } else this + tile
    return if (blockId == null) updateDock { it.copy(items = it.items.upsert()) }
    else updateBlock(blockId) { b -> if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.upsert()) else b }
}

fun Look.removeTile(blockId: String?, tileId: String): Look =
    if (blockId == null) updateDock { it.copy(items = it.items.filterNot { t -> t.id == tileId }) }
    else updateBlock(blockId) { b -> if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.filterNot { it.id == tileId }) else b }

fun Look.moveTile(blockId: String?, tileId: String, delta: Int): Look =
    if (blockId == null) updateDock { d -> d.copy(items = d.items.moved(d.items.indexOfFirst { it.id == tileId }, delta)) }
    else updateBlock(blockId) { b ->
        if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.moved(b.tiles.indexOfFirst { it.id == tileId }, delta)) else b
    }

fun appTile(key: String, label: String) =
    TileConfig(label = label, icon = "apps", action = TileAction(TileAction.Kind.LaunchApp, key), useAppIcon = true)

/** Every block type you can add from edit mode, with a fresh default. (Widgets go through the widget picker.) */
val addableBlocks: List<Pair<String, () -> HomeBlock>> = listOf(
    "Clock & date" to { HomeBlock.Clock() },
    "Weather" to { HomeBlock.Weather() },
    "Search bar" to { HomeBlock.SearchBar() },
    "Tiles" to { HomeBlock.Tiles(title = "Tiles", tiles = emptyList()) },
    "Resume card" to { HomeBlock.Resume() },
    "Poster row" to { HomeBlock.Posters() },
    "App row" to { HomeBlock.AppRow() },
    "Text" to { HomeBlock.Text() },
    "Spacer" to { HomeBlock.Spacer() },
)
