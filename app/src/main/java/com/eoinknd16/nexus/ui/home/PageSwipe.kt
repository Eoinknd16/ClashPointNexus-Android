package com.eoinknd16.nexus.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eoinknd16.nexus.look.HomePager
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.fx.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

/** Wrapper so "main page" (null id) can be a target. */
data class PageTarget(val id: String?)

/**
 * Home pages that follow your finger: the current page slides with the drag
 * and the destination slides in beside it. Let go past a third of the way (or
 * flick) and it completes; otherwise it springs back.
 *
 * The position is plain state written synchronously on every touch event
 * (never via queued jobs), so no movement is ever lost.
 */
class PageSwipe(private val scope: CoroutineScope, private val haptics: Haptics) {
    var offset by mutableFloatStateOf(0f)
        private set
    var target by mutableStateOf<PageTarget?>(null)
        private set
    /** -1 = finger moving left (new page comes from the right), +1 = moving right. */
    var dir by mutableStateOf(-1)
        private set
    var width = 1f
    private var instant = false
    private var pastCommit = false
    private var settle: Job? = null

    /** A swipe is still settling; ignore new touches for that split second. */
    val busy: Boolean get() = settle?.isActive == true

    /** True once after a finger swipe completes, so the page swap doesn't animate again. */
    fun consumeInstant(): Boolean = instant.also { instant = false }

    /** Called when a horizontal drag starts. Returns false if this swipe doesn't go to a page. */
    fun start(pageAction: String, fingerDir: Int): Boolean {
        if (settle?.isActive == true) return false // still finishing the last one
        val dest = HomePager.resolve(pageAction) ?: return false
        if (dest.id == HomePager.current) return false
        instant = false
        dir = fingerDir
        offset = 0f
        pastCommit = false
        target = dest
        return true
    }

    fun drag(dx: Float) {
        if (target == null) return
        offset = if (dir < 0) (offset + dx).coerceIn(-width, 0f) else (offset + dx).coerceIn(0f, width)
        val commit = abs(offset) > width / 3
        if (commit != pastCommit) {
            pastCommit = commit
            if (commit) haptics.tick()
        }
    }

    fun end(velocityX: Float) {
        val t = target ?: return
        val moved = abs(offset)
        val commit = moved > width / 3 || (velocityX * dir > 900f && moved > width * 0.06f)
        val fingerDir = dir
        settle = scope.launch {
            val m = LookState.look.motion
            if (m.animations) {
                val spec = spring<Float>(dampingRatio = (1f - m.bounce * 0.4f).coerceIn(0.6f, 1f), stiffness = Spring.StiffnessMediumLow * m.speed.coerceIn(0.4f, 2.5f))
                animate(offset, if (commit) fingerDir * width else 0f, velocityX, spec) { v, _ -> offset = v }
            }
            if (commit) {
                instant = true
                HomePager.goTo(t.id, fingerDir)
                haptics.thud()
            }
            target = null
            offset = 0f
        }
    }
}


/**
 * Areas that scroll sideways themselves (poster rows, app rows, a scrolling
 * tray). Page swipes that start inside one are left to the row.
 */
object SideScrollZones {
    private val zones = java.util.concurrent.ConcurrentHashMap<Any, androidx.compose.ui.geometry.Rect>()
    fun set(key: Any, r: androidx.compose.ui.geometry.Rect) { zones[key] = r }
    fun remove(key: Any) { zones.remove(key) }
    fun contains(p: androidx.compose.ui.geometry.Offset): Boolean = zones.values.any { it.contains(p) }
}

/** Marks this element as a sideways-scrolling area (see [SideScrollZones]). */
fun androidx.compose.ui.Modifier.sideScrollZone(): androidx.compose.ui.Modifier = composed {
    val key = androidx.compose.runtime.remember { Any() }
    androidx.compose.runtime.DisposableEffect(key) { onDispose { SideScrollZones.remove(key) } }
    this.onGloballyPositioned { SideScrollZones.set(key, it.boundsInRoot()) }
}
