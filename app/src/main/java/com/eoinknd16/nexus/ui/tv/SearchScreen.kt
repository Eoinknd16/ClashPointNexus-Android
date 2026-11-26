package com.eoinknd16.nexus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.ui.EmptyBlock
import com.eoinknd16.nexus.ui.LoadingBlock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

data class SearchState(val loading: Boolean = false, val results: List<CatalogItem>? = null)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    init {
        query
            .debounce(350)
            .distinctUntilChanged()
            .onEach { q -> _state.value = if (q.isBlank()) SearchState() else _state.value.copy(loading = true) }
            .mapLatest { q -> if (q.isBlank()) null else c.media.search(q) }
            .onEach { results -> _state.value = SearchState(loading = false, results = results) }
            .launchIn(viewModelScope)
    }
}

@Composable
fun SearchScreen() {
    val vm = nexusViewModel { SearchViewModel(it) }
    val nav = LocalNav.current
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    NexusBackdrop(intensity = 0.5f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            ScreenHeader("Search", "Movies & series")
            val shape = nexusShape(0.71f)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(shape)
                    .background(Nx.Panel).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Nx.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("TITLE…", style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
                    BasicTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Nx.Text, fontSize = 17.sp),
                        cursorBrush = SolidColor(Nx.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            when {
                state.loading -> LoadingBlock("Querying Cinemeta…")
                state.results?.isEmpty() == true -> EmptyBlock("No matches", "Try another title.")
                state.results != null -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.results.orEmpty(), key = { "${it.type}:${it.id}" }) { item ->
                        PosterCard(item, width = 108.dp) { nav.navigate(Routes.detail(item.type.name, item.id)) }
                    }
                }
            }
        }
    }
}
