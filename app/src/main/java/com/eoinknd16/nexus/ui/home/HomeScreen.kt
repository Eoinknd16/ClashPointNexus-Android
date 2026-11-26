package com.eoinknd16.nexus.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.look.appTile
import com.eoinknd16.nexus.look.moveBlock
import com.eoinknd16.nexus.look.removeBlock
import com.eoinknd16.nexus.look.updateBlock
import com.eoinknd16.nexus.look.updateDock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlinx.coroutines.delay

/** What the home screen's editor sheet is currently showing. */
sealed interface HomeEdit {
    data class Block(val id: String) : HomeEdit
    /** [blockId] null = a dock slot. [tile] null = adding a new one. */
    data class Tile(val blockId: String?, val tile: TileConfig?) : HomeEdit
    data object AddBlock : HomeEdit
    data object AddWidget : HomeEdit
    data object Dock : HomeEdit
}

/** A folder being shown, and where on screen it was tapped (its animation grows from there). */
data class OpenFolder(val tile: TileConfig, val origin: Offset)

@Composable
fun HomeScreen() {
    val c = appContainer()
    val vm = nexusViewModel { HomeViewModel(it) }
    val nav = LocalNav.current
    val context = LocalContext.current
    val look = LookState.look
    val home = look.home
    val apps by c.apps.apps.collectAsStateWithLifecycle()
    val pins by c.apps.prefs.state.collectAsStateWithLifecycle()

    var editMode by rememberSaveable { mutableStateOf(false) }
    val editRequest by c.homeEditRequest.collectAsStateWithLifecycle()
    LaunchedEffect(editRequest) {
        if (editRequest) {
            editMode = true
            c.homeEditRequest.value = false
        }
    }
    var editing by remember { mutableStateOf<HomeEdit?>(null) }
    var folder by remember { mutableStateOf<OpenFolder?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }
    val runner = remember { TileRunner(context, c, nav) { toast = it } }

    // One-time: apps pinned from the drawer in v0.1 become dock slots.
    LaunchedEffect(apps, pins, home.dock.migratedPins) {
        if (!home.dock.migratedPins && apps.isNotEmpty()) {
            val byKey = apps.associateBy { it.key }
            val items = pins.pinned.mapNotNull { k -> byKey[k]?.let { appTile(k, it.label) } }
            c.editLook { l -> l.updateDock { it.copy(items = it.items + items, migratedPins = true) } }
        }
    }

    BackHandler {
        when {
            folder != null -> folder = null
            editMode -> editMode = false
            // A launcher has nothing behind it to go back to.
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }

    // Over-scroll past the top/bottom of home = the classic launcher swipes.
    val swipes = remember(home.swipeUpForApps, home.swipeDownForNotifications) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pull += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull < -140f && home.swipeUpForApps) nav.navigate(Routes.APPS)
                else if (pull > 160f && home.swipeDownForNotifications) TileRunner.expandNotifications(context)
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    NexusBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AnimatedVisibility(editMode, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
                EditBar(
                    onDone = { editMode = false },
                    onAddBlock = { editing = HomeEdit.AddBlock },
                    onAddWidget = { editing = HomeEdit.AddWidget },
                    onCustomise = { nav.navigate(Routes.CUSTOMIZE) },
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .nestedScroll(swipes)
                    .verticalScroll(rememberScrollState())
                    // Long-press empty space = edit the home screen.
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { editMode = true }) }
                    .padding(horizontal = home.sidePaddingDp.dp),
                verticalArrangement = Arrangement.spacedBy(home.blockSpacingDp.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                home.blocks.filter { it.visible || editMode }.forEach { block ->
                    BlockFrame(
                        block = block,
                        editMode = editMode,
                        onUp = { c.editLook { it.moveBlock(block.id, -1) } },
                        onDown = { c.editLook { it.moveBlock(block.id, 1) } },
                        onToggle = { c.editLook { l -> l.updateBlock(block.id) { it.withVisible(!it.visible) } } },
                        onEdit = { editing = HomeEdit.Block(block.id) },
                        onRemove = {
                            if (block is HomeBlock.Widget) c.widgets.delete(block.appWidgetId)
                            c.editLook { it.removeBlock(block.id) }
                        },
                    ) {
                        BlockContent(
                            block = block,
                            vm = vm,
                            runner = runner,
                            editMode = editMode,
                            onOpenFolder = { tile, origin -> folder = OpenFolder(tile, origin) },
                            onEditTile = { tile -> editing = HomeEdit.Tile(block.id, tile) },
                            onAddTile = { editing = HomeEdit.Tile(block.id, null) },
                        )
                    }
                }
                if (editMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NexusButton("Add block", icon = Icons.Filled.Add) { editing = HomeEdit.AddBlock }
                        NexusButton("Add widget", icon = Icons.Filled.Widgets, accent = Nx.Magenta) { editing = HomeEdit.AddWidget }
                    }
                }
                if (home.blocks.isEmpty() && !editMode) {
                    Text(
                        "Long-press to add things to your home screen",
                        style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (home.dock.visible || editMode) {
                Dock(
                    config = home.dock,
                    runner = runner,
                    editMode = editMode,
                    onOpenFolder = { tile, origin -> folder = OpenFolder(tile, origin) },
                    onEditSlot = { tile -> editing = HomeEdit.Tile(null, tile) },
                    onAddSlot = { editing = HomeEdit.Tile(null, null) },
                    onDockSettings = { editing = HomeEdit.Dock },
                    onDrawer = { nav.navigate(Routes.APPS) },
                )
            }
        }

        folder?.let { f -> FolderOverlay(f, runner, onClose = { folder = null }, onEdit = { blockless ->
            folder = null
            editing = HomeEdit.Tile(findTileOwner(blockless.id), blockless)
        }) }

        toast?.let {
            Text(
                hud(it), style = MaterialTheme.typography.labelMedium, color = Nx.Text,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
                    .holoPanel().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    editing?.let { e -> HomeEditorHost(e, onDismiss = { editing = null }, onEdit = { editing = it }) }
}

/** Which Tiles block (or the dock, null) a tile lives in. */
private fun findTileOwner(tileId: String): String? =
    LookState.look.home.blocks.filterIsInstance<HomeBlock.Tiles>().firstOrNull { b -> b.tiles.any { it.id == tileId } }?.id

@Composable
private fun EditBar(onDone: () -> Unit, onAddBlock: () -> Unit, onAddWidget: () -> Unit, onCustomise: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).holoPanel(Nx.Amber).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hud("Editing home"), style = MaterialTheme.typography.labelMedium, color = Nx.Amber, modifier = Modifier.weight(1f))
        EditIcon(Icons.Filled.Add, "Add block", onAddBlock)
        EditIcon(Icons.Filled.Widgets, "Add widget", onAddWidget)
        EditIcon(Icons.Filled.Palette, "Customise", onCustomise)
        EditIcon(Icons.Filled.Check, "Done", onDone, Nx.Lime)
    }
}

@Composable
fun EditIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = Nx.Cyan) {
    Box(Modifier.size(38.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
}

/** Wraps a block; in edit mode adds a toolbar (move, hide, settings, remove). */
@Composable
private fun BlockFrame(
    block: HomeBlock,
    editMode: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!editMode) {
        content()
        return
    }
    Column(Modifier.fillMaxWidth().holoPanel(Nx.Amber, glow = 0.05f).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hud(block.kindLabel), style = MaterialTheme.typography.labelSmall, color = Nx.Amber, modifier = Modifier.weight(1f))
            EditIcon(Icons.Filled.KeyboardArrowUp, "Move up", onUp)
            EditIcon(Icons.Filled.KeyboardArrowDown, "Move down", onDown)
            EditIcon(if (block.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Show/hide", onToggle, if (block.visible) Nx.Cyan else Nx.Muted)
            EditIcon(Icons.Filled.Settings, "Configure", onEdit)
            EditIcon(Icons.Filled.Close, "Remove", onRemove, Nx.Danger)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().then(if (block.visible) Modifier else Modifier.background(Nx.Void.copy(alpha = 0.5f)))) { content() }
    }
}

/** Kept so call sites that only know a tile can still tell what it does. */
fun TileConfig.isFolder(): Boolean = action.kind == TileAction.Kind.Folder
