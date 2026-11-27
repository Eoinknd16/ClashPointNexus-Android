package com.eoinknd16.nexus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.look.BorderOverride
import com.eoinknd16.nexus.look.BorderStyle
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.SkinAnim
import com.eoinknd16.nexus.look.SkinCase
import com.eoinknd16.nexus.look.SkinFill
import com.eoinknd16.nexus.look.SkinFont
import com.eoinknd16.nexus.look.SkinIconPos
import com.eoinknd16.nexus.look.SkinIconStyle
import com.eoinknd16.nexus.look.SkinPattern
import com.eoinknd16.nexus.look.SkinPress
import com.eoinknd16.nexus.look.SkinShadow
import com.eoinknd16.nexus.look.SkinShape
import com.eoinknd16.nexus.look.TileConfig
import com.eoinknd16.nexus.look.TileLayout
import com.eoinknd16.nexus.look.TileSkin
import com.eoinknd16.nexus.look.tileSkinPresets
import com.eoinknd16.nexus.look.toHex
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.settings.ChoiceRow
import com.eoinknd16.nexus.ui.settings.ColorRow
import com.eoinknd16.nexus.ui.settings.SliderRow
import com.eoinknd16.nexus.ui.settings.Swatch
import com.eoinknd16.nexus.ui.settings.ToggleRow
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import kotlin.math.roundToInt

/**
 * The Style tab of the tile editor: a gallery of ready-made looks, then every
 * knob grouped into sections. Changes show live in the preview at the top.
 */
@Composable
fun TileStyleEditor(t: TileConfig, runner: TileRunner, onChange: (TileConfig) -> Unit) {
    val c = appContainer()
    val home = LookState.look.home
    val skin = t.skin ?: home.tileSkin
    val ownStyle = t.skin != null
    fun set(f: (TileSkin) -> TileSkin) = onChange(t.copy(skin = f(skin)))
    val accent = LookState.palette.slot(t.color)

    Spacer(Modifier.height(10.dp))
    HudLabel("Looks")
    Spacer(Modifier.height(8.dp))
    // Gallery: each one is the real tile, drawn in that style.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tileSkinPresets.forEach { (name, preset) ->
            val selected = skin.preset == name
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
                Box(
                    Modifier.size(92.dp, 84.dp).clip(nexusShape(0.5f))
                        .border(if (selected) 2.dp else 0.dp, if (selected) Nx.Cyan else Nx.Void, nexusShape(0.5f))
                        .padding(6.dp),
                ) {
                    TileView(
                        tile = t.copy(skin = preset), runner = runner, showSublabel = false, modifier = Modifier.fillMaxSize(),
                        onOpenFolder = { _, _ -> }, onLongPress = {}, style = TileStyle(iconSize = 24.dp, labelScale = 0.75f, padding = 8.dp),
                    )
                    // Tap anywhere on the card picks the look (sits over the tile so it doesn't launch anything).
                    Box(Modifier.fillMaxSize().nexusPress({ onChange(t.copy(skin = preset)) }))
                }
                Spacer(Modifier.height(4.dp))
                Text(name, style = MaterialTheme.typography.labelSmall, color = if (selected) Nx.Cyan else Nx.Muted, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        if (ownStyle) "This tile has its own style." else "This tile uses the default style (shared by every tile without its own). Changing anything here gives it its own.",
        style = MaterialTheme.typography.bodySmall, color = Nx.Muted,
    )

    // ---- Surface ----
    StyleSection("Surface", open = true) {
        ChoiceRow("Fill", SkinFill.entries, skin.fill, { it.label }) { v -> set { it.copy(fill = v) } }
        when (skin.fill) {
            SkinFill.Solid, SkinFill.Glass -> SkinColourRow("Colour", skin.color1) { v -> set { it.copy(color1 = v) } }
            SkinFill.Gradient, SkinFill.Mesh -> {
                SkinColourRow("First colour", skin.color1) { v -> set { it.copy(color1 = v) } }
                SkinColourRow("Second colour", skin.color2) { v -> set { it.copy(color2 = v) } }
                SkinColourRow("Third colour (optional)", skin.color3, allowNone = true) { v -> set { it.copy(color3 = v) } }
                if (skin.fill == SkinFill.Gradient) SliderRow("Direction", skin.angle, 0f..360f, { "${it.roundToInt()}°" }) { v -> set { it.copy(angle = v) } }
            }
            else -> {}
        }
        if (skin.fill != SkinFill.Theme && skin.fill != SkinFill.None && skin.fill != SkinFill.Outline) {
            SliderRow("Opacity", skin.opacity, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(opacity = v) } }
        }
        ChoiceRow("Texture", SkinPattern.entries, skin.pattern, { it.label }) { v -> set { it.copy(pattern = v) } }
        if (skin.pattern != SkinPattern.None) {
            SliderRow("Texture strength", skin.patternOpacity, 0.02f..0.6f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(patternOpacity = v) } }
            SliderRow("Texture size", skin.patternScale, 0.4f..3f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(patternScale = v) } }
        }
        ToggleRow("Glossy shine", skin.shine, "A highlight across the top, like glass or plastic") { v -> set { it.copy(shine = v) } }
        SliderRow("Inner glow", skin.innerGlow, 0f..1f, { if (it < 0.02f) "Off" else "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(innerGlow = v) } }
    }

    // ---- Shape & depth ----
    StyleSection("Shape & depth") {
        ChoiceRow("Shape", SkinShape.entries, skin.shape, { it.label }) { v -> set { it.copy(shape = v) } }
        if (skin.shape in listOf(SkinShape.Rounded, SkinShape.Cut, SkinShape.Leaf, SkinShape.Ticket)) {
            SliderRow("Corners", skin.corner, 0f..60f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(corner = v) } }
        }
        ChoiceRow("Shadow", SkinShadow.entries, skin.shadow, { it.label }) { v -> set { it.copy(shadow = v) } }
        if (skin.shadow != SkinShadow.None) {
            SliderRow("Shadow size", skin.shadowSize, 1f..40f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(shadowSize = v) } }
            if (skin.shadow != SkinShadow.Neumorphic) SkinColourRow("Shadow colour", skin.shadowColor, noneLabel = if (skin.shadow == SkinShadow.Glow) "Tile colour" else "Black") { v -> set { it.copy(shadowColor = v) } }
        }
        ToggleRow("Tips in 3D as you tilt the phone", skin.tilt3d) { v -> set { it.copy(tilt3d = v) } }
    }

    // ---- Border ----
    StyleSection("Border") {
        val b = t.border ?: BorderOverride()
        val inherit = "inherit"
        ChoiceRow(
            "Style", listOf(inherit) + BorderStyle.entries.map { it.name }, b.style?.name ?: inherit,
            { if (it == inherit) (if (skin.fill == SkinFill.Theme) "Same as everywhere" else "Automatic") else BorderStyle.valueOf(it).label },
        ) { v -> onChange(t.copy(border = b.copy(style = if (v == inherit) null else BorderStyle.valueOf(v)))) }
        SliderRow("Width", b.width ?: LookState.look.shape.borderWidth, 0f..6f, { "%.1fdp".format(it) }) { v -> onChange(t.copy(border = b.copy(width = v))) }
        if (skin.fill == SkinFill.Theme) SliderRow("Glow", b.glow ?: LookState.look.shape.glow, 0f..1.5f, { "${(it * 100).toInt()}%" }) { v -> onChange(t.copy(border = b.copy(glow = v))) }
        ColorRow("Border colour", b.color?.takeIf { it.startsWith("#") } ?: accent.toHex()) { v -> onChange(t.copy(border = b.copy(color = v))) }
        if (t.border != null) NexusButton("Reset border", accent = Nx.Muted) { onChange(t.copy(border = null)) }
    }

    // ---- Icon ----
    StyleSection("Icon") {
        ChoiceRow("Icon style", SkinIconStyle.entries, skin.iconStyle, { it.label }) { v -> set { it.copy(iconStyle = v) } }
        ChoiceRow("Icon position", SkinIconPos.entries, skin.iconPos, { it.label }) { v -> set { it.copy(iconPos = v) } }
        SliderRow("Icon size", skin.iconScale, 0.3f..2.5f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(iconScale = v) } }
        SkinColourRow("Icon colour", skin.iconColor, noneLabel = "Tile colour") { v -> set { it.copy(iconColor = v) } }
        ChoiceRow("Layout", listOf<TileLayout?>(null) + TileLayout.entries, skin.layout, { it?.label ?: "Same as the block" }) { v -> set { it.copy(layout = v) } }
    }

    // ---- Text ----
    StyleSection("Text") {
        ToggleRow("Show the label", skin.showLabel) { v -> set { it.copy(showLabel = v) } }
        ToggleRow("Show the sub-label", skin.showSublabel) { v -> set { it.copy(showSublabel = v) } }
        ChoiceRow("Font", SkinFont.entries, skin.font, { it.label }) { v -> set { it.copy(font = v) } }
        ChoiceRow("Letters", SkinCase.entries, skin.case, { it.label }) { v -> set { it.copy(case = v) } }
        SliderRow("Weight", skin.labelWeight.toFloat(), 100f..900f, { weightName(it.roundToInt()) }, steps = 7) { v -> set { it.copy(labelWeight = (v / 100).roundToInt() * 100) } }
        SliderRow("Size", skin.labelScale, 0.5f..2.5f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(labelScale = v) } }
        SliderRow("Letter spacing", skin.letterSpacing, -1f..6f, { "%.1f".format(it) }) { v -> set { it.copy(letterSpacing = v) } }
        SkinColourRow("Label colour", skin.labelColor, noneLabel = "Tile colour") { v -> set { it.copy(labelColor = v) } }
        SkinColourRow("Sub-label colour", skin.subColor, noneLabel = "Tile colour") { v -> set { it.copy(subColor = v) } }
        ToggleRow("Text shadow", skin.textShadow, "Helps text read over images and bright fills") { v -> set { it.copy(textShadow = v) } }
        SliderRow("Padding", if (skin.padding < 0f) 14f else skin.padding, 0f..32f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(padding = v) } }
    }

    // ---- Motion ----
    StyleSection("Motion") {
        ChoiceRow("Animation", SkinAnim.entries, skin.anim, { it.label }) { v -> set { it.copy(anim = v) } }
        if (skin.anim != SkinAnim.None) SliderRow("Speed", skin.animSpeed, 0.1f..3f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(animSpeed = v) } }
        ChoiceRow("When you press it", SkinPress.entries, skin.press, { it.label }) { v -> set { it.copy(press = v) } }
    }

    // ---- Everywhere ----
    Spacer(Modifier.height(14.dp))
    NexusButton("Use this style on every tile", icon = Icons.Filled.Check, accent = Nx.Magenta) {
        val chosen = skin
        c.editLook("Style used on every tile") { l ->
            fun TileConfig.reset() = copy(skin = null)
            l.copy(
                home = l.home.copy(
                    tileSkin = chosen,
                    blocks = l.home.blocks.map { b -> if (b is com.eoinknd16.nexus.look.HomeBlock.Tiles) b.copy(tiles = b.tiles.map { it.reset() }) else b },
                    pages = l.home.pages.map { pg -> pg.copy(blocks = pg.blocks.map { b -> if (b is com.eoinknd16.nexus.look.HomeBlock.Tiles) b.copy(tiles = b.tiles.map { it.reset() }) else b }) },
                ),
            )
        }
        onChange(t.copy(skin = null))
    }
    if (ownStyle) {
        Spacer(Modifier.height(8.dp))
        NexusButton("Go back to the default style", accent = Nx.Muted) { onChange(t.copy(skin = null)) }
    }
}

private fun weightName(w: Int) = when {
    w <= 200 -> "Thin"; w <= 300 -> "Light"; w <= 400 -> "Regular"; w <= 500 -> "Medium"
    w <= 600 -> "Semibold"; w <= 700 -> "Bold"; w <= 800 -> "Extra bold"; else -> "Black"
}

/** A collapsible group of options. */
@Composable
private fun StyleSection(title: String, open: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(open) }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().nexusPress({ expanded = !expanded }).clip(nexusShape(0.5f)).background(Nx.Cyan.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hud(title), style = MaterialTheme.typography.labelLarge, color = Nx.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Nx.Cyan)
    }
    if (expanded) Column(Modifier.padding(top = 4.dp)) { content() }
}

/** Pick a colour: the tile's own, a palette colour, or any colour. */
@Composable
private fun SkinColourRow(title: String, value: String, allowNone: Boolean = false, noneLabel: String = "Tile colour", onChange: (String) -> Unit) {
    val slots = listOf("" to noneLabel, "accent" to "Accent", "accent2" to "Second accent", "warn" to "Highlight", "good" to "Success", "danger" to "Danger", "text" to "Text", "muted" to "Muted", "custom" to "Pick a colour")
    val key = if (value.startsWith("#")) "custom" else value
    ChoiceRow(
        title, slots.map { it.first }, key, { k -> slots.first { it.first == k }.second.let { if (allowNone && k == "") "None" else it } },
        preview = { k -> Swatch(if (k == "custom") LookState.palette.slot(value.ifBlank { "#3CF2FF" }) else if (k == "") Nx.Muted else LookState.palette.slot(k)) },
    ) { k -> onChange(if (k == "custom") (if (value.startsWith("#")) value else "#3CF2FF") else k) }
    if (value.startsWith("#")) ColorRow("$title (custom)", value) { onChange(it) }
}
