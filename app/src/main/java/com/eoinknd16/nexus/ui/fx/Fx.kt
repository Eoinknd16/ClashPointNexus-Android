package com.eoinknd16.nexus.ui.fx

import androidx.compose.ui.graphics.graphicsLayer

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.look.BackdropStyle
import com.eoinknd16.nexus.look.BorderOverride
import com.eoinknd16.nexus.look.BorderStyle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.eoinknd16.nexus.look.CornerStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Backdrop. One AGSL shader with five looks (grid horizon, nebula, starfield,
// waves, plain), coloured from the live palette and tuned by Customise >
// Backdrop. Android 13+; a Canvas approximation below that.
// ---------------------------------------------------------------------------

private const val BACKDROP_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float intensity;
uniform float style;      // 0 grid, 1 nebula, 2 stars, 3 waves, 4 plain
uniform float scan;       // 0/1
uniform float vignette;   // 0/1
uniform float3 bg;
uniform float3 accent;
uniform float3 accent2;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float3 col = bg;

    if (style < 3.5) {
        // Nebula glows (every style except plain gets some).
        float neb = style > 0.5 && style < 1.5 ? 1.0 : 0.6;
        float2 p1 = uv - float2(0.25 + 0.08 * sin(time * 0.07), 0.22 + 0.05 * cos(time * 0.05));
        float2 p2 = uv - float2(0.82 + 0.06 * cos(time * 0.06), 0.40 + 0.06 * sin(time * 0.04));
        float2 p3 = uv - float2(0.55 + 0.10 * sin(time * 0.03), 0.75);
        col += accent * 0.22 * neb * exp(-dot(p1, p1) * 7.0);
        col += accent2 * 0.16 * neb * exp(-dot(p2, p2) * 9.0);
        if (style > 0.5 && style < 1.5) col += mix(accent, accent2, 0.5) * 0.10 * exp(-dot(p3, p3) * 5.0);
    }

    if (style < 0.5) {
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
            col += accent * (lx * 0.5 + lz) * 0.20 * fade;
        }
        col += accent * 0.25 * exp(-abs(uv.y - horizon) * 90.0);
    } else if (style > 1.5 && style < 2.5) {
        // Starfield drifting upward, two depth layers.
        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 60.0 : 120.0;
            float speed = layer == 0 ? 0.02 : 0.008;
            float2 g = float2(uv.x * scale, (uv.y + time * speed) * scale * resolution.y / resolution.x);
            float2 cell = floor(g);
            float h = hash(cell + float(layer) * 17.0);
            if (h > 0.965) {
                float2 f = fract(g) - 0.5;
                float twinkle = 0.6 + 0.4 * sin(time * 2.0 + h * 40.0);
                float star = exp(-dot(f, f) * 60.0) * twinkle;
                col += mix(accent, float3(1.0), 0.6) * star * (layer == 0 ? 0.9 : 0.5);
            }
        }
    } else if (style > 2.5 && style < 3.5) {
        // Layered glowing sine waves.
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float y = 0.55 + 0.06 * fi + 0.05 * sin(uv.x * (6.0 + fi * 2.0) + time * (0.4 + fi * 0.15) + fi);
            float glow = exp(-abs(uv.y - y) * (120.0 - fi * 20.0));
            col += mix(accent, accent2, fi / 3.0) * glow * 0.35;
        }
    }

    if (scan > 0.5) col *= 0.93 + 0.07 * sin(fragCoord.y * 1.4);
    if (vignette > 0.5) {
        float2 v = uv - 0.5;
        col *= 1.0 - dot(v, v) * 0.9;
    }
    return half4(half3(mix(bg, col, intensity)), 1.0);
}
"""

@Composable
fun NexusBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    val b = LookState.look.backdrop
    val m = LookState.look.motion
    Box(modifier.fillMaxSize().background(Nx.Void)) {
        if (b.style != BackdropStyle.Plain || b.scanlines) Box(
            // Deepest layer: drifts with the phone's tilt (see Tilt).
            Modifier.fillMaxSize().then(
                if (m.parallax) Modifier.graphicsLayer {
                    val px = 14.dp.toPx() * m.parallaxStrength.coerceIn(0f, 3f)
                    scaleX = 1f + 0.06f * m.parallaxStrength.coerceIn(0f, 3f)
                    scaleY = scaleX
                    translationX = Tilt.x * px
                    translationY = Tilt.y * px
                } else Modifier,
            ),
        ) {
            val shader = remember {
                // A driver that rejects the shader shouldn't take the launcher down with it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(BACKDROP_AGSL) }.getOrNull() else null
            }
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ShaderBackdrop(shader, (intensity * b.intensity).coerceIn(0f, 2f))
            } else {
                CanvasBackdrop(intensity * b.intensity)
            }
        }
        content()
    }
}

/**
 * How far the phone is tilted from how you're holding it, -1..1 each way
 * (x right, y down). The resting angle adapts slowly, so whatever angle you
 * hold it at is "level"; only movement shows. Updated by [TiltSensor]; read
 * in draw/layer blocks only, so it never causes recomposition.
 */
object Tilt {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/** Feeds [Tilt] from the gravity sensor while the calling screen is resumed. */
@Composable
fun TiltSensor() {
    val m = LookState.look.motion
    if (!m.parallax && !m.sheen) return
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var bx = Float.NaN
        var by = 0f
        var sx = 0f
        var sy = 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val gx = -e.values[0] / 9.81f
                val gy = e.values[1] / 9.81f
                if (bx.isNaN()) { bx = gx; by = gy }
                // Slow baseline = how you're holding it; fast smoothing = the movement.
                bx += (gx - bx) * 0.012f
                by += (gy - by) * 0.012f
                sx += ((gx - bx) - sx) * 0.18f
                sy += ((gy - by) - sy) * 0.18f
                Tilt.x = (sx * 3.2f).coerceIn(-1f, 1f)
                Tilt.y = (-sy * 3.2f).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            sm?.unregisterListener(listener)
            Tilt.x = 0f
            Tilt.y = 0f
        }
    }
}

@Composable
private fun rememberBackdropTime(): Float {
    val b = LookState.look.backdrop
    var time by remember { mutableFloatStateOf(12f) }
    LaunchedEffect(b.animated, b.fps, b.speed) {
        if (!b.animated) return@LaunchedEffect
        // 0 = every display frame (60/90/120Hz...), otherwise a cap.
        val frameNs = if (b.fps <= 0) 0L else 1_000_000_000L / b.fps.coerceIn(10, 144)
        var last = 0L
        var prev = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (now - last >= frameNs) {
                    time += (now - prev) / 1_000_000_000f * b.speed
                    prev = now
                    last = now
                }
            }
        }
    }
    return time
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBackdrop(shader: RuntimeShader, intensity: Float) {
    val time = rememberBackdropTime()
    val look = LookState.look
    val p = LookState.palette
    val style = when (look.backdrop.style) {
        BackdropStyle.Grid -> 0f
        BackdropStyle.Nebula -> 1f
        BackdropStyle.Stars -> 2f
        BackdropStyle.Waves -> 3f
        BackdropStyle.Plain -> 4f
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val brush = ShaderBrush(shader)
            onDrawBehind {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("intensity", intensity)
                shader.setFloatUniform("style", style)
                shader.setFloatUniform("scan", if (look.backdrop.scanlines) 1f else 0f)
                shader.setFloatUniform("vignette", if (look.backdrop.vignette) 1f else 0f)
                shader.setFloatUniform("bg", p.background.red, p.background.green, p.background.blue)
                shader.setFloatUniform("accent", p.accent.red, p.accent.green, p.accent.blue)
                shader.setFloatUniform("accent2", p.accent2.red, p.accent2.green, p.accent2.blue)
                drawRect(brush)
            }
        },
    )
}

@Composable
private fun CanvasBackdrop(intensity: Float) {
    val t = rememberBackdropTime()
    val style = LookState.look.backdrop.style
    val a = Nx.Cyan
    val a2 = Nx.Magenta
    Canvas(Modifier.fillMaxSize()) {
        val k = intensity.coerceIn(0f, 2f)
        drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.2f * k), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.2f), size.width * 0.8f))
        drawRect(Brush.radialGradient(listOf(a2.copy(alpha = 0.13f * k), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.45f), size.width * 0.7f))
        when (style) {
            BackdropStyle.Grid -> {
                val horizon = size.height * 0.64f
                val cx = size.width / 2
                val line = a.copy(alpha = 0.2f * k)
                for (i in -8..8) drawLine(line, Offset(cx + i * size.width * 0.04f, horizon), Offset(cx + i * size.width * 0.35f, size.height), 1.5f)
                val phase = (t * 0.18f) % 1f
                for (i in 0 until 10) {
                    val f = (i + phase) / 10f
                    val y = horizon + (size.height - horizon) * f * f
                    drawLine(a.copy(alpha = (0.1f + 0.35f * f) * k), Offset(0f, y), Offset(size.width, y), 1.5f)
                }
                drawLine(a.copy(alpha = 0.4f * k), Offset(0f, horizon), Offset(size.width, horizon), 2f)
            }
            BackdropStyle.Waves -> for (i in 0 until 4) {
                var prev: Offset? = null
                val steps = 48
                for (s in 0..steps) {
                    val x = size.width * s / steps
                    val y = size.height * (0.55f + 0.06f * i + 0.05f * sin(x / size.width * (6 + i * 2) + t * (0.4f + i * 0.15f) + i))
                    val pt = Offset(x, y)
                    prev?.let { drawLine((if (i % 2 == 0) a else a2).copy(alpha = 0.35f * k), it, pt, 2f) }
                    prev = pt
                }
            }
            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------
// Shapes + holo panel, from Customise > Panels.
// ---------------------------------------------------------------------------

/** The panel shape, as configured (angled cut / rounded / square / pill). */
fun nexusShape(scale: Float = 1f): Shape {
    val s = LookState.look.shape
    val size = (s.cornerSize * scale).dp
    return when (s.corners) {
        CornerStyle.Cut -> CutCornerShape(topStart = size, bottomEnd = size)
        CornerStyle.Round -> RoundedCornerShape(size)
        CornerStyle.Square -> RectangleShape
        CornerStyle.Pill -> RoundedCornerShape(50)
    }
}

/** Kept for call sites that used the original constant. */
val HoloShape: Shape get() = nexusShape()

/**
 * One shared clock for animated borders, advanced by [NeonClockDriver] (once,
 * at the app root) only while a neon style is in use. Panels read it in the
 * draw phase, so it only redraws -- never recomposes -- the panels.
 */
object NeonClock {
    var time by mutableFloatStateOf(0f)
}

@Composable
fun NeonClockDriver() {
    val look = LookState.look
    val needed = look.motion.animations && (look.shape.borderStyle == BorderStyle.Neon ||
        (look.home.blocks + look.home.pages.flatMap { it.blocks }).any { b -> b is com.eoinknd16.nexus.look.HomeBlock.Tiles && b.tiles.any { it.border?.style == BorderStyle.Neon } } ||
        look.home.dock.items.any { it.border?.style == BorderStyle.Neon })
    LaunchedEffect(needed, look.motion.speed) {
        if (!needed) return@LaunchedEffect
        var prev = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                NeonClock.time += (now - prev) / 1_000_000_000f * look.motion.speed
                prev = now
            }
        }
    }
}

/**
 * The holo panel: translucent fill, configurable shape, and a rim in any of
 * the border styles (Customise › Panels, or per tile via [override]).
 * Glow is drawn before the clip so it can spill outside the panel.
 */
fun Modifier.holoPanel(accent: Color = Nx.Cyan, glow: Float = 0.35f, override: BorderOverride? = null): Modifier {
    val s = LookState.look.shape
    val p = LookState.palette
    val shape = nexusShape()
    val style = override?.style ?: if (!s.gradientBorder && s.borderStyle == BorderStyle.Gradient) BorderStyle.Solid else s.borderStyle
    val width = (override?.width ?: s.borderWidth).coerceAtLeast(0f)
    val rimColor = override?.color?.let { p.slot(it) } ?: if (s.borderColor == "accent") accent else p.slot(s.borderColor)
    val glowAmount = ((override?.glow ?: s.glow) * (glow / 0.35f)).coerceIn(0f, 1.5f)
    val glowRadius = s.glowRadius + if (style == BorderStyle.Glow) 6f else 0f
    val neonPeriod = s.neonSpeed.coerceIn(0.5f, 30f)
    val sheen = LookState.look.motion.sheen

    return this
        .drawBehind {
            if (glowRadius <= 0f || glowAmount <= 0f) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            // Three soft passes, widest + faintest first.
            for (i in 3 downTo 1) {
                drawOutline(outline, rimColor.copy(alpha = 0.07f * glowAmount * (4 - i)), style = Stroke(width = (glowRadius * i / 1.5f).dp.toPx()))
            }
        }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Nx.Panel, Nx.Panel.copy(alpha = (Nx.Panel.alpha + 0.1f).coerceAtMost(1f)))))
        .drawWithContent {
            drawContent()
            if (sheen) {
                // Light from the direction the phone is tipped: a soft glint
                // across the glass, and a brighter catch on the facing rim.
                val lx = size.width * (0.5f - Tilt.x * 0.7f)
                val ly = size.height * (0.15f - Tilt.y * 0.7f)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.02f), Color.Transparent),
                        center = Offset(lx, ly), radius = maxOf(size.width, size.height) * 0.75f,
                    ),
                )
                if (width > 0f && style != BorderStyle.None) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline,
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(lx, ly), radius = maxOf(size.width, size.height) * 0.55f),
                        style = Stroke(width.dp.toPx() * 2f),
                    )
                }
            }
            if (width <= 0f || style == BorderStyle.None || style == BorderStyle.Glow) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            val w = width.dp.toPx() * (if (style == BorderStyle.Double) 1f else 2f) // stroke is centred on the clipped edge
            when (style) {
                BorderStyle.Gradient -> drawOutline(outline, Brush.linearGradient(listOf(rimColor.copy(alpha = 0.9f), rimColor.copy(alpha = 0.15f), Nx.Magenta.copy(alpha = 0.5f))), style = Stroke(w))
                BorderStyle.Solid -> drawOutline(outline, rimColor.copy(alpha = 0.75f), style = Stroke(w))
                BorderStyle.Dashed -> drawOutline(outline, rimColor.copy(alpha = 0.8f), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))))
                BorderStyle.Double -> {
                    drawOutline(outline, rimColor.copy(alpha = 0.8f), style = Stroke(w * 2))
                    val inset = (width * 3 + 2).dp.toPx()
                    val inner = shape.createOutline(androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
                    translate(inset, inset) { drawOutline(inner, rimColor.copy(alpha = 0.45f), style = Stroke(width.dp.toPx())) }
                }
                BorderStyle.Neon -> {
                    // A bright arc chasing round the rim.
                    val phase = (NeonClock.time / neonPeriod) % 1f
                    val c2 = Nx.Magenta
                    val stops = listOf(0f to rimColor.copy(alpha = 0.15f), 0.35f to rimColor, 0.5f to c2, 0.65f to rimColor, 1f to rimColor.copy(alpha = 0.15f))
                    val rotated = stops.map { (f, c) -> ((f + phase) % 1f) to c }.sortedBy { it.first }
                    val brush = Brush.sweepGradient(*rotated.toTypedArray(), center = Offset(size.width / 2, size.height / 2))
                    drawOutline(outline, brush, style = Stroke(w * 1.4f))
                }
                else -> {}
            }
            if (s.brackets && s.glow > 0f && style != BorderStyle.Neon) {
                val l = 14.dp.toPx()
                val bw = 2.dp.toPx()
                val c = rimColor.copy(alpha = (0.4f + glowAmount).coerceAtMost(1f))
                drawLine(c, Offset(size.width - l * 1.6f, 0f), Offset(size.width, 0f), bw * 2, StrokeCap.Square)
                drawLine(c, Offset(size.width, 0f), Offset(size.width, l), bw * 2, StrokeCap.Square)
                drawLine(c, Offset(0f, size.height), Offset(l * 1.6f, size.height), bw * 2, StrokeCap.Square)
                drawLine(c, Offset(0f, size.height - l), Offset(0f, size.height), bw * 2, StrokeCap.Square)
            }
        }
}

// ---- Motion helpers (Customise › Motion: speed + bounce) -------------------------

/** A spring tuned by the user's bounce and speed settings. */
fun <T> nexusSpring(): androidx.compose.animation.core.SpringSpec<T> {
    val m = LookState.look.motion
    val damping = (1f - m.bounce.coerceIn(0f, 1f) * 0.65f) // 1.0 (no bounce) .. 0.35 (bouncy)
    return spring(dampingRatio = damping, stiffness = 500f * m.speed.coerceIn(0.25f, 4f) * m.speed.coerceIn(0.25f, 4f))
}

/** Duration scaled by the user's speed setting. */
fun nexusMs(base: Int): Int = (base / LookState.look.motion.speed.coerceIn(0.25f, 4f)).toInt().coerceAtLeast(1)

@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    accent: Color = Nx.Cyan,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.holoPanel(accent).padding(padding), content = content)
}

/** "// LABEL" section heading in the HUD style. */
@Composable
fun HudLabel(text: String, modifier: Modifier = Modifier, color: Color = Nx.Cyan) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(hud(text), style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent))))
    }
}

// ---------------------------------------------------------------------------
// Pressables: scale on press + haptic, both from Customise > Motion.
// ---------------------------------------------------------------------------

@Composable
fun Modifier.nexusPress(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val haptics = LocalHapticFeedback.current
    val motion = LookState.look.motion
    var pressed by remember { mutableStateOf(false) }
    val target = if (pressed) motion.pressScale.coerceIn(0.8f, 1f) else 1f
    val scale by animateFloatAsState(
        target,
        if (motion.animations) nexusSpring() else snap(),
        label = "press",
    )
    return this
        .scale(scale)
        .pointerInput(onClick, onLongClick, motion.haptics) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                // Haptic on the tap itself, not on touch-down -- otherwise just
                // scrolling a list of tiles buzzes on every finger landing.
                onTap = {
                    if (motion.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongPress = onLongClick?.let { lp ->
                    { _: Offset ->
                        if (motion.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    val motion = LookState.look.motion
    val pulse = if (motion.pulse && motion.animations) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.25f, 0.55f, infiniteRepeatable(tween(nexusMs(2400)), RepeatMode.Reverse), label = "glow",
        ).value
    } else 0.35f
    Column(
        modifier
            .nexusPress(onClick, onLongClick)
            .holoPanel(accent, pulse)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(nexusShape(0.55f))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent) }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Nx.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!sublabel.isNullOrBlank()) Text(hud(sublabel), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1)
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
    val shape = nexusShape(0.6f)
    Row(
        modifier
            .then(if (enabled) Modifier.nexusPress(onClick) else Modifier)
            .clip(shape)
            .background(if (filled) a.copy(alpha = 0.9f) else a.copy(alpha = 0.10f))
            .border(1.dp, a.copy(alpha = 0.7f), shape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Nx.Void else a, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(hud(text), style = MaterialTheme.typography.labelMedium, color = if (filled) Nx.Void else a, fontWeight = FontWeight.Bold)
    }
}

/** Thin progress line with a glowing head. */
@Composable
fun GlowProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Nx.Cyan) {
    Canvas(modifier.height(3.dp)) {
        drawRect(color.copy(alpha = 0.15f))
        val w = size.width * fraction.coerceIn(0f, 1f)
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color)), size = size.copy(width = w))
        drawCircle(color, radius = size.height * 1.6f, center = Offset(w, size.height / 2))
    }
}
