package com.eoinknd16.nexus.ui.home

import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Close

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.material.icons.filled.SkipPrevious

import androidx.compose.material.icons.filled.SkipNext

import androidx.compose.material.icons.filled.Pause

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.look.Align
import com.eoinknd16.nexus.look.DockConfig
import com.eoinknd16.nexus.look.DockArrange
import com.eoinknd16.nexus.look.HomeBlock
import com.eoinknd16.nexus.look.IconCatalog
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.PosterSource
import com.eoinknd16.nexus.look.TempUnit
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.look.family
import com.eoinknd16.nexus.look.moveTile
import com.eoinknd16.nexus.look.removeTile
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import com.eoinknd16.nexus.ui.tv.PosterCard
import com.eoinknd16.nexus.weather.WeatherNow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlockContent(
    block: HomeBlock,
    vm: HomeViewModel,
    runner: TileRunner,
    editMode: Boolean,
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onEditTile: (TileConfig) -> Unit,
    onAddTile: () -> Unit,
) {
    val headings = LookState.look.home.showHeadings
    when (block) {
        is HomeBlock.Clock -> ClockView(block, vm)
        is HomeBlock.Weather -> WeatherPanel(block, vm)
        is HomeBlock.SearchBar -> SearchBarView(block, runner)
        is HomeBlock.Tiles -> Column {
            if (headings && block.title.isNotBlank()) {
                HudLabel(block.title)
                Spacer(Modifier.height(12.dp))
            }
            TilesGrid(block, runner, editMode, onOpenFolder, onEditTile, onAddTile)
        }
        is HomeBlock.Resume -> ResumeView(block, vm, headings, editMode)
        is HomeBlock.Posters -> PostersView(block, vm, headings, editMode)
        is HomeBlock.AppRow -> AppRowView(block, runner, headings, editMode)
        is HomeBlock.Widget -> WidgetView(block)
        is HomeBlock.Text -> Text(
            block.text,
            fontSize = block.sizeSp.sp,
            color = if (block.accent) Nx.Cyan else Nx.Text,
            fontFamily = LookState.look.fonts.heading.family(),
            textAlign = block.align.textAlign(),
            modifier = Modifier.fillMaxWidth(),
        )
        is HomeBlock.Spacer -> Spacer(Modifier.height(block.heightDp.dp))
    }
}

private fun Align.textAlign() = when (this) {
    Align.Start -> TextAlign.Start
    Align.Center -> TextAlign.Center
    Align.End -> TextAlign.End
}

private fun Align.horizontal() = when (this) {
    Align.Start -> Alignment.Start
    Align.Center -> Alignment.CenterHorizontally
    Align.End -> Alignment.End
}

// ---- Clock ---------------------------------------------------------------------

@Composable
private fun ClockView(b: HomeBlock.Clock, vm: HomeViewModel) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(b.showSeconds) {
        while (true) {
            now = Date()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val animate = LookState.look.motion.animations && b.blinkColon
    val blink = if (animate) {
        rememberInfiniteTransition(label = "blink").animateFloat(
            1f, 0.2f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "colon",
        ).value
    } else 1f
    val pattern = buildString {
        append(if (b.use24h) "HH mm" else "h mm")
        if (b.showSeconds) append(" ss")
    }
    val parts = SimpleDateFormat(pattern, Locale.getDefault()).format(now).split(" ")
    val clockFont = LookState.look.fonts.clock.family()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = b.align.horizontal()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            parts.forEachIndexed { i, part ->
                if (i > 0) Text(":", fontSize = (b.sizeSp * 0.9f).sp, fontFamily = clockFont, color = Nx.Cyan, modifier = Modifier.alpha(blink))
                Text(part, fontSize = (if (i == 2) b.sizeSp * 0.45f else b.sizeSp).sp, fontFamily = clockFont, color = Nx.Text, letterSpacing = 4.sp)
            }
            if (!b.use24h) {
                Spacer(Modifier.width(8.dp))
                Text(SimpleDateFormat("a", Locale.getDefault()).format(now), style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
            }
        }
        if (b.showDate) {
            val date = runCatching { SimpleDateFormat(b.dateFormat, Locale.getDefault()).format(now) }.getOrDefault("")
            Text(hud(date), style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
        }
        if (b.showWeather && LookState.look.weather.enabled) {
            weather?.let { w ->
                Spacer(Modifier.height(6.dp))
                WeatherLine(w)
            }
        }
        if (b.showPcStatus) {
            Spacer(Modifier.height(10.dp))
            StatusChip(state.pc)
        }
    }
}

@Composable
private fun WeatherLine(w: WeatherNow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(weatherIcon(w.code, w.isDay), null, tint = Nx.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            hud("${w.temp}°${w.unit.name} · ${w.summary}${if (w.place.isNotBlank()) " · ${w.place}" else ""}"),
            style = MaterialTheme.typography.labelMedium, color = Nx.Text,
        )
    }
}

fun weatherIcon(code: Int, isDay: Boolean = true): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2 -> Icons.Filled.WbCloudy
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Dehaze
    in 51..67, in 80..82 -> Icons.Filled.Grain
    in 71..77, 85, 86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

@Composable
private fun StatusChip(pc: PcState) {
    val color = when (pc.kind) {
        PcState.Kind.Online -> Nx.Lime
        PcState.Kind.Offline -> Nx.Danger
        PcState.Kind.Checking -> Nx.Amber
        PcState.Kind.Unpaired -> Nx.Muted
    }
    Row(
        Modifier.clip(nexusShape(0.45f)).background(color.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(hud(pc.label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ---- Weather panel -------------------------------------------------------------

@Composable
private fun WeatherPanel(b: HomeBlock.Weather, vm: HomeViewModel) {
    val weather by vm.weather.collectAsStateWithLifecycle()
    val error by vm.weatherError.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val w = weather
    Column(Modifier.fillMaxWidth().nexusPress({ vm.refreshWeather(force = true) }).holoPanel(Nx.Amber, 0.2f).padding(14.dp)) {
        if (w == null) {
            Text(error ?: "Loading weather…", style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(hud("Tap to set a location"), style = MaterialTheme.typography.labelSmall, color = Nx.Amber,
                    modifier = Modifier.nexusPress({ nav.navigate(Routes.CUSTOMIZE) }))
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(w.code, w.isDay), null, tint = Nx.Amber, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${w.temp}°", fontSize = 36.sp, fontWeight = FontWeight.Light, color = Nx.Text)
                Text(hud(listOf(w.summary, w.place).filter { it.isNotBlank() }.joinToString(" · ")), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
            }
            if (b.showDetails) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(hud("Feels ${w.feelsLike}°"), style = MaterialTheme.typography.labelSmall, color = Nx.Text)
                    w.humidity?.let { Text(hud("Humidity $it%"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted) }
                    w.windKph?.let { Text(hud("Wind $it km/h"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted) }
                }
            }
        }
        if (b.forecastDays > 0 && w.days.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                w.days.drop(1).take(b.forecastDays).forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val day = runCatching {
                            SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!)
                        }.getOrDefault(d.date)
                        Text(hud(day), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                        Icon(weatherIcon(d.code), null, tint = Nx.Cyan, modifier = Modifier.size(20.dp))
                        Text("${d.max}° ${d.min}°", style = MaterialTheme.typography.labelSmall, color = Nx.Text)
                    }
                }
            }
        }
    }
}

// ---- Search bar ----------------------------------------------------------------

@Composable
private fun SearchBarView(b: HomeBlock.SearchBar, runner: TileRunner) {
    val c = appContainer()
    val apps by c.apps.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                runner.webSearch(spoken, b.engine, b.customUrl)
            }
        }
    }
    val shape = nexusShape(0.7f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Nx.Panel).border(1.dp, Nx.CyanDim, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Nx.Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(b.placeholder, color = Nx.Muted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Nx.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(Nx.Cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        runner.webSearch(query, b.engine, b.customUrl)
                        query = ""
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (b.showMic) {
                Box(
                    Modifier.size(32.dp).nexusPress({
                        runCatching {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                            )
                        }
                    }),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Mic, "Voice search", tint = Nx.Cyan) }
            }
        }
        if (b.matchApps && query.isNotBlank()) {
            val matches = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }.take(8)
            if (matches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(matches, key = { it.key }) { app ->
                        Column(
                            Modifier.width(64.dp).nexusPress({
                                c.apps.launch(app)
                                query = ""
                            }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(app.icon, app.label, Modifier.size(44.dp))
                            Text(app.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---- Tiles ---------------------------------------------------------------------

@Composable
private fun TilesGrid(
    b: HomeBlock.Tiles,
    runner: TileRunner,
    editMode: Boolean,
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onEditTile: (TileConfig) -> Unit,
    onAddTile: () -> Unit,
) {
    val cols = b.columns.coerceIn(1, 6)
    val cells: List<TileConfig?> = b.tiles + if (editMode || b.tiles.isEmpty()) listOf(null) else emptyList()
    val spans = cells.map { t -> (t?.colSpan ?: 1).coerceIn(1, cols) to (t?.rowSpan ?: 1).coerceIn(1, 4) }
    val gap = b.gapDp.coerceIn(0f, 40f).dp
    val style = TileStyle(b.iconSizeDp.dp, b.labelScale, b.paddingDp.dp, b.tileLayout)
    androidx.compose.ui.layout.Layout(
        content = {
            cells.forEachIndexed { i, tile ->
                if (tile == null) AddCell(Modifier, onAddTile)
                else TileView(
                    tile = tile, runner = runner, showSublabel = b.showSublabels, index = i, modifier = Modifier,
                    onOpenFolder = onOpenFolder, onLongPress = { onEditTile(tile) }, style = style,
                )
            }
        },
    ) { measurables, constraints ->
        // Pack tiles into the grid in order, each taking colSpan × rowSpan cells
        // at the first spot it fits (like a home-screen grid).
        val g = gap.roundToPx()
        val w = constraints.maxWidth
        val cellW = ((w - g * (cols - 1)) / cols).coerceAtLeast(1)
        val cellH = if (b.rowHeightDp > 0f) b.rowHeightDp.dp.roundToPx() else (cellW / b.aspect.coerceIn(0.4f, 3f)).toInt()
        val taken = mutableListOf<BooleanArray>()
        fun free(r: Int, c: Int, cs: Int, rs: Int): Boolean {
            if (c + cs > cols) return false
            for (rr in r until r + rs) for (cc in c until c + cs) if (rr < taken.size && taken[rr][cc]) return false
            return true
        }
        val spots = spans.map { (cs, rs) ->
            var r = 0
            var spot: Pair<Int, Int>? = null
            while (spot == null) {
                for (c in 0..cols - cs) if (free(r, c, cs, rs)) { spot = r to c; break }
                if (spot == null) r++
            }
            val (sr, sc) = spot
            while (taken.size < sr + rs) taken.add(BooleanArray(cols))
            for (rr in sr until sr + rs) for (cc in sc until sc + cs) taken[rr][cc] = true
            spot
        }
        val placeables = measurables.mapIndexed { i, m ->
            val (cs, rs) = spans[i]
            val pw = cellW * cs + g * (cs - 1)
            val ph = cellH * rs + g * (rs - 1)
            m.measure(androidx.compose.ui.unit.Constraints.fixed(pw, ph))
        }
        val rows = taken.size
        val height = if (rows == 0) 0 else cellH * rows + g * (rows - 1)
        layout(w, height) {
            placeables.forEachIndexed { i, p ->
                val (r, c) = spots[i]
                p.place(c * (cellW + g), r * (cellH + g))
            }
        }
    }
}

/** How a Tiles block draws its tiles (from the block's settings). */
data class TileStyle(
    val iconSize: androidx.compose.ui.unit.Dp = 40.dp,
    val labelScale: Float = 1f,
    val padding: androidx.compose.ui.unit.Dp = 14.dp,
    val layout: com.eoinknd16.nexus.look.TileLayout = com.eoinknd16.nexus.look.TileLayout.Stack,
)

@Composable
private fun AddCell(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.nexusPress(onClick).clip(nexusShape()).border(1.dp, Nx.CyanDim, nexusShape()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, null, tint = Nx.Cyan)
            Text(hud("Add tile"), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
        }
    }
}

@Composable
fun TileView(
    tile: TileConfig,
    runner: TileRunner,
    showSublabel: Boolean,
    modifier: Modifier,
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onLongPress: () -> Unit,
    index: Int = 0,
    style: TileStyle = TileStyle(),
) {
    val accent = LookState.palette.slot(tile.color)
    var center by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val entrance = entranceModifier(tile.entrance ?: LookState.look.motion.tileEntrance, index)
    val motion = LookState.look.motion
    val pulse = if (motion.pulse && motion.animations) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.25f, 0.55f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "glow",
        ).value
    } else 0.35f
    Box(
        modifier
            .then(entrance)
            .onGloballyPositioned {
                bounds = it.boundsInRoot()
                center = it.boundsInRoot().center
            }
            .nexusPress({ if (tile.isFolder()) onOpenFolder(tile, center) else runner.run(tile, bounds) }, onLongPress)
            .holoPanel(accent, pulse, tile.border),
    ) {
        tile.image?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.45f))
        }
        val titleStyle = MaterialTheme.typography.titleMedium.let { it.copy(fontSize = it.fontSize * style.labelScale.coerceIn(0.5f, 2.5f)) }
        val subStyle = MaterialTheme.typography.labelSmall.let { it.copy(fontSize = it.fontSize * style.labelScale.coerceIn(0.5f, 2.5f)) }
        val texts: @Composable (Alignment.Horizontal) -> Unit = { h ->
            Column(horizontalAlignment = h) {
                Text(tile.label, style = titleStyle, color = Nx.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showSublabel && tile.sublabel.isNotBlank()) {
                    Text(hud(tile.sublabel), style = subStyle, color = Nx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val pad = Modifier.matchParentSize().padding(style.padding.coerceIn(0.dp, 40.dp))
        when (style.layout) {
            com.eoinknd16.nexus.look.TileLayout.Stack -> Column(pad) {
                TileIcon(tile, runner, accent, style.iconSize)
                Spacer(Modifier.weight(1f))
                texts(Alignment.Start)
            }
            com.eoinknd16.nexus.look.TileLayout.Centered -> Column(pad, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                TileIcon(tile, runner, accent, style.iconSize)
                Spacer(Modifier.height(8.dp))
                texts(Alignment.CenterHorizontally)
            }
            com.eoinknd16.nexus.look.TileLayout.Inline -> Row(pad, verticalAlignment = Alignment.CenterVertically) {
                TileIcon(tile, runner, accent, style.iconSize)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { texts(Alignment.Start) }
            }
            com.eoinknd16.nexus.look.TileLayout.IconOnly -> Box(pad, contentAlignment = Alignment.Center) {
                TileIcon(tile, runner, accent, style.iconSize)
            }
        }
    }
}

/** The icon slot of a tile/dock item: app icon, folder preview, or a catalog icon. */
@Composable
fun TileIcon(tile: TileConfig, runner: TileRunner, accent: Color, size: androidx.compose.ui.unit.Dp) {
    val apps by appContainer().apps.apps.collectAsStateWithLifecycle()
    val shape = nexusShape(0.45f)
    when {
        tile.isFolder() && tile.folderPreview -> {
            val icons = tile.folderApps.mapNotNull { k -> apps.firstOrNull { it.key == k }?.icon }.take(4)
            Box(Modifier.size(size).clip(shape).background(accent.copy(alpha = 0.18f)).padding(3.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    icons.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            pair.forEach { Image(it, null, Modifier.size((size - 8.dp) / 2)) }
                        }
                    }
                }
                if (icons.isEmpty()) Icon(IconCatalog.icon(tile.icon), null, tint = accent, modifier = Modifier.align(Alignment.Center))
            }
        }
        // Look the icon up in the collected list (not a one-off snapshot) so
        // tray/tile icons appear as soon as the app list finishes loading after
        // an update or cold start.
        tile.action.kind == com.eoinknd16.nexus.look.TileAction.Kind.LaunchApp && tile.useAppIcon &&
            apps.firstOrNull { it.key == tile.action.arg }?.icon != null ->
            Image(apps.first { it.key == tile.action.arg }.icon!!, null, Modifier.size(size))
        else -> Box(
            Modifier.size(size).clip(shape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(IconCatalog.icon(tile.icon), null, tint = accent, modifier = Modifier.size(size * 0.6f)) }
    }
}

// ---- Resume / posters / apps ---------------------------------------------------

/** One entry in the Resume block: a Nexus TV title or another app's media. */
private sealed interface ResumeEntry {
    val time: Long
    data class Nexus(val p: WatchProgress) : ResumeEntry { override val time get() = p.updatedAt }
    data class App(val m: com.eoinknd16.nexus.media.ExternalMediaItem) : ResumeEntry {
        // Something playing right now always goes first.
        override val time get() = if (m.playing) Long.MAX_VALUE else m.updatedAt
    }
}

@Composable
private fun ResumeView(block: HomeBlock.Resume, vm: HomeViewModel, headings: Boolean, editMode: Boolean) {
    val c = appContainer()
    val nav = LocalNav.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val nexusItems by vm.continueWatching.collectAsStateWithLifecycle()
    val appItems by c.externalMedia.items.collectAsStateWithLifecycle()
    val hasAccess by c.externalMedia.hasAccess.collectAsStateWithLifecycle()
    val hiddenNexus by c.resumeHidden.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        c.externalMedia.refresh()
        onPauseOrDispose { }
    }
    val cutoff = if (block.maxAgeHours > 0) System.currentTimeMillis() - block.maxAgeHours * 3_600_000L else 0L
    val entries = buildList<ResumeEntry> {
        // A hidden title comes back once you watch more of it.
        if (block.showNexus) nexusItems.filter { p -> (hiddenNexus[p.id] ?: -1L) < p.updatedAt }.forEach { add(ResumeEntry.Nexus(it)) }
        if (block.showApps) appItems.filter { it.packageName !in block.hiddenApps && (it.playing || it.updatedAt >= cutoff) }
            .forEach { add(ResumeEntry.App(it)) }
    }.sortedByDescending { it.time }.take(block.maxItems.coerceIn(1, 6))

    if (entries.isEmpty() && !editMode && !(block.showApps && !hasAccess)) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (headings && block.title.isNotBlank()) HudLabel(block.title)
        entries.forEach { e ->
            when (e) {
                is ResumeEntry.Nexus -> ContinueCard(
                    e.p,
                    onDismiss = if (block.showDismiss) ({ scope.launch { c.resumeHidden.update { it + (e.p.id to System.currentTimeMillis()) } } }) else null,
                ) { vm.openContinue(e.p, nav) }
                is ResumeEntry.App -> AppMediaCard(
                    e.m, block.showControls, onOpen = { c.externalMedia.open(e.m, context) },
                    onDismiss = if (block.showDismiss) ({ c.externalMedia.dismiss(e.m.packageName) }) else null,
                ) { c.externalMedia }
            }
        }
        if (block.showApps && !hasAccess) {
            Row(
                Modifier.fillMaxWidth().nexusPress({ c.externalMedia.openAccessSettings(context) }).holoPanel(Nx.Amber, glow = 0f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Nx.Amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Tap to show Spotify, YouTube & other apps here. Turn on \"Nexus now playing\" in the list that opens.",
                    style = MaterialTheme.typography.bodySmall, color = Nx.Text,
                )
            }
        } else if (entries.isEmpty() && editMode) {
            Text("Shows up when something's playing or in progress", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppMediaCard(
    m: com.eoinknd16.nexus.media.ExternalMediaItem,
    controls: Boolean,
    onOpen: () -> Unit,
    onDismiss: (() -> Unit)?,
    media: () -> com.eoinknd16.nexus.media.ExternalMedia,
) {
    val apps by appContainer().apps.apps.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.packageName == m.packageName }
    // Keep the progress bar moving while it plays.
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(m.playing, m.positionMs) {
        elapsed = 0L
        val started = System.currentTimeMillis()
        while (m.playing) { delay(1000); elapsed = System.currentTimeMillis() - started }
    }
    val frac = if (m.durationMs > 0) ((m.positionMs + elapsed).toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.fillMaxWidth().nexusPress(onOpen).holoPanel().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(nexusShape(0.45f)).background(Nx.Deep), contentAlignment = Alignment.Center) {
            when {
                m.art != null -> Image(m.art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app != null -> Image(app.icon, null, Modifier.size(40.dp))
                else -> Icon(Icons.Filled.PlayArrow, null, tint = Nx.Cyan)
            }
            if (m.art != null && app != null) {
                Image(app.icon, null, Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(m.subtitle.takeIf { it.isNotBlank() }, app?.label).joinToString(" · ")
            if (sub.isNotBlank()) Text(hud(sub), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.durationMs > 0) {
                Spacer(Modifier.height(10.dp))
                GlowProgress(frac, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(8.dp))
        if (controls && m.controller != null) {
            Box(Modifier.size(36.dp).nexusPress({ media().previous(m) }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipPrevious, "Previous", tint = Nx.Cyan)
            }
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Nx.Cyan).nexusPress({ media().playPause(m) }), contentAlignment = Alignment.Center) {
            Icon(if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (m.playing) "Pause" else "Play", tint = Nx.Void)
        }
        if (controls && m.controller != null) {
            Box(Modifier.size(36.dp).nexusPress({ media().next(m) }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Nx.Cyan)
            }
        }
        if (onDismiss != null) {
            Box(Modifier.size(30.dp).nexusPress(onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Hide until something new plays", tint = Nx.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ContinueCard(p: WatchProgress, onDismiss: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().nexusPress(onClick).holoPanel().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = p.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(nexusShape(0.45f)).background(Nx.Deep),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (p.season != null && p.episode != null) "S${p.season} · E${p.episode}" else p.type.label
            Text(hud(sub), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
            Spacer(Modifier.height(10.dp))
            GlowProgress(p.fraction, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(44.dp).clip(CircleShape).background(Nx.Cyan), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Resume", tint = Nx.Void)
        }
        if (onDismiss != null) {
            Box(Modifier.size(30.dp).nexusPress(onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Hide until you watch it again", tint = Nx.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PostersView(b: HomeBlock.Posters, vm: HomeViewModel, headings: Boolean, editMode: Boolean) {
    val nav = LocalNav.current
    val recent by vm.continueWatching.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val items: List<Pair<CatalogItem, Float?>> = when (b.source) {
        PosterSource.Recent -> recent.map { CatalogItem(it.id, it.type, it.name, it.poster) to it.fraction }
        PosterSource.Library -> library.map { it to null }
    }.take(b.count.coerceAtLeast(1))
    if (items.isEmpty()) {
        if (editMode) Text("${b.source.label}: nothing yet", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        if (headings && b.title.isNotBlank()) {
            HudLabel(b.title)
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { "${it.first.type}:${it.first.id}" }) { (item, progress) ->
                PosterCard(item, width = b.posterWidthDp.coerceIn(56f, 200f).dp, progress = progress) { nav.navigate(Routes.detail(item.type.name, item.id)) }
            }
        }
    }
}

@Composable
private fun AppRowView(b: HomeBlock.AppRow, runner: TileRunner, headings: Boolean, editMode: Boolean) {
    val c = appContainer()
    val apps by c.apps.apps.collectAsStateWithLifecycle()
    val chosen = b.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    if (chosen.isEmpty()) {
        if (editMode) Text("No apps chosen. Tap ⚙ to pick some.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        if (headings && b.title.isNotBlank()) {
            HudLabel(b.title)
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(chosen, key = { it.key }) { app ->
                Column(Modifier.width((b.iconSizeDp + 16).dp).nexusPress({ c.apps.launch(app) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.icon, app.label, Modifier.size(b.iconSizeDp.dp))
                    if (b.showLabels) Text(app.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---- Android widget --------------------------------------------------------------

@Composable
private fun WidgetView(b: HomeBlock.Widget) {
    val widgets = appContainer().widgets
    val content = @Composable {
        BoxWithConstraints(Modifier.fillMaxWidth().height(b.heightDp.dp)) {
            val w = maxWidth.value.toInt()
            val h = b.heightDp.toInt()
            AndroidView(
                factory = { ctx -> widgets.createView(ctx, b.appWidgetId) ?: FrameLayout(ctx) },
                update = { v -> if (v is android.appwidget.AppWidgetHostView) widgets.updateSize(v, w, h) },
                modifier = Modifier.fillMaxWidth().height(b.heightDp.dp),
            )
        }
    }
    if (b.framed) Box(Modifier.fillMaxWidth().holoPanel(glow = 0.1f).padding(6.dp)) { content() } else content()
}

// ---- Dock ------------------------------------------------------------------------

@Composable
fun Dock(
    config: DockConfig,
    runner: TileRunner,
    editMode: Boolean,
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onEditSlot: (TileConfig) -> Unit,
    onAddSlot: () -> Unit,
    onDockSettings: () -> Unit,
    onDrawer: () -> Unit,
) {
    val c = appContainer()
    val size = config.iconSizeDp.coerceIn(28f, 80f).dp
    val slotW = if (config.showLabels) size + 16.dp else size
    val gap = config.spacingDp.coerceIn(0f, 48f).dp
    val outer = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    val inner = if (config.framed) outer.holoPanel(glow = 0.1f).padding(horizontal = 12.dp, vertical = 10.dp) else outer
    val sideButtons = (if (editMode) 1 else 0) + (if (config.showDrawerButton) 1 else 0)
    val slots: @Composable (Modifier) -> Unit = { m ->
        config.items.forEach { tile ->
            DockSlot(
                tile = tile, runner = runner, size = size, showLabel = config.showLabels,
                modifier = m,
                onOpenFolder = onOpenFolder,
                onEdit = { onEditSlot(tile) },
                onMove = { d -> c.editLook { it.moveTile(null, tile.id, d) } },
                onRemove = { c.editLook { it.removeTile(null, tile.id) } },
            )
        }
        if (editMode || config.items.isEmpty()) {
            Box(m, contentAlignment = Alignment.Center) {
                Box(Modifier.size(size).nexusPress(onAddSlot).clip(nexusShape(0.5f)).border(1.dp, Nx.CyanDim, nexusShape(0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Add to tray", tint = Nx.Cyan)
                }
            }
        }
    }
    Row(inner, verticalAlignment = Alignment.CenterVertically) {
        // Balance the drawer/settings buttons on the right with matching empty
        // space on the left, so the apps are centred on the whole tray.
        val balance = config.centerOnTray && sideButtons > 0 && config.arrange != DockArrange.Start && config.arrange != DockArrange.End
        if (balance) Spacer(Modifier.width((size + 8.dp) * sideButtons))
        BoxWithConstraints(Modifier.weight(1f)) {
            val count = config.items.size + if (editMode || config.items.isEmpty()) 1 else 0
            val needed = slotW * count + gap * (count - 1).coerceAtLeast(0)
            val overflow = needed > maxWidth
            val slotMod = if (config.equalSlots) Modifier.width(slotW) else Modifier
            if (overflow && config.scrollable) {
                Row(
                    Modifier.fillMaxWidth().horizontalScrollCompat(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) { slots(slotMod) }
            } else if (config.arrange == DockArrange.Even || overflow) {
                // Equal cells across the width: perfectly even spacing.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    slots(if (config.equalSlots) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = when (config.arrange) {
                        DockArrange.Center -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                        DockArrange.End -> Arrangement.spacedBy(gap, Alignment.End)
                        else -> Arrangement.spacedBy(gap, Alignment.Start)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) { slots(slotMod) }
            }
        }
        if (editMode) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(size).nexusPress(onDockSettings), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Tune, "Tray settings", tint = Nx.Amber)
            }
        }
        if (config.showDrawerButton) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(size).nexusPress(onDrawer).clip(nexusShape(0.6f)).background(Nx.Cyan.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Apps, "All apps", tint = Nx.Cyan) }
        }
    }
}

@Composable
private fun DockSlot(
    tile: TileConfig,
    runner: TileRunner,
    size: androidx.compose.ui.unit.Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val accent = LookState.palette.slot(tile.color)
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .onGloballyPositioned {
                    bounds = it.boundsInRoot()
                    center = it.boundsInRoot().center
                }
                .nexusPress({ if (tile.isFolder()) onOpenFolder(tile, center) else runner.run(tile, bounds) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileIcon(tile, runner, accent, size)
            if (showLabel) Text(tile.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 16.dp), textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Nx.PanelSolid) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Move left") }, onClick = { menu = false; onMove(-1) })
            DropdownMenuItem(text = { Text("Move right") }, onClick = { menu = false; onMove(1) })
            DropdownMenuItem(text = { Text("Remove from tray", color = Nx.Danger) }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.horizontalScroll(rememberScrollState())


/**
 * Entrance animation for a tile when the home screen appears, staggered by
 * its position for Cascade. Plays once per composition (i.e. each time home
 * is opened), honouring Customise › Motion speed and the animations switch.
 */
@Composable
fun entranceModifier(kind: com.eoinknd16.nexus.look.Entrance, index: Int): Modifier {
    val motion = LookState.look.motion
    if (!motion.animations || kind == com.eoinknd16.nexus.look.Entrance.None) return Modifier
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        val stagger = if (kind == com.eoinknd16.nexus.look.Entrance.Cascade) index * 45L else 0L
        delay((stagger / motion.speed.coerceIn(0.25f, 4f)).toLong())
        p.animateTo(1f, com.eoinknd16.nexus.ui.fx.nexusSpring())
    }
    return Modifier.graphicsLayer {
        val v = p.value
        when (kind) {
            com.eoinknd16.nexus.look.Entrance.Fade -> alpha = v.coerceIn(0f, 1f)
            com.eoinknd16.nexus.look.Entrance.Rise, com.eoinknd16.nexus.look.Entrance.Cascade -> {
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 40.dp.toPx()
            }
            com.eoinknd16.nexus.look.Entrance.Pop -> {
                alpha = v.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
            }
            com.eoinknd16.nexus.look.Entrance.Flip -> {
                alpha = v.coerceIn(0f, 1f)
                rotationX = (1f - v) * 80f
                cameraDistance = 12 * density
            }
            com.eoinknd16.nexus.look.Entrance.None -> {}
        }
    }
}
