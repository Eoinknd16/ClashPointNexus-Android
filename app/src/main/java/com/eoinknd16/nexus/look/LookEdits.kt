package com.eoinknd16.nexus.look

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Pure edits on the look tree, used by the home edit mode and Customise. */

fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val j = index + delta
    if (index !in indices || j !in indices) return this
    return toMutableList().apply { add(j, removeAt(index)) }
}

/** Every block on every page (main first). Block ids are unique across pages. */
val Look.allBlocks: List<HomeBlock> get() = home.blocks + home.pages.flatMap { it.blocks }

/** Apply [f] to the block list of the main page and of every extra page. */
fun Look.mapAllBlocks(f: (List<HomeBlock>) -> List<HomeBlock>): Look =
    copy(home = home.copy(blocks = f(home.blocks), pages = home.pages.map { it.copy(blocks = f(it.blocks)) }))

fun Look.updateBlock(id: String, transform: (HomeBlock) -> HomeBlock): Look =
    mapAllBlocks { list -> list.map { if (it.id == id) transform(it) else it } }

fun Look.moveBlock(id: String, delta: Int): Look =
    mapAllBlocks { list -> list.indexOfFirst { it.id == id }.let { i -> if (i < 0) list else list.moved(i, delta) } }

fun Look.removeBlock(id: String): Look = mapAllBlocks { list -> list.filterNot { it.id == id } }

/** Adds to [pageId]'s blocks (null = main page; defaults to the page on screen). */
fun Look.addBlock(block: HomeBlock, afterId: String? = null, pageId: String? = HomePager.current): Look {
    fun List<HomeBlock>.with(): List<HomeBlock> {
        val list = toMutableList()
        val i = afterId?.let { a -> list.indexOfFirst { it.id == a } } ?: -1
        if (i >= 0) list.add(i + 1, block) else list.add(block)
        return list
    }
    val page = home.pages.firstOrNull { it.id == pageId }
    return if (page == null) copy(home = home.copy(blocks = home.blocks.with()))
    else copy(home = home.copy(pages = home.pages.map { if (it.id == page.id) it.copy(blocks = it.blocks.with()) else it }))
}

fun Look.updatePage(id: String, transform: (HomePage) -> HomePage): Look =
    copy(home = home.copy(pages = home.pages.map { if (it.id == id) transform(it) else it }))

fun Look.removePage(id: String): Look = copy(home = home.copy(pages = home.pages.filterNot { it.id == id }))

fun Look.movePage(id: String, delta: Int): Look =
    copy(home = home.copy(pages = home.pages.moved(home.pages.indexOfFirst { it.id == id }, delta)))

fun Look.addPage(page: HomePage): Look = copy(home = home.copy(pages = home.pages + page))

/**
 * Which home page is on screen (null = main) and which side the next one
 * slides in from. Kept outside the saved look: it resets to main on restart
 * and whenever the home button is pressed.
 */
object HomePager {
    var current: String? by androidx.compose.runtime.mutableStateOf(null)
    /** +1 = new page slides in from the right, -1 = from the left. */
    var direction: Int by androidx.compose.runtime.mutableIntStateOf(1)
    /** The swipe direction (-1 left / +1 right / 0 tile) that opened the current page. */
    var openedBy: Int by androidx.compose.runtime.mutableIntStateOf(0)

    /** Where a page action would go from here (null = no such page). */
    fun resolve(target: String): com.eoinknd16.nexus.ui.home.PageTarget? {
        val pages = LookState.look.home.pages
        val order = listOf<String?>(null) + pages.map { it.id }
        val here = order.indexOf(current).coerceAtLeast(0)
        return when (target) {
            "main", "" -> com.eoinknd16.nexus.ui.home.PageTarget(null)
            "next" -> com.eoinknd16.nexus.ui.home.PageTarget(order[(here + 1) % order.size])
            "prev" -> com.eoinknd16.nexus.ui.home.PageTarget(order[(here - 1 + order.size) % order.size])
            // Asking for the page you're already on takes you back home.
            else -> if (target == current) com.eoinknd16.nexus.ui.home.PageTarget(null)
            else pages.firstOrNull { it.id == target }?.let { com.eoinknd16.nexus.ui.home.PageTarget(it.id) }
        }
    }

    /** [target]: a page id, "main", "next" or "prev". [swipe]: -1 left, +1 right, 0 not a swipe. */
    fun go(target: String, swipe: Int = 0) {
        val t = resolve(target) ?: return
        goTo(t.id, swipe)
    }

    fun goTo(dest: String?, swipe: Int = 0) {
        val pages = LookState.look.home.pages
        val order = listOf<String?>(null) + pages.map { it.id }
        val here = order.indexOf(current).coerceAtLeast(0)
        if (dest == current) return
        val there = order.indexOf(dest)
        direction = when {
            swipe < 0 -> 1 // finger moved left: page comes in from the right
            swipe > 0 -> -1
            else -> if (there > here) 1 else -1
        }
        openedBy = if (dest == null) 0 else swipe
        current = dest
    }

    fun home() { if (current != null) go("main") }
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
    "Ask Nexus (AI) bar" to { HomeBlock.AskBar() },
    "Tiles" to { HomeBlock.Tiles(title = "Tiles", tiles = emptyList()) },
    "Resume card" to { HomeBlock.Resume() },
    "Poster row" to { HomeBlock.Posters() },
    "App row" to { HomeBlock.AppRow() },
    "Text" to { HomeBlock.Text() },
    "Spacer" to { HomeBlock.Spacer() },
    "Suggested apps" to { HomeBlock.AppRow(title = "Suggested", suggested = true) },
    "At a glance" to { HomeBlock.Glance() },
    "Liquid battery (sloshes when you tilt)" to { HomeBlock.Liquid() },
    "Device rings (battery, storage, memory, day)" to { HomeBlock.Rings() },
    "Quick toggles (torch, Wi-Fi, Do Not Disturb…)" to { HomeBlock.Toggles() },
    "Calendar (your next events)" to { HomeBlock.Agenda() },
    "Note" to { HomeBlock.Note() },
    "Countdown" to { HomeBlock.Countdown(startedAt = System.currentTimeMillis()) },
    "Photo" to { HomeBlock.Photo() },
    "Picked for you (what to watch)" to { HomeBlock.Posters(title = "Picked for you", source = PosterSource.ForYou) },
)
