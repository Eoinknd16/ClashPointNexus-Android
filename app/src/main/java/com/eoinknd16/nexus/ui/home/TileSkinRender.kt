package com.eoinknd16.nexus.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eoinknd16.nexus.look.BorderOverride
import com.eoinknd16.nexus.look.BorderStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.SkinAnim
import com.eoinknd16.nexus.look.SkinCase
import com.eoinknd16.nexus.look.SkinFill
import com.eoinknd16.nexus.look.SkinFont
import com.eoinknd16.nexus.look.SkinPattern
import com.eoinknd16.nexus.look.SkinShadow
import com.eoinknd16.nexus.look.SkinShape
import com.eoinknd16.nexus.look.TileSkin
import com.eoinknd16.nexus.look.family
import com.eoinknd16.nexus.ui.fx.NeonClock
import com.eoinknd16.nexus.ui.fx.Tilt
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** A colour setting: "" = the tile's own colour, otherwise a palette slot or #hex. */
fun skinColour(key: String, tileColour: Color): Color = if (key.isBlank()) tileColour else LookState.palette.slot(key)

// ---------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------

fun skinShape(skin: TileSkin): Shape = when (skin.shape) {
    SkinShape.Theme -> nexusShape()
    SkinShape.Rounded -> RoundedCornerShape(skin.corner.dp)
    SkinShape.Squircle -> SquircleShape
    SkinShape.Cut -> CutCornerShape(skin.corner.coerceAtMost(40f).dp)
    SkinShape.Pill -> RoundedCornerShape(50)
    SkinShape.Leaf -> RoundedCornerShape(topStart = (skin.corner * 2).dp, bottomEnd = (skin.corner * 2).dp, topEnd = 4.dp, bottomStart = 4.dp)
    SkinShape.Ticket -> TicketShape(skin.corner)
    SkinShape.Square -> RectangleShape
}

/** iOS-style continuous corners (a superellipse). */
private object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val a = size.width / 2; val b = size.height / 2
        val n = 4.0
        for (i in 0..72) {
            val t = 2 * PI * i / 72
            val c = cos(t); val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2 / n)
            val y = b + b * sign(s) * abs(s).pow(2 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Rounded rectangle with a semicircle bitten out of each side, like a ticket stub. */
private class TicketShape(val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.coerceAtMost(30f).dp.toPx() }
        val notch = minOf(size.height * 0.12f, with(density) { 12.dp.toPx() })
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
        val bites = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(0f, size.height / 2), notch))
            addOval(androidx.compose.ui.geometry.Rect(Offset(size.width, size.height / 2), notch))
        }
        return Outline.Generic(Path().apply { op(body, bites, PathOperation.Difference) })
    }
}

// ---------------------------------------------------------------------------
// The tile's surface: shadow, fill, pattern, shine, glow, border, animation
// ---------------------------------------------------------------------------

/**
 * Draws a tile in its [skin]. [accent] is the tile's colour; [appColour] is
 * the main colour of its app icon (for the "From the app's icon" fill).
 */
fun Modifier.tileSkin(skin: TileSkin, accent: Color, border: BorderOverride?, pulse: Float, appColour: Color?): Modifier {
    val shape = skinShape(skin)
    val c1 = if (skin.fill == SkinFill.AppColour) (appColour ?: accent) else skinColour(skin.color1, accent)
    val c2 = skinColour(skin.color2, c1)
    val c3 = skinColour(skin.color3, c2)
    val shadowC = skinColour(skin.shadowColor, if (skin.shadow == SkinShadow.Glow) accent else Color.Black)
    val speed = skin.animSpeed.coerceIn(0.1f, 4f)
    val sh = LookState.look.shape
    val p = LookState.palette

    var m: Modifier = this
    // 3D: tips with the phone (read in the layer block only, so it never recomposes).
    if (skin.tilt3d) m = m.graphicsLayer {
        rotationY = Tilt.x * 9f
        rotationX = -Tilt.y * 9f
        cameraDistance = 14f * density
    }
    // Shadows sit outside the shape.
    m = when (skin.shadow) {
        SkinShadow.None -> m
        SkinShadow.Soft -> m.shadow(skin.shadowSize.coerceIn(0f, 40f).dp, shape, clip = false, ambientColor = shadowC, spotColor = shadowC)
        SkinShadow.Floating -> m.graphicsLayer { translationY = -2.dp.toPx() }
            .shadow((skin.shadowSize * 1.8f).coerceIn(0f, 60f).dp, shape, clip = false, ambientColor = shadowC, spotColor = shadowC)
        SkinShadow.Hard -> m.drawBehind {
            val o = skin.shadowSize.coerceIn(1f, 20f).dp.toPx()
            translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), shadowC) }
        }
        SkinShadow.Neumorphic -> m.drawBehind {
            val o = (skin.shadowSize * 0.45f).dp.toPx()
            val blur = skin.shadowSize.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            blurred(outline, Color.White.copy(alpha = 0.07f), blur, -o, -o)
            blurred(outline, Color.Black.copy(alpha = 0.65f), blur, o, o)
        }
        SkinShadow.Glow -> m.drawBehind {
            blurred(shape.createOutline(size, layoutDirection, this), shadowC.copy(alpha = 0.55f * (pulse / 0.35f).coerceIn(0.6f, 1.6f)), skin.shadowSize.dp.toPx(), 0f, 0f)
        }
    }

    // Fill.
    m = if (skin.fill == SkinFill.Theme) {
        m.holoPanel(accent, pulse, border, shape)
    } else {
        m.clip(shape).drawBehind {
            val t = NeonClock.time * speed
            val alpha = skin.opacity.coerceIn(0f, 1f)
            when (skin.fill) {
                SkinFill.Solid -> drawRect(c1.copy(alpha = c1.alpha * alpha))
                SkinFill.AppColour -> drawRect(
                    Brush.linearGradient(
                        listOf(c1.copy(alpha = alpha), lerpColour(c1, Color.Black, 0.35f).copy(alpha = alpha)),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    ),
                )
                SkinFill.Gradient -> {
                    val a = Math.toRadians((skin.angle + if (skin.anim == SkinAnim.Flow) t * 45f else 0f).toDouble())
                    val cx = size.width / 2; val cy = size.height / 2
                    val r = maxOf(size.width, size.height) * 0.6f
                    val dx = (cos(a) * r).toFloat(); val dy = (sin(a) * r).toFloat()
                    val colours = if (skin.color3.isBlank()) listOf(c1, c2) else listOf(c1, c2, c3)
                    drawRect(Brush.linearGradient(colours.map { it.copy(alpha = it.alpha * alpha) }, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy)))
                }
                SkinFill.Glass -> {
                    drawRect(c1.copy(alpha = 0.10f * alpha))
                    drawRect(Color.White.copy(alpha = 0.06f * alpha))
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f * alpha), Color.Transparent), Offset.Zero, Offset(size.width * 0.8f, size.height)))
                }
                SkinFill.Mesh -> {
                    drawRect(lerpColour(c1, Color.Black, 0.7f).copy(alpha = alpha))
                    val moving = skin.anim == SkinAnim.Aurora || skin.anim == SkinAnim.Flow
                    val blobs = listOf(c1 to 0f, c2 to 2.1f, c3 to 4.2f)
                    blobs.forEach { (col, ph) ->
                        val bx = size.width * (0.5f + 0.38f * cos(ph + if (moving) t * 0.6f else 0f))
                        val by = size.height * (0.5f + 0.38f * sin(ph * 1.3f + if (moving) t * 0.45f else 0f))
                        drawRect(Brush.radialGradient(listOf(col.copy(alpha = 0.85f * alpha), Color.Transparent), Offset(bx, by), maxOf(size.width, size.height) * 0.75f))
                    }
                }
                SkinFill.Outline, SkinFill.None, SkinFill.Theme -> {}
            }
        }
    }

    // Overlays on top of the fill (under the icon and text).
    m = m.drawBehind {
        val t = NeonClock.time * speed
        if (skin.pattern != SkinPattern.None) drawPattern(skin.pattern, Color.White.copy(alpha = skin.patternOpacity.coerceIn(0f, 1f)), skin.patternScale.coerceIn(0.4f, 3f))
        when (skin.anim) {
            SkinAnim.Breathe -> drawRect(accent.copy(alpha = 0.05f + 0.07f * (0.5f + 0.5f * sin(t * 2f))))
            SkinAnim.Shimmer -> {
                val x = ((t * 0.35f) % 1.8f - 0.4f) * size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        Offset(x - size.width * 0.25f, 0f), Offset(x + size.width * 0.25f, size.height),
                    ),
                )
            }
            else -> {}
        }
    }

    // On top of everything: shine, inner glow, the border, animated edges.
    m = m.drawWithContent {
        drawContent()
        val t = NeonClock.time * speed
        val outline = shape.createOutline(size, layoutDirection, this)
        if (skin.shine) drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.04f), Color.Transparent), 0f, size.height * 0.55f),
            size = Size(size.width, size.height * 0.55f),
        )
        if (skin.innerGlow > 0f) {
            val g = skin.innerGlow.coerceIn(0f, 1f)
            drawOutline(outline, accent.copy(alpha = 0.18f * g), style = Stroke(14.dp.toPx() * g))
            drawOutline(outline, accent.copy(alpha = 0.35f * g), style = Stroke(4.dp.toPx() * g))
        }
        if (skin.fill != SkinFill.Theme) {
            // Border (the theme panel draws its own).
            val style = border?.style ?: when (skin.fill) {
                SkinFill.Outline -> BorderStyle.Solid
                SkinFill.Glass -> BorderStyle.Solid
                else -> if (border?.width != null) BorderStyle.Solid else BorderStyle.None
            }
            val width = (border?.width ?: if (skin.fill == SkinFill.Outline) 1.5f else 1f).coerceAtLeast(0f).dp.toPx() * 2
            val rim = border?.color?.let { p.slot(it) } ?: if (skin.fill == SkinFill.Glass) Color.White.copy(alpha = 0.28f) else accent
            if (width > 0f) when (style) {
                BorderStyle.Solid -> drawOutline(outline, rim.copy(alpha = rim.alpha * 0.85f), style = Stroke(width))
                BorderStyle.Dashed -> drawOutline(outline, rim, style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))))
                BorderStyle.Gradient -> drawOutline(outline, Brush.linearGradient(listOf(rim, rim.copy(alpha = 0.15f), Nx.Magenta.copy(alpha = 0.6f))), style = Stroke(width))
                BorderStyle.Double -> {
                    drawOutline(outline, rim.copy(alpha = 0.8f), style = Stroke(width))
                    val inset = 5.dp.toPx()
                    val inner = shape.createOutline(Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
                    translate(inset, inset) { drawOutline(inner, rim.copy(alpha = 0.45f), style = Stroke(width / 2)) }
                }
                BorderStyle.Neon -> {
                    val phase = (NeonClock.time / sh.neonSpeed.coerceIn(0.5f, 30f)) % 1f
                    val stops = listOf(0f to rim.copy(alpha = 0.15f), 0.35f to rim, 0.5f to Nx.Magenta, 0.65f to rim, 1f to rim.copy(alpha = 0.15f))
                        .map { (f, c) -> ((f + phase) % 1f) to c }.sortedBy { it.first }
                    drawOutline(outline, Brush.sweepGradient(*stops.toTypedArray(), center = center), style = Stroke(width * 1.4f))
                }
                else -> {}
            }
        }
        when (skin.anim) {
            SkinAnim.Pulse -> drawOutline(outline, accent.copy(alpha = 0.3f + 0.6f * (0.5f + 0.5f * sin(t * 3f))), style = Stroke(3.dp.toPx()))
            SkinAnim.Rainbow -> {
                val hues = (0..6).map { Color.hsv(((it * 60f) + t * 90f) % 360f, 0.85f, 1f) }
                drawOutline(outline, Brush.sweepGradient(hues, center), style = Stroke(4.dp.toPx()))
            }
            else -> {}
        }
    }
    return m
}

private fun DrawScope.blurred(outline: Outline, colour: Color, blur: Float, dx: Float, dy: Float) {
    if (blur <= 0f) return
    val path = Path().apply { addOutline(outline) }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = colour.toArgb()
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.translate(dx, dy)
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        canvas.nativeCanvas.restore()
    }
}

private fun lerpColour(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)

/** Procedural texture over the fill. */
private fun DrawScope.drawPattern(pattern: SkinPattern, ink: Color, scale: Float) {
    val u = 8.dp.toPx() * scale
    val w = size.width; val h = size.height
    when (pattern) {
        SkinPattern.Dots -> {
            var y = u / 2
            while (y < h) { var x = u / 2; while (x < w) { drawCircle(ink, u * 0.12f, Offset(x, y)); x += u }; y += u }
        }
        SkinPattern.Grid -> {
            var x = 0f; while (x < w) { drawLine(ink, Offset(x, 0f), Offset(x, h), 1f); x += u * 1.5f }
            var y = 0f; while (y < h) { drawLine(ink, Offset(0f, y), Offset(w, y), 1f); y += u * 1.5f }
        }
        SkinPattern.Diagonal -> {
            var k = -h; while (k < w) { drawLine(ink, Offset(k, h), Offset(k + h, 0f), 1.5f); k += u }
        }
        SkinPattern.Scanlines -> {
            var y = 0f; while (y < h) { drawLine(ink, Offset(0f, y), Offset(w, y), 1f); y += 3.dp.toPx() * scale }
        }
        SkinPattern.Grain -> {
            val rnd = java.util.Random(42)
            repeat(((w * h) / (u * u) * 6).toInt().coerceAtMost(4000)) {
                drawCircle(ink, 0.7f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
            }
        }
        SkinPattern.Carbon -> {
            val s = u * 0.75f
            var y = 0f; var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) 0f else s
                while (x < w) {
                    drawRect(Brush.linearGradient(listOf(ink, Color.Transparent), Offset(x, y), Offset(x + s, y + s)), Offset(x, y), Size(s, s))
                    x += s * 2
                }
                y += s; row++
            }
        }
        SkinPattern.Hex -> {
            val r = u * 1.1f
            val dx = r * 1.732f; val dy = r * 1.5f
            var row = 0; var y = 0f
            while (y < h + r) {
                var x = if (row % 2 == 0) 0f else dx / 2
                while (x < w + r) {
                    val path = Path()
                    for (i in 0..6) {
                        val a = PI / 3 * i + PI / 6
                        val px = x + (r * cos(a)).toFloat(); val py = y + (r * sin(a)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, ink, style = Stroke(1f))
                    x += dx
                }
                y += dy; row++
            }
        }
        SkinPattern.Circuit -> {
            val rnd = java.util.Random(7)
            val step = u * 2
            repeat(((w * h) / (step * step) * 1.2f).toInt().coerceAtMost(200)) {
                val x = (rnd.nextInt((w / step).toInt().coerceAtLeast(1)) * step) + step / 2
                val y = (rnd.nextInt((h / step).toInt().coerceAtLeast(1)) * step) + step / 2
                val len = step * (1 + rnd.nextInt(3))
                val horizontal = rnd.nextBoolean()
                val end = if (horizontal) Offset(x + len, y) else Offset(x, y + len)
                drawLine(ink, Offset(x, y), end, 1.5f, StrokeCap.Round)
                drawCircle(ink, 2.5f, end)
            }
        }
        SkinPattern.Waves -> {
            var y = u; while (y < h + u) {
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= w) { lineTo(x, y + sin(x / (u * 1.4f)) * u * 0.35f); x += 4f }
                }
                drawPath(path, ink, style = Stroke(1.2f)); y += u * 1.2f
            }
        }
        SkinPattern.None -> {}
    }
}

// ---------------------------------------------------------------------------
// Text and icon helpers
// ---------------------------------------------------------------------------

fun skinFont(f: SkinFont): FontFamily? = when (f) {
    SkinFont.Theme -> null
    SkinFont.Sans -> FontFamily.SansSerif
    SkinFont.Serif -> FontFamily.Serif
    SkinFont.Mono -> FontFamily.Monospace
    SkinFont.Script -> FontFamily.Cursive
    else -> runCatching { com.eoinknd16.nexus.look.FontChoice.valueOf(f.name).family() }.getOrNull()
}

fun skinCase(text: String, c: SkinCase) = when (c) {
    SkinCase.AsTyped -> text
    SkinCase.Upper -> text.uppercase()
    SkinCase.Lower -> text.lowercase()
}

fun TextStyle.skinned(skin: TileSkin, colour: Color, weight: Int = skin.labelWeight): TextStyle = copy(
    color = colour,
    fontFamily = skinFont(skin.font) ?: fontFamily,
    fontWeight = FontWeight(weight.coerceIn(100, 900)),
    letterSpacing = skin.letterSpacing.sp,
    shadow = if (skin.textShadow) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f) else null,
)

/** Black-and-white or tinted versions of app icons. */
fun iconFilter(skin: TileSkin, tint: Color): ColorFilter? = when (skin.iconStyle) {
    com.eoinknd16.nexus.look.SkinIconStyle.Mono -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    com.eoinknd16.nexus.look.SkinIconStyle.Tinted -> ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.SrcIn)
    else -> null
}

/** The badge / ring / glow around a tile's icon. */
@Composable
fun SkinIconFrame(skin: TileSkin, accent: Color, size: Dp, content: @Composable () -> Unit) {
    when (skin.iconStyle) {
        com.eoinknd16.nexus.look.SkinIconStyle.Circle, com.eoinknd16.nexus.look.SkinIconStyle.Squircle -> {
            val shape = if (skin.iconStyle == com.eoinknd16.nexus.look.SkinIconStyle.Circle) CircleShape else RoundedCornerShape(30)
            Box(
                Modifier.size(size * 1.3f).clip(shape).drawBehind { drawRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)))) },
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        com.eoinknd16.nexus.look.SkinIconStyle.Ring -> Box(
            Modifier.size(size * 1.3f).drawBehind { drawCircle(accent.copy(alpha = 0.8f), style = Stroke(2.dp.toPx())) },
            contentAlignment = Alignment.Center,
        ) { content() }
        com.eoinknd16.nexus.look.SkinIconStyle.Glow -> Box(
            Modifier.size(size * 1.2f).drawBehind {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent)), radius = this.size.minDimension * 0.75f)
            },
            contentAlignment = Alignment.Center,
        ) { content() }
        else -> content()
    }
}

/** The main colour of an app icon (for the "From the app's icon" fill). */
@Composable
fun rememberIconColour(icon: ImageBitmap?, wanted: Boolean): Color? = remember(icon, wanted) {
    if (!wanted || icon == null) return@remember null
    runCatching {
        val bmp = icon.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
        val pal = androidx.palette.graphics.Palette.from(bmp).generate()
        (pal.vibrantSwatch ?: pal.dominantSwatch ?: pal.mutedSwatch)?.rgb?.let { Color(it) }
    }.getOrNull()
}
