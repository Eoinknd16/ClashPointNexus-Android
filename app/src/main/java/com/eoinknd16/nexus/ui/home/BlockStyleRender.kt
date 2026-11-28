package com.eoinknd16.nexus.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.look.BlockBg
import com.eoinknd16.nexus.look.BlockStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.fx.Tilt
import com.eoinknd16.nexus.ui.fx.nexusShape
import kotlin.math.cos
import kotlin.math.sin

/** Wraps any home block in its own background, corners, border, glow and motion. */
@Composable
fun StyledBlock(style: BlockStyle?, content: @Composable () -> Unit) {
    if (style == null || style == BlockStyle()) { content(); return }
    val p = LookState.palette
    val shape = if (style.corner < 0f) nexusShape() else RoundedCornerShape(style.corner.coerceIn(0f, 60f).dp)
    val c1 = p.slot(style.color1)
    val c2 = p.slot(style.color2)
    val glowC = p.slot(style.glowColor)
    val hasSurface = style.bg != BlockBg.None || style.borderWidth > 0f
    var m: Modifier = Modifier.fillMaxWidth().padding(top = style.marginTop.dp, bottom = style.marginBottom.dp)
    if (style.tilt3d || style.rotate != 0f || style.alpha < 1f) m = m.graphicsLayer {
        if (style.tilt3d) { rotationY = Tilt.x * 6f; rotationX = -Tilt.y * 6f; cameraDistance = 16f * density }
        rotationZ = style.rotate
        alpha = style.alpha.coerceIn(0.1f, 1f)
    }
    if (style.glow > 0f) m = m.shadow(style.glow.dp, shape, clip = false, ambientColor = glowC, spotColor = glowC)
    else if (style.shadow > 0f) m = m.shadow(style.shadow.dp, shape, clip = false)
    if (hasSurface) m = m.clip(shape)
    val a = style.opacity.coerceIn(0f, 1f)
    m = when (style.bg) {
        BlockBg.None -> m
        BlockBg.Panel -> m.drawBehind { drawRect(p.panel) }
        BlockBg.Solid -> m.drawBehind { drawRect(c1.copy(alpha = c1.alpha * a)) }
        BlockBg.Glass -> m.drawBehind {
            drawRect(Color.White.copy(alpha = 0.07f * (0.4f + a)))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)))
        }
        BlockBg.Gradient -> m.drawBehind {
            val r = Math.toRadians(style.angle.toDouble())
            val dx = cos(r).toFloat() * size.width / 2; val dy = sin(r).toFloat() * size.height / 2
            val ctr = Offset(size.width / 2, size.height / 2)
            drawRect(Brush.linearGradient(listOf(c1.copy(alpha = a), c2.copy(alpha = a)), ctr - Offset(dx, dy), ctr + Offset(dx, dy)))
        }
        BlockBg.Mesh -> m.drawBehind {
            drawRect(p.deep.copy(alpha = 0.6f))
            listOf(c1 to Offset(0.2f, 0.3f), c2 to Offset(0.85f, 0.7f), p.warn to Offset(0.6f, 0.1f)).forEach { (col, o) ->
                drawRect(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), Offset(size.width * o.x, size.height * o.y), maxOf(size.width, size.height) * 0.7f))
            }
        }
    }
    if (style.shine) m = m.drawBehind {
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), Offset.Zero, Offset(size.width * 0.5f, size.height * 0.8f)))
    }
    if (style.borderWidth > 0f) {
        val bc = p.slot(style.borderColor)
        m = if (style.borderGradient) m.border(style.borderWidth.dp, Brush.linearGradient(listOf(p.accent, p.accent2)), shape)
        else m.border(style.borderWidth.dp, bc, shape)
    }
    Box(m.padding(if (hasSurface) style.padding.coerceIn(0f, 40f).dp else 0.dp)) { content() }
}
