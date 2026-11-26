package com.eoinknd16.nexus.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.eoinknd16.nexus.look.FitMode
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays [content] out at its natural size, then -- if it's taller than the
 * [viewportPx] it has to sit in -- shrinks it uniformly (from the top centre)
 * so it fits exactly, as long as the overflow is small enough for [mode].
 * Everything keeps its proportions, so the result is as close as possible to
 * what was set; it just stops the screen being a few pixels scrollable.
 * Put it inside the scroll container: when it fits, there's nothing left to scroll.
 */
@Composable
fun AutoFit(
    viewportPx: Int,
    mode: FitMode,
    tolerance: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = Constraints(minWidth = constraints.minWidth, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val natural = placeables.sumOf { it.height }
        val overflow = natural - viewportPx
        val scale = when {
            viewportPx <= 0 || overflow <= 0 || mode == FitMode.Off -> 1f
            mode == FitMode.Always -> (viewportPx.toFloat() / natural).coerceAtLeast(0.5f)
            overflow <= viewportPx * tolerance.coerceIn(0f, 0.6f) -> viewportPx.toFloat() / natural
            else -> 1f
        }
        val height = if (scale < 1f) min(viewportPx, (natural * scale).roundToInt()) else natural
        layout(width, height) {
            var y = 0
            placeables.forEach { p ->
                if (scale < 1f) {
                    p.placeWithLayer(0, (y * scale).roundToInt()) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                } else {
                    p.place(0, y)
                }
                y += p.height
            }
        }
    }
}
