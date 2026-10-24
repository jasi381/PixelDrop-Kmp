package com.jasmeet.pixeldrop.ui.haptics

import androidx.compose.runtime.Composable

/**
 * Platform seam for console haptics, mirroring [com.jasmeet.pixeldrop.ui.audio.RetroAudio].
 * Call sites name a [HapticEvent]; each platform runs it through the shared
 * [HapticPolicy] and turns the resulting plan into hardware calls. Every
 * method is a silent no-op when the device has no haptics.
 */
interface RetroHaptics {
    fun play(event: HapticEvent?)
    fun setEnabled(enabled: Boolean)

    /** App foreground/background: nothing may buzz while hidden. */
    fun setLifecycleActive(active: Boolean)
    fun setWindowFocused(focused: Boolean)
    fun release()
}

@Composable
expect fun rememberRetroHaptics(initiallyEnabled: Boolean): RetroHaptics
