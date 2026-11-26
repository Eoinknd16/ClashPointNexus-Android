package com.eoinknd16.nexus.ui.home

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
        is HomeBlock.Resume -> {
            val items by vm.continueWatching.collectAsStateWithLifecycle()
            val nav = LocalNav.current
            items.firstOrNull()?.let { top ->
                Column {
                    if (headings && block.title.isNotBlank()) {
                        HudLabel(block.title)
                        Spacer(Modifier.height(10.dp))
                    }
                    ContinueCard(top) { vm.openContinue(top, nav) }
                }
            } ?: run { if (editMode) Text("Shows up when something's in progress", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium) }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    val mod = Modifier.weight(1f).aspectRatio(b.aspect.coerceIn(0.4f, 3f))
                    if (tile == null) {
                        AddCell(mod, onAddTile)
                    } else {
                        TileView(
                            tile = tile,
                            runner = runner,
                            showSublabel = b.showSublabels,
                            modifier = mod,
                            onOpenFolder = onOpenFolder,
                            onLongPress = { onEditTile(tile) },
                        )
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

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
) {
    val accent = LookState.palette.slot(tile.color)
    var center by remember { mutableStateOf(Offset.Zero) }
    val motion = LookState.look.motion
    val pulse = if (motion.pulse && motion.animations) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.25f, 0.55f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "glow",
        ).value
    } else 0.35f
    Box(
        modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .nexusPress({ if (tile.isFolder()) onOpenFolder(tile, center) else runner.run(tile) }, onLongPress)
            .holoPanel(accent, pulse),
    ) {
        tile.image?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.45f))
        }
        Column(Modifier.padding(14.dp)) {
            TileIcon(tile, runner, accent, 40.dp)
            Spacer(Modifier.weight(1f))
            Text(tile.label, style = MaterialTheme.typography.titleMedium, color = Nx.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showSublabel && tile.sublabel.isNotBlank()) {
                Text(hud(tile.sublabel), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        runner.appIcon(tile) != null -> Image(runner.appIcon(tile)!!, null, Modifier.size(size))
        else -> Box(
            Modifier.size(size).clip(shape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(IconCatalog.icon(tile.icon), null, tint = accent, modifier = Modifier.size(size * 0.6f)) }
    }
}

// ---- Resume / posters / apps ---------------------------------------------------

@Composable
private fun ContinueCard(p: WatchProgress, onClick: () -> Unit) {
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
                PosterCard(item, width = 96.dp, progress = progress) { nav.navigate(Routes.detail(item.type.name, item.id)) }
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
    val outer = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    val inner = if (config.framed) outer.holoPanel(glow = 0.1f).padding(horizontal = 12.dp, vertical = 10.dp) else outer
    Row(inner, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).then(if (config.scrollable) Modifier.horizontalScrollCompat() else Modifier),
            horizontalArrangement = if (config.scrollable) Arrangement.spacedBy(14.dp) else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            config.items.forEach { tile ->
                DockSlot(
                    tile = tile, runner = runner, size = size, showLabel = config.showLabels,
                    onOpenFolder = onOpenFolder,
                    onEdit = { onEditSlot(tile) },
                    onMove = { d -> c.editLook { it.moveTile(null, tile.id, d) } },
                    onRemove = { c.editLook { it.removeTile(null, tile.id) } },
                )
            }
            if (editMode || config.items.isEmpty()) {
                Box(Modifier.size(size).nexusPress(onAddSlot).clip(nexusShape(0.5f)).border(1.dp, Nx.CyanDim, nexusShape(0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Add to tray", tint = Nx.Cyan)
                }
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
    onOpenFolder: (TileConfig, Offset) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val accent = LookState.palette.slot(tile.color)
    Box {
        Column(
            Modifier
                .onGloballyPositioned { center = it.boundsInRoot().center }
                .nexusPress({ if (tile.isFolder()) onOpenFolder(tile, center) else runner.run(tile) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileIcon(tile, runner, accent, size)
            if (showLabel) Text(tile.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 12.dp), textAlign = TextAlign.Center)
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
