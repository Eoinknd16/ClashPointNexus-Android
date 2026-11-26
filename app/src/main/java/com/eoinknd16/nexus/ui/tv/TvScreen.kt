package com.eoinknd16.nexus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.media.CatalogRow
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.media.MediaRepository
import com.eoinknd16.nexus.ui.EmptyBlock
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvTab(val label: String) { Movies("Movies"), Series("Series"), Library("Library") }

data class TabRows(val loading: Boolean = true, val rows: List<CatalogRow> = emptyList())

class TvViewModel(private val c: AppContainer) : ViewModel() {
    private val _tab = MutableStateFlow(TvTab.Movies)
    val tab: StateFlow<TvTab> = _tab

    private val _rows = MutableStateFlow<Map<TvTab, TabRows>>(emptyMap())
    val rows: StateFlow<Map<TvTab, TabRows>> = _rows

    val progress = c.media.progress
    val library = c.media.library
    val addons = c.media.addons

    init {
        load(TvTab.Movies)
    }

    fun select(tab: TvTab) {
        _tab.value = tab
        if (tab != TvTab.Library && _rows.value[tab]?.rows.isNullOrEmpty()) load(tab)
    }

    fun reload() = load(_tab.value)

    private fun load(tab: TvTab) {
        val type = if (tab == TvTab.Series) CatalogType.series else CatalogType.movie
        _rows.update { it + (tab to TabRows(loading = true)) }
        viewModelScope.launch {
            // Your own addon catalogs first, then Cinemeta's built-ins -- rows
            // stream in as each lands instead of waiting on the slowest one.
            val addonRows = runCatching { c.media.addonRows(type) }.getOrDefault(emptyList())
            publish(tab, addonRows, done = false)

            val builtIns = coroutineScope {
                val base = listOf("top" to "Popular", "imdbRating" to "Top rated").map { (id, label) ->
                    async { CatalogRow("cm:$type:$id", label, c.media.cinemetaRow(type, id)) }
                }
                val genres = (if (type == CatalogType.movie) MediaRepository.MOVIE_GENRES else MediaRepository.SERIES_GENRES).map { g ->
                    async { CatalogRow("cm:$type:genre:$g", g, c.media.cinemetaRow(type, "top", g)) }
                }
                (base + genres).awaitAll()
            }
            publish(tab, addonRows + builtIns, done = true)
        }
    }

    private fun publish(tab: TvTab, rows: List<CatalogRow>, done: Boolean) {
        val clean = rows
            .map { r -> r.copy(items = r.items.distinctBy { "${it.type}:${it.id}" }) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.key }
        _rows.update { it + (tab to TabRows(loading = !done && clean.isEmpty(), rows = clean)) }
    }

    fun progressFor(item: CatalogItem): Float? = c.media.progressFor(item.type, item.id)?.takeIf { it.isResumable }?.fraction
}

@Composable
fun TvScreen() {
    val vm = nexusViewModel { TvViewModel(it) }
    val nav = LocalNav.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val addons by vm.addons.collectAsStateWithLifecycle()

    val open: (CatalogItem) -> Unit = { nav.navigate(Routes.detail(it.type.name, it.id)) }

    NexusBackdrop(intensity = 0.55f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader("TV", "${addons.count { "stream" in it.resources }} stream addons") {
                Box(Modifier.size(40.dp).nexusPress({ nav.navigate(Routes.SEARCH) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Search, "Search", tint = Nx.Cyan)
                }
                Box(Modifier.size(40.dp).nexusPress({ nav.navigate(Routes.ADDONS) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Extension, "Addons", tint = Nx.Cyan)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvTab.entries.forEach { t -> TabPill(t.label, t == tab) { vm.select(t) } }
            }

            if (tab == TvTab.Library) {
                if (library.isEmpty()) {
                    EmptyBlock("Library is empty", "Tap + on any title to save it here.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(library, key = { "${it.type}:${it.id}" }) { item -> PosterCard(item, width = 108.dp) { open(item) } }
                    }
                }
                return@Column
            }

            val type = if (tab == TvTab.Series) CatalogType.series else CatalogType.movie
            val continueItems = remember(progress, type) {
                progressItems(progress.values.filter { it.type == type }.sortedByDescending { it.updatedAt }.take(20))
            }
            val tabRows = rows[tab] ?: TabRows()

            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (addons.none { "stream" in it.resources }) {
                    item {
                        NoAddonsBanner { nav.navigate(Routes.ADDONS) }
                    }
                }
                if (continueItems.isNotEmpty()) {
                    item(key = "continue") {
                        PosterRow("Continue watching", continueItems, { item -> progress["${item.type}:${item.id}"]?.fraction }, open)
                    }
                }
                if (tabRows.loading) item { LoadingBlock("Scanning catalogs…") }
                items(tabRows.rows, key = { it.key }) { row ->
                    PosterRow(row.label, row.items, vm::progressFor, open)
                }
                if (!tabRows.loading && tabRows.rows.isEmpty()) {
                    item { EmptyBlock("No catalogs loaded", "Check your connection.") { NexusButton("Retry") { vm.reload() } } }
                }
            }
        }
    }
}

/** Progress entries -> poster-row items (progress only keeps display basics). */
private fun progressItems(entries: List<com.eoinknd16.nexus.media.WatchProgress>): List<CatalogItem> =
    entries.map { CatalogItem(id = it.id, type = it.type, name = it.name, poster = it.poster) }

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Cyan,
        modifier = Modifier
            .nexusPress(onClick)
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
                "You can browse everything, but nothing will play until you add a stream addon " +
                    "or import your list from the PC. Tap to set up.",
                style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
            )
        }
    }
}
