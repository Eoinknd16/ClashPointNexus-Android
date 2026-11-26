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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.eoinknd16.nexus.look.BlockPin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.eoinknd16.nexus.look.allBlocks
import androidx.compose.animation.togetherWith
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
    data object Pages : HomeEdit
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
    var folderClosing by remember { mutableStateOf(false) }
    var folderCloseRequest by remember { mutableStateOf(0) }
    // Home blurs behind an open folder (Android 12+), easing in/out with it.
    val blurTarget = folder?.takeIf { !folderClosing }?.tile?.folderBlur ?: 0f
    val blur by androidx.compose.animation.core.animateFloatAsState(
        if (look.motion.animations) blurTarget else 0f,
        androidx.compose.animation.core.tween(com.eoinknd16.nexus.ui.fx.nexusMs(240)),
        label = "folderBlur",
    )
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }
    val hostView = androidx.compose.ui.platform.LocalView.current
    val runner = remember { TileRunner(context, c, nav, toast = { toast = it }, view = hostView) }

    // One-time: apps pinned from the drawer in v0.1 become dock slots.
    LaunchedEffect(apps, pins, home.dock.migratedPins) {
        if (!home.dock.migratedPins && apps.isNotEmpty()) {
            val byKey = apps.associateBy { it.key }
            val items = pins.pinned.mapNotNull { k -> byKey[k]?.let { appTile(k, it.label) } }
            c.editLook { l -> l.updateDock { it.copy(items = it.items + items, migratedPins = true) } }
        }
    }

    val haptics = com.eoinknd16.nexus.ui.fx.rememberHaptics()
    val drawerScope = rememberCoroutineScope()
    val drawer = remember { DrawerController(drawerScope, haptics) }
    androidx.compose.runtime.DisposableEffect(drawer) {
        HomeDrawerHub.controller = drawer
        onDispose { if (HomeDrawerHub.controller === drawer) HomeDrawerHub.controller = null }
    }
    // Leaving home (an app opened, the screen turned off) resets the drawer.
    LifecycleResumeEffect(drawer) { onPauseOrDispose { if (!drawer.dragging) drawer.snapClosed() } }
    val drawerOpen by remember { androidx.compose.runtime.derivedStateOf { drawer.isOpen } }

    BackHandler {
        when {
            drawerOpen -> drawer.close()
            folder != null -> folderCloseRequest++
            editMode -> editMode = false
            // A launcher has nothing behind it to go back to.
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }

    val haptic = rememberGestureHaptic()

    val pager = com.eoinknd16.nexus.look.HomePager
    // A deleted page falls back to main.
    LaunchedEffect(home.pages, pager.current) {
        if (pager.current != null && home.pages.none { it.id == pager.current }) pager.current = null
    }
    val currentPage = home.pages.firstOrNull { it.id == pager.current }
    BackHandler(enabled = currentPage != null && folder == null && !editMode) { pager.go("main") }

    @Composable
    fun Blocks(list: List<HomeBlock>) {
        list.forEach { block ->
            BlockFrame(
                block = block,
                editMode = editMode,
                onUp = { c.editLook { it.moveBlock(block.id, -1) } },
                onDown = { c.editLook { it.moveBlock(block.id, 1) } },
                onToggle = { c.editLook { l -> l.updateBlock(block.id) { it.withVisible(!it.visible) } } },
                onPin = { p -> c.editLook { l -> l.updateBlock(block.id) { it.withPin(p) } } },
                onLock = { c.editLook { l -> l.updateBlock(block.id) { it.withLocked(!it.locked) } } },
                onEdit = { editing = HomeEdit.Block(block.id) },
                onRemove = {
                    if (block is HomeBlock.Widget) c.widgets.delete(block.appWidgetId)
                    // Widgets can't come back once released, so no Undo for those.
                    c.editLook(if (block is HomeBlock.Widget) null else "${block.kindLabel} removed") { it.removeBlock(block.id) }
                },
            ) {
                SizedBlock(home.blockSizes[block.id]) {
                BlockContent(
                    block = block,
                    vm = vm,
                    runner = runner,
                    editMode = editMode,
                    onOpenFolder = { tile, origin -> folderClosing = false; folder = OpenFolder(tile, origin) },
                    onEditTile = { tile ->
                        if (tile.locked && !editMode) toast = "Locked · unlock it in edit mode"
                        else editing = HomeEdit.Tile(block.id, tile)
                    },
                    onAddTile = { editing = HomeEdit.Tile(block.id, null) },
                )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    NexusBackdrop(
        Modifier
            .homeGestures(
                gestures = look.gestures,
                enabled = !editMode && folder == null && !drawerOpen,
                drawer = drawer,
                onAction = { a, dir -> runner.runAction(a, dir) },
                onFired = haptic,
                // On an extra page, swiping back the way you came returns to main
                // (either way if it was opened by a tile/dots).
                overrideFor = { dir ->
                    val back = when (pager.openedBy) { -1 -> SwipeDir.Right; 1 -> SwipeDir.Left; else -> null }
                    val horizontal = dir == SwipeDir.Left || dir == SwipeDir.Right
                    if (pager.current != null && horizontal && (back == null || dir == back)) TileAction(TileAction.Kind.Page, "main") else null
                },
            )
            .then(if (blur > 0.5f) Modifier.blur(blur.dp) else Modifier),
    ) {
        // How tall the tray area (page dots + tray + nav bar) is; the page runs
        // underneath it, blurring and fading out instead of being cut off.
        var trayPx by remember { mutableStateOf(0) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val trayDp = with(density) { trayPx.toDp() }
        // Where the tray's icons start (in the page area's coordinates): blur begins only there.
        var pageTopY by remember { mutableStateOf(0f) }
        var pageH by remember { mutableStateOf(0) }
        var dockIconsY by remember { mutableStateOf(Float.NaN) }
        val blurFrom = if (dockIconsY.isNaN()) pageH - trayPx.toFloat() else dockIconsY - pageTopY
        Column(Modifier.fillMaxSize().recedeBehindDrawer { drawer.progress.value }.statusBarsPadding()) {
            AnimatedVisibility(editMode, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
                EditBar(
                    pageName = currentPage?.name,
                    onDone = { editMode = false },
                    onAddBlock = { editing = HomeEdit.AddBlock },
                    onAddWidget = { editing = HomeEdit.AddWidget },
                    onPages = { editing = HomeEdit.Pages },
                    onCustomise = { nav.navigate(Routes.CUSTOMIZE) },
                )
            }
            Box(Modifier.weight(1f).onGloballyPositioned { pageTopY = it.positionInRoot().y; pageH = it.size.height }) {
            androidx.compose.animation.AnimatedContent(
                targetState = pager.current,
                modifier = Modifier.fillMaxSize().trayBackdrop(if (trayPx > 0) blurFrom else 0f, home.trayBlurDp, home.trayFade),
                transitionSpec = {
                    val d = pager.direction
                    val spec = com.eoinknd16.nexus.ui.fx.nexusSpring<androidx.compose.ui.unit.IntOffset>()
                    if (!look.motion.animations) fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap())
                    else (androidx.compose.animation.slideInHorizontally(spec) { w -> w * d } + fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(spec) { w -> -w * d } + fadeOut())
                },
                label = "homePage",
            ) { pageId ->
            val pageBlocks = home.pages.firstOrNull { it.id == pageId }?.blocks ?: home.blocks
            val visibleBlocks = pageBlocks.filter { it.visible || editMode }
            val topBlocks = visibleBlocks.filter { it.pin == BlockPin.Top }
            val middleBlocks = visibleBlocks.filter { it.pin == BlockPin.None }
            val bottomBlocks = visibleBlocks.filter { it.pin == BlockPin.Bottom }
            Column(Modifier.fillMaxSize()) {
            val pad = Modifier.padding(horizontal = home.sidePaddingDp.dp)
            val spacing = Arrangement.spacedBy(home.blockSpacingDp.dp)
            // Pinned to the top: never scrolls.
            if (topBlocks.isNotEmpty()) {
                Column(pad.padding(top = 12.dp, bottom = home.blockSpacingDp.dp / 2), verticalArrangement = spacing) {
                    com.eoinknd16.nexus.ui.update.UpdateBanner()
                    Blocks(topBlocks)
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                // With nothing pinned to the bottom, the scroll area runs under the tray.
                val underTray = bottomBlocks.isEmpty()
                val viewport = constraints.maxHeight - if (underTray) trayPx else 0
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // Long-press empty space = edit the home screen; double-tap = your double-tap action.
                        .pointerInput(look.gestures.doubleTap) {
                            detectTapGestures(
                                onLongPress = { editMode = true },
                                onDoubleTap = if (look.gestures.doubleTap.kind != TileAction.Kind.None) ({
                                    haptic()
                                    runner.runAction(look.gestures.doubleTap)
                                }) else null,
                            )
                        },
                ) {
                    // Auto-fit is off while editing, so toolbars never squash the real layout.
                    AutoFit(viewport, if (editMode) com.eoinknd16.nexus.look.FitMode.Off else home.fit, home.fitTolerance) {
                        Column(pad, verticalArrangement = spacing) {
                            Spacer(Modifier.height(if (topBlocks.isEmpty()) 12.dp else 0.dp))
                            if (topBlocks.isEmpty()) com.eoinknd16.nexus.ui.update.UpdateBanner()
                            Blocks(middleBlocks)
                            if (editMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NexusButton("Add block", icon = Icons.Filled.Add) { editing = HomeEdit.AddBlock }
                                    NexusButton("Add widget", icon = Icons.Filled.Widgets, accent = Nx.Magenta) { editing = HomeEdit.AddWidget }
                                }
                            }
                            if (pageBlocks.isEmpty() && !editMode) {
                                Text(
                                    if (pageId == null) "Long-press to add things to your home screen" else "Blank page · long-press to start building it",
                                    style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                                )
                            }
                        }
                    }
                    // Room to scroll the last block clear of the tray.
                    if (underTray) Spacer(Modifier.height(trayDp))
                }
            }
            // Pinned to the bottom: sits just above the tray.
            if (bottomBlocks.isNotEmpty()) {
                Column(pad.padding(top = home.blockSpacingDp.dp / 2), verticalArrangement = spacing) { Blocks(bottomBlocks) }
                Spacer(Modifier.height(trayDp))
            }
            }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { trayPx = it.height }
                    .navigationBarsPadding(),
            ) {
            if (home.showPageDots && home.pages.isNotEmpty()) {
                PageDots(listOf<String?>(null) + home.pages.map { it.id }, pager.current) { pager.go(it ?: "main") }
            }
            val dockShown = (home.dock.visible && currentPage?.showDock != false) || editMode
            if (!dockShown) dockIconsY = Float.NaN
            if (dockShown) {
                val iconInset = with(density) { (12.dp + if (home.dock.framed) 10.dp else 0.dp).toPx() }
                Box(Modifier.onGloballyPositioned { dockIconsY = it.positionInRoot().y + iconInset }) {
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
            }
            }
        }

        if (editMode && look.gestures.showZonesInEditMode) GestureZonesOverlay(look.gestures)
    }

        DrawerOverlay(drawer)

        folder?.let { f ->
            FolderOverlay(
                folder = f,
                runner = runner,
                closeRequest = folderCloseRequest,
                onClosing = { folderClosing = true },
                onClose = {
                    folder = null
                    folderClosing = false
                    folderCloseRequest = 0
                },
                onEdit = { t -> editing = HomeEdit.Tile(findTileOwner(t.id), t) },
            )
        }

        toast?.let {
            Text(
                hud(it), style = MaterialTheme.typography.labelMedium, color = Nx.Text,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
                    .holoPanel().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    editing?.let { e -> HomeEditorHost(e, onDismiss = { editing = null }, onEdit = { editing = it }) }
    PcHubPanel(onOpenRemote = { nav.navigate(Routes.PC) })
}

/** Which Tiles block (or the dock, null) a tile lives in. */
private fun findTileOwner(tileId: String): String? =
    LookState.look.allBlocks.filterIsInstance<HomeBlock.Tiles>().firstOrNull { b -> b.tiles.any { it.id == tileId } }?.id

@Composable
private fun EditBar(pageName: String?, onDone: () -> Unit, onAddBlock: () -> Unit, onAddWidget: () -> Unit, onPages: () -> Unit, onCustomise: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).holoPanel(Nx.Amber).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            hud(if (pageName == null) "Editing home" else "Editing · $pageName"), style = MaterialTheme.typography.labelMedium, color = Nx.Amber,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        EditIcon(Icons.Filled.Add, "Add block", onAddBlock)
        EditIcon(Icons.Filled.Widgets, "Add widget", onAddWidget)
        EditIcon(Icons.Filled.ViewCarousel, "Pages", onPages)
        val c = appContainer()
        val canUndo by c.canUndo.collectAsStateWithLifecycle()
        if (canUndo) EditIcon(Icons.AutoMirrored.Filled.Undo, "Undo", { c.undoLook() })
        EditIcon(Icons.Filled.Palette, "Customise", onCustomise)
        EditIcon(Icons.Filled.Check, "Done", onDone, Nx.Lime)
    }
}

@Composable
fun EditIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = Nx.Cyan) {
    Box(Modifier.size(38.dp).nexusPress(onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
}

/** Wraps a block; in edit mode adds a toolbar (move, pin, lock, hide, settings, remove). */
@Composable
private fun BlockFrame(
    block: HomeBlock,
    editMode: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: () -> Unit,
    onPin: (BlockPin) -> Unit,
    onLock: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!editMode) {
        content()
        return
    }
    var pinMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().holoPanel(if (block.locked) Nx.Muted else Nx.Amber, glow = 0.05f).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hud(block.kindLabel), style = MaterialTheme.typography.labelSmall, color = if (block.locked) Nx.Muted else Nx.Amber)
                if (block.pin != BlockPin.None || block.locked) {
                    Text(
                        hud(listOfNotNull(block.pin.takeIf { it != BlockPin.None }?.label, "Locked".takeIf { block.locked }).joinToString(" · ")),
                        style = MaterialTheme.typography.labelSmall, color = Nx.Cyan,
                    )
                }
            }
            if (!block.locked) {
                EditIcon(Icons.Filled.KeyboardArrowUp, "Move up", onUp)
                EditIcon(Icons.Filled.KeyboardArrowDown, "Move down", onDown)
                Box {
                    EditIcon(Icons.Filled.PushPin, "Pin", { pinMenu = true }, if (block.pin != BlockPin.None) Nx.Cyan else Nx.Muted)
                    DropdownMenu(expanded = pinMenu, onDismissRequest = { pinMenu = false }, containerColor = Nx.PanelSolid) {
                        BlockPin.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label + if (p == BlockPin.None) "" else " (stays on screen)", color = if (p == block.pin) Nx.Cyan else Nx.Text) },
                                onClick = { pinMenu = false; onPin(p) },
                            )
                        }
                    }
                }
                EditIcon(if (block.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Show/hide", onToggle, if (block.visible) Nx.Cyan else Nx.Muted)
                EditIcon(Icons.Filled.Settings, "Configure", onEdit)
            }
            EditIcon(if (block.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (block.locked) "Unlock" else "Lock", onLock, if (block.locked) Nx.Amber else Nx.Muted)
            if (!block.locked) EditIcon(Icons.Filled.Close, "Remove", onRemove, Nx.Danger)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().then(if (block.visible) Modifier else Modifier.background(Nx.Void.copy(alpha = 0.5f)))) { content() }
    }
}

/** Kept so call sites that only know a tile can still tell what it does. */
fun TileConfig.isFolder(): Boolean = action.kind == TileAction.Kind.Folder


/** Dots for the home pages; tap one to jump there. */
@Composable
private fun PageDots(order: List<String?>, current: String?, onPick: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        order.forEach { id ->
            val on = id == current
            val w by androidx.compose.animation.core.animateDpAsState(if (on) 18.dp else 6.dp, label = "dot")
            Box(
                Modifier.padding(horizontal = 3.dp).size(width = w, height = 6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(if (on) Nx.Cyan else Nx.Muted.copy(alpha = 0.5f))
                    .nexusPress({ onPick(id) }),
            )
        }
    }
}


/**
 * Applies a block's size settings: narrower than the screen (aligned left,
 * centre or right) and/or scaled up or down as a whole. Scaling lays the
 * block out at the matching width first, so it reflows rather than overflows.
 */
@Composable
fun SizedBlock(size: com.eoinknd16.nexus.look.BlockSize?, content: @Composable () -> Unit) {
    if (size == null || (size.widthPct >= 99.5f && kotlin.math.abs(size.scale - 1f) < 0.01f)) {
        content()
        return
    }
    val pct = size.widthPct.coerceIn(20f, 100f) / 100f
    val scale = size.scale.coerceIn(0.4f, 2f)
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val full = constraints.maxWidth
        val target = (full * pct).toInt()
        val inner = (target / scale).toInt().coerceAtLeast(1)
        val p = measurables.first().measure(androidx.compose.ui.unit.Constraints(minWidth = 0, maxWidth = inner, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val w = (p.width * scale).toInt()
        val h = (p.height * scale).toInt()
        val x = when (size.align) {
            com.eoinknd16.nexus.look.Align.Start -> 0
            com.eoinknd16.nexus.look.Align.Center -> (full - w) / 2
            com.eoinknd16.nexus.look.Align.End -> full - w
        }
        layout(full, h) {
            p.placeWithLayer(x, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}


/**
 * Lets the page run under the tray without a hard edge: the page fades out as
 * it reaches the tray while a blurred copy of it fades in behind the tray, and
 * that blurred copy itself fades away towards the bottom of the screen.
 * One recorded layer drawn twice, so it costs no extra composition.
 */
@Composable
private fun Modifier.trayBackdrop(fromPx: Float, blurDp: Float, fade: Boolean): Modifier {
    if (fromPx <= 0f || (!fade && blurDp <= 0f)) return this
    val sharp = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val blurred = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val canBlur = blurDp > 0f && android.os.Build.VERSION.SDK_INT >= 31
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { blurDp.dp.toPx() }
    val edgePx = with(density) { 14.dp.toPx() }
    return this.drawWithContent {
        sharp.record { this@drawWithContent.drawContent() }
        val h = size.height
        val top = fromPx.coerceIn(0f, h)
        fun stop(y: Float) = (y / h).coerceIn(0f, 1f)
        val black = androidx.compose.ui.graphics.Color.Black
        val clear = androidx.compose.ui.graphics.Color.Transparent
        val paint = androidx.compose.ui.graphics.Paint()
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, h)
        // Sharp page: untouched above the tray icons, fading out just below where they start.
        drawContext.canvas.saveLayer(bounds, paint)
        drawLayer(sharp)
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to black, stop(top) to black, stop(top + edgePx) to clear, 1f to clear,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
        drawContext.canvas.restore()
        if (canBlur) {
            // Only what's actually behind the icons gets blurred (clipped first,
            // so tiles above don't smear their blur down into the tray).
            blurred.renderEffect = androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Decal)
            blurred.record { clipRect(top = top) { drawLayer(sharp) } }
            drawContext.canvas.saveLayer(bounds, paint)
            drawLayer(blurred)
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to clear, stop(top) to clear, stop(top + edgePx) to black,
                    stop(top + (h - top) * 0.6f) to black, 1f to if (fade) clear else black,
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
            drawContext.canvas.restore()
        }
    }
}
