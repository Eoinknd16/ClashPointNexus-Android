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
    val gestures: Gestures = Gestures(),
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
    val spotify: SpotifyLook = SpotifyLook(),
)

enum class SpArtStyle(val label: String) { Rounded("Rounded"), Square("Square"), Vinyl("Spinning vinyl") }

/** The Spotify control centre, from its own look panel (tune icon, top right). */
@Serializable
data class SpotifyLook(
    /** "library" or "player". */
    val openOn: String = "library",
    val miniPlayer: Boolean = true,
    val artStyle: SpArtStyle = SpArtStyle.Rounded,
    /** Soft glow of the album art behind the player. */
    val artGlow: Boolean = true,
    val glowStrength: Float = 0.45f,
    /** Tint buttons and bars with the album art's colour. */
    val artColours: Boolean = true,
    /** Playlists / albums as a grid (else a list). */
    val gridLibrary: Boolean = true,
    val gridColumns: Int = 2,
    val compactRows: Boolean = false,
    val showUpNext: Boolean = true,
    val showPlaylistsShelf: Boolean = true,
    val showOnRepeat: Boolean = true,
    val showRecent: Boolean = true,
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
enum class BorderStyle(val label: String) {
    Gradient("Gradient"), Solid("Solid"), Neon("Neon (animated)"), Double("Double line"), Dashed("Dashed"), Glow("Soft glow only"), None("No border"),
}

@Serializable
data class ShapeStyle(
    val corners: CornerStyle = CornerStyle.Cut,
    val borderStyle: BorderStyle = BorderStyle.Gradient,
    /** Palette slot or #hex for the rim; "accent" by default. */
    val borderColor: String = "accent",
    /** Outer glow spread in dp (0 = none). */
    val glowRadius: Float = 0f,
    /** Seconds per rotation of the animated neon rim. */
    val neonSpeed: Float = 4f,
    /** Corner size in dp. */
    val cornerSize: Float = 14f,
    val borderWidth: Float = 1f,
    /** 0..1 rim + bracket glow strength. */
    val glow: Float = 0.35f,
    val brackets: Boolean = true,
    /** Legacy (pre-borderStyle) setting; kept so old configs load. */
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
    /** Backdrop frame rate cap; 0 = match the display (60/90/120/144Hz). */
    val fps: Int = 0,
)

@Serializable
enum class ScreenTransition(val label: String) { Fade("Fade"), Scale("Zoom"), Slide("Slide"), Rise("Rise"), Warp("Warp"), None("Instant") }

@Serializable
enum class Entrance(val label: String) { None("None"), Fade("Fade in"), Rise("Rise up"), Pop("Pop"), Cascade("Cascade"), Flip("Flip") }

@Serializable
enum class AppLaunch(val label: String) { Zoom("Zoom from icon"), Reveal("Circular reveal"), Fade("Fade"), System("Android default") }

@Serializable
data class Motion(
    val animations: Boolean = true,
    /** Scale a tile shrinks to while pressed (1 = no shrink). */
    val pressScale: Float = 0.94f,
    val haptics: Boolean = true,
    /** Slow glow pulse on tiles. */
    val pulse: Boolean = true,
    /** Multiplies every animation's speed (2 = twice as fast). */
    val speed: Float = 1f,
    /** 0 = stiff and snappy, 1 = loose and bouncy. */
    val bounce: Float = 0.45f,
    val screenTransition: ScreenTransition = ScreenTransition.Scale,
    val tileEntrance: Entrance = Entrance.Cascade,
    val appLaunch: AppLaunch = AppLaunch.Zoom,
    /** Haptic strength for gestures: light tick vs heavy thud. */
    val strongHaptics: Boolean = false,
    /** Ask the phone for its fastest refresh rate (90/120/144Hz) while Nexus is open. */
    val highRefreshRate: Boolean = true,
    /** Tilting the phone shifts the background and home slightly, like layered glass. */
    val parallax: Boolean = true,
    val parallaxStrength: Float = 1f,
    /** A soft light that slides across panels as the phone moves. */
    val sheen: Boolean = true,
    /** Night mode: dim and warm everything between these hours. */
    val night: Boolean = false,
    val nightStart: Int = 22,
    val nightEnd: Int = 7,
    val nightStrength: Float = 0.35f,
)

// ---- Home layout ---------------------------------------------------------------

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
enum class Align { Start, Center, End }

/** How a tile arranges its icon and text. */
@Serializable
enum class TileLayout(val label: String) {
    Stack("Icon top, text bottom"), Centered("Centred"), Inline("Icon beside text"), IconOnly("Icon only"),
}

/** Size and placement of any home block (stored by block id in [HomeLayout.blockSizes]). */
@Serializable
data class BlockSize(
    /** Width as a % of the screen's content width. */
    val widthPct: Float = 100f,
    val align: Align = Align.Center,
    /** Shrinks or enlarges everything inside the block. */
    val scale: Float = 1f,
)

/**
 * One thing on the home screen. The home screen is just an ordered list of
 * these -- add, remove, reorder and configure any of them from edit mode.
 */
/** Pinned blocks stay fixed on screen; only the unpinned middle scrolls. */
@Serializable
enum class BlockPin(val label: String) { None("Scrolls"), Top("Pinned top"), Bottom("Pinned bottom") }

@Serializable
sealed class HomeBlock {
    abstract val id: String
    abstract val visible: Boolean
    abstract val pin: BlockPin
    /** Locked = can't be moved, removed or reconfigured until unlocked. */
    abstract val locked: Boolean

    @Serializable
    @SerialName("clock")
    data class Clock(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
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
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val forecastDays: Int = 4,
        val showDetails: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("search")
    data class SearchBar(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val engine: SearchEngine = SearchEngine.Google,
        /** For [SearchEngine.Custom]: %s is replaced with the query. */
        val customUrl: String = "https://www.google.com/search?q=%s",
        val placeholder: String = "Search your phone and the web",
        val showMic: Boolean = true,
        /** Matching installed apps appear as you type. */
        val matchApps: Boolean = true,
        /** Also offer "Ask Nexus" (AI) inside this search. Off = a plain search. */
        val aiInline: Boolean = false,
    ) : HomeBlock()

    /** A bar that goes straight to Ask Nexus (the AI), separate from search. */
    @Serializable
    @SerialName("askbar")
    data class AskBar(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val placeholder: String = "Ask Nexus to do something…",
        val showMic: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("tiles")
    data class Tiles(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val title: String = "Command deck",
        val columns: Int = 2,
        val aspect: Float = 1.05f,
        val showSublabels: Boolean = true,
        val tiles: List<TileConfig> = emptyList(),
        val gapDp: Float = 12f,
        /** 0 = use [aspect]; otherwise every row is this tall. */
        val rowHeightDp: Float = 0f,
        val iconSizeDp: Float = 40f,
        val labelScale: Float = 1f,
        val paddingDp: Float = 14f,
        val tileLayout: TileLayout = TileLayout.Stack,
    ) : HomeBlock()

    @Serializable
    @SerialName("resume")
    data class Resume(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val title: String = "Resume",
        /** Movies & shows from Nexus TV. */
        val showNexus: Boolean = true,
        /** Anything else playing: Spotify, YouTube, podcasts… */
        val showApps: Boolean = true,
        val maxItems: Int = 2,
        val showControls: Boolean = true,
        /** Apps never shown here (package names). */
        val hiddenApps: List<String> = emptyList(),
        /** Forget app media older than this many hours (0 = keep). */
        val maxAgeHours: Int = 72,
        /** Small × on app cards to hide them until something new starts. */
        val showDismiss: Boolean = true,
    ) : HomeBlock()

    @Serializable
    @SerialName("posters")
    data class Posters(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val title: String = "Recently watched",
        val source: PosterSource = PosterSource.Recent,
        val count: Int = 15,
        val posterWidthDp: Float = 96f,
    ) : HomeBlock()

    @Serializable
    @SerialName("apps")
    data class AppRow(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val title: String = "Apps",
        /** Launcher component keys. */
        val apps: List<String> = emptyList(),
        val iconSizeDp: Float = 52f,
        val showLabels: Boolean = true,
        /** Fill the row with suggested apps (by time of day and habit) instead of a fixed list. */
        val suggested: Boolean = false,
        val count: Int = 5,
    ) : HomeBlock()

    /** A real Android app widget (Google search bar, calendar, music...). */
    @Serializable
    @SerialName("widget")
    data class Widget(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
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
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
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
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val heightDp: Float = 24f,
    ) : HomeBlock()

    /** "At a glance": the few things worth knowing right now, changing through the day. */
    @Serializable
    @SerialName("glance")
    data class Glance(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val greeting: Boolean = true,
        val calendar: Boolean = true,
        val alarm: Boolean = true,
        val rain: Boolean = true,
        val battery: Boolean = true,
        val nowPlaying: Boolean = true,
        val pcLateNight: Boolean = true,
        val maxLines: Int = 3,
        val framed: Boolean = false,
    ) : HomeBlock()

    /** Battery level as liquid in a glass that sloshes when you tilt or shake the phone. */
    @Serializable
    @SerialName("liquid")
    data class Liquid(
        override val id: String = newId(),
        override val visible: Boolean = true,
        override val pin: BlockPin = BlockPin.None,
        override val locked: Boolean = false,
        val heightDp: Float = 110f,
        val showPercent: Boolean = true,
        /** Green when full, amber getting low, red when nearly empty (else the theme colour). */
        val colourByLevel: Boolean = true,
        val framed: Boolean = true,
        /** How freely it sloshes (0 = thick like honey, 1 = water). */
        val wobble: Float = 0.7f,
    ) : HomeBlock()

    val kindLabel: String
        get() = when (this) {
            is Clock -> "Clock"
            is Weather -> "Weather"
            is SearchBar -> "Search bar"
            is AskBar -> "Ask Nexus bar"
            is Tiles -> "Tiles"
            is Resume -> "Resume card"
            is Posters -> "Poster row"
            is AppRow -> "App row"
            is Widget -> "Widget: $label"
            is Text -> "Text"
            is Spacer -> "Spacer"
            is Glance -> "At a glance"
            is Liquid -> "Liquid battery"
        }

    fun withVisible(v: Boolean): HomeBlock = copyMeta(visible = v)
    fun withPin(p: BlockPin): HomeBlock = copyMeta(pin = p)
    fun withLocked(l: Boolean): HomeBlock = copyMeta(locked = l)

    private fun copyMeta(visible: Boolean = this.visible, pin: BlockPin = this.pin, locked: Boolean = this.locked): HomeBlock = when (this) {
        is Clock -> copy(visible = visible, pin = pin, locked = locked)
        is Weather -> copy(visible = visible, pin = pin, locked = locked)
        is SearchBar -> copy(visible = visible, pin = pin, locked = locked)
        is AskBar -> copy(visible = visible, pin = pin, locked = locked)
        is Tiles -> copy(visible = visible, pin = pin, locked = locked)
        is Resume -> copy(visible = visible, pin = pin, locked = locked)
        is Posters -> copy(visible = visible, pin = pin, locked = locked)
        is AppRow -> copy(visible = visible, pin = pin, locked = locked)
        is Widget -> copy(visible = visible, pin = pin, locked = locked)
        is Text -> copy(visible = visible, pin = pin, locked = locked)
        is Spacer -> copy(visible = visible, pin = pin, locked = locked)
        is Glance -> copy(visible = visible, pin = pin, locked = locked)
        is Liquid -> copy(visible = visible, pin = pin, locked = locked)
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
enum class PosterSource(val label: String) { Recent("Recently watched"), Library("Your library"), ForYou("Picked for you") }

/** Where on screen a swipe has to start for it to count. */
@Serializable
enum class GestureZone(val label: String) {
    Edge("From the matching edge"), Anywhere("Anywhere"),
}

@Serializable
data class GestureBinding(
    val action: TileAction = TileAction(TileAction.Kind.None),
    val zone: GestureZone = GestureZone.Edge,
)

/**
 * Home-screen gestures. A swipe only counts if it starts in its zone (by
 * default: the bottom edge for up, the top edge for down, the side edges for
 * left/right), travels far enough and fast enough in one clear direction --
 * so scrolling the middle of the screen never sets one off by accident.
 */
@Serializable
data class Gestures(
    val swipeUp: GestureBinding = GestureBinding(TileAction(TileAction.Kind.Screen, "apps")),
    val swipeDown: GestureBinding = GestureBinding(TileAction(TileAction.Kind.Notifications)),
    // Off by default: Android's own back gesture owns the side edges, and
    // sideways swipes elsewhere would fight the scrolling rows.
    val swipeLeft: GestureBinding = GestureBinding(TileAction(TileAction.Kind.None), GestureZone.Anywhere),
    val swipeRight: GestureBinding = GestureBinding(TileAction(TileAction.Kind.None), GestureZone.Anywhere),
    val twoFingerUp: GestureBinding = GestureBinding(TileAction(TileAction.Kind.Screen, "customize"), GestureZone.Anywhere),
    val twoFingerDown: GestureBinding = GestureBinding(TileAction(TileAction.Kind.QuickSettings), GestureZone.Anywhere),
    val doubleTap: TileAction = TileAction(TileAction.Kind.LockScreen),
    /** How deep the edge zones are, as a fraction of screen height/width. */
    val edgeSize: Float = 0.2f,
    /** Minimum swipe length in dp. */
    val minDistanceDp: Float = 70f,
    /** Show the zones while editing home. */
    val showZonesInEditMode: Boolean = true,
)

/** What a tile does when tapped. [kind] + [arg] keeps the JSON simple and hand-editable. */
@Serializable
data class TileAction(val kind: Kind, val arg: String = "") {
    @Serializable
    enum class Kind(val label: String) {
        Screen("Open Nexus screen"),
        LaunchApp("Launch app"),
        PcHub("PC controls (all in one)"),
        WakePc("Wake PC"),
        SwitchPc("Switch PC"),
        PcPower("Shut down / restart / sleep PC"),
        PcNav("PC remote button"),
        PcScreen("Show PC screen"),
        Url("Open link"),
        WebSearch("Web search"),
        Settings("Android setting"),
        Folder("Folder of apps"),
        Notifications("Pull down notifications"),
        QuickSettings("Open quick settings"),
        LockScreen("Lock the screen"),
        Recents("Recent apps"),
        Page("Go to a home page"),
        None("Nothing"),
    }
}

@Serializable
enum class FolderAnimation(val label: String) { Bloom("Bloom from tile"), Rise("Rise up"), Fade("Fade"), Spin("Warp spin") }

@Serializable
enum class FolderTileStyle(val label: String) {
    Icon("Small preview icon"), Fill("Icons fill the tile"), Physics("Loose icons with physics"),
}

@Serializable
enum class FolderStyle(val label: String) { Grid("Grid"), Ring("Orbit ring") }

/** What sits behind a folder's apps. None = the icons float on the blurred home screen. */
@Serializable
enum class FolderBackground(val label: String) { Panel("Holo panel"), Glass("Frosted glass"), None("None (floating)") }

@Serializable
enum class FolderPosition(val label: String) { Center("Centre"), FromTile("At the tile"), Bottom("Bottom") }

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
    val folderBackground: FolderBackground = FolderBackground.Panel,
    /** Home-screen blur behind the open folder, in dp (Android 12+; older phones dim instead). */
    val folderBlur: Float = 16f,
    /** 0..1 darkening behind the open folder. */
    val folderDim: Float = 0.45f,
    val folderPosition: FolderPosition = FolderPosition.Center,
    val folderIconSize: Float = 52f,
    val folderShowLabels: Boolean = true,
    val folderShowTitle: Boolean = true,
    /** Play the opening animation backwards when closing. */
    val folderAnimateClose: Boolean = true,
    /** Pencil button inside the open folder (long-pressing the tile edits it either way). */
    val folderShowEditButton: Boolean = false,
    /** How a folder looks on its tile: a small preview icon, icons filling the tile, or loose icons with physics. */
    val folderTileStyle: FolderTileStyle = FolderTileStyle.Fill,
    /** Icon size on the tile in Fill / Physics, relative to the space available. */
    val folderTileIconScale: Float = 1f,
    /** Show the folder's name on the tile under the icons. */
    val folderTileLabel: Boolean = true,
    /** Physics: how bouncy the icons are (0 = thud, 1 = superball). */
    val folderPhysicsBounce: Float = 0.45f,
    /** Physics: tilting the phone moves the icons. */
    val folderPhysicsTilt: Boolean = true,
    /** Physics: how hard the icons get thrown when home scrolls / pages slide. */
    val folderPhysicsInertia: Float = 1f,
    /** Physics: 1 = real-life speed and weight for the phone's actual size; lower = floatier. */
    val folderPhysicsRealism: Float = 1f,
    /** Physics: how much shaking / bumping the phone throws them about (0 = off). */
    val folderPhysicsShake: Float = 1f,
    /** Physics: grip on the glass when the phone's lying flat (0 = ice, 1 = real, 2 = sticky). */
    val folderPhysicsGrip: Float = 1f,
    /** Physics: icons spin when they glance off things. */
    val folderPhysicsSpin: Boolean = true,
    /** Physics: a small buzz when they hit something. */
    val folderPhysicsHaptics: Boolean = true,
    /** Locked = long-press won't open the editor (unlock from home edit mode). */
    val locked: Boolean = false,
    /** Per-tile border look; null fields fall back to Customise › Panels. */
    val border: BorderOverride? = null,
    /** Per-tile entrance animation; null = Customise › Motion. */
    val entrance: Entrance? = null,
    /** How many columns / rows of its grid this tile covers. */
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
)

@Serializable
data class BorderOverride(
    val style: BorderStyle? = null,
    val width: Float? = null,
    /** Palette slot or #hex. */
    val color: String? = null,
    val glow: Float? = null,
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
    /** How the slots sit in the tray when they don't fill it. */
    val arrange: DockArrange = DockArrange.Even,
    /** Every slot gets the same width, so icons line up evenly. */
    val equalSlots: Boolean = true,
    /** Centre the apps across the whole tray, not just the space left of the drawer button. */
    val centerOnTray: Boolean = true,
    val spacingDp: Float = 14f,
)

@Serializable
enum class DockArrange(val label: String) { Even("Spread evenly"), Center("Centre"), Start("Left"), End("Right") }

/**
 * How the scrolling middle of home handles being a bit too tall for the screen.
 * Auto: if it overflows by no more than [HomeLayout.fitTolerance], shrink it
 * evenly to fit exactly (no stray scrolling); otherwise scroll. Always: always
 * fit (down to half size). Off: always scroll.
 */
@Serializable
enum class FitMode(val label: String) { Auto("Auto-fit"), Always("Always fit"), Off("Scroll") }

/** An extra home page with its own blocks. The tray is shared. */
@Serializable
data class HomePage(
    val id: String = newId(),
    val name: String = "New page",
    val blocks: List<HomeBlock> = emptyList(),
    val showDock: Boolean = true,
)

@Serializable
data class HomeLayout(
    val fit: FitMode = FitMode.Auto,
    /** Auto-fit kicks in when the overflow is at most this fraction of the screen. */
    val fitTolerance: Float = 0.25f,
    val blocks: List<HomeBlock> = emptyList(),
    val dock: DockConfig = DockConfig(),
    val sidePaddingDp: Float = 20f,
    val blockSpacingDp: Float = 22f,
    /** Show "// LABEL" headings above blocks that have titles. */
    val showHeadings: Boolean = true,
    /** Extra home pages besides the main one ([blocks]); each starts blank. */
    val pages: List<HomePage> = emptyList(),
    /** Little dots above the tray showing which page you're on. */
    val showPageDots: Boolean = true,
    /** Which side of the main page the extra pages sit: "auto", "left" or "right". */
    val pagesSide: String = "auto",
    /** Blur of the page where it slides under the tray (dp; 0 = off; Android 12+). */
    val trayBlurDp: Float = 18f,
    /** Fade the page out under the tray and off the bottom of the screen. */
    val trayFade: Boolean = true,
    /** Dots on app icons and tiles with unread notifications. */
    val notificationDots: Boolean = true,
    /** Long-pressing an app shows its shortcuts menu (otherwise opens the editor). */
    val appQuickMenus: Boolean = true,
    /** Per-block width / alignment / scale, by block id. */
    val blockSizes: Map<String, BlockSize> = emptyMap(),
) {
    companion object {
        fun defaultTiles(): List<TileConfig> = listOf(
            TileConfig(label = "TV", sublabel = "Movies & shows", icon = "movie", action = TileAction(TileAction.Kind.Screen, "tv")),
            TileConfig(label = "PC", sublabel = "Remote · wake · power", icon = "remote", color = "accent2", action = TileAction(TileAction.Kind.PcHub)),
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
    /** A row of suggested apps at the top of the drawer. */
    val showSuggestions: Boolean = true,
    /** Showing hidden apps needs your fingerprint / face / screen lock. */
    val lockHidden: Boolean = false,
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
