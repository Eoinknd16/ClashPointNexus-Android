package com.eoinknd16.nexus.ui.home

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
import androidx.compose.foundation.layout.fillMaxWidth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Nx.PanelSolid,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            HudLabel(title)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HomeEditorHost(edit: HomeEdit, onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    when (edit) {
        is HomeEdit.Block -> LookState.look.home.blocks.firstOrNull { it.id == edit.id }?.let { BlockEditor(it, onDismiss, onEdit) }
        is HomeEdit.Tile -> TileEditor(edit.blockId, edit.tile, onDismiss)
        HomeEdit.AddBlock -> AddBlockSheet(onDismiss, onEdit)
        HomeEdit.AddWidget -> WidgetPickerSheet(onDismiss)
        HomeEdit.Dock -> DockEditor(onDismiss, onEdit)
    }
}

// ---- Block editors -------------------------------------------------------------

@Composable
private fun BlockEditor(block: HomeBlock, onDismiss: () -> Unit, onEdit: (HomeEdit) -> Unit) {
    val c = appContainer()
    fun set(b: HomeBlock) = c.editLook { it.updateBlock(block.id) { b } }
    EditorSheet(block.kindLabel, onDismiss) {
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
                Text(
                    "Want Google's own search bar? Add it as a widget: edit mode › Add widget › Google.",
                    style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                )
            }
            is HomeBlock.Tiles -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                SliderRow("Columns", block.columns.toFloat(), 1f..6f, { it.roundToInt().toString() }, steps = 4) { set(block.copy(columns = it.roundToInt())) }
                SliderRow("Tile shape (width ÷ height)", block.aspect, 0.5f..3f, { "%.2f".format(it) }) { set(block.copy(aspect = it)) }
                ToggleRow("Show sub-labels", block.showSublabels) { set(block.copy(showSublabels = it)) }
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
            is HomeBlock.Resume -> TextRow("Heading", block.title) { set(block.copy(title = it)) }
            is HomeBlock.Posters -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                ChoiceRow("Show", PosterSource.entries, block.source, { it.label }) { set(block.copy(source = it)) }
                SliderRow("How many", block.count.toFloat(), 3f..40f, { it.roundToInt().toString() }) { set(block.copy(count = it.roundToInt())) }
            }
            is HomeBlock.AppRow -> {
                TextRow("Heading", block.title) { set(block.copy(title = it)) }
                SliderRow("Icon size", block.iconSizeDp, 32f..80f, { "${it.roundToInt()}dp" }) { set(block.copy(iconSizeDp = it)) }
                ToggleRow("Labels", block.showLabels) { set(block.copy(showLabels = it)) }
                HudLabel("Apps")
                AppPicker(selected = block.apps, multi = true) { set(block.copy(apps = it)) }
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

    EditorSheet(if (existing == null) (if (blockId == null) "Add to tray" else "New tile") else "Edit · ${existing.label}", onDismiss) {
        ChoiceRow("What it does", TileAction.Kind.entries, t.action.kind, { it.label }) { k ->
            val arg = when (k) {
                TileAction.Kind.Screen -> SCREEN_TARGETS.first().first
                TileAction.Kind.PcNav -> PC_NAV_TARGETS.first().first
                TileAction.Kind.PcScreen -> PC_SCREEN_TARGETS.first().first
                TileAction.Kind.Settings -> SETTINGS_TARGETS.first().first
                else -> ""
            }
            t = t.copy(action = TileAction(k, arg), icon = defaultIcon(k, t.icon))
        }

        when (t.action.kind) {
            TileAction.Kind.Screen -> TargetChips("Screen", SCREEN_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.PcNav -> TargetChips("Button", PC_NAV_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.PcScreen -> TargetChips("PC screen", PC_SCREEN_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
            TileAction.Kind.Settings -> TargetChips("Setting", SETTINGS_TARGETS, t.action.arg) { key, label -> t = t.copy(action = t.action.copy(arg = key), label = relabel(t, label)) }
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
                ChoiceRow("Opening animation", FolderAnimation.entries, t.folderAnimation, { it.label }) { t = t.copy(folderAnimation = it) }
                ChoiceRow("Layout", FolderStyle.entries, t.folderStyle, { it.label }) { t = t.copy(folderStyle = it) }
                if (t.folderStyle == FolderStyle.Grid) {
                    SliderRow("Columns", t.folderColumns.toFloat(), 2f..6f, { it.roundToInt().toString() }, steps = 3) { t = t.copy(folderColumns = it.roundToInt()) }
                }
                ToggleRow("Show app previews on the tile", t.folderPreview) { t = t.copy(folderPreview = it) }
            }
            else -> {}
        }

        Spacer(Modifier.height(8.dp))
        HudLabel("Look")
        TextRow("Label", t.label) { t = t.copy(label = it) }
        TextRow("Sub-label", t.sublabel) { t = t.copy(sublabel = it) }
        ChoiceRow("Colour", listOf("accent", "accent2", "warn", "good", "danger", "muted", "text"), if (t.color.startsWith("#")) "" else t.color, { it }) { t = t.copy(color = it) }
        ColorRow("Custom colour", if (t.color.startsWith("#")) t.color else "#3CF2FF") { t = t.copy(color = it) }
        if (!(t.action.kind == TileAction.Kind.LaunchApp && t.useAppIcon)) {
            Text("Icon", style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                IconCatalog.all.forEach { (key, icon) ->
                    val sel = key == t.icon
                    Box(
                        Modifier.size(42.dp).nexusPress({ t = t.copy(icon = key) }).clip(nexusShape(0.4f))
                            .background(if (sel) Nx.Cyan.copy(alpha = 0.3f) else Nx.Deep)
                            .border(1.dp, if (sel) Nx.Cyan else Nx.CyanDim, nexusShape(0.4f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, key, tint = if (sel) Nx.Cyan else Nx.Text) }
                }
            }
        }
        TextRow("Background image URL (optional)", t.image ?: "", "https://…/image.jpg", KeyboardType.Uri) { t = t.copy(image = it.ifBlank { null }) }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton("Save", icon = Icons.Filled.Check, filled = true) { save() }
            if (existing != null) NexusButton("Delete", icon = Icons.Filled.Delete, accent = Nx.Danger) {
                c.editLook { it.removeTile(blockId, existing.id) }
                onDismiss()
            }
        }
    }
}

private fun defaultIcon(kind: TileAction.Kind, current: String): String = when (kind) {
    TileAction.Kind.WakePc -> "power"
    TileAction.Kind.PcNav -> "remote"
    TileAction.Kind.PcScreen -> "computer"
    TileAction.Kind.Url -> "web"
    TileAction.Kind.WebSearch -> "search"
    TileAction.Kind.Settings -> "settings"
    TileAction.Kind.Folder -> "apps"
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
    EditorSheet("Tray", onDismiss) {
        ToggleRow("Show the tray", d.visible) { v -> set { it.copy(visible = v) } }
        ToggleRow("App drawer button", d.showDrawerButton) { v -> set { it.copy(showDrawerButton = v) } }
        ToggleRow("Labels under icons", d.showLabels) { v -> set { it.copy(showLabels = v) } }
        ToggleRow("Panel behind it", d.framed) { v -> set { it.copy(framed = v) } }
        ToggleRow("Scroll when full (off = spread evenly)", d.scrollable) { v -> set { it.copy(scrollable = v) } }
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
