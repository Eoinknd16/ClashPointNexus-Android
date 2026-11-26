package com.eoinknd16.nexus.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.ui.theme.Nx

// ---------------------------------------------------------------------------
// Backdrop: a slow-scrolling perspective grid under a drifting nebula, with
// faint scanlines. GPU shader (AGSL) on Android 13+, cheap Canvas version
// below that.
// ---------------------------------------------------------------------------

private const val BACKDROP_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float intensity;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float3 col = float3(0.018, 0.026, 0.05);

    // Nebula: two soft drifting glows.
    float2 p1 = uv - float2(0.25 + 0.08 * sin(time * 0.07), 0.22 + 0.05 * cos(time * 0.05));
    float2 p2 = uv - float2(0.82 + 0.06 * cos(time * 0.06), 0.40 + 0.06 * sin(time * 0.04));
    col += float3(0.10, 0.55, 0.65) * 0.22 * exp(-dot(p1, p1) * 7.0);
    col += float3(0.70, 0.12, 0.45) * 0.16 * exp(-dot(p2, p2) * 9.0);

    // Perspective floor grid below the horizon, scrolling towards you.
    float horizon = 0.64;
    if (uv.y > horizon) {
        float d = uv.y - horizon;
        float z = 0.09 / d;
        float gx = fract((uv.x - 0.5) * z * 5.0);
        float gz = fract(z * 1.6 + time * 0.18);
        float lx = smoothstep(0.93, 1.0, gx) + (1.0 - smoothstep(0.0, 0.07, gx));
        float lz = smoothstep(0.90, 1.0, gz) + (1.0 - smoothstep(0.0, 0.10, gz));
        float fade = smoothstep(0.0, 0.30, d);
        col += float3(0.24, 0.95, 1.0) * (lx * 0.5 + lz) * 0.20 * fade;
    }
    // Horizon glow line.
    col += float3(0.24, 0.95, 1.0) * 0.25 * exp(-abs(uv.y - horizon) * 90.0);

    // Scanlines + vignette.
    col *= 0.93 + 0.07 * sin(fragCoord.y * 1.4);
    float2 v = uv - 0.5;
    col *= 1.0 - dot(v, v) * 0.9;

    return half4(half3(col * intensity), 1.0);
}
"""

@Composable
fun NexusBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Nx.Void)) {
        val shader = remember {
            // A driver that rejects the shader shouldn't take the launcher down with it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(BACKDROP_AGSL) }.getOrNull() else null
        }
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderBackdrop(shader, intensity) else CanvasBackdrop()
        content()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBackdrop(shader: RuntimeShader, intensity: Float) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // ~30fps is plenty for a slow ambient backdrop and halves the GPU cost.
                if (now - last > 33_000_000L) {
                    time = (now - start) / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val brush = ShaderBrush(shader)
            onDrawBehind {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("intensity", intensity)
                drawRect(brush)
            }
        },
    )
}

@Composable
private fun CanvasBackdrop() {
    val t by rememberInfiniteTransition(label = "grid").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0x3329C6D6), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.2f), size.width * 0.8f))
        drawRect(Brush.radialGradient(listOf(Color(0x22B21E73), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.45f), size.width * 0.7f))
        val horizon = size.height * 0.64f
        val cx = size.width / 2
        for (i in -8..8) {
            drawLine(Nx.Line, Offset(cx + i * size.width * 0.04f, horizon), Offset(cx + i * size.width * 0.35f, size.height), 1.5f)
        }
        for (i in 0 until 10) {
            val f = ((i + t) / 10f)
            val y = horizon + (size.height - horizon) * f * f
            drawLine(Nx.Line.copy(alpha = 0.15f + 0.5f * f), Offset(0f, y), Offset(size.width, y), 1.5f)
        }
        drawLine(Nx.Cyan.copy(alpha = 0.4f), Offset(0f, horizon), Offset(size.width, horizon), 2f)
    }
}

// ---------------------------------------------------------------------------
// Holo panel: translucent fill, cut corners, gradient rim, corner brackets.
// ---------------------------------------------------------------------------

val HoloShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

fun Modifier.holoPanel(accent: Color = Nx.Cyan, glow: Float = 0.35f): Modifier = this
    .clip(HoloShape)
    .background(Brush.verticalGradient(listOf(Nx.Panel, Color(0xE6070B14))))
    .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.15f), Nx.Magenta.copy(alpha = 0.5f))), HoloShape)
    .drawBehind {
        // Corner brackets.
        val l = 14.dp.toPx()
        val s = 2.dp.toPx()
        val c = accent.copy(alpha = 0.4f + glow)
        drawLine(c, Offset(size.width - l * 1.6f, 0f), Offset(size.width, 0f), s, StrokeCap.Square)
        drawLine(c, Offset(size.width, 0f), Offset(size.width, l), s, StrokeCap.Square)
        drawLine(c, Offset(0f, size.height), Offset(l * 1.6f, size.height), s, StrokeCap.Square)
        drawLine(c, Offset(0f, size.height - l), Offset(0f, size.height), s, StrokeCap.Square)
    }

@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    accent: Color = Nx.Cyan,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.holoPanel(accent).padding(padding), content = content)
}

/** "// LABEL" section heading in the monospace HUD style. */
@Composable
fun HudLabel(text: String, modifier: Modifier = Modifier, color: Color = Nx.Cyan) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent))))
    }
}

// ---------------------------------------------------------------------------
// Pressables: scale + glow on press, haptic tick, long-press support.
// ---------------------------------------------------------------------------

@Composable
fun Modifier.nexusPress(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return this
        .scale(scale)
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                // Haptic on the tap itself, not on touch-down -- otherwise just
                // scrolling a list of tiles buzzes on every finger landing.
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongPress = onLongClick?.let { lp ->
                    { _: Offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lp()
                    }
                },
            )
        }
}

@Composable
fun HoloTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    accent: Color = Nx.Cyan,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.25f, 0.55f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "glow",
    )
    Column(
        modifier
            .nexusPress(onClick, onLongClick)
            .holoPanel(accent, pulse)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CutCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent) }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Nx.Text, fontWeight = FontWeight.SemiBold)
        if (sublabel != null) Text(sublabel, style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1)
    }
}

@Composable
fun NexusButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Nx.Cyan,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val a = if (enabled) accent else Nx.Muted
    Row(
        modifier
            .then(if (enabled) Modifier.nexusPress(onClick) else Modifier)
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(if (filled) a.copy(alpha = 0.9f) else a.copy(alpha = 0.10f))
            .border(1.dp, a.copy(alpha = 0.7f), CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Nx.Void else a, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (filled) Nx.Void else a, fontWeight = FontWeight.Bold)
    }
}

/** Thin animated progress line with a glowing head. */
@Composable
fun GlowProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Nx.Cyan) {
    Canvas(modifier.height(3.dp)) {
        drawRect(color.copy(alpha = 0.15f))
        val w = size.width * fraction.coerceIn(0f, 1f)
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color)), size = size.copy(width = w))
        drawCircle(color, radius = size.height * 1.6f, center = Offset(w, size.height / 2))
    }
}
