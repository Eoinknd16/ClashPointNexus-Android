package com.eoinknd16.nexus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.look.FolderAnimation
import com.eoinknd16.nexus.look.FolderBackground
import com.eoinknd16.nexus.look.FolderPosition
import com.eoinknd16.nexus.look.FolderStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusMs
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.fx.nexusSpring
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An open folder. Opens with its animation (bloom from the tile, rise, fade,
 * warp spin), icons pop in one after another, and -- if set -- it all plays
 * backwards on close. Background: holo panel, frosted glass, or nothing at
 * all (icons float over the blurred, dimmed home screen; HomeScreen applies
 * the blur until [onClosing] fires).
 *
 * [closeRequest] increments to ask for an animated close from outside (Back).
 */
@Composable
fun FolderOverlay(
    folder: OpenFolder,
    runner: TileRunner,
    closeRequest: Int,
    onClosing: () -> Unit,
    onClose: () -> Unit,
    onEdit: (TileConfig) -> Unit,
) {
    val tile = folder.tile
    val c = appContainer()
    val allApps by c.apps.apps.collectAsStateWithLifecycle()
    val apps = tile.folderApps.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
    val animate = LookState.look.motion.animations
    val accent = LookState.palette.slot(tile.color)
    val scope = rememberCoroutineScope()

    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val scrim = remember { Animatable(if (animate) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(nexusMs(220))) }
        progress.animateTo(1f, if (tile.folderAnimation == FolderAnimation.Fade) tween(nexusMs(260)) else nexusSpring())
    }

    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        onClosing()
        if (!animate || !tile.folderAnimateClose) {
            onClose()
            then()
            return
        }
        scope.launch {
            launch { scrim.animateTo(0f, tween(nexusMs(200))) }
            progress.animateTo(0f, tween(nexusMs(200)))
            onClose()
            then()
        }
    }

    LaunchedEffect(closeRequest) { if (closeRequest > 0) close() }

    val launchApp: (LaunchableApp) -> Unit = { app ->
        runner.launch(app, null)
        close()
    }

    val panelOrigin = remember { FloatPair() }
    val panelSize = remember { IntPair() }
    val dim = tile.folderDim.coerceIn(0f, 0.95f)
    val floating = tile.folderBackground == FolderBackground.None

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrim.value }
            .background(Color.Black.copy(alpha = dim))
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        val bodyMod = Modifier
            .fillMaxWidth(0.88f)
            .graphicsLayer {
                val p = progress.value
                when (tile.folderAnimation) {
                    FolderAnimation.Bloom -> {
                        val ox = if (panelSize.w > 0) ((folder.origin.x - panelOrigin.x) / panelSize.w).coerceIn(0f, 1f) else 0.5f
                        val oy = if (panelSize.h > 0) ((folder.origin.y - panelOrigin.y) / panelSize.h).coerceIn(0f, 1f) else 0.5f
                        transformOrigin = TransformOrigin(ox, oy)
                        scaleX = 0.15f + 0.85f * p
                        scaleY = 0.15f + 0.85f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    FolderAnimation.Rise -> {
                        translationY = (1f - p) * size.height * 0.8f
                        alpha = p.coerceIn(0f, 1f)
                    }
                    FolderAnimation.Fade -> alpha = p.coerceIn(0f, 1f)
                    FolderAnimation.Spin -> {
                        rotationZ = (1f - p) * -120f
                        scaleX = 0.3f + 0.7f * p
                        scaleY = 0.3f + 0.7f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                }
            }
            .then(
                when (tile.folderBackground) {
                    FolderBackground.Panel -> Modifier.holoPanel(accent, 0.5f, tile.border)
                    FolderBackground.Glass -> Modifier
                        .clip(nexusShape())
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), nexusShape())
                    FolderBackground.None -> Modifier
                },
            )
            // With a panel, taps between icons stay inside; with none, empty space closes it.
            .then(if (floating) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { } })
            .padding(16.dp)

        FolderPlacement(tile.folderPosition, folder.origin, panelOrigin, panelSize) {
        Column(bodyMod, horizontalAlignment = Alignment.CenterHorizontally) {
            if (tile.folderShowTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (floating) {
                            Text(
                                tile.label, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                                fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            )
                        } else {
                            HudLabel(tile.label, color = accent)
                        }
                    }
                    if (tile.folderShowEditButton) {
                        Box(Modifier.size(36.dp).nexusPress({ close { onEdit(tile) } }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Edit, "Edit folder", tint = if (floating) Color.White.copy(alpha = 0.7f) else Nx.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (apps.isEmpty()) {
                Text("Empty folder. Long-press its tile to add apps.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
            } else if (tile.folderStyle == FolderStyle.Ring) {
                RingLayout(apps, accent, tile, progress.value, launchApp)
            } else {
                val cols = tile.folderColumns.coerceIn(2, 6)
                // Big folders scroll inside themselves instead of running off screen.
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    apps.chunked(cols).forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { i, app ->
                                FolderApp(app, tile, r * cols + i, animate, closing, Modifier.weight(1f)) { launchApp(app) }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    }
}

private class FloatPair { var x = 0f; var y = 0f }
private class IntPair { var w = 0; var h = 0 }

/** Icon that pops in after its neighbours (and shrinks away on close). */
@Composable
private fun FolderApp(app: LaunchableApp, tile: TileConfig, delayIndex: Int, animate: Boolean, closing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(closing) {
        if (!animate) return@LaunchedEffect
        if (closing) {
            if (tile.folderAnimateClose) pop.animateTo(0f, tween(nexusMs(160)))
        } else {
            delay(nexusMs(90 + delayIndex * 35).toLong())
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }
    val floating = tile.folderBackground == FolderBackground.None
    Column(
        modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
        }.nexusPress(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, app.label, Modifier.size(tile.folderIconSize.coerceIn(32f, 96f).dp))
        if (tile.folderShowLabels) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (floating) Color.White else Nx.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Apps orbiting a core; they fly out from the centre as it opens (and back in on close). */
@Composable
private fun RingLayout(apps: List<LaunchableApp>, accent: Color, tile: TileConfig, spread: Float, onLaunch: (LaunchableApp) -> Unit) {
    val iconSize = tile.folderIconSize.coerceIn(32f, 96f)
    BoxWithConstraints(Modifier.fillMaxWidth().height((iconSize * 6).dp.coerceAtMost(420.dp)), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val radiusPx = with(density) { (minOf(maxWidth, maxHeight) / 2 - (iconSize / 2 + 20).dp).toPx() }
        if (tile.folderBackground != FolderBackground.None) Box(Modifier.size(56.dp).holoPanel(accent, 0.8f))
        apps.forEachIndexed { i, app ->
            val angle = 2 * PI * i / apps.size - PI / 2
            val r = radiusPx * spread
            Column(
                Modifier
                    .offset { IntOffset((cos(angle) * r).roundToInt(), (sin(angle) * r).roundToInt()) }
                    .width((iconSize + 22).dp)
                    .nexusPress({ onLaunch(app) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, app.label, Modifier.size(iconSize.dp))
                if (tile.folderShowLabels) {
                    Text(
                        app.label, style = MaterialTheme.typography.labelSmall,
                        color = if (tile.folderBackground == FolderBackground.None) Color.White else Nx.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/**
 * Places the folder exactly: centred, centred on the tapped tile's height, or
 * just above the tray -- always clamped inside the visible area (below the
 * status bar, above the navigation bar), so it can never land off screen.
 * Records where it put it so the Bloom animation can grow from the tile.
 */
@Composable
private fun FolderPlacement(
    position: FolderPosition,
    origin: androidx.compose.ui.geometry.Offset,
    placedAt: FloatPair,
    placedSize: IntPair,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val topInset = androidx.compose.foundation.layout.WindowInsets.systemBars.getTop(density)
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.systemBars.getBottom(density)
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val top = topInset + margin
        val bottom = constraints.maxHeight - bottomInset - margin
        val available = (bottom - top).coerceAtLeast(0)
        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth, maxHeight = available),
        )
        val h = placeable.height
        val x = (constraints.maxWidth - placeable.width) / 2
        val wanted = when (position) {
            FolderPosition.Center -> (constraints.maxHeight - h) / 2
            FolderPosition.FromTile -> (origin.y - h / 2f).roundToInt()
            FolderPosition.Bottom -> bottom - h - 72.dp.roundToPx() // clear of the tray
        }
        val y = wanted.coerceIn(top, (bottom - h).coerceAtLeast(top))
        placedAt.x = x.toFloat()
        placedAt.y = y.toFloat()
        placedSize.w = placeable.width
        placedSize.h = h
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
    }
}
