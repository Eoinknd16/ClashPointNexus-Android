package com.eoinknd16.nexus.look

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Everything about how Nexus looks and what it shows, in one serializable
 * tree. Persisted as JSON (so it can be exported/imported/shared whole) and
 * pushed into [LookState] so every composable reads it live -- change a
 * value anywhere and the whole UI recomposes with it.
 *
 * Every field has a default, so configs saved by older builds keep loading
 * after new options are added.
 */
@Serializable
data class Look(
    val presetName: String = "Nexus",
    val palette: Palette = Palette(),
    val fonts: Fonts = Fonts(),
    val shape: ShapeStyle = ShapeStyle(),
    val backdrop: Backdrop = Backdrop(),
    val motion: Motion = Motion(),
    val home: HomeLayout = HomeLayout.default(),
    val drawer: DrawerStyle = DrawerStyle(),
    val tv: TvStyle = TvStyle(),
    val player: PlayerPrefs = PlayerPrefs(),
    val weather: WeatherPrefs = WeatherPrefs(),
)

/** Colours as #RRGGBB / #AARRGGBB strings (readable + hand-editable in exported JSON). */
@Serializable
data class Palette(
    val accent: String = "#3CF2FF",
    val accent2: String = "#FF3CAC",
    val warn: String = "#FFB23C",
    val good: String = "#7CFF6B",
    val danger: String = "#FF4D6D",
    val background: String = "#05070D",
    val deep: String = "#0A0F1C",
    val panel: String = "#0D1526",
    val text: String = "#E6F7FF",
    val muted: String = "#7A8FA6",
    /** 0..1 -- how see-through panels are over the backdrop. */
    val panelOpacity: Float = 0.8f,
)

@Serializable
enum class FontChoice(val label: String) {
    Default("Default"), Light("Light"), Thin("Thin"), Medium("Medium"), Condensed("Condensed"),
    Serif("Serif"), Mono("Monospace"), Casual("Casual"), Cursive("Cursive"), SmallCaps("Small caps"),
}

@Serializable
data class Fonts(
    val heading: FontChoice = FontChoice.Default,
    val body: FontChoice = FontChoice.Default,
    /** The HUD-style labels ("// COMMAND DECK"). */
    val label: FontChoice = FontChoice.Mono,
    val clock: FontChoice = FontChoice.Thin,
    /** Multiplies every text size. */
    val scale: Float = 1f,
    val uppercaseLabels: Boolean = true,
)

@Serializable
enum class CornerStyle(val label: String) { Cut("Angled cut"), Round("Rounded"), Square("Square"), Pill("Pill") }

@Serializable
data class ShapeStyle(
    val corners: CornerStyle = CornerStyle.Cut,
    /** Corner size in dp. */
    val cornerSize: Float = 14f,
    val borderWidth: Float = 1f,
    /** 0..1 rim + bracket glow strength. */
    val glow: Float = 0.35f,
    val brackets: Boolean = true,
    /** Gradient (accent -> accent2) rim vs single-colour rim. */
    val gradientBorder: Boolean = true,
)

@Serializable
enum class BackdropStyle(val label: String) {
    Grid("Grid horizon"), Nebula("Nebula"), Stars("Starfield"), Waves("Waves"), Plain("Plain colour"),
}

@Serializable
data class Backdrop(
    val style: BackdropStyle = BackdropStyle.Grid,
    val intensity: Float = 1f,
    val speed: Float = 1f,
    val scanlines: Boolean = true,
    val vignette: Boolean = true,
    /** Animated at all (static saves battery). */
    val animated: Boolean = true,
    val fps: Int = 30,
)

@Serializable
data class Motion(
    val animations: Boolean = true,
    /** Scale a tile shrinks to while pressed (1 = no shrink). */
    val pressScale: Float = 0.94f,
    val haptics: Boolean = true,
    /** Slow glow pulse on tiles. */
    val pulse: Boolean = true,
)

// ---- Home layout ---------------------------------------------------------------

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
enum class Align { Start, Center, End }

/**
 * One thing on the home screen. The home screen is just an ordered list of
 * these -- add, remove, reorder and configure any of them from edit mode.
 */
@Serializable
sealed class HomeBlock {
    abstract val id: String
    abstract val visible: Boolean

    @Serializable
    @SerialName("clock")
    data class Clock(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val use24h: Boolean = true,
        val showSeconds: Boolean = false,
        val showDate: Boolean = true,
        val dateFormat: String = "EEEE · d MMMM",
        val sizeSp: Float = 72f,
        val align: Align = Align.Start,
        val blinkColon: Boolean = true,
        val showPcStatus: Boolean = true,
        /** Weather line under the date (uses Settings > Weather location). */
        val showWeather: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("weather")
    data class Weather(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val forecastDays: Int = 4,
        val showDetails: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("search")
    data class SearchBar(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val engine: SearchEngine = SearchEngine.Google,
        /** For [SearchEngine.Custom]: %s is replaced with the query. */
        val customUrl: String = "https://www.google.com/search?q=%s",
        val placeholder: String = "Search the web",
        val showMic: Boolean = true,
        /** Matching installed apps appear as you type. */
        val matchApps: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("tiles")
    data class Tiles(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val title: String = "Command deck",
        val columns: Int = 2,
        val aspect: Float = 1.05f,
        val showSublabels: Boolean = true,
        val tiles: List<TileConfig> = emptyList(),
    ) : HomeBlock()

    @Serializable
    @SerialName("resume")
    data class Resume(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val title: String = "Resume",
    ) : HomeBlock()

    @Serializable
    @SerialName("posters")
    data class Posters(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val title: String = "Recently watched",
        val source: PosterSource = PosterSource.Recent,
        val count: Int = 15,
    ) : HomeBlock()

    @Serializable
    @SerialName("apps")
    data class AppRow(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val title: String = "Apps",
        /** Launcher component keys. */
        val apps: List<String> = emptyList(),
        val iconSizeDp: Float = 52f,
        val showLabels: Boolean = true,
    ) : HomeBlock()

    /** A real Android app widget (Google search bar, calendar, music...). */
    @Serializable
    @SerialName("widget")
    data class Widget(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val appWidgetId: Int,
        val label: String = "Widget",
        val heightDp: Float = 120f,
        /** Draw it inside a holo panel, or bare. */
        val framed: Boolean = false,
    ) : HomeBlock()

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val text: String = "Welcome back",
        val sizeSp: Float = 20f,
        val align: Align = Align.Start,
        val accent: Boolean = false,
    ) : HomeBlock()

    @Serializable
    @SerialName("spacer")
    data class Spacer(
        override val id: String = newId(),
        override val visible: Boolean = true,
        val heightDp: Float = 24f,
    ) : HomeBlock()

    val kindLabel: String
        get() = when (this) {
            is Clock -> "Clock"
            is Weather -> "Weather"
            is SearchBar -> "Search bar"
            is Tiles -> "Tiles"
            is Resume -> "Resume card"
            is Posters -> "Poster row"
            is AppRow -> "App row"
            is Widget -> "Widget: $label"
            is Text -> "Text"
            is Spacer -> "Spacer"
        }

    fun withVisible(v: Boolean): HomeBlock = when (this) {
        is Clock -> copy(visible = v)
        is Weather -> copy(visible = v)
        is SearchBar -> copy(visible = v)
        is Tiles -> copy(visible = v)
        is Resume -> copy(visible = v)
        is Posters -> copy(visible = v)
        is AppRow -> copy(visible = v)
        is Widget -> copy(visible = v)
        is Text -> copy(visible = v)
        is Spacer -> copy(visible = v)
    }
}

@Serializable
enum class SearchEngine(val label: String, val url: String) {
    Google("Google", "https://www.google.com/search?q=%s"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    Bing("Bing", "https://www.bing.com/search?q=%s"),
    Brave("Brave", "https://search.brave.com/search?q=%s"),
    YouTube("YouTube", "https://www.youtube.com/results?search_query=%s"),
    Custom("Custom URL", ""),
}

@Serializable
enum class PosterSource(val label: String) { Recent("Recently watched"), Library("Your library") }

/** What a tile does when tapped. [kind] + [arg] keeps the JSON simple and hand-editable. */
@Serializable
data class TileAction(val kind: Kind, val arg: String = "") {
    @Serializable
    enum class Kind(val label: String) {
        Screen("Open Nexus screen"),
        LaunchApp("Launch app"),
        WakePc("Wake PC"),
        PcNav("PC remote button"),
        PcScreen("Show PC screen"),
        Url("Open link"),
        WebSearch("Web search"),
        Settings("Android setting"),
        Folder("Folder of apps"),
        Notifications("Pull down notifications"),
        None("Nothing"),
    }
}

@Serializable
enum class FolderAnimation(val label: String) { Bloom("Bloom from tile"), Rise("Rise up"), Fade("Fade"), Spin("Warp spin") }

@Serializable
enum class FolderStyle(val label: String) { Grid("Grid"), Ring("Orbit ring") }

@Serializable
data class TileConfig(
    val id: String = newId(),
    val label: String,
    val sublabel: String = "",
    /** Key in [Icons] (see look/IconCatalog.kt). */
    val icon: String = "bolt",
    /** Palette slot ("accent", "accent2", "warn", "good", "danger", "muted") or a #hex colour. */
    val color: String = "accent",
    val action: TileAction,
    /** Optional image (URL) drawn behind the tile. */
    val image: String? = null,
    /** For LaunchApp: show the app's own icon instead of [icon]. */
    val useAppIcon: Boolean = true,
    /** For Folder: launcher component keys of the apps inside, in order. */
    val folderApps: List<String> = emptyList(),
    val folderColumns: Int = 4,
    val folderAnimation: FolderAnimation = FolderAnimation.Bloom,
    val folderStyle: FolderStyle = FolderStyle.Grid,
    /** Folder tiles show a mini preview of the first apps' icons. */
    val folderPreview: Boolean = true,
)

/**
 * The tray at the bottom of home. Each slot is a full [TileConfig], so a slot
 * can be one app, any action, or a folder of apps -- same options as tiles.
 */
@Serializable
data class DockConfig(
    val visible: Boolean = true,
    val items: List<TileConfig> = emptyList(),
    /** Old builds pinned apps from the drawer; those get copied in once. */
    val migratedPins: Boolean = false,
    val iconSizeDp: Float = 46f,
    val showDrawerButton: Boolean = true,
    val showLabels: Boolean = false,
    val framed: Boolean = true,
    /** Scroll sideways when there are more slots than fit, instead of shrinking. */
    val scrollable: Boolean = true,
)

@Serializable
data class HomeLayout(
    val blocks: List<HomeBlock> = emptyList(),
    val dock: DockConfig = DockConfig(),
    val swipeUpForApps: Boolean = true,
    val swipeDownForNotifications: Boolean = true,
    val sidePaddingDp: Float = 20f,
    val blockSpacingDp: Float = 22f,
    /** Show "// LABEL" headings above blocks that have titles. */
    val showHeadings: Boolean = true,
) {
    companion object {
        fun defaultTiles(): List<TileConfig> = listOf(
            TileConfig(label = "TV", sublabel = "Movies & shows", icon = "movie", action = TileAction(TileAction.Kind.Screen, "tv")),
            TileConfig(label = "Remote", sublabel = "Control the PC", icon = "remote", color = "accent2", action = TileAction(TileAction.Kind.Screen, "pc")),
            TileConfig(label = "Wake PC", sublabel = "Magic packet", icon = "power", color = "warn", action = TileAction(TileAction.Kind.WakePc)),
            TileConfig(label = "Apps", sublabel = "Swipe up anywhere", icon = "apps", color = "good", action = TileAction(TileAction.Kind.Screen, "apps")),
            TileConfig(label = "Search", sublabel = "Find anything", icon = "search", action = TileAction(TileAction.Kind.Screen, "search")),
            TileConfig(label = "Customise", sublabel = "Make it yours", icon = "palette", color = "muted", action = TileAction(TileAction.Kind.Screen, "customize")),
        )

        fun default() = HomeLayout(
            blocks = listOf(
                HomeBlock.Clock(),
                HomeBlock.SearchBar(),
                HomeBlock.Resume(),
                HomeBlock.Tiles(tiles = defaultTiles()),
                HomeBlock.Posters(),
            ),
        )
    }
}

@Serializable
data class DrawerStyle(
    /** 0 = fit as many as the width allows. */
    val columns: Int = 0,
    val iconSizeDp: Float = 52f,
    val showLabels: Boolean = true,
    val showSearch: Boolean = true,
    /** Pop the keyboard as soon as the drawer opens. */
    val autoFocusSearch: Boolean = false,
)

@Serializable
enum class BoardMode(val label: String) {
    /** Stremio-style if your addons include Cinemeta (e.g. after an import), else Nexus. */
    Auto("Auto"),
    /** Exactly your addons' catalogs, in your addon order, like the Stremio app. */
    Stremio("Stremio (addons only)"),
    /** Your addons' rows plus Nexus' built-in popular / top rated / genre rows. */
    Nexus("Nexus (addons + built-in rows)"),
}

@Serializable
data class TvStyle(
    val board: BoardMode = BoardMode.Auto,
    val posterWidthDp: Float = 112f,
    val showRatings: Boolean = true,
    val showTitles: Boolean = true,
    val showYear: Boolean = true,
    val showContinue: Boolean = true,
    /** Row keys in your preferred order; rows not listed keep their natural order after these. */
    val rowOrder: List<String> = emptyList(),
    val hiddenRows: List<String> = emptyList(),
    /** Content types (tabs) hidden from TV, e.g. "channel". */
    val hiddenTypes: List<String> = emptyList(),
    val defaultType: String = "movie",
    /** Main Play button: start the top source immediately vs always show the source list. */
    val autoPlayTopSource: Boolean = true,
)

@Serializable
enum class SubtitleEdge(val label: String) { None("None"), Outline("Outline"), Shadow("Drop shadow"), Raised("Raised"), Depressed("Depressed") }

@Serializable
enum class ResizeMode(val label: String) { Fit("Fit"), Fill("Stretch"), Zoom("Zoom / crop") }

@Serializable
data class PlayerPrefs(
    /** ISO 639 code; "" = phone language. */
    val audioLanguage: String = "",
    val subtitleLanguage: String = "en",
    /** Load a subtitle in [subtitleLanguage] automatically when one exists. */
    val autoSubtitles: Boolean = false,
    val subtitleScale: Float = 1f,
    val subtitleColor: String = "#FFFFFF",
    val subtitleBackground: String = "#00000000",
    val subtitleEdge: SubtitleEdge = SubtitleEdge.Outline,
    val subtitleBottomPadding: Float = 0.08f,
    val autoFallback: Boolean = true,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 10,
    val resizeMode: ResizeMode = ResizeMode.Fit,
    /** Landscape lock while playing (off = follow the phone's rotation). */
    val forceLandscape: Boolean = true,
    val showSourceBadge: Boolean = true,
)

@Serializable
enum class TempUnit { C, F }

@Serializable
data class WeatherPrefs(
    val enabled: Boolean = true,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Use the phone's (coarse) location instead of a fixed city. */
    val useDeviceLocation: Boolean = false,
    val unit: TempUnit = TempUnit.C,
)
