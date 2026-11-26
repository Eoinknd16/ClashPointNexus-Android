package com.eoinknd16.nexus.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.apps.AppDrawerContent
import com.eoinknd16.nexus.ui.fx.Haptics
import com.eoinknd16.nexus.ui.theme.Nx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app drawer as a layer over home that follows your finger: 0 = closed,
 * 1 = open. Dragging moves it 1:1; letting go springs it open or shut using
 * the speed of your flick, and it can be caught again mid-flight.
 */
class DrawerController(private val scope: CoroutineScope, private val haptics: Haptics) {
    val progress = Animatable(0f)
    /** Height of the travel in px (set from layout). */
    var travel = 1f
    var dragging by mutableStateOf(false)
        private set
    /** Bumped each time it finishes opening (to focus the search box). */
    var openedCount by mutableStateOf(0)
        private set
    private var pastCommit = false

    /** Visible at all. Only the position counts, so a stuck flag can never leave an invisible layer over home. */
    val isOpen get() = progress.value > 0.002f

    private fun spec(velocity: Float) = run {
        val m = LookState.look.motion
        val damping = (1f - m.bounce.coerceIn(0f, 1f) * 0.55f).coerceIn(0.45f, 1f)
        val stiffness = Spring.StiffnessMediumLow * m.speed.coerceIn(0.4f, 2.5f)
        spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = 0.0005f) to velocity
    }

    fun startDrag() {
        dragging = true
        pastCommit = progress.value > 0.35f
        scope.launch { progress.stop() }
    }

    /** [dyUp] = px the finger moved upwards since last call (negative = down). */
    fun dragBy(dyUp: Float) {
        val next = (progress.value + dyUp / travel).coerceIn(0f, 1.04f)
        scope.launch { progress.snapTo(next) }
        val commit = next > 0.35f
        if (commit != pastCommit) {
            pastCommit = commit
            if (commit) haptics.rise() else haptics.fall()
        }
    }

    /** [velocityUp] in px/s, positive = flung upwards. */
    fun endDrag(velocityUp: Float) {
        if (!dragging) return
        dragging = false
        val open = when {
            velocityUp > 900f -> true
            velocityUp < -900f -> false
            else -> progress.value > 0.35f
        }
        settle(open, velocityUp / travel)
    }

    fun open() = settle(true, 0f)
    fun close() = settle(false, 0f)
    fun snapClosed() = scope.launch { progress.snapTo(0f) }

    /** Hard reset: closed, not dragging (leaving home, Home button). */
    fun reset() {
        dragging = false
        pastCommit = false
        scope.launch { progress.snapTo(0f) }
    }

    private fun settle(open: Boolean, velocity: Float) {
        scope.launch {
            val (s, v) = spec(velocity)
            if (LookState.look.motion.animations) progress.animateTo(if (open) 1f else 0f, s, v) else progress.snapTo(if (open) 1f else 0f)
            if (open) { haptics.thud(); openedCount++ }
        }
    }

    /**
     * Inside the open drawer: a downward drag when the list is already at the
     * top pulls the drawer closed (following the finger); dragging back up
     * reopens it before the list scrolls again.
     */
    fun nestedScroll(grid: LazyGridState) = object : NestedScrollConnection {
        private fun atTop() = grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val dy = available.y
            if (dy > 0 && atTop()) {
                if (!dragging) startDrag()
                dragBy(-dy)
                return Offset(0f, dy)
            }
            if (dy < 0 && progress.value < 1f && dragging) {
                dragBy(-dy)
                return Offset(0f, dy)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            endDrag(-available.y)
            return available
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (dragging) endDrag(-available.y)
            return Velocity.Zero
        }
    }
}

/** Lets tiles / gestures open the drawer overlay when home is showing. */
object HomeDrawerHub {
    var controller: DrawerController? = null
    fun open(): Boolean {
        val c = controller ?: return false
        c.open()
        return true
    }
}

/** How much home recedes behind the drawer (scale / dim / blur), driven by [progress]. */
fun Modifier.recedeBehindDrawer(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    val s = 1f - 0.07f * p
    scaleX = s
    scaleY = s
    alpha = 1f - 0.55f * p
    if (android.os.Build.VERSION.SDK_INT >= 31 && p > 0.01f) {
        val r = 26f * p * density
        renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
    }
}

@Composable
fun DrawerOverlay(controller: DrawerController) {
    val grid = rememberLazyGridState()
    // Derived so this only recomposes when these flip, not on every frame of a drag.
    val mostlyOpen by remember { androidx.compose.runtime.derivedStateOf { controller.progress.value > 0.5f } }
    val fullyOpen by remember { androidx.compose.runtime.derivedStateOf { controller.progress.value > 0.95f && !controller.dragging } }
    val visible by remember { androidx.compose.runtime.derivedStateOf { controller.isOpen } }
    BackHandler(enabled = mostlyOpen) { controller.close() }
    // Back to the top of the list each time it closes.
    LaunchedEffect(Unit) {
        snapshotFlow { controller.progress.value <= 0.001f && !controller.dragging }.collect { closed ->
            if (closed) grid.scrollToItem(0)
        }
    }
    if (!visible) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = constraints.maxHeight * 0.85f
        controller.travel = travel
        val connection = remember(controller, grid) { controller.nestedScroll(grid) }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val q = controller.progress.value
                    translationY = (1f - q.coerceIn(0f, 1.04f)) * travel
                    alpha = (q * 2.2f).coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(
                        0f to Nx.Void.copy(alpha = 0.0f),
                        0.06f to Nx.Void.copy(alpha = 0.55f),
                        1f to Nx.Void.copy(alpha = 0.78f),
                    ),
                )
                .nestedScroll(connection),
        ) {
            AppDrawerContent(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
                gridState = grid,
                autoFocus = LookState.look.drawer.autoFocusSearch && fullyOpen,
                onLaunched = { controller.close() },
            )
        }
    }
}

