package com.eoinknd16.nexus.look

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a tile can use, by the stable key stored in the config. */
object IconCatalog {
    val all: List<Pair<String, ImageVector>> = listOf(
        "bolt" to Icons.Filled.Bolt,
        "movie" to Icons.Filled.Movie,
        "tv" to Icons.Filled.Tv,
        "play" to Icons.Filled.PlayArrow,
        "remote" to Icons.Filled.SettingsRemote,
        "cast" to Icons.Filled.Cast,
        "power" to Icons.Filled.PowerSettingsNew,
        "computer" to Icons.Filled.Computer,
        "games" to Icons.Filled.SportsEsports,
        "apps" to Icons.Filled.Apps,
        "search" to Icons.Filled.Search,
        "palette" to Icons.Filled.Palette,
        "settings" to Icons.Filled.Settings,
        "home" to Icons.Filled.Home,
        "music" to Icons.Filled.MusicNote,
        "camera" to Icons.Filled.CameraAlt,
        "photo" to Icons.Filled.Photo,
        "video" to Icons.Filled.Videocam,
        "phone" to Icons.Filled.Phone,
        "chat" to Icons.AutoMirrored.Filled.Chat,
        "mail" to Icons.Filled.Email,
        "calendar" to Icons.Filled.CalendarMonth,
        "alarm" to Icons.Filled.Alarm,
        "web" to Icons.Filled.Language,
        "map" to Icons.Filled.Map,
        "explore" to Icons.Filled.Explore,
        "car" to Icons.Filled.DirectionsCar,
        "flight" to Icons.Filled.Flight,
        "food" to Icons.Filled.Restaurant,
        "cart" to Icons.Filled.ShoppingCart,
        "fitness" to Icons.Filled.FitnessCenter,
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "work" to Icons.Filled.Work,
        "code" to Icons.Filled.Code,
        "terminal" to Icons.Filled.Terminal,
        "server" to Icons.Filled.Storage,
        "cloud" to Icons.Filled.Cloud,
        "wifi" to Icons.Filled.Wifi,
        "light" to Icons.Filled.Lightbulb,
        "sun" to Icons.Filled.WbSunny,
        "snow" to Icons.Filled.AcUnit,
        "fire" to Icons.Filled.LocalFireDepartment,
        "rocket" to Icons.Filled.RocketLaunch,
        "star" to Icons.Filled.Star,
        "heart" to Icons.Filled.Favorite,
        "pets" to Icons.Filled.Pets,
        "lock" to Icons.Filled.Lock,
        "shield" to Icons.Filled.Security,
        "fingerprint" to Icons.Filled.Fingerprint,
        "puzzle" to Icons.Filled.Extension,
    )
    private val byKey = all.toMap()

    fun icon(key: String): ImageVector = byKey[key] ?: Icons.Filled.Bolt
}
