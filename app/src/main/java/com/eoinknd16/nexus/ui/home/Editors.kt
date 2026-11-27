package com.eoinknd16.nexus.ui.home

import androidx.compose.foundation.layout.navigationBars

import androidx.compose.foundation.layout.statusBars

import androidx.compose.foundation.layout.WindowInsets

import com.eoinknd16.nexus.ui.settings.Hint

import androidx.compose.ui.graphics.Color

import com.eoinknd16.nexus.look.allBlocks

import com.eoinknd16.nexus.look.addPage

import com.eoinknd16.nexus.look.movePage

import com.eoinknd16.nexus.look.removePage

import com.eoinknd16.nexus.look.updatePage

import com.eoinknd16.nexus.look.mapAllBlocks

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.look.Align
import com.eoinknd16.nexus.look.FolderAnimation
import com.eoinknd16.nexus.look.FolderStyle
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.IconCatalog
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.PosterSource
import com.eoinknd16.nexus.look.SearchEngine
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.look.addBlock
import com.eoinknd16.nexus.look.addableBlocks
import com.eoinknd16.nexus.look.moved
import com.eoinknd16.nexus.look.toHex
import com.eoinknd16.nexus.look.removeTile
import com.eoinknd16.nexus.look.updateBlock
import com.eoinknd16.nexus.look.updateDock
import com.eoinknd16.nexus.look.upsertTile
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.settings.ChoiceRow
import com.eoinknd16.nexus.ui.settings.ColorRow
import com.eoinknd16.nexus.ui.settings.SliderRow
import com.eoinknd16.nexus.ui.settings.TextRow
import com.eoinknd16.nexus.ui.settings.ToggleRow
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.roundToInt

/**
 * Full-screen editor panel. Replaces the old swipe-down bottom sheet, which
 * could get dismissed by a scroll that went past the top of its content --
 * this only closes via Done or Back.
 */
@Composable
fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit = onDismiss,
    doneLabel: String = "Done",
    /** Shown above the scrolling options and never scrolls away (live previews, tabs). */
    pinned: (@Composable () -> Unit)? = null,
    /** "Cancel" link for editors that only save on Done. */
    onCancel: (() -> Unit)? = null,
    /** Undo for editors whose changes apply live. */
    showUndo: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = appContainer()
    val canUndo by c.canUndo.collectAsStateWithLifecycle()
    // Measured from the main window: dialog windows don't always get told about
    // the status bar or the on-screen Home / Back / Recents buttons.
    val bars = rememberSystemBarPadding()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().background(Nx.PanelSolid).padding(top = bars.top, bottom = bars.bottom).imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Nx.Deep).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onCancel != null) {
                    Text(hud("Cancel"), style = MaterialTheme.typography.labelLarge, color = Nx.Muted, modifier = Modifier.nexusPress(onCancel).padding(end = 12.dp, top = 8.dp, bottom = 8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = Nx.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showUndo && canUndo) {
                    Text(hud("Undo"), style = MaterialTheme.typography.labelLarge, color = Nx.Amber, modifier = Modifier.nexusPress({ c.undoLook() }).padding(horizontal = 10.dp, vertical = 8.dp))
                }
                NexusButton(doneLabel, icon = Icons.Filled.Check, filled = true, onClick = onDone)
            }
            if (pinned != null) {
                Column(Modifier.fillMaxWidth().background(Nx.PanelSolid).padding(horizontal = 16.dp, vertical = 8.dp)) { pinned() }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Nx.CyanDim))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                content()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Tabs for long editors: one row of equal segments. */
@Composable
fun EditorTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = nexusShape(0.5f)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, Nx.CyanDim, shape)) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).nexusPress({ onSelect(i) }).background(if (on) Nx.Cyan else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(hud(t), style = MaterialTheme.typography.labelMedium, color = if (on) Nx.Void else Nx.Cyan, maxLines = 1)
            }
        }
    }
}

@Composable
fun HomeEditorHost(edit: HomeEdit, onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    when (edit) {
        is HomeEdit.Block -> LookState.look.allBlocks.firstOrNull { it.id == edit.id }?.let { BlockEditor(it, onDismiss, onEdit) }
        is HomeEdit.Tile -> TileEditor(edit.blockId, edit.tile, onDismiss)
        HomeEdit.AddBlock -> AddBlockSheet(onDismiss, onEdit)
        HomeEdit.AddWidget -> WidgetPickerSheet(onDismiss)
        HomeEdit.Dock -> DockEditor(onDismiss, onEdit)
        HomeEdit.Pages -> PagesEditor(onDismiss)
    }
}

// ---- Block editors -------------------------------------------------------------

@Composable
private fun BlockEditor(block: HomeBlock, onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    val c = appContainer()
    fun set(b: HomeBlock) = c.editLook { it.updateBlock(block.id) { b } }
    val vm = com.eoinknd16.nexus.ui.nexusViewModel { HomeViewModel(it) }
    val context = LocalContext.current
    val nav = com.eoinknd16.nexus.ui.LocalNav.current
    val runner = remember { TileRunner(context, c, nav, toast = {}) }
    EditorSheet(block.kindLabel, onDismiss, showUndo = true) {
        // Live preview of this block with the settings below applied.
        if (block !is HomeBlock.Widget && block !is HomeBlock.Spacer) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(nexusShape()).background(Nx.Void)
                    .border(1.dp, Nx.CyanDim, nexusShape()).padding(12.dp),
            ) {
                SizedBlock(LookState.look.home.blockSizes[block.id]) {
                    BlockContent(block, vm, runner, editMode = false, onOpenFolder = { _, _ -> }, onEditTile = {}, onAddTile = {})
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        BlockSizeSection(block.id)
        when (block) {
            is HomeBlock.Clock -> {
                ToggleRow("24-hour", block.use24h) { set(block.copy(use24h = it)) }
                ToggleRow("Show seconds", block.showSeconds) { set(block.copy(showSeconds = it)) }
                ToggleRow("Blinking colon", block.blinkColon) { set(block.copy(blinkColon = it)) }
                ToggleRow("Show date", block.showDate) { set(block.copy(showDate = it)) }
                TextRow("Date format", block.dateFormat, "EEEE · d MMMM") { set(block.copy(dateFormat = it)) }
                Text("e.g. EEE d MMM · dd/MM/yyyy · EEEE, MMMM d", style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                ToggleRow("Weather under the date", block.showWeather, "Location: Customise › Weather") { set(block.copy(showWeather = it)) }
                ToggleRow("PC status light", block.showPcStatus) { set(block.copy(showPcStatus = it)) }
                SliderRow("Size", block.sizeSp, 32f..120f, { "${it.roundToInt()}sp" }) { set(block.copy(sizeSp = it)) }
                ChoiceRow("Alignment", Align.entries, block.align, { it.name }) { set(block.copy(align = it)) }
            }
            is HomeBlock.Weather -> {
                SliderRow("Forecast days", block.forecastDays.toFloat(), 0f..6f, { it.roundToInt().toString() }, steps = 5) { set(block.copy(forecastDays = it.roundToInt())) }
                ToggleRow("Feels like / humidity / wind", block.showDetails) { set(block.copy(showDetails = it)) }
                Text("Location and units: Customise › Weather", style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
            }
            is HomeBlock.SearchBar -> {
                ChoiceRow("Search with", SearchEngine.entries, block.engine, { it.label }) { set(block.copy(engine = it)) }
                if (block.engine == SearchEngine.Custom) TextRow("Search URL (%s = query)", block.customUrl, "https://…%s", KeyboardType.Uri) { set(block.copy(customUrl = it)) }
                TextRow("Placeholder", block.placeholder) { set(block.copy(placeholder = it)) }
                ToggleRow("Voice search button", block.showMic) { set(block.copy(showMic = it)) }
                ToggleRow("Show matching apps while typing", block.matchApps) { set(block.copy(matchApps = it)) }
                ToggleRow("Ask Nexus (AI) inside this search", block.aiInline, "Off = a plain search of your phone and the web. Add an \"Ask Nexus bar\" for the AI on its own.") { set(block.copy(aiInline = it)) }
                Text(
                    "Want Google's own search bar? Add it as a widget: edit mode › Add widget › Google.",
                    style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                )
            }
            is HomeBlock.AskBar -> {
                TextRow("Placeholder", block.placeholder) { set(block.copy(placeholder = it)) }
                ToggleRow("Voice button", block.showMic) { set(block.copy(showMic = it)) }
                Hint("Set up which AI it uses in Nexus settings › Ask Nexus.")
            }
            is HomeBlock.Tiles -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                SliderRow("Columns", block.columns.toFloat(), 1f..6f, { it.roundToInt().toString() }, steps = 4) { set(block.copy(columns = it.roundToInt())) }
                SliderRow("Tile shape (width ÷ height)", block.aspect, 0.5f..3f, { "%.2f".format(it) }) { set(block.copy(aspect = it)) }
                ToggleRow("Show sub-labels", block.showSublabels) { set(block.copy(showSublabels = it)) }
                SliderRow("Fixed row height", block.rowHeightDp, 0f..260f, { if (it < 1f) "Off (use shape)" else "${it.roundToInt()}dp" }) { set(block.copy(rowHeightDp = if (it < 20f) 0f else it)) }
                SliderRow("Gap between tiles", block.gapDp, 0f..40f, { "${it.roundToInt()}dp" }) { set(block.copy(gapDp = it)) }
                ChoiceRow("Tile layout", com.eoinknd16.nexus.look.TileLayout.entries, block.tileLayout, { it.label }) { set(block.copy(tileLayout = it)) }
                SliderRow("Icon size", block.iconSizeDp, 16f..120f, { "${it.roundToInt()}dp" }) { set(block.copy(iconSizeDp = it)) }
                SliderRow("Text size", block.labelScale, 0.5f..2.5f, { "${(it * 100).roundToInt()}%" }) { set(block.copy(labelScale = it)) }
                SliderRow("Inner padding", block.paddingDp, 0f..40f, { "${it.roundToInt()}dp" }) { set(block.copy(paddingDp = it)) }
                Text("Make one tile bigger: edit it and set its width/height in grid cells.", style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                Spacer(Modifier.height(8.dp))
                HudLabel("Tiles")
                block.tiles.forEachIndexed { i, t ->
                    ListRow(
                        title = t.label, subtitle = t.action.kind.label,
                        onClick = { onEdit(HomeEdit.Tile(block.id, t)) },
                        onUp = { set(block.copy(tiles = block.tiles.moved(i, -1))) },
                        onDown = { set(block.copy(tiles = block.tiles.moved(i, 1))) },
                        onDelete = { c.editLook { it.removeTile(block.id, t.id) } },
                    )
                }
                Spacer(Modifier.height(8.dp))
                NexusButton("Add tile", filled = true) { onEdit(HomeEdit.Tile(block.id, null)) }
            }
            is HomeBlock.Resume -> {
                val media = c.externalMedia
                val hasAccess by media.hasAccess.collectAsStateWithLifecycle()
                val seen by media.items.collectAsStateWithLifecycle()
                val apps by c.apps.apps.collectAsStateWithLifecycle()
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                ToggleRow("Nexus movies & shows", block.showNexus) { set(block.copy(showNexus = it)) }
                ToggleRow("Other apps (Spotify, YouTube…)", block.showApps, "Anything that shows media controls in your notifications") { set(block.copy(showApps = it)) }
                if (block.showApps && !hasAccess) {
                    val ctx = LocalContext.current
                    NexusButton("Allow \"Nexus now playing\"", accent = Nx.Amber) { media.openAccessSettings(ctx) }
                }
                SliderRow("How many to show", block.maxItems.toFloat(), 1f..6f, { "${it.roundToInt()}" }) { set(block.copy(maxItems = it.roundToInt())) }
                ToggleRow("Previous / next buttons", block.showControls) { set(block.copy(showControls = it)) }
                ToggleRow("× to hide a card", block.showDismiss, "Comes back when you watch more of it, or the app plays something again") { set(block.copy(showDismiss = it)) }
                SliderRow("Forget paused app media after", block.maxAgeHours.toFloat(), 0f..336f, { if (it < 1f) "Never" else "${it.roundToInt()}h" }) { set(block.copy(maxAgeHours = it.roundToInt())) }
                val pkgs = (seen.map { it.packageName } + block.hiddenApps).distinct()
                if (pkgs.isNotEmpty()) {
                    HudLabel("Apps seen")
                    pkgs.forEach { pkg ->
                        val label = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        ToggleRow(label, pkg !in block.hiddenApps) { show ->
                            set(block.copy(hiddenApps = if (show) block.hiddenApps - pkg else block.hiddenApps + pkg))
                        }
                    }
                }
            }
            is HomeBlock.Posters -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                ChoiceRow("Show", PosterSource.entries, block.source, { it.label }) { set(block.copy(source = it)) }
                SliderRow("How many", block.count.toFloat(), 3f..40f, { it.roundToInt().toString() }) { set(block.copy(count = it.roundToInt())) }
                SliderRow("Poster size", block.posterWidthDp, 56f..200f, { "${it.roundToInt()}dp wide" }) { set(block.copy(posterWidthDp = it)) }
            }
            is HomeBlock.AppRow -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                SliderRow("Icon size", block.iconSizeDp, 32f..80f, { "${it.roundToInt()}dp" }) { set(block.copy(iconSizeDp = it)) }
                ToggleRow("Labels", block.showLabels) { set(block.copy(showLabels = it)) }
                ToggleRow("Suggest apps automatically", block.suggested, "Picks what you usually open around this time") { set(block.copy(suggested = it)) }
                if (block.suggested) {
                    SliderRow("How many", block.count.toFloat(), 1f..10f, { "${it.roundToInt()}" }) { set(block.copy(count = it.roundToInt())) }
                } else {
                    HudLabel("Apps")
                    AppPicker(selected = block.apps, multi = true) { set(block.copy(apps = it)) }
                }
            }
            is HomeBlock.Widget -> {
                TextRow("Name", block.label) { set(block.copy(label = it)) }
                SliderRow("Height", block.heightDp, 40f..600f, { "${it.roundToInt()}dp" }) { set(block.copy(heightDp = it)) }
                ToggleRow("Draw inside a panel", block.framed) { set(block.copy(framed = it)) }
            }
            is HomeBlock.Text -> {
                TextRow("Text", block.text, singleLine = false) { set(block.copy(text = it)) }
                SliderRow("Size", block.sizeSp, 10f..64f, { "${it.roundToInt()}sp" }) { set(block.copy(sizeSp = it)) }
                ChoiceRow("Alignment", Align.entries, block.align, { it.name }) { set(block.copy(align = it)) }
                ToggleRow("Accent colour", block.accent) { set(block.copy(accent = it)) }
            }
            is HomeBlock.Spacer -> SliderRow("Height", block.heightDp, 4f..240f, { "${it.roundToInt()}dp" }) { set(block.copy(heightDp = it)) }
            is HomeBlock.Glance -> {
                ToggleRow("Greeting", block.greeting) { set(block.copy(greeting = it)) }
                ToggleRow("Next calendar event", block.calendar) { set(block.copy(calendar = it)) }
                ToggleRow("Rain in the next few hours", block.rain) { set(block.copy(rain = it)) }
                ToggleRow("What's playing", block.nowPlaying) { set(block.copy(nowPlaying = it)) }
                ToggleRow("Battery (when low or charging)", block.battery) { set(block.copy(battery = it)) }
                ToggleRow("Tomorrow's alarm (evenings)", block.alarm) { set(block.copy(alarm = it)) }
                ToggleRow("PC still on (late at night)", block.pcLateNight) { set(block.copy(pcLateNight = it)) }
                SliderRow("Most lines shown", block.maxLines.toFloat(), 1f..6f, { "${it.roundToInt()}" }) { set(block.copy(maxLines = it.roundToInt())) }
                ToggleRow("Inside a panel", block.framed) { set(block.copy(framed = it)) }
            }
            is HomeBlock.Liquid -> {
                SliderRow("Height", block.heightDp, 60f..260f, { "${it.roundToInt()}dp" }) { set(block.copy(heightDp = it)) }
                SliderRow("Wobble", block.wobble, 0f..1f, { if (it < 0.15f) "Honey" else if (it > 0.85f) "Water" else "${(it * 100).roundToInt()}%" }) { set(block.copy(wobble = it)) }
                ToggleRow("Show the percentage", block.showPercent) { set(block.copy(showPercent = it)) }
                ToggleRow("Colour by level", block.colourByLevel, "Green when full, amber when low, red when nearly empty") { set(block.copy(colourByLevel = it)) }
                ToggleRow("Inside a panel", block.framed) { set(block.copy(framed = it)) }
            }
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String?, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).holoPanel(glow = 0.05f).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).nexusPress(onClick)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
            if (subtitle != null) Text(hud(subtitle), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
        EditIcon(Icons.Filled.KeyboardArrowUp, "Up", onUp)
        EditIcon(Icons.Filled.KeyboardArrowDown, "Down", onDown)
        EditIcon(Icons.Filled.Delete, "Remove", onDelete, Nx.Danger)
    }
}

// ---- Tile editor ---------------------------------------------------------------

/** Full editor for one tile or tray slot: what it does, how it looks, folder contents. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileEditor(blockId: String?, existing: TileConfig?, onDismiss: () -> Unit) {
    val c = appContainer()
    val apps by c.apps.apps.collectAsStateWithLifecycle()
    var t by remember {
        mutableStateOf(
            existing ?: TileConfig(
                label = if (blockId == null) "App" else "New tile",
                action = TileAction(if (blockId == null) TileAction.Kind.LaunchApp else TileAction.Kind.Screen, if (blockId == null) "" else "tv"),
            ),
        )
    }
    fun save() {
        c.editLook { it.upsertTile(blockId, t) }
        onDismiss()
    }

    val context = LocalContext.current
    val nav = com.eoinknd16.nexus.ui.LocalNav.current
    val runner = remember { TileRunner(context, c, nav, toast = {}) }
    val isFolder = t.action.kind == TileAction.Kind.Folder
    val tabs = buildList {
        add("Does")
        if (isFolder) add("Folder")
        add("Look")
        add("Style")
        if (blockId != null) add("Size")
    }
    var tabName by remember { mutableStateOf("Does") }
    if (tabName !in tabs) tabName = "Does"
    // Done saves. Back also keeps changes to an existing tile (but doesn't add a new one); Cancel throws them away.
    EditorSheet(
        if (existing == null) (if (blockId == null) "Add to tray" else "New tile") else "Edit tile",
        onDismiss = { if (existing != null) save() else onDismiss() },
        onDone = { save() },
        doneLabel = if (existing == null) "Add" else "Done",
        onCancel = onDismiss,
        pinned = {
            // Live preview: exactly how the tile (or tray slot) will look.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(130.dp).height(118.dp)) {
                    TileView(tile = t, runner = runner, showSublabel = true, modifier = Modifier.fillMaxWidth().height(118.dp), onOpenFolder = { _, _ -> }, onLongPress = {})
                }
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TileIcon(t, runner, LookState.palette.slot(t.color), LookState.look.home.dock.iconSizeDp.dp)
                    Text(hud("In the tray"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            EditorTabs(tabs, tabs.indexOf(tabName)) { tabName = tabs[it] }
        },
    ) {
        when (tabName) {
        "Does" -> {
        Spacer(Modifier.height(12.dp))
        ChoiceRow(
            "What it does", TileAction.Kind.entries, t.action.kind, { it.label },
            preview = { k -> Icon(IconCatalog.icon(defaultIcon(k, "bolt")), null, tint = Nx.Cyan, modifier = Modifier.size(20.dp)) },
            group = ::actionGroup,
        ) { k ->
            val arg = when (k) {
                TileAction.Kind.Screen -> SCREEN_TARGETS.first().first
                TileAction.Kind.PcNav -> PC_NAV_TARGETS.first().first
                TileAction.Kind.PcScreen -> PC_SCREEN_TARGETS.first().first
                TileAction.Kind.Settings -> SETTINGS_TARGETS.first().first
                TileAction.Kind.Page -> "next"
                TileAction.Kind.PcPower -> "shutdown"
                else -> ""
            }
            t = t.copy(action = TileAction(k, arg), icon = defaultIcon(k, t.icon))
        }

        when (t.action.kind) {
            TileAction.Kind.Screen -> TargetChips("Screen", SCREEN_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.PcNav -> TargetChips("Button", PC_NAV_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.PcScreen -> TargetChips("PC screen", PC_SCREEN_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.Settings -> TargetChips("Setting", SETTINGS_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.WakePc, TileAction.Kind.SwitchPc -> {
                val active by c.pcStore.state.collectAsStateWithLifecycle()
                val saved by c.pcSaved.state.collectAsStateWithLifecycle()
                val pcs = com.eoinknd16.nexus.link.allPcs(active, saved)
                if (pcs.size > 1) {
                    val first = if (t.action.kind == TileAction.Kind.WakePc) "" to "Whichever PC is active" else "next" to "Next PC (cycles)"
                    TargetChips("Which PC", listOf(first) + pcs.map { it.key to it.displayName }, t.action.arg) { key, _ -> t = t.copy(action = t.action.copy(arg = key)) }
                }
            }
            TileAction.Kind.PcPower -> TargetChips(
                "Do", listOf("shutdown" to "Shut down", "restart" to "Restart", "sleep" to "Sleep", "cancel" to "Cancel shutdown"), t.action.arg,
            ) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.Page -> {
                val targets = listOf("next" to "Next page", "prev" to "Previous page", "main" to "Main page") +
                    LookState.look.home.pages.map { it.id to it.name }
                TargetChips("Page", targets, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            }
            TileAction.Kind.Url -> TextRow("Link", t.action.arg, "https://…", KeyboardType.Uri) { t = t.copy(action = t.action.copy(arg = it)) }
            TileAction.Kind.WebSearch -> TextRow("Search for (blank = open search page)", t.action.arg) { t = t.copy(action = t.action.copy(arg = it)) }
            TileAction.Kind.LaunchApp -> {
                HudLabel("App")
                AppPicker(selected = listOfNotNull(t.action.arg.takeIf { it.isNotBlank() }), multi = false) { picked ->
                    val key = picked.firstOrNull() ?: ""
                    val label = apps.firstOrNull { it.key == key }?.label
                    t = t.copy(action = t.action.copy(arg = key), label = if (label != null && (t.label == "App" || t.label == "New tile" || existing == null)) label else t.label)
                }
                ToggleRow("Use the app's own icon", t.useAppIcon) { t = t.copy(useAppIcon = it) }
            }
            TileAction.Kind.Folder -> {
                HudLabel("Apps in the folder (tap to add/remove, order = tap order)")
                AppPicker(selected = t.folderApps, multi = true) { t = t.copy(folderApps = it) }
                if (t.folderApps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    t.folderApps.forEachIndexed { i, k ->
                        val name = apps.firstOrNull { it.key == k }?.label ?: k
                        ListRow(
                            name, null, {},
                            onUp = { t = t.copy(folderApps = t.folderApps.moved(i, -1)) },
                            onDown = { t = t.copy(folderApps = t.folderApps.moved(i, 1)) },
                            onDelete = { t = t.copy(folderApps = t.folderApps - k) },
                        )
                    }
                }
                Hint("Tile style and how the folder opens are on the Folder tab.")
            }
            else -> {}
        }
        }
        "Folder" -> {
                HudLabel("On the tile")
                ChoiceRow("Tile style", com.eoinknd16.nexus.look.FolderTileStyle.entries, t.folderTileStyle, { it.label }) { t = t.copy(folderTileStyle = it) }
                if (t.folderTileStyle == com.eoinknd16.nexus.look.FolderTileStyle.Icon) {
                    ToggleRow("Show app previews on the tile", t.folderPreview) { t = t.copy(folderPreview = it) }
                } else {
                    SliderRow("Icon size on the tile", t.folderTileIconScale, 0.5f..1.5f, { "${(it * 100).roundToInt()}%" }) { t = t.copy(folderTileIconScale = it) }
                    ToggleRow("Folder name on the tile", t.folderTileLabel) { t = t.copy(folderTileLabel = it) }
                }
                if (t.folderTileStyle == com.eoinknd16.nexus.look.FolderTileStyle.Physics) {
                    SliderRow("Bounciness", t.folderPhysicsBounce, 0f..1f, { "${(it * 100).roundToInt()}%" }) { t = t.copy(folderPhysicsBounce = it) }
                    ToggleRow("Tilt the phone to move them", t.folderPhysicsTilt) { t = t.copy(folderPhysicsTilt = it) }
                    SliderRow("React to the screen moving", t.folderPhysicsInertia, 0f..3f, { if (it < 0.05f) "Off" else "${(it * 100).roundToInt()}%" }) { t = t.copy(folderPhysicsInertia = it) }
                    SliderRow("Weight", t.folderPhysicsRealism, 0.1f..1f, { if (it >= 0.98f) "Real-life" else if (it < 0.3f) "Floaty" else "${(it * 100).roundToInt()}%" }) { t = t.copy(folderPhysicsRealism = it) }
                    SliderRow("Shaking the phone throws them", t.folderPhysicsShake, 0f..3f, { if (it < 0.05f) "Off" else "${(it * 100).roundToInt()}%" }) { t = t.copy(folderPhysicsShake = it) }
                    SliderRow("Grip on the glass", t.folderPhysicsGrip, 0f..2f, { when { it < 0.1f -> "Ice"; it in 0.9f..1.1f -> "Real"; else -> "${(it * 100).roundToInt()}%" } }) { t = t.copy(folderPhysicsGrip = it) }
                    ToggleRow("Spin when they hit things", t.folderPhysicsSpin) { t = t.copy(folderPhysicsSpin = it) }
                    ToggleRow("Buzz when they knock together", t.folderPhysicsHaptics) { t = t.copy(folderPhysicsHaptics = it) }
                }
                Spacer(Modifier.height(8.dp))
                HudLabel("When it opens")
                ChoiceRow("Opening animation", FolderAnimation.entries, t.folderAnimation, { it.label }) { t = t.copy(folderAnimation = it) }
                ChoiceRow("Layout", FolderStyle.entries, t.folderStyle, { it.label }) { t = t.copy(folderStyle = it) }
                if (t.folderStyle == FolderStyle.Grid) {
                    SliderRow("Columns", t.folderColumns.toFloat(), 2f..6f, { it.roundToInt().toString() }, steps = 3) { t = t.copy(folderColumns = it.roundToInt()) }
                }
                ChoiceRow("Background", com.eoinknd16.nexus.look.FolderBackground.entries, t.folderBackground, { it.label }) { t = t.copy(folderBackground = it) }
                SliderRow(
                    "Blur behind",
                    t.folderBlur, 0f..40f,
                    { if (it < 0.5f) "off" else "${it.roundToInt()}dp" + if (android.os.Build.VERSION.SDK_INT < 31) " (Android 12+)" else "" },
                ) { t = t.copy(folderBlur = it) }
                SliderRow("Darken behind", t.folderDim, 0f..0.9f, { "${(it * 100).roundToInt()}%" }) { t = t.copy(folderDim = it) }
                ChoiceRow("Opens", com.eoinknd16.nexus.look.FolderPosition.entries, t.folderPosition, { it.label }) { t = t.copy(folderPosition = it) }
                SliderRow("Icon size", t.folderIconSize, 32f..96f, { "${it.roundToInt()}dp" }) { t = t.copy(folderIconSize = it) }
                if (t.folderStyle == FolderStyle.Ring) {
                    SliderRow(
                        "Ring size", t.folderRingSize, 0f..600f,
                        { if (it < 100f) "Auto (fits the apps)" else "${it.roundToInt()}dp across" },
                    ) { t = t.copy(folderRingSize = if (it < 100f) 0f else it) }
                    Text(
                        "Auto makes one ring just big enough for all the apps. If it would be wider than the screen, the icons shrink to fit, so they never overlap. Only very full folders get a second ring.",
                        style = MaterialTheme.typography.bodySmall, color = Nx.Muted,
                    )
                }
                ToggleRow("App names", t.folderShowLabels) { t = t.copy(folderShowLabels = it) }
                ToggleRow("Folder title", t.folderShowTitle) { t = t.copy(folderShowTitle = it) }
                ToggleRow("Animate closing too", t.folderAnimateClose) { t = t.copy(folderAnimateClose = it) }
                ToggleRow("Edit button in the open folder", t.folderShowEditButton, "Off keeps the opening clean; long-press the tile to edit") { t = t.copy(folderShowEditButton = it) }
                NexusButton("Use this style for every folder", accent = Nx.Magenta) {
                    val src = t
                    fun TileConfig.styled() = if (action.kind != TileAction.Kind.Folder) this else copy(
                        folderAnimation = src.folderAnimation, folderStyle = src.folderStyle, folderColumns = src.folderColumns,
                        folderPreview = src.folderPreview, folderBackground = src.folderBackground, folderBlur = src.folderBlur,
                        folderDim = src.folderDim, folderPosition = src.folderPosition, folderIconSize = src.folderIconSize, folderRingSize = src.folderRingSize,
                        folderShowLabels = src.folderShowLabels, folderShowTitle = src.folderShowTitle, folderAnimateClose = src.folderAnimateClose,
                        folderShowEditButton = src.folderShowEditButton,
                        folderTileStyle = src.folderTileStyle, folderTileIconScale = src.folderTileIconScale, folderTileLabel = src.folderTileLabel,
                        folderPhysicsBounce = src.folderPhysicsBounce, folderPhysicsTilt = src.folderPhysicsTilt, folderPhysicsInertia = src.folderPhysicsInertia,
                        folderPhysicsRealism = src.folderPhysicsRealism, folderPhysicsShake = src.folderPhysicsShake, folderPhysicsGrip = src.folderPhysicsGrip,
                        folderPhysicsSpin = src.folderPhysicsSpin, folderPhysicsHaptics = src.folderPhysicsHaptics,
                    )
                    c.editLook("Style copied to every folder") { l0 ->
                        val l = l0.mapAllBlocks { list -> list.map { b -> if (b is HomeBlock.Tiles) b.copy(tiles = b.tiles.map { it.styled() }) else b } }
                        l.copy(home = l.home.copy(dock = l.home.dock.copy(items = l.home.dock.items.map { it.styled() })))
                    }
                }
        }
        "Look" -> {
        TextRow("Label", t.label) { t = t.copy(label = it) }
        TextRow("Sub-label", t.sublabel) { t = t.copy(sublabel = it) }
        val slots = listOf("accent" to "Accent", "accent2" to "Second accent", "warn" to "Highlight", "good" to "Success", "danger" to "Danger", "muted" to "Muted", "text" to "Text", "custom" to "Custom colour")
        ChoiceRow(
            "Colour", slots.map { it.first }, if (t.color.startsWith("#")) "custom" else t.color,
            { key -> slots.first { it.first == key }.second },
            preview = { key -> com.eoinknd16.nexus.ui.settings.Swatch(if (key == "custom") LookState.palette.slot(t.color) else LookState.palette.slot(key)) },
        ) { key -> t = t.copy(color = if (key == "custom") (if (t.color.startsWith("#")) t.color else "#3CF2FF") else key) }
        if (t.color.startsWith("#")) ColorRow("Custom colour", t.color) { t = t.copy(color = it) }
        if (!(t.action.kind == TileAction.Kind.LaunchApp && t.useAppIcon)) {
            var pickIcon by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().nexusPress({ pickIcon = !pickIcon }).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Icon", style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f))
                Icon(IconCatalog.icon(t.icon), null, tint = LookState.palette.slot(t.color))
                Spacer(Modifier.width(10.dp))
                Text(hud(if (pickIcon) "Done" else "Change"), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
            }
            if (pickIcon) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    IconCatalog.all.forEach { (key, icon) ->
                        val sel = key == t.icon
                        Box(
                            Modifier.size(42.dp).nexusPress({ t = t.copy(icon = key); pickIcon = false }).clip(nexusShape(0.4f))
                                .background(if (sel) Nx.Cyan.copy(alpha = 0.3f) else Nx.Deep)
                                .border(1.dp, if (sel) Nx.Cyan else Nx.CyanDim, nexusShape(0.4f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, key, tint = if (sel) Nx.Cyan else Nx.Text) }
                    }
                }
            }
        }
        ToggleRow("Lock this tile", t.locked, "Long-press won't open the editor (except in home edit mode)") { t = t.copy(locked = it) }
        val inheritE = "inherit"
        ChoiceRow(
            "Entrance animation", listOf(inheritE) + com.eoinknd16.nexus.look.Entrance.entries.map { it.name },
            t.entrance?.name ?: inheritE, { if (it == inheritE) "Same as everywhere" else com.eoinknd16.nexus.look.Entrance.valueOf(it).label },
        ) { v -> t = t.copy(entrance = if (v == inheritE) null else com.eoinknd16.nexus.look.Entrance.valueOf(v)) }
        TextRow("Background image URL (optional)", t.image ?: "", "https://…/image.jpg", KeyboardType.Uri) { t = t.copy(image = it.ifBlank { null }) }
        }
        "Style" -> TileStyleEditor(t, runner) { t = it }
        "Size" -> if (blockId != null) {
            val cols = (LookState.look.allBlocks.firstOrNull { it.id == blockId } as? HomeBlock.Tiles)?.columns?.coerceIn(1, 6) ?: 4
            HudLabel("Size in the grid")
            SliderRow("Width", t.colSpan.toFloat().coerceAtMost(cols.toFloat()), 1f..cols.toFloat().coerceAtLeast(2f), { "${it.roundToInt()} column" + if (it.roundToInt() > 1) "s" else "" }) { t = t.copy(colSpan = it.roundToInt()) }
            SliderRow("Height", t.rowSpan.toFloat(), 1f..4f, { "${it.roundToInt()} row" + if (it.roundToInt() > 1) "s" else "" }) { t = t.copy(rowSpan = it.roundToInt()) }
            Hint("Other tiles fill in around a bigger one. Icon and text sizes for the whole block are in the block's settings.")
        }
        }

        Spacer(Modifier.height(16.dp))
        // Several PC tiles in this block? Merge them into one "PC controls" tile.
        val pcTiles = (LookState.look.allBlocks.firstOrNull { it.id == blockId } as? HomeBlock.Tiles)?.tiles?.filter { isPcTile(it) }.orEmpty()
        if (existing != null && blockId != null && isPcTile(existing) && pcTiles.size > 1) {
            NexusButton("Combine ${pcTiles.size} PC tiles into one", icon = Icons.Filled.Check, accent = Nx.Lime) {
                c.editLook("PC tiles combined") { l ->
                    l.updateBlock(blockId) { b ->
                        if (b !is HomeBlock.Tiles) b else b.copy(
                            tiles = b.tiles.mapNotNull { x ->
                                when {
                                    x.id == existing.id -> x.copy(label = "PC", sublabel = "Remote · wake · power", icon = "remote", action = TileAction(TileAction.Kind.PcHub))
                                    isPcTile(x) -> null
                                    else -> x
                                }
                            },
                        )
                    }
                }
                onDismiss()
            }
            Spacer(Modifier.height(10.dp))
        }
        if (existing != null) {
            NexusButton("Delete tile", icon = Icons.Filled.Delete, accent = Nx.Danger) {
                c.editLook("“${existing.label.ifBlank { "Tile" }}” deleted") { it.removeTile(blockId, existing.id) }
                onDismiss()
            }
        }
    }
}

private fun defaultIcon(kind: TileAction.Kind, current: String): String = when (kind) {
    TileAction.Kind.WakePc -> "power"
    TileAction.Kind.SwitchPc -> "computer"
    TileAction.Kind.PcPower -> "power"
    TileAction.Kind.PcHub -> "remote"
    TileAction.Kind.PcNav -> "remote"
    TileAction.Kind.PcScreen -> "computer"
    TileAction.Kind.Url -> "web"
    TileAction.Kind.WebSearch -> "search"
    TileAction.Kind.Settings -> "settings"
    TileAction.Kind.Folder -> "apps"
    TileAction.Kind.LockScreen -> "lock"
    TileAction.Kind.QuickSettings -> "settings"
    TileAction.Kind.Recents -> "apps"
    TileAction.Kind.Notifications -> "mail"
    TileAction.Kind.Page -> "home"
    else -> current
}

/** Auto-fill the label from the chosen target unless the user already typed their own. */
private fun relabel(t: TileConfig, suggestion: String): String =
    if (t.label.isBlank() || t.label == "New tile" || t.label == "App" || allTargetLabels.contains(t.label)) suggestion else t.label

private val allTargetLabels = (SCREEN_TARGETS + PC_NAV_TARGETS + PC_SCREEN_TARGETS + SETTINGS_TARGETS).map { it.second }.toSet()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetChips(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String, String) -> Unit) {
    ChoiceRow(title, options, options.firstOrNull { it.first == selected } ?: options.first(), { it.second }) { onPick(it.first, it.second) }
}

// ---- App picker ------------------------------------------------------------------

/** Searchable app list. Single: tapping picks. Multi: tapping toggles, order kept. */
@Composable
fun AppPicker(selected: List<String>, multi: Boolean, onChange: (List<String>) -> Unit) {
    val apps by appContainer().apps.apps.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    TextRow("Find app", filter, "Type to filter") { filter = it }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        items(apps.filter { filter.isBlank() || it.label.contains(filter, true) }, key = { it.key }) { app ->
            val on = app.key in selected
            Row(
                Modifier.fillMaxWidth().nexusPress({
                    onChange(
                        if (!multi) listOf(app.key)
                        else if (on) selected - app.key else selected + app.key,
                    )
                }).background(if (on) Nx.Cyan.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(app.icon, null, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (on) Icon(Icons.Filled.Check, null, tint = Nx.Cyan)
            }
        }
    }
}

// ---- Add block / dock ------------------------------------------------------------

@Composable
private fun AddBlockSheet(onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    val c = appContainer()
    EditorSheet("Add to home", onDismiss) {
        addableBlocks.forEach { (label, make) ->
            Text(
                label, style = MaterialTheme.typography.titleMedium, color = Nx.Text,
                modifier = Modifier.fillMaxWidth().nexusPress({
                    val block = make()
                    c.editLook { it.addBlock(block) }
                    onEdit(HomeEdit.Block(block.id))
                }).padding(vertical = 14.dp),
            )
        }
        Text(
            "Android widget (Google search bar, calendar, music…)", style = MaterialTheme.typography.titleMedium, color = Nx.Magenta,
            modifier = Modifier.fillMaxWidth().nexusPress({ onEdit(HomeEdit.AddWidget) }).padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun DockEditor(onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    val c = appContainer()
    val d = LookState.look.home.dock
    fun set(transform: (com.eoinknd16.nexus.look.DockConfig) -> com.eoinknd16.nexus.look.DockConfig) = c.editLook { it.updateDock(transform) }
    EditorSheet("Tray", onDismiss, showUndo = true) {
        ToggleRow("Show the tray", d.visible) { v -> set { it.copy(visible = v) } }
        ToggleRow("App drawer button", d.showDrawerButton) { v -> set { it.copy(showDrawerButton = v) } }
        ToggleRow("Labels under icons", d.showLabels) { v -> set { it.copy(showLabels = v) } }
        ToggleRow("Panel behind it", d.framed) { v -> set { it.copy(framed = v) } }
        ChoiceRow("Line up apps", com.eoinknd16.nexus.look.DockArrange.entries, d.arrange, { it.label }) { v -> set { it.copy(arrange = v) } }
        ToggleRow("Equal-width slots", d.equalSlots, "Every icon gets the same space so they line up evenly") { v -> set { it.copy(equalSlots = v) } }
        ToggleRow("Centre on the whole tray", d.centerOnTray, "Ignore the drawer button when centring") { v -> set { it.copy(centerOnTray = v) } }
        SliderRow("Gap between apps", d.spacingDp, 0f..48f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(spacingDp = v) } }
        ToggleRow("Scroll when full", d.scrollable, "Off squeezes every app into one row") { v -> set { it.copy(scrollable = v) } }
        SliderRow("Icon size", d.iconSizeDp, 28f..80f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(iconSizeDp = v) } }
        HudLabel("Slots")
        d.items.forEachIndexed { i, t ->
            ListRow(
                t.label, t.action.kind.label,
                onClick = { onEdit(HomeEdit.Tile(null, t)) },
                onUp = { set { it.copy(items = it.items.moved(i, -1)) } },
                onDown = { set { it.copy(items = it.items.moved(i, 1)) } },
                onDelete = { c.editLook { it.removeTile(null, t.id) } },
            )
        }
        Spacer(Modifier.height(8.dp))
        NexusButton("Add slot", filled = true) { onEdit(HomeEdit.Tile(null, null)) }
    }
}

// ---- Android widget picker -------------------------------------------------------

@Composable
private fun WidgetPickerSheet(onDismiss: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val providers = remember { c.widgets.providers() }
    var filter by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    fun place(id: Int, info: AppWidgetProviderInfo) {
        val density = context.resources.displayMetrics.density
        val minH = (info.minHeight / density).coerceIn(60f, 400f)
        val label = info.loadLabel(context.packageManager)
        c.editLook { it.addBlock(HomeBlock.Widget(appWidgetId = id, label = label, heightDp = minH + 16f)) }
        onDismiss()
    }

    fun configureThenPlace(id: Int, info: AppWidgetProviderInfo) {
        if (activity != null && c.widgets.needsConfigure(id)) {
            c.widgets.startConfigure(activity, id) { ok -> if (ok) place(id, info) else c.widgets.delete(id) }
        } else place(id, info)
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val (id, info) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (r.resultCode == android.app.Activity.RESULT_OK) configureThenPlace(id, info) else c.widgets.delete(id)
    }

    EditorSheet("Add a widget", onDismiss) {
        TextRow("Find widget", filter, "e.g. Google, Calendar") { filter = it }
        val shown = providers.filter {
            filter.isBlank() || c.widgets.appLabel(it).contains(filter, true) || it.loadLabel(context.packageManager).contains(filter, true)
        }
        if (shown.isEmpty()) Text("No widgets match.", color = Nx.Muted)
        shown.groupBy { c.widgets.appLabel(it) }.forEach { (app, list) ->
            Text(hud(app), style = MaterialTheme.typography.labelMedium, color = Nx.Cyan, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.Bold)
            list.forEach { info ->
                val preview = remember(info) {
                    runCatching {
                        (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.toBitmap(160, 160)?.asImageBitmap()
                    }.getOrNull()
                }
                Row(
                    Modifier.fillMaxWidth().nexusPress({
                        val id = c.widgets.allocate()
                        if (c.widgets.bindIfAllowed(id, info)) {
                            configureThenPlace(id, info)
                        } else {
                            pending = id to info
                            bindLauncher.launch(c.widgets.bindIntent(id, info))
                        }
                    }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).clip(nexusShape(0.4f)).background(Nx.Deep), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it, null, Modifier.size(56.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.loadLabel(context.packageManager), style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                        val density = context.resources.displayMetrics.density
                        Text(hud("${(info.minWidth / density).roundToInt()} × ${(info.minHeight / density).roundToInt()} dp"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                    }
                }
            }
        }
    }
}


@Composable
private fun PagesEditor(onDismiss: () -> Unit) {
    val c = appContainer()
    val home = LookState.look.home
    val pager = com.eoinknd16.nexus.look.HomePager
    EditorSheet("Home pages", onDismiss, showUndo = true) {
        Text(
            "Extra pages start blank. Swipe sideways to move between pages, like any launcher; Back or Home returns to the main page. Your left/right swipe gestures still work where there's no page to swipe to.",
            style = MaterialTheme.typography.bodySmall, color = Nx.Muted,
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow("Page dots above the tray", home.showPageDots) { v -> c.editLook { it.copy(home = it.home.copy(showPageDots = v)) } }
        ChoiceRow(
            "Extra pages sit", listOf("auto", "left", "right"), home.pagesSide,
            { when (it) { "left" -> "Left of main"; "right" -> "Right of main"; else -> "Auto" } },
            subtitle = "Auto: on the left if your swipe-right gesture opens a page, otherwise on the right",
        ) { v -> c.editLook { it.copy(home = it.home.copy(pagesSide = v)) } }
        HudLabel("Pages")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).holoPanel(if (pager.current == null) Nx.Cyan else Nx.CyanDim, glow = 0f).nexusPress({ pager.go("main"); onDismiss() }).padding(14.dp)) {
            Text("Main page", style = MaterialTheme.typography.titleMedium, color = Nx.Text, modifier = Modifier.weight(1f))
            Text(hud(if (pager.current == null) "On screen" else "Tap to go"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
        home.pages.forEachIndexed { i, page ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).holoPanel(if (pager.current == page.id) Nx.Cyan else Nx.CyanDim, glow = 0f).padding(10.dp)) {
                TextRow("Name", page.name) { v -> c.editLook { it.updatePage(page.id) { p -> p.copy(name = v) } } }
                ToggleRow("Show the tray on this page", page.showDock) { v -> c.editLook { it.updatePage(page.id) { p -> p.copy(showDock = v) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NexusButton("Go to", icon = Icons.Filled.Check) { pager.go(page.id); onDismiss() }
                    if (i > 0) EditIcon(Icons.Filled.KeyboardArrowUp, "Move earlier", { c.editLook { it.movePage(page.id, -1) } })
                    if (i < home.pages.lastIndex) EditIcon(Icons.Filled.KeyboardArrowDown, "Move later", { c.editLook { it.movePage(page.id, 1) } })
                    Spacer(Modifier.weight(1f))
                    EditIcon(Icons.Filled.Delete, "Delete page", {
                        if (pager.current == page.id) pager.go("main")
                        page.blocks.filterIsInstance<HomeBlock.Widget>().forEach { c.widgets.delete(it.appWidgetId) }
                        c.editLook("Page “${page.name}” deleted") { it.removePage(page.id) }
                    }, Nx.Danger)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NexusButton("New blank page", icon = Icons.Filled.Add, filled = true) {
            val page = com.eoinknd16.nexus.look.HomePage(name = "Page ${home.pages.size + 2}")
            c.editLook { it.addPage(page) }
            pager.go(page.id)
            onDismiss()
        }
    }
}


/** Width / alignment / scale for any block. */
@Composable
private fun BlockSizeSection(blockId: String) {
    val c = appContainer()
    val size = LookState.look.home.blockSizes[blockId] ?: com.eoinknd16.nexus.look.BlockSize()
    fun set(v: com.eoinknd16.nexus.look.BlockSize) = c.editLook { l ->
        l.copy(home = l.home.copy(blockSizes = l.home.blockSizes + (blockId to v)))
    }
    com.eoinknd16.nexus.ui.settings.Section(
        "Size & position",
        if (size == com.eoinknd16.nexus.look.BlockSize()) "Full width · 100%" else "${size.widthPct.roundToInt()}% wide · ${(size.scale * 100).roundToInt()}% scale",
    ) {
    SliderRow("Width", size.widthPct, 20f..100f, { "${it.roundToInt()}% of screen" }) { set(size.copy(widthPct = it)) }
    if (size.widthPct < 99.5f || kotlin.math.abs(size.scale - 1f) > 0.01f) {
        ChoiceRow("Sit", com.eoinknd16.nexus.look.Align.entries, size.align, { when (it) { com.eoinknd16.nexus.look.Align.Start -> "Left"; com.eoinknd16.nexus.look.Align.Center -> "Centre"; else -> "Right" } }) { set(size.copy(align = it)) }
    }
    SliderRow("Scale", size.scale, 0.4f..2f, { "${(it * 100).roundToInt()}%" }) { set(size.copy(scale = it)) }
    if (size != com.eoinknd16.nexus.look.BlockSize()) NexusButton("Reset size", accent = Nx.Muted) { set(com.eoinknd16.nexus.look.BlockSize()) }
    }
    Spacer(Modifier.height(4.dp))
}


/** Headings for the "What it does" list. */
private fun actionGroup(k: TileAction.Kind): String = when (k) {
    TileAction.Kind.LaunchApp, TileAction.Kind.Folder -> "Apps"
    TileAction.Kind.Screen, TileAction.Kind.Page -> "Nexus"
    TileAction.Kind.PcHub, TileAction.Kind.WakePc, TileAction.Kind.SwitchPc, TileAction.Kind.PcPower,
    TileAction.Kind.PcNav, TileAction.Kind.PcScreen -> "PC"
    TileAction.Kind.Settings, TileAction.Kind.Notifications, TileAction.Kind.QuickSettings,
    TileAction.Kind.LockScreen, TileAction.Kind.Recents -> "Phone"
    TileAction.Kind.Url, TileAction.Kind.WebSearch -> "Web"
    else -> "Other"
}


data class SystemBarPadding(val top: androidx.compose.ui.unit.Dp, val bottom: androidx.compose.ui.unit.Dp)

/** Status bar and navigation bar (gesture pill or 3 buttons) heights, from the main window. */
@Composable
fun rememberSystemBarPadding(): SystemBarPadding {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val top = WindowInsets.statusBars.getTop(d)
    val bottom = WindowInsets.navigationBars.getBottom(d)
    val view = androidx.compose.ui.platform.LocalView.current
    // Fallback if Compose hasn't seen insets yet: ask the window directly.
    val raw = androidx.core.view.ViewCompat.getRootWindowInsets(view)
    val sysTop = raw?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val sysBottom = raw?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    return with(d) { SystemBarPadding(maxOf(top, sysTop).toDp(), maxOf(bottom, sysBottom).toDp()) }
}
