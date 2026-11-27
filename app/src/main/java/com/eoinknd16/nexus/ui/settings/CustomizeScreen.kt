package com.eoinknd16.nexus.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Palette as PaletteIcon
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eoinknd16.nexus.core.Http
import com.eoinknd16.nexus.core.describe
import com.eoinknd16.nexus.look.BackdropStyle
import com.eoinknd16.nexus.look.BoardMode
import com.eoinknd16.nexus.look.CornerStyle
import com.eoinknd16.nexus.look.FontChoice
import com.eoinknd16.nexus.look.Look
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.Palette
import com.eoinknd16.nexus.look.Presets
import com.eoinknd16.nexus.look.ResizeMode
import com.eoinknd16.nexus.look.SubtitleEdge
import com.eoinknd16.nexus.look.TempUnit
import com.eoinknd16.nexus.look.TileAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eoinknd16.nexus.look.parseColor
import com.eoinknd16.nexus.look.toHex
import com.eoinknd16.nexus.look.updateDock
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.ScreenHeader
import com.eoinknd16.nexus.ui.appContainer
import com.eoinknd16.nexus.ui.fx.HoloPanel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud
import com.eoinknd16.nexus.weather.Place
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Every customisation option in one place. Changes apply live (the screen
 * you're looking at re-themes as you go) and persist immediately.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen() {
    val c = appContainer()
    val nav = LocalNav.current
    val context = LocalContext.current
    val look = LookState.look
    fun edit(undoLabel: String? = null, t: (Look) -> Look) = c.editLook(undoLabel, t)
    fun pal(t: (Palette) -> Palette) = edit { it.copy(palette = t(it.palette)) }

    // Overview -> one group at a time, or search across everything.
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val canUndo by c.canUndo.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = openSection != null || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else openSection = null
    }
    val mode: SectionMode = when {
        query.isNotBlank() -> SectionMode.Search(query)
        openSection != null -> SectionMode.Open(openSection!!)
        else -> SectionMode.Index
    }
    val scroll = rememberScrollState()
    LaunchedEffect(openSection) { scroll.scrollTo(0) }

    NexusBackdrop(intensity = 0.5f) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            var showPreview by rememberSaveable { mutableStateOf(true) }
            ScreenHeader(openSection ?: "Customise", if (openSection == null) "Preset: ${look.presetName}" else "Changes apply as you go") {
                if (canUndo) {
                    Text(
                        hud("Undo"), style = MaterialTheme.typography.labelMedium, color = Nx.Amber,
                        modifier = Modifier.nexusPress({ c.undoLook() }).padding(8.dp),
                    )
                }
                Text(
                    hud(if (showPreview) "Hide preview" else "Preview"),
                    style = MaterialTheme.typography.labelSmall, color = Nx.Cyan,
                    modifier = Modifier.nexusPress({ showPreview = !showPreview }).padding(8.dp),
                )
            }
            // Pinned above the options so it stays in view while you scroll and tweak.
            if (showPreview) LookPreview(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (openSection == null) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            } else {
                Text(
                    hud("‹ All settings"), style = MaterialTheme.typography.labelLarge, color = Nx.Cyan,
                    modifier = Modifier.padding(horizontal = 12.dp).nexusPress({ openSection = null }).padding(8.dp),
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalSectionMode provides mode,
                LocalOpenSection provides { t -> query = ""; openSection = t },
            ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)) {

                if (mode == SectionMode.Index) {
                // ---- Home layout shortcut ----
                HoloPanel(Modifier.fillMaxWidth(), accent = Nx.Amber) {
                    Text("Home screen layout", style = MaterialTheme.typography.titleMedium, color = Nx.Text)
                    Text(
                        "Add, move and set up blocks, tiles, folders, pages and the tray. Long-press empty space on home does the same.",
                        style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    NexusButton("Edit home screen", icon = Icons.Filled.Edit, accent = Nx.Amber, filled = true) {
                        c.homeEditRequest.value = true
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    }
                }
                }

                Section("Presets", "Current: ${look.presetName}", icon = androidx.compose.material.icons.Icons.Filled.AutoAwesome, heading = "Style", searchable = false, keywords = "theme style preset") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Presets.all.forEach { p ->
                            val preview = p.apply(look)
                            Column(
                                Modifier.width(150.dp).nexusPress({ edit("Preset “${p.name}” applied", p.apply) }).clip(com.eoinknd16.nexus.ui.fx.nexusShape(0.6f))
                                    .background(parseColor(preview.palette.background)).padding(10.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(preview.palette.accent, preview.palette.accent2, preview.palette.warn, preview.palette.text).forEach {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(parseColor(it)))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(p.name, style = MaterialTheme.typography.titleMedium, color = parseColor(preview.palette.text))
                                Text(p.description, style = MaterialTheme.typography.labelSmall, color = parseColor(preview.palette.muted), maxLines = 2)
                            }
                        }
                    }
                }

                Section("Colours", "Accent ${look.palette.accent.uppercase()} · panels ${(look.palette.panelOpacity * 100).roundToInt()}%", icon = androidx.compose.material.icons.Icons.Filled.PaletteIcon, onReset = { edit(undoLabel = "Colours reset") { it.copy(palette = Palette()) } }) {
                    if (android.os.Build.VERSION.SDK_INT >= 31 && LocalRowQuery.current.isBlank()) {
                        // Material You: pull the accents from the phone's wallpaper.
                        val scheme = androidx.compose.material3.dynamicDarkColorScheme(context)
                        NexusButton("Use my wallpaper colours", icon = Icons.Filled.Edit, accent = Nx.Magenta) {
                            edit("Wallpaper colours applied") {
                                it.copy(
                                    palette = it.palette.copy(
                                        accent = scheme.primary.toHex(),
                                        accent2 = scheme.tertiary.toHex(),
                                        warn = scheme.secondary.toHex(),
                                        text = scheme.onSurface.toHex(),
                                        muted = scheme.onSurfaceVariant.toHex(),
                                    ),
                                    presetName = "Wallpaper",
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    ColorRow("Accent", look.palette.accent) { v -> pal { it.copy(accent = v) } }
                    ColorRow("Second accent", look.palette.accent2) { v -> pal { it.copy(accent2 = v) } }
                    ColorRow("Warning / highlight", look.palette.warn) { v -> pal { it.copy(warn = v) } }
                    ColorRow("Success", look.palette.good) { v -> pal { it.copy(good = v) } }
                    ColorRow("Danger", look.palette.danger) { v -> pal { it.copy(danger = v) } }
                    ColorRow("Background", look.palette.background) { v -> pal { it.copy(background = v) } }
                    ColorRow("Deep background", look.palette.deep) { v -> pal { it.copy(deep = v) } }
                    ColorRow("Panels", look.palette.panel) { v -> pal { it.copy(panel = v) } }
                    ColorRow("Text", look.palette.text) { v -> pal { it.copy(text = v) } }
                    ColorRow("Muted text", look.palette.muted) { v -> pal { it.copy(muted = v) } }
                    SliderRow("Panel opacity", look.palette.panelOpacity, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> pal { it.copy(panelOpacity = v) } }
                }

                Section("Text", "${look.fonts.body.label} · ${(look.fonts.scale * 100).roundToInt()}%", icon = androidx.compose.material.icons.Icons.Filled.TextFields, onReset = { edit(undoLabel = "Text reset") { it.copy(fonts = com.eoinknd16.nexus.look.Fonts()) } }) {
                    val f = look.fonts
                    ChoiceRow("Headings", FontChoice.entries, f.heading, { it.label }, preview = { FontSample(it) }) { v -> edit { it.copy(fonts = f.copy(heading = v)) } }
                    ChoiceRow("Body text", FontChoice.entries, f.body, { it.label }, preview = { FontSample(it) }) { v -> edit { it.copy(fonts = f.copy(body = v)) } }
                    ChoiceRow("HUD labels", FontChoice.entries, f.label, { it.label }, preview = { FontSample(it) }) { v -> edit { it.copy(fonts = f.copy(label = v)) } }
                    ChoiceRow("Clock", FontChoice.entries, f.clock, { it.label }, preview = { FontSample(it) }) { v -> edit { it.copy(fonts = f.copy(clock = v)) } }
                    SliderRow("Text size", f.scale, 0.8f..1.4f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(fonts = f.copy(scale = v)) } }
                    ToggleRow("UPPERCASE labels", f.uppercaseLabels) { v -> edit { it.copy(fonts = f.copy(uppercaseLabels = v)) } }
                }

                Section("Panels & borders", "${look.shape.borderStyle.label} · ${look.shape.cornerSize.roundToInt()}dp corners", icon = androidx.compose.material.icons.Icons.Filled.CropSquare, onReset = { edit(undoLabel = "Panels reset") { it.copy(shape = com.eoinknd16.nexus.look.ShapeStyle()) } }) {
                    val s = look.shape
                    ChoiceRow("Border style", com.eoinknd16.nexus.look.BorderStyle.entries, s.borderStyle, { it.label }) { v -> edit { it.copy(shape = s.copy(borderStyle = v, gradientBorder = true)) } }
                    val bslots = listOf("accent" to "Accent (per tile)", "accent2" to "Second accent", "warn" to "Highlight", "text" to "Text", "custom" to "Custom")
                    ChoiceRow(
                        "Border colour", bslots.map { it.first }, if (s.borderColor.startsWith("#")) "custom" else s.borderColor,
                        { k -> bslots.first { it.first == k }.second },
                        preview = { k -> Swatch(if (k == "custom") parseColor(s.borderColor, Nx.Cyan) else LookState.palette.slot(k)) },
                    ) { k -> edit { it.copy(shape = s.copy(borderColor = if (k == "custom") "#3CF2FF" else k)) } }
                    if (s.borderColor.startsWith("#")) ColorRow("Custom border colour", s.borderColor) { v -> edit { it.copy(shape = s.copy(borderColor = v)) } }
                    SliderRow("Outer glow", s.glowRadius, 0f..16f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(shape = s.copy(glowRadius = v)) } }
                    if (s.borderStyle == com.eoinknd16.nexus.look.BorderStyle.Neon) {
                        SliderRow("Neon loop time", s.neonSpeed, 0.5f..15f, { "%.1fs".format(it) }) { v -> edit { it.copy(shape = s.copy(neonSpeed = v)) } }
                    }
                    ChoiceRow("Corners", CornerStyle.entries, s.corners, { it.label }) { v -> edit { it.copy(shape = s.copy(corners = v)) } }
                    SliderRow("Corner size", s.cornerSize, 0f..32f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(shape = s.copy(cornerSize = v)) } }
                    SliderRow("Border width", s.borderWidth, 0f..4f, { "%.1fdp".format(it) }) { v -> edit { it.copy(shape = s.copy(borderWidth = v)) } }
                    SliderRow("Glow", s.glow, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(shape = s.copy(glow = v)) } }
                    ToggleRow("Corner brackets", s.brackets) { v -> edit { it.copy(shape = s.copy(brackets = v)) } }
                }

                Section("Backdrop", "${look.backdrop.style.label}" + if (look.backdrop.animated) "" else " · still", icon = androidx.compose.material.icons.Icons.Filled.Wallpaper, onReset = { edit(undoLabel = "Backdrop reset") { it.copy(backdrop = com.eoinknd16.nexus.look.Backdrop()) } }) {
                    val b = look.backdrop
                    ChoiceRow("Style", BackdropStyle.entries, b.style, { it.label }) { v -> edit { it.copy(backdrop = b.copy(style = v)) } }
                    SliderRow("Intensity", b.intensity, 0f..1.6f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(backdrop = b.copy(intensity = v)) } }
                    SliderRow("Speed", b.speed, 0f..3f, { "%.1f×".format(it) }) { v -> edit { it.copy(backdrop = b.copy(speed = v)) } }
                    ToggleRow("Animated", b.animated, "Off saves a little battery") { v -> edit { it.copy(backdrop = b.copy(animated = v)) } }
                    SliderRow("Frame rate", if (b.fps <= 0) 150f else b.fps.toFloat(), 10f..150f, { if (it >= 145f) "Match display" else "${it.roundToInt()} fps" }) { v ->
                        edit { it.copy(backdrop = b.copy(fps = if (v >= 145f) 0 else v.roundToInt())) }
                    }
                    ToggleRow("Scanlines", b.scanlines) { v -> edit { it.copy(backdrop = b.copy(scanlines = v)) } }
                    ToggleRow("Vignette", b.vignette) { v -> edit { it.copy(backdrop = b.copy(vignette = v)) } }
                }

                Section("Motion & feel", if (!look.motion.animations) "Animations off" else "%.1f× speed".format(look.motion.speed) + " · ${(look.motion.bounce * 100).roundToInt()}% bounce", icon = androidx.compose.material.icons.Icons.Filled.Animation, heading = "Feel", onReset = { edit(undoLabel = "Motion reset") { it.copy(motion = com.eoinknd16.nexus.look.Motion()) } }) {
                    val m = look.motion
                    ToggleRow("Animations", m.animations) { v -> edit { it.copy(motion = m.copy(animations = v)) } }
                    SliderRow("Animation speed", m.speed, 0.4f..2.5f, { "%.1f×".format(it) }) { v -> edit { it.copy(motion = m.copy(speed = v)) } }
                    SliderRow("Bounce", m.bounce, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(motion = m.copy(bounce = v)) } }
                    ChoiceRow("Screen transition", com.eoinknd16.nexus.look.ScreenTransition.entries, m.screenTransition, { it.label }) { v -> edit { it.copy(motion = m.copy(screenTransition = v)) } }
                    ChoiceRow("Tiles appearing", com.eoinknd16.nexus.look.Entrance.entries, m.tileEntrance, { it.label }) { v -> edit { it.copy(motion = m.copy(tileEntrance = v)) } }
                    ChoiceRow("Opening an app", com.eoinknd16.nexus.look.AppLaunch.entries, m.appLaunch, { it.label }) { v -> edit { it.copy(motion = m.copy(appLaunch = v)) } }
                    ToggleRow("Strong haptics", m.strongHaptics, "Heavier buzz for gestures") { v -> edit { it.copy(motion = m.copy(strongHaptics = v)) } }
                    ToggleRow("High refresh rate", m.highRefreshRate, "Run at your screen's fastest rate (90/120Hz+) while Nexus is open") { v -> edit { it.copy(motion = m.copy(highRefreshRate = v)) } }
                    ToggleRow("Tilt parallax", m.parallax, "Background and home shift slightly as you move the phone") { v -> edit { it.copy(motion = m.copy(parallax = v)) } }
                    if (m.parallax) SliderRow("Parallax depth", m.parallaxStrength, 0.2f..3f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(motion = m.copy(parallaxStrength = v)) } }
                    ToggleRow("Moving light on panels", m.sheen, "A soft glint that follows how you hold the phone") { v -> edit { it.copy(motion = m.copy(sheen = v)) } }
                    ToggleRow("Night mode", m.night, "Dims and warms Nexus during the hours below") { v -> edit { it.copy(motion = m.copy(night = v)) } }
                    if (m.night) {
                        SliderRow("Night starts", m.nightStart.toFloat(), 0f..23f, { "%02d:00".format(it.roundToInt()) }, steps = 22) { v -> edit { it.copy(motion = m.copy(nightStart = v.roundToInt())) } }
                        SliderRow("Night ends", m.nightEnd.toFloat(), 0f..23f, { "%02d:00".format(it.roundToInt()) }, steps = 22) { v -> edit { it.copy(motion = m.copy(nightEnd = v.roundToInt())) } }
                        SliderRow("Night strength", m.nightStrength, 0.1f..0.8f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(motion = m.copy(nightStrength = v)) } }
                    }
                    ToggleRow("Glow pulse on tiles", m.pulse) { v -> edit { it.copy(motion = m.copy(pulse = v)) } }
                    ToggleRow("Haptic feedback", m.haptics) { v -> edit { it.copy(motion = m.copy(haptics = v)) } }
                    SliderRow("Press squish", 1f - m.pressScale, 0f..0.15f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(motion = m.copy(pressScale = 1f - v)) } }
                }

                Section("Gestures", "Swipes, double-tap and where they start", icon = androidx.compose.material.icons.Icons.Filled.Swipe, searchable = false, keywords = "swipe up down left right double tap pinch zone edge drawer notifications", onReset = { edit(undoLabel = "Gestures reset") { it.copy(gestures = com.eoinknd16.nexus.look.Gestures()) } }) { GestureSettings() }

                Section("Home screen", "${look.home.fit.label} · ${look.home.blockSpacingDp.roundToInt()}dp spacing", icon = androidx.compose.material.icons.Icons.Filled.Home, heading = "Home") {
                    val h = look.home
                    ChoiceRow(
                        "Fitting to the screen", com.eoinknd16.nexus.look.FitMode.entries, h.fit, { it.label },
                        subtitle = "Auto-fit shrinks the scrolling part evenly when it's only a little too tall, so there's no stray scrolling",
                    ) { v -> edit { it.copy(home = h.copy(fit = v)) } }
                    if (h.fit == com.eoinknd16.nexus.look.FitMode.Auto) {
                        SliderRow("Shrink up to", h.fitTolerance, 0.05f..0.5f, { "${(it * 100).roundToInt()}% overflow" }) { v -> edit { it.copy(home = h.copy(fitTolerance = v)) } }
                    }
                    ToggleRow("Section headings", h.showHeadings) { v -> edit { it.copy(home = h.copy(showHeadings = v)) } }
                    SliderRow("Side margins", h.sidePaddingDp, 0f..40f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(home = h.copy(sidePaddingDp = v)) } }
                    SliderRow("Space between blocks", h.blockSpacingDp, 0f..48f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(home = h.copy(blockSpacingDp = v)) } }
                }

                Section("Tray", if (look.home.dock.visible) "${look.home.dock.items.size} apps · ${look.home.dock.iconSizeDp.roundToInt()}dp icons" else "Hidden", icon = androidx.compose.material.icons.Icons.Filled.Dock) {
                    val d = look.home.dock
                    ToggleRow("Show the tray", d.visible) { v -> edit { it.updateDock { x -> x.copy(visible = v) } } }
                    ToggleRow("App drawer button", d.showDrawerButton) { v -> edit { it.updateDock { x -> x.copy(showDrawerButton = v) } } }
                    ToggleRow("Labels", d.showLabels) { v -> edit { it.updateDock { x -> x.copy(showLabels = v) } } }
                    ToggleRow("Panel behind it", d.framed) { v -> edit { it.updateDock { x -> x.copy(framed = v) } } }
                    SliderRow("Icon size", d.iconSizeDp, 28f..80f, { "${it.roundToInt()}dp" }) { v -> edit { it.updateDock { x -> x.copy(iconSizeDp = v) } } }
                    val hh = look.home
                    ToggleRow("Notification dots", hh.notificationDots, "On app icons and tiles (uses the now-playing access; counts only)") { v -> edit { it.copy(home = it.home.copy(notificationDots = v)) } }
                    ToggleRow("Long-press an app for its shortcuts", hh.appQuickMenus, "Off = long-press goes straight to the editor") { v -> edit { it.copy(home = it.home.copy(appQuickMenus = v)) } }
                    ToggleRow("Page dots above the tray", hh.showPageDots, "Only shows when you have more than one home page") { v -> edit { it.copy(home = it.home.copy(showPageDots = v)) } }
                    ToggleRow("Fade the page out under the tray", hh.trayFade, "No hard edge where the page meets the tray") { v -> edit { it.copy(home = it.home.copy(trayFade = v)) } }
                    SliderRow("Blur behind the tray", hh.trayBlurDp, 0f..40f, { if (it < 0.5f) "Off" else "${it.roundToInt()}dp" }) { v -> edit { it.copy(home = it.home.copy(trayBlurDp = v)) } }
                    Hint("Add, remove and reorder slots from home edit mode (or long-press a slot).")
                }

                Section("App drawer", if (look.drawer.columns == 0) "Auto columns" else "${look.drawer.columns} columns", icon = androidx.compose.material.icons.Icons.Filled.Apps, onReset = { edit(undoLabel = "App drawer reset") { it.copy(drawer = com.eoinknd16.nexus.look.DrawerStyle()) } }) {
                    val d = look.drawer
                    SliderRow("Columns (0 = auto)", d.columns.toFloat(), 0f..7f, { if (it < 0.5f) "auto" else it.roundToInt().toString() }, steps = 6) { v -> edit { it.copy(drawer = d.copy(columns = v.roundToInt())) } }
                    SliderRow("Icon size", d.iconSizeDp, 32f..80f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(drawer = d.copy(iconSizeDp = v)) } }
                    ToggleRow("Labels", d.showLabels) { v -> edit { it.copy(drawer = d.copy(showLabels = v)) } }
                    val appsRepo = c.apps
                    val packs = remember { appsRepo.iconPacks() }
                    val appPrefs by appsRepo.prefs.state.collectAsStateWithLifecycle()
                    ChoiceRow(
                        "Icon pack", listOf("") + packs.map { it.first }, appPrefs.iconPack,
                        { k -> if (k.isEmpty()) "Apps' own icons" else packs.firstOrNull { it.first == k }?.second ?: k },
                        subtitle = if (packs.isEmpty()) "Install a Nova/ADW-style icon pack from the Play Store to use it here" else null,
                    ) { k -> appsRepo.setIconPack(k) }
                    ToggleRow("Suggested apps row", d.showSuggestions, "Based on what you open around this time of day") { v -> edit { it.copy(drawer = d.copy(showSuggestions = v)) } }
                    ToggleRow("Search box", d.showSearch) { v -> edit { it.copy(drawer = d.copy(showSearch = v)) } }
                    ToggleRow("Open keyboard straight away", d.autoFocusSearch) { v -> edit { it.copy(drawer = d.copy(autoFocusSearch = v)) } }
                }

                Section("TV", "${look.tv.board.label} · ${look.tv.posterWidthDp.roundToInt()}dp posters", icon = androidx.compose.material.icons.Icons.Filled.Movie, heading = "Media") {
                    val t = look.tv
                    ChoiceRow("Board", BoardMode.entries, t.board, { it.label }) { v -> edit { it.copy(tv = t.copy(board = v)) } }
                    SliderRow("Poster size", t.posterWidthDp, 80f..180f, { "${it.roundToInt()}dp" }) { v -> edit { it.copy(tv = t.copy(posterWidthDp = v)) } }
                    ToggleRow("Titles under posters", t.showTitles) { v -> edit { it.copy(tv = t.copy(showTitles = v)) } }
                    ToggleRow("Year", t.showYear) { v -> edit { it.copy(tv = t.copy(showYear = v)) } }
                    ToggleRow("Ratings badge", t.showRatings) { v -> edit { it.copy(tv = t.copy(showRatings = v)) } }
                    ToggleRow("Continue watching row", t.showContinue) { v -> edit { it.copy(tv = t.copy(showContinue = v)) } }
                    ToggleRow("Play starts the top source", t.autoPlayTopSource, "Off = always pick from the list") { v -> edit { it.copy(tv = t.copy(autoPlayTopSource = v)) } }
                    Hint("Reorder or hide individual rows with ✎ on the TV screen.")
                    if (LocalRowQuery.current.isBlank() && (t.rowOrder.isNotEmpty() || t.hiddenRows.isNotEmpty() || t.hiddenTypes.isNotEmpty())) {
                        Spacer(Modifier.height(8.dp))
                        NexusButton("Reset row order & hidden rows", icon = Icons.Filled.Refresh) { edit { it.copy(tv = t.copy(rowOrder = emptyList(), hiddenRows = emptyList(), hiddenTypes = emptyList())) } }
                    }
                }

                Section("Player", "Subtitles ${if (look.player.autoSubtitles) "auto" else "manual"} · skip ${look.player.seekBackSeconds}s/${look.player.seekForwardSeconds}s", icon = androidx.compose.material.icons.Icons.Filled.PlayCircle, onReset = { edit(undoLabel = "Player reset") { it.copy(player = com.eoinknd16.nexus.look.PlayerPrefs()) } }) {
                    val p = look.player
                    TextRow("Audio language (ISO code, blank = phone's)", p.audioLanguage, "e.g. en, fr, ja") { v -> edit { it.copy(player = p.copy(audioLanguage = v.trim())) } }
                    TextRow("Subtitle language", p.subtitleLanguage, "e.g. en") { v -> edit { it.copy(player = p.copy(subtitleLanguage = v.trim())) } }
                    ToggleRow("Turn subtitles on automatically", p.autoSubtitles, "When one exists in that language") { v -> edit { it.copy(player = p.copy(autoSubtitles = v)) } }
                    SliderRow("Subtitle size", p.subtitleScale, 0.6f..2f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(player = p.copy(subtitleScale = v)) } }
                    ColorRow("Subtitle colour", p.subtitleColor) { v -> edit { it.copy(player = p.copy(subtitleColor = v)) } }
                    ColorRow("Subtitle background", p.subtitleBackground, allowAlpha = true) { v -> edit { it.copy(player = p.copy(subtitleBackground = v)) } }
                    ChoiceRow("Subtitle edge", SubtitleEdge.entries, p.subtitleEdge, { it.label }) { v -> edit { it.copy(player = p.copy(subtitleEdge = v)) } }
                    SliderRow("Subtitle height from bottom", p.subtitleBottomPadding, 0f..0.3f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(player = p.copy(subtitleBottomPadding = v)) } }
                    SliderRow("Skip back", p.seekBackSeconds.toFloat(), 5f..60f, { "${it.roundToInt()}s" }) { v -> edit { it.copy(player = p.copy(seekBackSeconds = v.roundToInt())) } }
                    SliderRow("Skip forward", p.seekForwardSeconds.toFloat(), 5f..120f, { "${it.roundToInt()}s" }) { v -> edit { it.copy(player = p.copy(seekForwardSeconds = v.roundToInt())) } }
                    ChoiceRow("Picture", ResizeMode.entries, p.resizeMode, { it.label }) { v -> edit { it.copy(player = p.copy(resizeMode = v)) } }
                    ToggleRow("Landscape while playing", p.forceLandscape) { v -> edit { it.copy(player = p.copy(forceLandscape = v)) } }
                    ToggleRow("Try the next source if one fails", p.autoFallback) { v -> edit { it.copy(player = p.copy(autoFallback = v)) } }
                    ToggleRow("Source name in the player", p.showSourceBadge) { v -> edit { it.copy(player = p.copy(showSourceBadge = v)) } }
                }

                Section("Weather", look.weather.city.ifBlank { "No location set" }, icon = androidx.compose.material.icons.Icons.Filled.Cloud, heading = "Other", searchable = false, keywords = "location city temperature celsius fahrenheit units") { WeatherSettings() }

                Section("Backup & share", "Copy, paste or share your whole setup", icon = androidx.compose.material.icons.Icons.Filled.Backup, searchable = false, keywords = "export import restore copy paste share") { BackupSettings(context) }

                if (mode is SectionMode.Search) {
                    Spacer(Modifier.height(12.dp))
                    Hint("Tap a group name to open it.")
                }
                Spacer(Modifier.height(32.dp))
            }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = com.eoinknd16.nexus.ui.fx.nexusShape(0.5f)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Nx.Deep).border(1.dp, if (query.isEmpty()) Nx.CyanDim else Nx.Cyan, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Search, null, tint = Nx.Cyan)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search settings (e.g. blur, corners, subtitles)", color = Nx.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Nx.Text, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Nx.Cyan),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text("✕", color = Nx.Muted, modifier = Modifier.nexusPress({ onChange("") }).padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun WeatherSettings() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val w = LookState.look.weather
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        c.editLook { it.copy(weather = it.weather.copy(useDeviceLocation = granted)) }
        if (!granted) status = "Location permission denied"
    }
    fun set(t: (com.eoinknd16.nexus.look.WeatherPrefs) -> com.eoinknd16.nexus.look.WeatherPrefs) {
        c.editLook { it.copy(weather = t(it.weather)) }
        scope.launch { c.weather.refresh(t(w), force = true) }
    }

    ToggleRow("Weather on", w.enabled) { v -> set { it.copy(enabled = v) } }
    ChoiceRow("Units", TempUnit.entries, w.unit, { "°${it.name}" }) { v -> set { it.copy(unit = v) } }
    ToggleRow("Use my location", w.useDeviceLocation, "Coarse location, no GPS") { v ->
        if (v && !c.weather.hasLocationPermission()) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        else set { it.copy(useDeviceLocation = v) }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { TextRow("City", query, w.city.ifBlank { "Search for a town or city" }) { query = it } }
        Spacer(Modifier.width(8.dp))
        NexusButton("Find", enabled = query.isNotBlank()) {
            scope.launch {
                status = "Searching…"
                runCatching { c.weather.searchPlaces(query) }
                    .onSuccess { results = it; status = if (it.isEmpty()) "No places found" else null }
                    .onFailure { status = "Search failed (${it.describe()})" }
            }
        }
    }
    results.forEach { p ->
        Column(
            Modifier.fillMaxWidth().nexusPress({
                set { it.copy(city = p.name, latitude = p.latitude, longitude = p.longitude, useDeviceLocation = false) }
                results = emptyList()
                query = ""
                status = "Set to ${p.name}"
            }).padding(vertical = 10.dp),
        ) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = Nx.Text)
            Text(p.detail, style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
        }
    }
    status?.let { Text(hud(it), style = MaterialTheme.typography.labelSmall, color = Nx.Amber) }
}

/** Look + your media data in one blob. The PC link and Stremio login are left out on purpose (re-pairing is one tap). */
@kotlinx.serialization.Serializable
data class FullBackup(
    val nexusBackup: Int = 1,
    val look: Look,
    val addons: List<com.eoinknd16.nexus.media.Addon> = emptyList(),
    val library: List<com.eoinknd16.nexus.media.CatalogItem> = emptyList(),
    val progress: Map<String, com.eoinknd16.nexus.media.WatchProgress> = emptyMap(),
)

@Composable
private fun BackupSettings(context: Context) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var includeData by remember { mutableStateOf(true) }
    suspend fun json(): String {
        if (!includeData) return Http.json.encodeToString(Look.serializer(), LookState.look)
        val (addons, library, progress) = c.media.snapshot()
        return Http.json.encodeToString(FullBackup.serializer(), FullBackup(look = LookState.look, addons = addons, library = library, progress = progress))
    }

    Text(
        "Copy or share your full setup (colours, fonts, home layout, tiles, folders, tray, TV and player settings) " +
            "to back it up or move it to another phone. Paste one in to load it.",
        style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
    )
    ToggleRow("Include addons, library & watch progress", includeData) { includeData = it }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NexusButton("Copy", icon = Icons.Filled.ContentCopy) {
            scope.launch {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Nexus setup", json()))
                status = "Setup copied"
            }
        }
        NexusButton("Share", icon = Icons.Filled.Share) {
            scope.launch {
                val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json())
                context.startActivity(Intent.createChooser(send, "Share Nexus setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    TextRow("Paste a setup", pasted, "{ …setup JSON… }", singleLine = false) { pasted = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NexusButton("From clipboard", icon = Icons.Filled.ContentPaste) {
            pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        }
        NexusButton("Load it", filled = true, enabled = pasted.isNotBlank()) {
            val text = pasted
            val full = if (text.contains("\"nexusBackup\"")) runCatching { Http.json.decodeFromString(FullBackup.serializer(), text) }.getOrNull() else null
            if (full != null) {
                c.editLook { full.look }
                scope.launch { c.media.restore(full.addons, full.library, full.progress) }
                status = "Setup + ${full.addons.size} addons, ${full.library.size} library titles restored"
                pasted = ""
            } else {
                runCatching { Http.json.decodeFromString(Look.serializer(), text) }
                    .onSuccess { loaded -> c.editLook { loaded }; status = "Setup loaded"; pasted = "" }
                    .onFailure { status = "That doesn't look like a Nexus setup" }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    var confirmReset by remember { mutableStateOf(false) }
    NexusButton(if (confirmReset) "Tap again to reset everything" else "Reset to defaults", accent = Nx.Danger) {
        if (confirmReset) {
            // Keep Android widget ids out of the reset layout -- they'd point at nothing.
            c.editLook { Look() }
            confirmReset = false
            status = "Back to defaults"
        } else confirmReset = true
    }
    status?.let {
        Spacer(Modifier.height(8.dp))
        Text(hud(it), style = MaterialTheme.typography.labelSmall, color = Nx.Amber, modifier = Modifier.holoPanel(glow = 0f).padding(8.dp))
    }
}


@Composable
private fun GestureSettings() {
    val c = appContainer()
    val g = LookState.look.gestures
    fun set(t: (com.eoinknd16.nexus.look.Gestures) -> com.eoinknd16.nexus.look.Gestures) = c.editLook { it.copy(gestures = t(it.gestures)) }

    Text(
        "A swipe only counts if it starts in its zone and is a clear, quick flick, so scrolling never sets one off. " +
            "The zones show up as bands in home edit mode.",
        style = MaterialTheme.typography.bodyMedium, color = Nx.Muted,
    )
    GestureRow("Swipe up", g.swipeUp, "from the bottom edge") { b -> set { it.copy(swipeUp = b) } }
    GestureRow("Swipe down", g.swipeDown, "from the top edge") { b -> set { it.copy(swipeDown = b) } }
    GestureRow("Swipe left", g.swipeLeft, "from the right edge") { b -> set { it.copy(swipeLeft = b) } }
    GestureRow("Swipe right", g.swipeRight, "from the left edge") { b -> set { it.copy(swipeRight = b) } }
    GestureRow("Two-finger swipe up", g.twoFingerUp, null) { b -> set { it.copy(twoFingerUp = b) } }
    GestureRow("Two-finger swipe down", g.twoFingerDown, null) { b -> set { it.copy(twoFingerDown = b) } }
    ActionPicker("Double-tap empty space", g.doubleTap) { a -> set { it.copy(doubleTap = a) } }
    SliderRow("Edge zone size", g.edgeSize, 0.08f..0.4f, { "${(it * 100).roundToInt()}% of screen" }) { v -> set { it.copy(edgeSize = v) } }
    SliderRow("Swipe length needed", g.minDistanceDp, 30f..160f, { "${it.roundToInt()}dp" }) { v -> set { it.copy(minDistanceDp = v) } }
    ToggleRow("Show zones in edit mode", g.showZonesInEditMode) { v -> set { it.copy(showZonesInEditMode = v) } }
    if (!com.eoinknd16.nexus.core.NexusAccessibilityService.isOn) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Lock screen / Recents need the \"Nexus gestures\" accessibility helper. It only performs those actions and reads nothing on screen.",
            style = MaterialTheme.typography.labelSmall, color = Nx.Muted,
        )
        val ctx = LocalContext.current
        NexusButton("Turn on gesture helper") { com.eoinknd16.nexus.core.NexusAccessibilityService.openSettings(ctx) }
    }
}

/** Actions a gesture can do (the tile actions that make sense without extra setup UI). */
private val GESTURE_ACTIONS: List<Pair<TileAction, String>> = buildList {
    add(TileAction(TileAction.Kind.None) to "Nothing")
    com.eoinknd16.nexus.ui.home.SCREEN_TARGETS.forEach { (k, l) -> add(TileAction(TileAction.Kind.Screen, k) to "Open $l") }
    add(TileAction(TileAction.Kind.Notifications) to "Notifications")
    add(TileAction(TileAction.Kind.QuickSettings) to "Quick settings")
    add(TileAction(TileAction.Kind.LockScreen) to "Lock screen")
    add(TileAction(TileAction.Kind.Recents) to "Recent apps")
    add(TileAction(TileAction.Kind.PcHub) to "PC controls panel")
    add(TileAction(TileAction.Kind.WakePc) to "Wake PC")
    add(TileAction(TileAction.Kind.SwitchPc, "next") to "Switch to next PC")
    add(TileAction(TileAction.Kind.PcPower, "sleep") to "Sleep the PC")
    add(TileAction(TileAction.Kind.PcPower, "shutdown") to "Shut down the PC")
    add(TileAction(TileAction.Kind.WebSearch) to "Web search")
    com.eoinknd16.nexus.ui.home.PC_SCREEN_TARGETS.forEach { (k, l) -> add(TileAction(TileAction.Kind.PcScreen, k) to "PC: $l") }
}

@Composable
private fun ActionPicker(title: String, action: TileAction, onChange: (TileAction) -> Unit) {
    val apps by appContainer().apps.apps.collectAsStateWithLifecycle()
    val appOptions = apps.map { TileAction(TileAction.Kind.LaunchApp, it.key) to "App: ${it.label}" }
    val pageOptions = listOf(
        TileAction(TileAction.Kind.Page, "next") to "Next home page",
        TileAction(TileAction.Kind.Page, "prev") to "Previous home page",
    ) + com.eoinknd16.nexus.look.LookState.look.home.pages.map { TileAction(TileAction.Kind.Page, it.id) to "Page: ${it.name}" }
    val all = GESTURE_ACTIONS + pageOptions + appOptions
    val current = all.firstOrNull { it.first == action } ?: (action to action.kind.label)
    ChoiceRow(title, all, current, { it.second }) { onChange(it.first) }
}

@Composable
private fun GestureRow(title: String, binding: com.eoinknd16.nexus.look.GestureBinding, edgeLabel: String?, onChange: (com.eoinknd16.nexus.look.GestureBinding) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).holoPanel(glow = 0f).padding(10.dp)) {
        ActionPicker(title, binding.action) { onChange(binding.copy(action = it)) }
        if (edgeLabel != null && binding.action.kind != TileAction.Kind.None) {
            ChoiceRow(
                "Starts", com.eoinknd16.nexus.look.GestureZone.entries, binding.zone,
                { if (it == com.eoinknd16.nexus.look.GestureZone.Edge) "Only $edgeLabel" else "Anywhere" },
            ) { onChange(binding.copy(zone = it)) }
        }
    }
}
