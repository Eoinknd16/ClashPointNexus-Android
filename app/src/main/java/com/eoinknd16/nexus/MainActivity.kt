package com.eoinknd16.nexus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
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
                CompositionLocalProvider(LocalNav provides nav) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
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
