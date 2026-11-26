package com.eoinknd16.nexus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.look.FolderAnimation
import com.eoinknd16.nexus.look.FolderStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An open folder. The panel grows out of the tile you tapped (Bloom), rises
 * from the bottom (Rise), fades (Fade) or warps in spinning (Spin), then the
 * app icons pop in one after another. Grid or orbit-ring layout.
 */
@Composable
fun FolderOverlay(folder: OpenFolder, runner: TileRunner, onClose: () -> Unit, onEdit: (TileConfig) -> Unit) {
    val tile = folder.tile
    val c = appContainer()
    val allApps by c.apps.apps.collectAsStateWithLifecycle()
    val apps = tile.folderApps.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
    val animate = LookState.look.motion.animations
    val accent = LookState.palette.slot(tile.color)

    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val scrim = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(220)) }
        progress.animateTo(1f, if (tile.folderAnimation == FolderAnimation.Fade) tween(260) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }

    val panelOrigin = remember { Offset0() }
    val panelSize = remember { IntSize0() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrim.value }
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .onGloballyPositioned {
                    panelOrigin.x = it.positionInRoot().x
                    panelOrigin.y = it.positionInRoot().y
                    panelSize.w = it.size.width
                    panelSize.h = it.size.height
                }
                .graphicsLayer {
                    val p = progress.value
                    when (tile.folderAnimation) {
                        FolderAnimation.Bloom -> {
                            // Grow out of the tapped tile's centre.
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
                        FolderAnimation.Fade -> alpha = p
                        FolderAnimation.Spin -> {
                            rotationZ = (1f - p) * -120f
                            scaleX = 0.3f + 0.7f * p
                            scaleY = 0.3f + 0.7f * p
                            alpha = p.coerceIn(0f, 1f)
                        }
                    }
                }
                .holoPanel(accent, 0.5f)
                // Taps inside the panel shouldn't close it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { HudLabel(tile.label, color = accent) }
                Box(Modifier.size(36.dp).nexusPress({ onEdit(tile) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, "Edit folder", tint = Nx.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (apps.isEmpty()) {
                Text("Empty folder. Tap ✎ to add apps.", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
            } else if (tile.folderStyle == FolderStyle.Ring) {
                RingLayout(apps, accent) { runner.launchAndClose(it, onClose) }
            } else {
                val cols = tile.folderColumns.coerceIn(2, 6)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    apps.chunked(cols).forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { i, app ->
                                FolderApp(app, delayIndex = r * cols + i, animate = animate, modifier = Modifier.weight(1f)) {
                                    runner.launchAndClose(app, onClose)
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private class Offset0 { var x = 0f; var y = 0f }
private class IntSize0 { var w = 0; var h = 0 }

private fun TileRunner.launchAndClose(app: LaunchableApp, onClose: () -> Unit) {
    launch(app, null)
    onClose()
}

/** Icon that pops in after its neighbours, staggered by position. */
@Composable
private fun FolderApp(app: LaunchableApp, delayIndex: Int, animate: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(90L + delayIndex * 35L)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    Column(
        modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
        }.nexusPress(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, app.label, Modifier.size(52.dp))
        Spacer(Modifier.height(4.dp))
        Text(app.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Apps orbiting a glowing core; they fly out from the centre as it opens. */
@Composable
private fun RingLayout(apps: List<LaunchableApp>, accent: Color, onLaunch: (LaunchableApp) -> Unit) {
    val animate = LookState.look.motion.animations
    val spread = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { spread.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val radiusPx = with(density) { (minOf(maxWidth, maxHeight) / 2 - 40.dp).toPx() }
        Box(Modifier.size(56.dp).holoPanel(accent, 0.8f))
        apps.forEachIndexed { i, app ->
            val angle = 2 * PI * i / apps.size - PI / 2
            val r = radiusPx * spread.value
            Column(
                Modifier
                    .offset { IntOffset((cos(angle) * r).roundToInt(), (sin(angle) * r).roundToInt()) }
                    .width(68.dp)
                    .nexusPress({ onLaunch(app) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, app.label, Modifier.size(46.dp))
                Text(app.label, style = MaterialTheme.typography.labelSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
