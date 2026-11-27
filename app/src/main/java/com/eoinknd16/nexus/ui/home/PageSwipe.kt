package com.eoinknd16.nexus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eoinknd16.nexus.look.HomePager
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.ui.fx.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Wrapper so "main page" (null id) can be a target. */
data class PageTarget(val id: String?)

/**
 * Home pages that follow your finger: the current page slides with the drag
 * and the destination slides in beside it. Let go past a third of the way (or
 * flick) and it completes; otherwise it springs back.
 */
class PageSwipe(private val scope: CoroutineScope, private val haptics: Haptics) {
    val offset = Animatable(0f)
    var target by mutableStateOf<PageTarget?>(null)
        private set
    /** -1 = finger moving left (new page comes from the right), +1 = moving right. */
    var dir by mutableStateOf(-1)
        private set
    var width = 1f
    private var swipe = 0
    private var instant = false
    private var pastCommit = false

    /** True once after a finger swipe completes, so the page swap doesn't animate again. */
    fun consumeInstant(): Boolean = instant.also { instant = false }

    /** Called when a horizontal drag starts. Returns false if this swipe doesn't go to a page. */
    fun start(pageAction: String, fingerDir: Int): Boolean {
        val dest = HomePager.resolve(pageAction) ?: return false
        if (dest.id == HomePager.current) return false
        dir = fingerDir
        swipe = fingerDir
        target = dest
        pastCommit = false
        scope.launch { offset.snapTo(0f) }
        return true
    }

    fun drag(dx: Float) {
        if (target == null) return
        val next = if (dir < 0) (offset.value + dx).coerceIn(-width, 0f) else (offset.value + dx).coerceIn(0f, width)
        scope.launch { offset.snapTo(next) }
        val commit = abs(next) > width / 3
        if (commit != pastCommit) {
            pastCommit = commit
            if (commit) haptics.tick()
        }
    }

    fun end(velocityX: Float) {
        val t = target ?: return
        val moved = abs(offset.value)
        val commit = moved > width / 3 || (velocityX * dir > 900f && moved > width * 0.06f)
        scope.launch {
            val m = LookState.look.motion
            val s = spring<Float>(dampingRatio = (1f - m.bounce * 0.4f).coerceIn(0.6f, 1f), stiffness = Spring.StiffnessMediumLow * m.speed.coerceIn(0.4f, 2.5f))
            runCatching { offset.animateTo(if (commit) dir * width else 0f, s, velocityX) }
            if (commit) {
                instant = true
                HomePager.goTo(t.id, swipe)
                haptics.thud()
            }
            target = null
            offset.snapTo(0f)
        }
    }

    fun cancel() {
        target = null
        scope.launch { offset.snapTo(0f) }
    }
}

