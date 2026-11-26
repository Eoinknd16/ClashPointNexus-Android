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

private enum class Dir { Up, Down, Left, Right }

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
    onAction: (TileAction) -> Unit,
    onFired: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(gestures) {
    val minDistance = gestures.minDistanceDp.dp.toPx()
    val minVelocity = 600.dp.toPx() // px/s
    awaitPointerEventScope {
        while (true) {
            // Wait for a fresh first finger.
            var event = awaitPointerEvent(PointerEventPass.Initial)
            val first = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
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
                if (maxOf(ax, ay) < minDistance) continue
                val dir = when {
                    ay > ax * 2 -> if (delta.y < 0) Dir.Up else Dir.Down
                    ax > ay * 2 -> if (delta.x < 0) Dir.Left else Dir.Right
                    else -> null
                } ?: continue
                val v = tracker.calculateVelocity()
                val speed = if (dir == Dir.Up || dir == Dir.Down) abs(v.y) else abs(v.x)
                if (speed < minVelocity) continue

                val binding: GestureBinding? = if (maxPointers >= 2) {
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
                onAction(binding.action)
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
    val haptics = LocalHapticFeedback.current
    val motion = LookState.look.motion
    return {
        if (motion.haptics) haptics.performHapticFeedback(if (motion.strongHaptics) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
    }
}

/** Gesture actions reuse the tile runner (folders aren't a gesture target). */
fun TileRunner.runAction(action: TileAction) = run(TileConfig(label = "", action = action))
