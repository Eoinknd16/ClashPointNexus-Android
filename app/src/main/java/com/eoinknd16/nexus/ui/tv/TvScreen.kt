package com.eoinknd16.nexus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.look.BoardMode
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.moved
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.media.CatalogRow
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.media.MediaRepository
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.EmptyBlock
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tab key: a content type name ("movie", "series", "anime", ...) or LIBRARY. */
const val LIBRARY_TAB = "__library"

data class TabRows(val loading: Boolean = true, val rows: List<CatalogRow> = emptyList())

class TvViewModel(private val c: AppContainer) : ViewModel() {
    private val _types = MutableStateFlow(listOf(CatalogType.movie, CatalogType.series))
    val types: StateFlow<List<CatalogType>> = _types

    private val _tab = MutableStateFlow(LookState.look.tv.defaultType)
    val tab: StateFlow<String> = _tab

    private val _rows = MutableStateFlow<Map<String, TabRows>>(emptyMap())
    val rows: StateFlow<Map<String, TabRows>> = _rows

    val progress = c.media.progress
    val library = c.media.library
    val addons = c.media.addons

    private var loadedFor: Int? = null

    init {
        viewModelScope.launch {
            // Reload the board whenever your addon list changes (an import, an install...).
            c.media.addons.collect { list ->
                val sig = list.map { it.url }.hashCode()
                if (sig == loadedFor) return@collect
                loadedFor = sig
                _types.value = c.media.boardTypes()
                _rows.value = emptyMap()
                load(_tab.value)
            }
        }
    }

    fun select(tab: String) {
        _tab.value = tab
        if (tab != LIBRARY_TAB && _rows.value[tab]?.rows.isNullOrEmpty()) load(tab)
    }

    fun reload() = load(_tab.value)

    private fun load(tab: String) {
        if (tab == LIBRARY_TAB) return
        val type = CatalogType(tab)
        _rows.update { it + (tab to TabRows(loading = true)) }
        viewModelScope.launch {
            val addonRows = runCatching { c.media.addonRows(type) }.getOrDefault(emptyList())
            publish(tab, addonRows, done = false)

            val mode = when (LookState.look.tv.board) {
                BoardMode.Auto -> if (c.media.hasCinemetaAddon()) BoardMode.Stremio else BoardMode.Nexus
                else -> LookState.look.tv.board
            }
            // Stremio mode = exactly your addons' board. Nexus mode adds built-in rows (movies/series only).
            val builtIns = if (mode == BoardMode.Nexus && (type == CatalogType.movie || type == CatalogType.series)) coroutineScope {
                val base = listOf("top" to "Popular", "imdbRating" to "Top rated").map { (id, label) ->
                    async { CatalogRow("cm:$type:$id", label, c.media.cinemetaRow(type, id)) }
                }
                val genres = (if (type == CatalogType.movie) MediaRepository.MOVIE_GENRES else MediaRepository.SERIES_GENRES).map { g ->
                    async { CatalogRow("cm:$type:genre:$g", g, c.media.cinemetaRow(type, "top", g)) }
                }
                (base + genres).awaitAll()
            } else emptyList()
            publish(tab, addonRows + builtIns, done = true)
        }
    }

    private fun publish(tab: String, rows: List<CatalogRow>, done: Boolean) {
        val clean = rows
            .map { r -> r.copy(items = r.items.distinctBy { "${it.type}:${it.id}" }) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.key }
        _rows.update { it + (tab to TabRows(loading = !done && clean.isEmpty(), rows = clean)) }
    }

    fun progressFor(item: CatalogItem): Float? = c.media.progressFor(item.type, item.id)?.takeIf { it.isResumable }?.fraction

    // ---- Row customisation (persisted in the look) ----

    fun moveRow(key: String, visibleOrder: List<String>, delta: Int) {
        val i = visibleOrder.indexOf(key)
        if (i < 0) return
        val newOrder = visibleOrder.moved(i, delta)
        c.editLook { l -> l.copy(tv = l.tv.copy(rowOrder = (newOrder + l.tv.rowOrder.filterNot { it in newOrder }))) }
    }

    fun toggleRow(key: String) = c.editLook { l ->
        val hidden = l.tv.hiddenRows
        l.copy(tv = l.tv.copy(hiddenRows = if (key in hidden) hidden - key else hidden + key))
    }

    fun toggleType(type: String) = c.editLook { l ->
        val hidden = l.tv.hiddenTypes
        l.copy(tv = l.tv.copy(hiddenTypes = if (type in hidden) hidden - type else hidden + type))
    }
}

/** Your saved order first, then anything new in its natural order. */
private fun ordered(rows: List<CatalogRow>, order: List<String>): List<CatalogRow> {
    val byKey = rows.associateBy { it.key }
    return order.mapNotNull { byKey[it] } + rows.filter { it.key !in order }
}

@Composable
fun TvScreen() {
    val vm = nexusViewModel { TvViewModel(it) }
    val nav = LocalNav.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val addons by vm.addons.collectAsStateWithLifecycle()
    val tv = LookState.look.tv
    var editing by rememberSaveable { mutableStateOf(false) }

    val visibleTypes = types.filter { editing || it.name !in tv.hiddenTypes }
    LaunchedEffect(visibleTypes) {
        if (tab != LIBRARY_TAB && visibleTypes.none { it.name == tab } && visibleTypes.isNotEmpty()) vm.select(visibleTypes.first().name)
    }

    val open: (CatalogItem) -> Unit = { nav.navigate(Routes.detail(it.type.name, it.id)) }

    NexusBackdrop(intensity = 0.55f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader("TV", "${addons.count { "stream" in it.resources }} stream addons") {
                HeaderIcon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Done" else "Edit rows", if (editing) Nx.Lime else Nx.Cyan) { editing = !editing }
                HeaderIcon(Icons.Filled.Search, "Search") { nav.navigate(Routes.SEARCH) }
                HeaderIcon(Icons.Filled.Extension, "Addons") { nav.navigate(Routes.ADDONS) }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleTypes.forEach { t ->
                    val hidden = t.name in tv.hiddenTypes
                    TabPill(t.label, t.name == tab, Modifier.alpha(if (hidden) 0.4f else 1f), onLongClick = if (editing) ({ vm.toggleType(t.name) }) else null) { vm.select(t.name) }
                }
                TabPill("Library", tab == LIBRARY_TAB) { vm.select(LIBRARY_TAB) }
            }
            if (editing) {
                Text(
                    hud("Reorder or hide rows below · long-press a tab to hide it"),
                    style = MaterialTheme.typography.labelSmall, color = Nx.Amber, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (tab == LIBRARY_TAB) {
                if (library.isEmpty()) {
                    EmptyBlock("Library is empty", "Tap Library on any title, or import your Stremio library in Settings.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(tv.posterWidthDp.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(library, key = { "${it.type}:${it.id}" }) { item -> PosterCard(item, width = tv.posterWidthDp.dp) { open(item) } }
                    }
                }
                return@Column
            }

            val type = CatalogType(tab)
            val continueItems = remember(progress, type) {
                progressItems(progress.values.filter { it.type == type }.sortedByDescending { it.updatedAt }.take(20))
            }
            val tabRows = rows[tab] ?: TabRows()
            // "Picked for you" from your watch history, for this tab's type.
            val cc = appContainer()
            val picks by androidx.compose.runtime.produceState<List<CatalogItem>>(emptyList(), tab, progress.size) {
                value = runCatching { cc.media.recommendations(40) }.getOrDefault(emptyList()).map { it.item }.filter { it.type == type }.take(20)
            }
            val all = ordered(tabRows.rows, tv.rowOrder)
            val shown = if (editing) all else all.filter { it.key !in tv.hiddenRows }
            val order = shown.map { it.key }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (addons.none { "stream" in it.resources }) {
                    item { NoAddonsBanner { nav.navigate(Routes.SETTINGS) } }
                }
                if (tv.showContinue && continueItems.isNotEmpty()) {
                    item(key = "continue") {
                        PosterRow("Continue watching", continueItems, { item -> progress["${item.type}:${item.id}"]?.fraction }, onItem = open)
                    }
                }
                if (picks.isNotEmpty()) {
                    item(key = "foryou") { PosterRow("Picked for you", picks, onItem = open) }
                }
                if (tabRows.loading) item { LoadingBlock("Scanning catalogs…") }
                items(shown, key = { it.key }) { row ->
                    val hidden = row.key in tv.hiddenRows
                    Box(Modifier.alpha(if (hidden) 0.4f else 1f)) {
                        PosterRow(
                            row.label, row.items, vm::progressFor,
                            headerActions = if (editing) {
                                {
                                    HeaderIcon(Icons.Filled.KeyboardArrowUp, "Move up") { vm.moveRow(row.key, order, -1) }
                                    HeaderIcon(Icons.Filled.KeyboardArrowDown, "Move down") { vm.moveRow(row.key, order, 1) }
                                    HeaderIcon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show/hide", if (hidden) Nx.Muted else Nx.Cyan) { vm.toggleRow(row.key) }
                                }
                            } else null,
                            onItem = open,
                        )
                    }
                }
                if (!tabRows.loading && tabRows.rows.isEmpty()) {
                    item { EmptyBlock("No catalogs for ${type.label}", "Your addons don't provide any, or they failed to load.") { NexusButton("Retry") { vm.reload() } } }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = Nx.Cyan, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
}

/** Progress entries -> poster-row items (progress only keeps display basics). */
private fun progressItems(entries: List<WatchProgress>): List<CatalogItem> =
    entries.map { CatalogItem(id = it.id, type = it.type, name = it.name, poster = it.poster) }

@Composable
private fun TabPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = nexusShape(0.57f)
    Text(
        hud(label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Cyan,
        modifier = modifier
            .nexusPress(onClick, onLongClick)
            .clip(shape)
            .background(if (selected) Nx.Cyan else Nx.Cyan.copy(alpha = 0.08f))
            .border(1.dp, Nx.CyanDim, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun NoAddonsBanner(onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().nexusPress(onClick)) {
        HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Amber) {
            Text("No stream addons yet", style = MaterialTheme.typography.titleMedium, color = Nx.Amber)
            Text(
                "Browse all you like, but nothing plays until there's a stream addon. Fastest: Settings › Stremio › " +
                    "sign in to import your whole Stremio setup. Or import from the PC, or add one in Addons.",
                style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
            )
        }
    }
}
