package com.eoinknd16.nexus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.navigationBarsPadding
import com.eoinknd16.nexus.ui.fx.holoPanel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.eoinknd16.nexus.AppContainer
import com.eoinknd16.nexus.NexusApp
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud

val LocalNav = staticCompositionLocalOf<NavHostController> { error("No NavController") }

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as NexusApp).container

@Composable
inline fun <reified VM : ViewModel> nexusViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val TV = "tv"
    const val SEARCH = "search"
    const val DETAIL = "detail/{type}/{id}"
    const val PLAYER = "player"
    const val PC = "pc"
    const val SETTINGS = "settings"
    const val ADDONS = "addons"
    const val CUSTOMIZE = "customize"
    const val SPOTIFY = "spotify"

    fun detail(type: String, id: String) = "detail/$type/${android.net.Uri.encode(id)}"
}

/** Top bar for every inner screen: back chevron + HUD-style title. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).nexusPress({ nav.popBackStack() }).clip(nexusShape(0.57f)).background(Nx.Cyan.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nx.Cyan) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(hud(subtitle), style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
fun LoadingBlock(text: String = "Linking…") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Nx.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(12.dp))
        Text(hud(text), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
    }
}

@Composable
fun EmptyBlock(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Nx.Text, fontWeight = FontWeight.SemiBold)
            if (body != null) {
                Spacer(Modifier.size(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Nx.Muted)
            }
            if (action != null) {
                Spacer(Modifier.size(16.dp))
                action()
            }
        }
    }
}

/** "Tile deleted · UNDO" snackbar, shown over every screen for a few seconds. */
@androidx.compose.runtime.Composable
fun UndoBar() {
    val c = appContainer()
    val prompt by c.undoPrompt.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(prompt) {
        if (prompt != null) {
            kotlinx.coroutines.delay(6_000)
            c.undoPrompt.value = null
        }
    }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 110.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = prompt != null,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        ) {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.padding(horizontal = 16.dp)
                    .holoPanel(com.eoinknd16.nexus.ui.theme.Nx.Amber, 0.25f)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(prompt ?: "", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = com.eoinknd16.nexus.ui.theme.Nx.Text)
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
                androidx.compose.material3.Text(
                    com.eoinknd16.nexus.ui.theme.hud("Undo"),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = com.eoinknd16.nexus.ui.theme.Nx.Amber,
                    modifier = androidx.compose.ui.Modifier.nexusPress({ c.undoLook() }).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}



/**
 * Night mode: between the chosen hours, a warm, dark veil over all of Nexus
 * (touches pass straight through). Fades in and out instead of switching.
 */
@androidx.compose.runtime.Composable
fun NightVeil() {
    val m = com.eoinknd16.nexus.look.LookState.look.motion
    var hour by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        }
    }
    val start = m.nightStart.coerceIn(0, 23)
    val end = m.nightEnd.coerceIn(0, 23)
    val isNight = m.night && if (start <= end) hour in start until end else (hour >= start || hour < end)
    val a by androidx.compose.animation.core.animateFloatAsState(if (isNight) m.nightStrength.coerceIn(0f, 0.8f) else 0f, androidx.compose.animation.core.tween(1500), label = "night")
    if (a <= 0.001f) return
    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
        drawRect(androidx.compose.ui.graphics.Color(0xFF140A02).copy(alpha = a))
    }
}
