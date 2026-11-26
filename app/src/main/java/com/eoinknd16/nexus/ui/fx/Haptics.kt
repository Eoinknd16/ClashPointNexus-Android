package com.eoinknd16.nexus.ui.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eoinknd16.nexus.look.LookState

/**
 * Nexus's vocabulary of touch feedback. Each interaction gets its own feel
 * (a crisp tick for snapping, a soft thud for landing, a rising swell when a
 * drag passes the point of no return) built from Android's haptic primitives
 * where the phone supports them, falling back to simpler effects otherwise.
 * Everything honours Customise › Motion › Haptic feedback / Strong haptics.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }.getOrNull()

    private val primitives: Set<Int> = runCatching {
        if (Build.VERSION.SDK_INT < 30 || vibrator == null) emptySet() else {
            val wanted = buildList {
                add(VibrationEffect.Composition.PRIMITIVE_TICK)
                add(VibrationEffect.Composition.PRIMITIVE_CLICK)
                add(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                add(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
                add(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
                if (Build.VERSION.SDK_INT >= 31) {
                    add(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                    add(VibrationEffect.Composition.PRIMITIVE_THUD)
                    add(VibrationEffect.Composition.PRIMITIVE_SPIN)
                }
            }
            wanted.filter { vibrator.areAllPrimitivesSupported(it) }.toSet()
        }
    }.getOrDefault(emptySet())

    private val enabled get() = LookState.look.motion.haptics && vibrator?.hasVibrator() == true
    private val gain get() = if (LookState.look.motion.strongHaptics) 1f else 0.6f

    /** Smallest feedback: grid snaps, scrubbing past a letter, crossing a step. */
    fun tick() = play(primitive(lowTick(), 0.5f) ?: predefined(VibrationEffect.EFFECT_TICK))

    /** A clear tap: a gesture recognised, a toggle, a selection. */
    fun click() = play(primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f) ?: predefined(VibrationEffect.EFFECT_CLICK))

    /** Something landing: a folder opening, a drawer settling, a drop. */
    fun thud() = play(
        (if (Build.VERSION.SDK_INT >= 31) primitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f) else null)
            ?: primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
            ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK),
    )

    /** A drag passing the point where letting go will commit it. */
    fun rise() = play(primitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f) ?: predefined(VibrationEffect.EFFECT_TICK))

    /** Dropping back below that point. */
    fun fall() = play(primitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.4f) ?: predefined(VibrationEffect.EFFECT_TICK))

    /** Long-press picked something up. */
    fun pickUp() = play(
        composition(
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.4f,
            VibrationEffect.Composition.PRIMITIVE_CLICK to 0.8f,
        ) ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK),
    )

    /** A hold-to-confirm reaching the end. */
    fun confirm() = play(
        composition(
            VibrationEffect.Composition.PRIMITIVE_CLICK to 0.6f,
            VibrationEffect.Composition.PRIMITIVE_CLICK to 1f,
        ) ?: predefined(VibrationEffect.EFFECT_DOUBLE_CLICK),
    )

    private fun lowTick() = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK

    private fun primitive(id: Int, scale: Float): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= 30 && id in primitives) {
            VibrationEffect.startComposition().addPrimitive(id, (scale * gain).coerceIn(0f, 1f)).compose()
        } else null

    private fun composition(vararg parts: Pair<Int, Float>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30 || parts.any { it.first !in primitives }) return null
        val c = VibrationEffect.startComposition()
        parts.forEachIndexed { i, (id, s) -> c.addPrimitive(id, (s * gain).coerceIn(0f, 1f), if (i == 0) 0 else 40) }
        return c.compose()
    }

    private fun predefined(id: Int): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)

    private fun play(effect: VibrationEffect?) {
        if (!enabled || effect == null) return
        runCatching { vibrator?.vibrate(effect) }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
