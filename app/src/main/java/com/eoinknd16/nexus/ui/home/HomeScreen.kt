package com.eoinknd16.nexus.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eoinknd16.nexus.media.WatchProgress
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HoloTile
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.nexusViewModel
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val vm = nexusViewModel { HomeViewModel(it) }
    val nav = LocalNav.current
    val state by vm.state.collectAsStateWithLifecycle()
    val continueItems by vm.continueWatching.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // A launcher has nothing "behind" it to go back to.
    BackHandler { }

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }

    NexusBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Swipe up anywhere on home opens the app drawer.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = { if (total < -120f) nav.navigate(Routes.APPS) },
                    )
                },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                ClockBlock(state.pc)
                Spacer(Modifier.height(28.dp))

                continueItems.firstOrNull()?.let { top ->
                    HudLabel("Resume")
                    Spacer(Modifier.height(10.dp))
                    ContinueCard(top) { vm.openContinue(top, nav) }
                    Spacer(Modifier.height(24.dp))
                }

                HudLabel("Command deck")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoloTile("TV", Icons.Filled.Movie, Modifier.weight(1f).aspectRatio(1.05f), sublabel = "Movies & shows") { nav.navigate(Routes.TV) }
                    HoloTile("Remote", Icons.Filled.SettingsRemote, Modifier.weight(1f).aspectRatio(1.05f), sublabel = state.pc.short, accent = Nx.Magenta) { nav.navigate(Routes.PC) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoloTile(
                        "Wake PC", Icons.Filled.PowerSettingsNew, Modifier.weight(1f).aspectRatio(1.05f),
                        sublabel = state.wakeMessage ?: "Magic packet", accent = Nx.Amber,
                    ) { scope.launch { vm.wakePc() } }
                    HoloTile("Apps", Icons.Filled.Apps, Modifier.weight(1f).aspectRatio(1.05f), sublabel = "Swipe up anywhere", accent = Nx.Lime) {
                        nav.navigate(Routes.APPS)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoloTile("Search", Icons.Filled.Search, Modifier.weight(1f).aspectRatio(1.05f), sublabel = "Find anything") { nav.navigate(Routes.SEARCH) }
                    HoloTile("Settings", Icons.Filled.Settings, Modifier.weight(1f).aspectRatio(1.05f), sublabel = "Link · addons", accent = Nx.Muted) {
                        nav.navigate(Routes.SETTINGS)
                    }
                }

                if (continueItems.size > 1) {
                    Spacer(Modifier.height(24.dp))
                    HudLabel("Recently watched")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueItems.drop(1), key = { "${it.type}:${it.id}" }) { p ->
                            PosterChip(p) { vm.openContinue(p, nav) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Dock(
                apps = dock,
                onApp = vm::launch,
                onDrawer = { nav.navigate(Routes.APPS) },
            )
        }
    }
}

@Composable
private fun ClockBlock(pc: PcState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        1f, 0.2f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "colon",
    )
    val time = SimpleDateFormat("HH mm", Locale.getDefault()).format(now).split(" ")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time[0], fontSize = 72.sp, fontWeight = FontWeight.Thin, color = Nx.Text, letterSpacing = 4.sp)
            Text(":", fontSize = 64.sp, fontWeight = FontWeight.Thin, color = Nx.Cyan, modifier = Modifier.alpha(blink))
            Text(time[1], fontSize = 72.sp, fontWeight = FontWeight.Thin, color = Nx.Text, letterSpacing = 4.sp)
        }
        Text(
            SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(now).uppercase(),
            style = MaterialTheme.typography.labelMedium, color = Nx.Muted,
        )
        Spacer(Modifier.height(12.dp))
        StatusChip(pc)
    }
}

@Composable
private fun StatusChip(pc: PcState) {
    val color = when (pc.kind) {
        PcState.Kind.Online -> Nx.Lime
        PcState.Kind.Offline -> Nx.Danger
        PcState.Kind.Checking -> Nx.Amber
        PcState.Kind.Unpaired -> Nx.Muted
    }
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a",
    )
    Row(
        Modifier.clip(CutCornerShape(6.dp)).background(color.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = if (pc.kind == PcState.Kind.Online) pulse else 1f)))
        Spacer(Modifier.width(8.dp))
        Text(pc.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ContinueCard(p: WatchProgress, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().nexusPress(onClick).holoPanel().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = p.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(CutCornerShape(6.dp)).background(Nx.Deep),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (p.season != null && p.episode != null) "S${p.season} · E${p.episode}" else "Movie"
            Text(sub.uppercase(), style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
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
private fun PosterChip(p: WatchProgress, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).nexusPress(onClick)) {
        AsyncImage(
            model = p.poster, contentDescription = p.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).background(Nx.Deep),
        )
        GlowProgress(p.fraction, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun Dock(apps: List<com.eoinknd16.nexus.launcher.LaunchableApp>, onApp: (com.eoinknd16.nexus.launcher.LaunchableApp) -> Unit, onDrawer: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .holoPanel(glow = 0.1f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.take(5).forEach { app ->
            Image(app.icon, app.label, Modifier.size(46.dp).nexusPress({ onApp(app) }))
        }
        AnimatedVisibility(apps.isEmpty()) {
            Text("LONG-PRESS AN APP IN THE DRAWER TO PIN IT", style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
        Box(
            Modifier.size(46.dp).nexusPress(onDrawer).clip(CutCornerShape(10.dp)).background(Color(0x223CF2FF)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Apps, "All apps", tint = Nx.Cyan) }
    }
}
