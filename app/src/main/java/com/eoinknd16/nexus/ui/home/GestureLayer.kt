package com.eoinknd16.nexus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.look.GestureBinding
import com.eoinknd16.nexus.look.GestureZone
import com.eoinknd16.nexus.look.Gestures
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.TileAction
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.abs

enum class SwipeDir { Up, Down, Left, Right }
private typealias Dir = SwipeDir

/**
 * Home-screen gesture detection that only fires when a swipe clearly means it:
 *
 * - It has to *start* in that gesture's zone (bottom edge for swipe-up, top
 *   edge for swipe-down, side edges for left/right) unless set to Anywhere.
 * - It has to go [Gestures.minDistanceDp] in one dominant direction (2:1 over
 *   the other axis) and be a real flick, not a slow drag.
 * - It watches events before the content does (Initial pass) without taking
 *   them, so taps and scrolling work as normal -- it only claims the gesture
 *   (consuming the rest of it) once it's certain.
 * - One gesture per touch, with a haptic tick when it fires.
 */
fun Modifier.homeGestures(
    gestures: Gestures,
    enabled: Boolean,
    onAction: (TileAction, SwipeDir) -> Unit,
    onFired: () -> Unit,
    /** Lets the screen take over a direction (e.g. "swipe back to the main page"). */
    overrideFor: (SwipeDir) -> TileAction? = { null },
    /** When swipe-up opens the app drawer, the drawer follows the finger instead of waiting for a flick. */
    drawer: DrawerController? = null,
    /** Swipes bound to a home page drag the pages with the finger. */
    pages: PageSwipe? = null,
): Modifier = composed {
    // The detector stays attached and is only restarted when the gesture
    // settings change. Everything else is read live, so a change mid-swipe
    // (e.g. the drawer starting to open) can never cut a gesture off halfway.
    val isEnabled by rememberUpdatedState(enabled)
    val act by rememberUpdatedState(onAction)
    val fired by rememberUpdatedState(onFired)
    val override by rememberUpdatedState(overrideFor)
    pointerInput(gestures, drawer, pages) { detectHomeGestures(gestures, { isEnabled }, { a, d -> act(a, d) }, { fired() }, { override(it) }, drawer, pages) }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHomeGestures(
    gestures: Gestures,
    enabled: () -> Boolean,
    onAction: (TileAction, SwipeDir) -> Unit,
    onFired: () -> Unit,
    overrideFor: (SwipeDir) -> TileAction?,
    drawer: DrawerController?,
    pages: PageSwipe?,
) {
    val touchSlop = viewConfiguration.touchSlop
    val minDistance = gestures.minDistanceDp.dp.toPx()
    val minVelocity = 600.dp.toPx() // px/s
    awaitPointerEventScope {
        while (true) {
            // Wait for a fresh first finger.
            var event = awaitPointerEvent(PointerEventPass.Initial)
            val first = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
            if (!enabled() || pages?.busy == true) continue
            val start = first.position
            val h = size.height.toFloat()
            val w = size.width.toFloat()
            val edge = gestures.edgeSize.coerceIn(0.05f, 0.5f)
            val inBottom = start.y > h * (1 - edge)
            val inTop = start.y < h * edge
            val inLeft = start.x < w * edge
            val inRight = start.x > w * (1 - edge)
            val tracker = VelocityTracker()
            tracker.addPosition(first.uptimeMillis, first.position)
            var maxPointers = 1
            var fired = false
            var centroidStart: Offset = start

            while (true) {
                event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size > maxPointers) {
                    maxPointers = pressed.size
                    centroidStart = centroid(pressed)
                }
                val main = event.changes.firstOrNull { it.id == first.id } ?: pressed.first()
                tracker.addPosition(main.uptimeMillis, main.position)
                if (fired) {
                    event.changes.forEach { it.consume() }
                    continue
                }
                val delta = if (maxPointers >= 2) centroid(pressed) - centroidStart else main.position - start
                val ax = abs(delta.x)
                val ay = abs(delta.y)
                // Drawer: as soon as it's clearly an upward drag from the right place, hand the finger to the drawer.
                val up = gestures.swipeUp
                val drawerBound = up.action.kind == TileAction.Kind.Screen && up.action.arg == "apps"
                if (drawer != null && drawerBound && maxPointers == 1 && delta.y < -touchSlop && ay > ax * 1.5f &&
                    (up.zone == GestureZone.Anywhere || inBottom)
                ) {
                    event.changes.forEach { it.consume() }
                    drawer.startDrag()
                    drawer.dragBy(-(delta.y + touchSlop).coerceAtMost(0f))
                    var last = main.position.y
                    try {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = e.changes.firstOrNull { it.id == first.id } ?: e.changes.firstOrNull()
                            if (ch == null || !ch.pressed) break
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            drawer.dragBy(last - ch.position.y)
                            last = ch.position.y
                            e.changes.forEach { it.consume() }
                        }
                    } finally {
                        // Always let go, even if this touch was interrupted.
                        drawer.endDrag(runCatching { -tracker.calculateVelocity().y }.getOrDefault(0f))
                    }
                    break
                }
                // Pages: a clearly sideways drag whose direction is bound to a page follows the finger.
                if (pages != null && maxPointers == 1 && ax > touchSlop && ax > ay * 1.5f) {
                    val hDir = if (delta.x < 0) Dir.Left else Dir.Right
                    // Page swipes work from anywhere on the screen, like any launcher.
                    val bound = overrideFor(hDir) ?: when (hDir) {
                        Dir.Left -> gestures.swipeLeft.action
                        else -> gestures.swipeRight.action
                    }
                    if (bound != null && bound.kind == TileAction.Kind.Page && pages.start(bound.arg.ifBlank { "next" }, if (hDir == Dir.Left) -1 else 1)) {
                        event.changes.forEach { it.consume() }
                        var lastX = main.position.x
                        try {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = e.changes.firstOrNull { it.id == first.id } ?: e.changes.firstOrNull()
                                if (ch == null || !ch.pressed) break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                pages.drag(ch.position.x - lastX)
                                lastX = ch.position.x
                                e.changes.forEach { it.consume() }
                            }
                        } finally {
                            pages.end(runCatching { tracker.calculateVelocity().x }.getOrDefault(0f))
                        }
                        break
                    }
                }
                if (maxOf(ax, ay) < minDistance) continue
                val dir = when {
                    ay > ax * 2 -> if (delta.y < 0) Dir.Up else Dir.Down
                    ax > ay * 2 -> if (delta.x < 0) Dir.Left else Dir.Right
                    else -> null
                } ?: continue
                val v = tracker.calculateVelocity()
                val speed = if (dir == Dir.Up || dir == Dir.Down) abs(v.y) else abs(v.x)
                if (speed < minVelocity) continue

                val forced = if (maxPointers == 1) overrideFor(dir) else null
                val binding: GestureBinding? = if (forced != null) GestureBinding(forced, GestureZone.Anywhere) else if (maxPointers >= 2) {
                    when (dir) {
                        Dir.Up -> gestures.twoFingerUp
                        Dir.Down -> gestures.twoFingerDown
                        else -> null
                    }
                } else {
                    when (dir) {
                        Dir.Up -> gestures.swipeUp.takeIf { it.zone == GestureZone.Anywhere || inBottom }
                        Dir.Down -> gestures.swipeDown.takeIf { it.zone == GestureZone.Anywhere || inTop }
                        // Swiping left starts at the right edge, and vice versa.
                        Dir.Left -> gestures.swipeLeft.takeIf { it.zone == GestureZone.Anywhere || inRight }
                        Dir.Right -> gestures.swipeRight.takeIf { it.zone == GestureZone.Anywhere || inLeft }
                    }
                }
                if (binding == null || binding.action.kind == TileAction.Kind.None) {
                    // Not a gesture we act on: stop evaluating this touch, let it scroll.
                    fired = false
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.none { it.pressed }) break
                    }
                    break
                }
                fired = true
                event.changes.forEach { it.consume() }
                onFired()
                onAction(binding.action, dir)
            }
        }
    }
}

private fun centroid(changes: List<PointerInputChange>): Offset {
    var x = 0f
    var y = 0f
    changes.forEach { x += it.position.x; y += it.position.y }
    return Offset(x / changes.size, y / changes.size)
}

/** Faint bands showing where each edge gesture has to start (edit mode only). */
@Composable
fun BoxScope.GestureZonesOverlay(gestures: Gestures) {
    val edge = gestures.edgeSize.coerceIn(0.05f, 0.5f)
    val band = Nx.Amber.copy(alpha = 0.10f)
    if (gestures.swipeUp.action.kind != TileAction.Kind.None && gestures.swipeUp.zone == GestureZone.Edge) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(edge)
                .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, band))),
            contentAlignment = Alignment.BottomCenter,
        ) { ZoneLabel("↑ ${gestures.swipeUp.action.kind.label}") }
    }
    if (gestures.swipeDown.action.kind != TileAction.Kind.None && gestures.swipeDown.zone == GestureZone.Edge) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(edge)
                .background(Brush.verticalGradient(listOf(band, androidx.compose.ui.graphics.Color.Transparent))),
            contentAlignment = Alignment.TopCenter,
        ) { ZoneLabel("↓ ${gestures.swipeDown.action.kind.label}") }
    }
}

@Composable
private fun ZoneLabel(text: String) {
    Text(hud(text), style = MaterialTheme.typography.labelSmall, color = Nx.Amber.copy(alpha = 0.7f), modifier = Modifier.padding(6.dp))
}

/** Haptic helper honouring Customise › Motion. */
@Composable
fun rememberGestureHaptic(): () -> Unit {
    val h = com.eoinknd16.nexus.ui.fx.rememberHaptics()
    return { h.click() }
}

/** Gesture actions reuse the tile runner (folders aren't a gesture target). */
fun TileRunner.runAction(action: TileAction, dir: SwipeDir? = null) = run(
    TileConfig(label = "", action = action),
    swipe = when (dir) { SwipeDir.Left -> -1; SwipeDir.Right -> 1; else -> 0 },
)
