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
    Row(
        Modifier.fillMaxWidth().nexusPress({ onChange(!checked) }).padding(vertical = 8.dp),
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
    // Local value while dragging so the whole UI doesn't re-theme on every pixel.
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.labelMedium, color = Nx.Cyan)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Nx.Cyan, activeTrackColor = Nx.Cyan, inactiveTrackColor = Nx.CyanDim),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Nx.Text)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> Chip(label(o), o == selected) { onSelect(o) } }
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
    var open by remember { mutableStateOf(false) }
    val color = parseColor(hex, Nx.Cyan)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().nexusPress({ open = !open }).padding(vertical = 8.dp),
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

/** Collapsible group header used by Customise. */
@Composable
fun Section(title: String, subtitle: String? = null, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().nexusPress({ open = !open }).clip(nexusShape(0.5f))
                .background(Nx.Cyan.copy(alpha = if (open) 0.12f else 0.05f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(hud(title), style = MaterialTheme.typography.labelLarge, color = Nx.Cyan, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
            }
            Text(if (open) "−" else "+", style = MaterialTheme.typography.headlineSmall, color = Nx.Cyan)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) { content() }
    }
}
