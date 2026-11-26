package com.eoinknd16.nexus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.launcher.AppShortcut
import com.eoinknd16.nexus.launcher.LaunchableApp
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.media.NotificationDots
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.rememberHaptics
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuickAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * Long-press menu for an app: its own shortcuts on top ("New message",
 * "Navigate home"…), then a row of quick actions. Grows out of the icon
 * with a spring, sits above it when there's room, and closes on any tap
 * outside.
 */
@Composable
fun AppQuickMenu(app: LaunchableApp?, actions: List<QuickAction>, onDismiss: () -> Unit) {
    val c = appContainer()
    val haptics = rememberHaptics()
    val shortcuts by produceState<List<AppShortcut>>(emptyList(), app?.key) {
        value = app?.let { a -> withContext(Dispatchers.IO) { c.apps.shortcuts(a, 5) } }.orEmpty()
    }
    val grow = remember { Animatable(0f) }
    var above = true
    LaunchedEffect(Unit) {
        haptics.pickUp()
        grow.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val gap = 12
                    val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(16, (windowSize.width - popupContentSize.width - 16).coerceAtLeast(16))
                    val aboveY = anchorBounds.top - popupContentSize.height - gap
                    above = aboveY > 48
                    val y = if (above) aboveY else (anchorBounds.bottom + gap).coerceAtMost(windowSize.height - popupContentSize.height - 16)
                    return IntOffset(x, y)
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val p = grow.value
                    scaleX = 0.85f + 0.15f * p
                    scaleY = 0.85f + 0.15f * p
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                }
                .widthIn(min = 220.dp, max = 300.dp)
                .holoPanel(Nx.Cyan, 0.25f)
                .background(Nx.PanelSolid.copy(alpha = 0.92f))
                .padding(vertical = 6.dp),
        ) {
            shortcuts.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().nexusPress({ c.apps.startShortcut(s); onDismiss() }).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val img = s.icon ?: s.app.icon
                    Image(img, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(s.label, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (shortcuts.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(1.dp).background(Nx.CyanDim))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                actions.forEach { a ->
                    Column(
                        Modifier.weight(1f).nexusPress({ onDismiss(); a.onClick() }).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(a.icon, a.label, tint = if (a.danger) Nx.Danger else Nx.Cyan)
                        Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (a.danger) Nx.Danger else Nx.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Standard actions for an app icon. */
fun appActions(app: LaunchableApp?, onEdit: (() -> Unit)?, onRemove: (() -> Unit)?, removeLabel: String = "Remove", c: com.eoinknd16.nexus.AppContainer): List<QuickAction> = buildList {
    if (onEdit != null) add(QuickAction("Edit", Icons.Filled.Edit, onClick = onEdit))
    if (app != null) add(QuickAction("App info", Icons.Filled.Info) { c.apps.openAppInfo(app) })
    if (onRemove != null) add(QuickAction(removeLabel, Icons.Filled.Delete, danger = true, onClick = onRemove))
}

/** Unread count for these packages (0 when dots are off). */
@Composable
fun notificationCount(packages: Collection<String>): Int {
    if (!LookState.look.home.notificationDots || packages.isEmpty()) return 0
    val counts by NotificationDots.counts.collectAsStateWithLifecycle()
    return packages.sumOf { counts[it] ?: 0 }
}

/** Small glowing dot in the corner of an icon/tile. */
@Composable
fun BoxScope.NotificationDot(count: Int, size: androidx.compose.ui.unit.Dp = 10.dp, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    if (count <= 0) return
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = -inset, y = inset).size(size).clip(CircleShape)
            .background(Nx.Amber).padding(1.dp),
    ) { Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) }
}
