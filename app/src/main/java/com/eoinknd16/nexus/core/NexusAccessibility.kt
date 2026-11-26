package com.eoinknd16.nexus.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper so gestures/tiles can lock the screen, open Recents or
 * Quick Settings -- Android only lets launchers do these through an
 * accessibility service. It reads nothing on screen (no event types are
 * subscribed in its config); it only performs those global actions.
 */
class NexusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: NexusAccessibilityService? = null

        val isOn: Boolean get() = instance != null

        /** True if done; false means the service isn't enabled yet. */
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        fun lockScreen(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && perform(GLOBAL_ACTION_LOCK_SCREEN)

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
