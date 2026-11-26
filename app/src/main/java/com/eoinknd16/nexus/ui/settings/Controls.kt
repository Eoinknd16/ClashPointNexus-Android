package com.eoinknd16.nexus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eoinknd16.nexus.look.parseColor
import com.eoinknd16.nexus.look.toHex
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.roundToInt

/** Shared building blocks for every settings/customisation screen. */

@Composable
fun SettingLabel(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    if (!rowVisible(title, subtitle)) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).nexusPress({ onChange(!checked) }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, subtitle, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Nx.Void, checkedTrackColor = Nx.Cyan,
                uncheckedThumbColor = Nx.Muted, uncheckedTrackColor = Nx.Deep, uncheckedBorderColor = Nx.CyanDim,
            ),
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = { "%.2f".format(it) },
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    if (!rowVisible(title)) return
    // Local value while dragging so the whole UI doesn't re-theme on every pixel.
    var local by remember(value) { mutableStateOf(value) }
    var typing by remember { mutableStateOf(false) }
    val span = range.endInclusive - range.start
    // Fine-step for the − / + buttons: the slider's own step, else 1 unit on big ranges, else 1%.
    val nudge = when {
        steps > 0 -> span / (steps + 1)
        span >= 10f -> 1f
        else -> span / 100f
    }
    fun commit(v: Float) {
        local = v.coerceIn(range.start, range.endInclusive)
        onChange(local)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f))
            if (typing) {
                // Type an exact value (in the slider's own units).
                var text by remember { mutableStateOf(trimNumber(local)) }
                val shape = nexusShape(0.4f)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Nx.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(Nx.Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        text.replace(',', '.').toFloatOrNull()?.let { commit(it) }
                        typing = false
                    }),
                    modifier = Modifier.width(80.dp).background(Nx.Deep, shape).border(1.dp, Nx.Cyan, shape).padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    hud("Set"), style = MaterialTheme.typography.labelMedium, color = Nx.Cyan,
                    modifier = Modifier.nexusPress({ text.replace(',', '.').toFloatOrNull()?.let { commit(it) }; typing = false }).padding(6.dp),
                )
            } else {
                Text(
                    format(local), style = MaterialTheme.typography.labelMedium, color = Nx.Cyan,
                    modifier = Modifier.nexusPress({ typing = true }).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NudgeButton("−") { commit(local - nudge) }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onChange(local) },
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(thumbColor = Nx.Cyan, activeTrackColor = Nx.Cyan, inactiveTrackColor = Nx.CyanDim),
                modifier = Modifier.weight(1f),
            )
            NudgeButton("+") { commit(local + nudge) }
        }
    }
}

@Composable
private fun NudgeButton(symbol: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).nexusPress(onClick), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = Nx.Cyan)
    }
}

private fun trimNumber(v: Float): String = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/**
 * Pick one of several options. Up to 4 short options show as a segmented
 * control; anything longer collapses into a single picker field that opens a
 * clean checklist, optionally with a live [preview] of each option (a font
 * drawn in itself, a colour swatch...). Keeps long option lists from
 * sprawling across the screen as a wall of chips.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    subtitle: String? = null,
    preview: (@Composable (T) -> Unit)? = null,
    /** Optional headings for long lists (e.g. "Apps", "PC", "Phone"). */
    group: ((T) -> String)? = null,
    onSelect: (T) -> Unit,
) {
    if (!rowVisible(title, subtitle)) return
    val segmented = preview == null && group == null && options.size <= 4 && options.all { label(it).length <= 12 }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
        Spacer(Modifier.height(8.dp))
        if (segmented) Segmented(options, selected, label, onSelect) else Picker(options, selected, label, preview, group, onSelect)
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val shape = nexusShape(0.5f)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, Nx.CyanDim, shape)) {
        options.forEachIndexed { i, o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).nexusPress({ onSelect(o) })
                    .background(if (on) Nx.Cyan else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(hud(label(o)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (on) Nx.Void else Nx.Cyan, maxLines = 1)
            }
            if (i < options.lastIndex) Box(Modifier.width(1.dp).height(38.dp).background(Nx.CyanDim))
        }
    }
}

@Composable
private fun <T> Picker(options: List<T>, selected: T, label: (T) -> String, preview: (@Composable (T) -> Unit)?, group: ((T) -> String)?, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = nexusShape(0.5f)
    Box {
        Row(
            Modifier.fillMaxWidth().nexusPress({ open = true }).clip(shape).background(Nx.Deep).border(1.dp, Nx.CyanDim, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview != null) {
                preview(selected)
                Spacer(Modifier.width(12.dp))
            }
            Text(label(selected), style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f), maxLines = 1)
            Text("▾", style = MaterialTheme.typography.titleMedium, color = Nx.Cyan)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Nx.PanelSolid,
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            val ordered = if (group == null) options else options.groupBy(group).values.flatten()
            var lastGroup: String? = null
            ordered.forEach { o ->
                val on = o == selected
                val g = group?.invoke(o)
                if (g != null && g != lastGroup) {
                    lastGroup = g
                    Text(
                        hud(g), style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (preview != null) {
                                preview(o)
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(label(o), color = if (on) Nx.Cyan else Nx.Text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    trailingIcon = { if (on) Text("✓", color = Nx.Cyan, fontWeight = FontWeight.Bold) },
                    onClick = {
                        open = false
                        onSelect(o)
                    },
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = nexusShape(0.45f)
    Text(
        hud(text),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) Nx.Void else Nx.Cyan,
        modifier = Modifier.nexusPress(onClick).clip(shape)
            .background(if (selected) Nx.Cyan else Nx.Cyan.copy(alpha = 0.08f))
            .border(1.dp, Nx.CyanDim, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun TextRow(
    title: String,
    value: String,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    if (!rowVisible(title)) return
    // Edits locally; commits on every change but debounced by the caller's store.
    var text by remember(value) { mutableStateOf(value) }
    val shape = nexusShape(0.5f)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(hud(title), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().background(Nx.Deep, shape).border(1.dp, Nx.CyanDim, shape).padding(12.dp)) {
            if (text.isEmpty()) Text(placeholder, color = Nx.Muted, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(it)
                },
                singleLine = singleLine,
                textStyle = TextStyle(color = Nx.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Nx.Cyan),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Colour row: swatch + hex. Tapping opens an inline picker with hue /
 * saturation / brightness / alpha sliders, a hex field, and quick swatches.
 */
@Composable
fun ColorRow(title: String, hex: String, allowAlpha: Boolean = false, onChange: (String) -> Unit) {
    if (!rowVisible(title)) return
    var open by remember { mutableStateOf(false) }
    val color = parseColor(hex, Nx.Cyan)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).nexusPress({ open = !open }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f))
            Text(hex.uppercase(), style = MaterialTheme.typography.labelMedium, color = Nx.Muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, Nx.Text.copy(alpha = 0.4f), CircleShape))
        }
        if (open) ColorPicker(color, allowAlpha) { onChange(it.toHex(withAlpha = allowAlpha && it.alpha < 1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(initial: Color, allowAlpha: Boolean, onChange: (Color) -> Unit) {
    val hsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb((initial.alpha * 255).roundToInt(), (initial.red * 255).roundToInt(), (initial.green * 255).roundToInt(), (initial.blue * 255).roundToInt()), it) }
    }
    var h by remember(initial) { mutableStateOf(hsv[0]) }
    var s by remember(initial) { mutableStateOf(hsv[1]) }
    var v by remember(initial) { mutableStateOf(hsv[2]) }
    var a by remember(initial) { mutableStateOf(initial.alpha) }
    fun current() = Color.hsv(h, s, v, a)
    var hexText by remember(initial) { mutableStateOf(initial.toHex(allowAlpha)) }
    LaunchedEffect(h, s, v, a) { hexText = current().toHex(allowAlpha) }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(36.dp).clip(nexusShape(0.4f)).background(current()))
        GradientSlider("Hue", h, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { h = it }
        GradientSlider("Saturation", s, 0f..1f, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v)))) { s = it }
        GradientSlider("Brightness", v, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f)))) { v = it }
        if (allowAlpha) GradientSlider("Opacity", a, 0f..1f, Brush.horizontalGradient(listOf(Color.Transparent, Color.hsv(h, s, v)))) { a = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextRow("Hex", hexText, "#RRGGBB") { t ->
                    hexText = t
                    val c = parseColor(t, Color.Unspecified)
                    if (c != Color.Unspecified) {
                        val out = FloatArray(3)
                        android.graphics.Color.colorToHSV(android.graphics.Color.rgb((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt()), out)
                        h = out[0]; s = out[1]; v = out[2]; a = c.alpha
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            com.eoinknd16.nexus.ui.fx.NexusButton("Apply", filled = true) { onChange(current()) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK.forEach { qh ->
                val qc = parseColor(qh)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(qc).border(1.dp, Nx.Text.copy(alpha = 0.3f), CircleShape)
                        .nexusPress({ onChange(qc.copy(alpha = if (allowAlpha) a else 1f)) }),
                )
            }
        }
    }
}

private val QUICK = listOf(
    "#3CF2FF", "#00B3FF", "#6FA8FF", "#7B5CFF", "#B44CFF", "#FF3CAC", "#FF4F81", "#FF3B3B",
    "#FF8A3B", "#FFB23C", "#FFE14D", "#C8FF6B", "#39FF6A", "#2EE6A8", "#FFFFFF", "#9AA4B2",
    "#3A4250", "#000000",
)

@Composable
private fun GradientSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(hud(title), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp).clip(CircleShape).background(track))
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
        }
    }
}

/**
 * A group of settings. On an overview screen it's one tappable row (icon,
 * name, current status) that opens the group on its own page; while
 * searching it shows only matching settings; inside editors it's a
 * collapsible block.
 *
 * @param searchable false for groups made of custom content rather than
 *   rows; they only show in search when their name or [keywords] match.
 * @param onReset puts a "Reset to default" button at the bottom of the group.
 */
@Composable
fun Section(
    title: String,
    subtitle: String? = null,
    initiallyOpen: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    heading: String? = null,
    keywords: String = "",
    searchable: Boolean = true,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (val mode = LocalSectionMode.current) {
        SectionMode.Index -> {
            val open = LocalOpenSection.current
            if (heading != null) {
                Spacer(Modifier.height(14.dp))
                Text(hud(heading), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 64.dp).nexusPress({ open(title) })
                    .clip(nexusShape(0.5f)).background(Nx.Cyan.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(Modifier.size(40.dp).clip(nexusShape(0.45f)).background(Nx.Cyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(icon, null, tint = Nx.Cyan)
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = Nx.Cyan)
            }
        }
        is SectionMode.Open -> if (mode.title == title) {
            Column(Modifier.fillMaxWidth()) {
                content()
                if (onReset != null) {
                    Spacer(Modifier.height(16.dp))
                    com.eoinknd16.nexus.ui.fx.NexusButton("Reset ${title.lowercase()} to default", accent = Nx.Muted, onClick = onReset)
                }
            }
        }
        is SectionMode.Search -> {
            val q = mode.query.trim()
            val nameHit = title.contains(q, true) || (subtitle?.contains(q, true) == true) || keywords.contains(q, true)
            if (!searchable && !nameHit) return
            var matched by remember(q) { mutableStateOf(false) }
            val open = LocalOpenSection.current
            if (nameHit || matched) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp).nexusPress({ open(title) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(hud(title), style = MaterialTheme.typography.labelLarge, color = Nx.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(hud("Open ›"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalRowQuery provides if (nameHit) "" else q,
                LocalRowMatched provides { if (!matched) matched = true },
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) { content() }
            }
        }
        SectionMode.Inline -> {
            var open by remember { mutableStateOf(initiallyOpen) }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).nexusPress({ open = !open }).clip(nexusShape(0.5f))
                        .background(Nx.Cyan.copy(alpha = if (open) 0.12f else 0.05f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(hud(title), style = MaterialTheme.typography.labelLarge, color = Nx.Cyan, fontWeight = FontWeight.Bold)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
                    }
                    Text(if (open) "−" else "+", style = MaterialTheme.typography.headlineSmall, color = Nx.Cyan)
                }
                if (open) Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    content()
                    if (onReset != null) {
                        Spacer(Modifier.height(12.dp))
                        com.eoinknd16.nexus.ui.fx.NexusButton("Reset to default", accent = Nx.Muted, onClick = onReset)
                    }
                }
            }
        }
    }
}
