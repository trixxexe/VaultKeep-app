package com.example.ui.util

import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    /**
     * Checks if the user or system has enabled "Remove animations" / reduce-motion setting.
     */
    fun isReduceMotionEnabled(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver
            val animatorScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transitionScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            animatorScale == 0f || transitionScale == 0f
        } catch (_: Throwable) {
            false
        }
    }
}
