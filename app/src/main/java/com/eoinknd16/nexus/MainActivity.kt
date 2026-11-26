package com.eoinknd16.nexus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eoinknd16.nexus.media.CatalogType
import com.eoinknd16.nexus.ui.LocalNav
import com.eoinknd16.nexus.ui.Routes
import com.eoinknd16.nexus.ui.apps.AppDrawerScreen
import com.eoinknd16.nexus.ui.home.HomeScreen
import com.eoinknd16.nexus.ui.pc.PcScreen
import com.eoinknd16.nexus.ui.settings.AddonsScreen
import com.eoinknd16.nexus.ui.settings.CustomizeScreen
import com.eoinknd16.nexus.ui.settings.SettingsScreen
import com.eoinknd16.nexus.ui.theme.NexusTheme
import com.eoinknd16.nexus.ui.tv.DetailScreen
import com.eoinknd16.nexus.ui.tv.PlayerScreen
import com.eoinknd16.nexus.ui.tv.SearchScreen
import com.eoinknd16.nexus.ui.tv.TvScreen
import kotlinx.coroutines.flow.MutableSharedFlow

/** Screen-change animations, from Customise › Motion › Screen transition. */
object ScreenTransitions {
    fun enter(t: com.eoinknd16.nexus.look.ScreenTransition, ms: Int, pop: Boolean): EnterTransition {
        val spec = tween<Float>(ms)
        return when (t) {
            com.eoinknd16.nexus.look.ScreenTransition.Fade -> fadeIn(spec)
            com.eoinknd16.nexus.look.ScreenTransition.Scale -> fadeIn(spec) + scaleIn(spec, initialScale = if (pop) 1.04f else 0.94f)
            com.eoinknd16.nexus.look.ScreenTransition.Slide -> fadeIn(spec) + slideInHorizontally(tween(ms)) { w -> if (pop) -w / 3 else w / 3 }
            com.eoinknd16.nexus.look.ScreenTransition.Rise -> fadeIn(spec) + slideInVertically(tween(ms)) { h -> if (pop) -h / 6 else h / 6 }
            com.eoinknd16.nexus.look.ScreenTransition.Warp -> fadeIn(tween(ms / 2, delayMillis = ms / 4)) + scaleIn(tween(ms), initialScale = if (pop) 1.4f else 0.6f)
            com.eoinknd16.nexus.look.ScreenTransition.None -> EnterTransition.None
        }
    }

    fun exit(t: com.eoinknd16.nexus.look.ScreenTransition, ms: Int, pop: Boolean): ExitTransition {
        val spec = tween<Float>(ms)
        return when (t) {
            com.eoinknd16.nexus.look.ScreenTransition.Fade -> fadeOut(spec)
            com.eoinknd16.nexus.look.ScreenTransition.Scale -> fadeOut(spec) + scaleOut(spec, targetScale = if (pop) 0.94f else 1.04f)
            com.eoinknd16.nexus.look.ScreenTransition.Slide -> fadeOut(spec) + slideOutHorizontally(tween(ms)) { w -> if (pop) w / 3 else -w / 3 }
            com.eoinknd16.nexus.look.ScreenTransition.Rise -> fadeOut(spec) + slideOutVertically(tween(ms)) { h -> if (pop) h / 6 else -h / 6 }
            com.eoinknd16.nexus.look.ScreenTransition.Warp -> fadeOut(tween(ms / 2)) + scaleOut(tween(ms), targetScale = if (pop) 0.6f else 1.4f)
            com.eoinknd16.nexus.look.ScreenTransition.None -> ExitTransition.None
        }
    }
}

class MainActivity : ComponentActivity() {

    /** Fires when the Home button is pressed while Nexus is already the
     * foreground home app -- Android delivers that as a new HOME intent. */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusTheme {
                val nav = rememberNavController()
                LaunchedEffect(Unit) {
                    homePressed.collect {
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    }
                }
                com.eoinknd16.nexus.ui.fx.NeonClockDriver()
                val highRefresh = com.eoinknd16.nexus.look.LookState.look.motion.highRefreshRate
                LaunchedEffect(highRefresh) { applyRefreshRate(highRefresh) }
                val motion = com.eoinknd16.nexus.look.LookState.look.motion
                val t = if (motion.animations) motion.screenTransition else com.eoinknd16.nexus.look.ScreenTransition.None
                val ms = com.eoinknd16.nexus.ui.fx.nexusMs(320)
                CompositionLocalProvider(LocalNav provides nav) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        enterTransition = { ScreenTransitions.enter(t, ms, pop = false) },
                        exitTransition = { ScreenTransitions.exit(t, ms, pop = false) },
                        popEnterTransition = { ScreenTransitions.enter(t, ms, pop = true) },
                        popExitTransition = { ScreenTransitions.exit(t, ms, pop = true) },
                    ) {
                        composable(Routes.HOME) { HomeScreen() }
                        composable(Routes.APPS) { AppDrawerScreen() }
                        composable(Routes.TV) { TvScreen() }
                        composable(Routes.SEARCH) { SearchScreen() }
                        composable(
                            Routes.DETAIL,
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("id") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val type = entry.arguments?.getString("type")?.takeIf { it.isNotBlank() }
                                ?.let(::CatalogType) ?: CatalogType.movie
                            DetailScreen(type = type, id = entry.arguments?.getString("id") ?: "")
                        }
                        composable(Routes.PLAYER) { PlayerScreen() }
                        composable(Routes.PC) { PcScreen() }
                        composable(Routes.SETTINGS) { SettingsScreen() }
                        composable(Routes.ADDONS) { AddonsScreen() }
                        composable(Routes.CUSTOMIZE) { CustomizeScreen() }
                    }
                }
            }
        }
    }

    /**
     * Many phones run apps at 60Hz unless they ask for more. This picks the
     * display mode with the highest refresh rate at the current resolution
     * (90/120/144Hz) -- or hands the choice back to the system when off.
     */
    private fun applyRefreshRate(high: Boolean) {
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val params = window.attributes
        params.preferredDisplayModeId = if (high) best.modeId else 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            params.preferredRefreshRate = if (high) best.refreshRate else 0f
        }
        window.attributes = params
    }

    override fun onStart() {
        super.onStart()
        (application as NexusApp).container.widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        (application as NexusApp).container.widgets.stopListening()
    }

    @Deprecated("Widget configure results still arrive here (AppWidgetHost API).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        (application as NexusApp).container.widgets.onActivityResult(requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressed.tryEmit(Unit)
        }
    }
}
