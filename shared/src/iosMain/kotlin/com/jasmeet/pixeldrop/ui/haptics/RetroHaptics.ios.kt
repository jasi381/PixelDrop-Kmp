package com.jasmeet.pixeldrop.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@Composable
actual fun rememberRetroHaptics(initiallyEnabled: Boolean): RetroHaptics {
    val haptics = remember { IosRetroHaptics(initiallyEnabled) }
    DisposableEffect(haptics) { onDispose { haptics.release() } }
    return haptics
}

/**
 * Runs the shared [HapticPolicy] and renders each plan with UIKit impact
 * generators: every "on" segment of a pattern becomes one impact whose
 * intensity is the segment's amplitude (0..255 -> 0..1), scheduled at its
 * offset. iOS has no waveform API, so rhythm and strength are kept, not length.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosRetroHaptics(initiallyEnabled: Boolean) : RetroHaptics {
    private val policy = HapticPolicy().also { it.enabled = initiallyEnabled }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private var lifecycleActive = true
    private var windowFocused = true

    override fun setEnabled(enabled: Boolean) {
        policy.enabled = enabled
    }

    override fun setLifecycleActive(active: Boolean) {
        lifecycleActive = active
        applySuspension()
    }

    override fun setWindowFocused(focused: Boolean) {
        windowFocused = focused
        applySuspension()
    }

    override fun release() {
        scope.cancel()
    }

    override fun play(event: HapticEvent?) {
        if (event == null) return
        val nowMs = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
        val plan = policy.plan(event, nowMs) ?: return
        val wave = when (plan) {
            is HapticPlan.ViewFeedback -> plan.fallback
            is HapticPlan.Predefined -> plan.fallback
            is HapticPlan.Waveform -> plan.wave
        }
        var startMs = 0L
        for (i in wave.timings.indices) {
            val amp = wave.amplitudes[i]
            if (amp > 0) {
                val at = startMs
                scope.launch {
                    if (at > 0L) delay(at)
                    impact(amp)
                }
            }
            startMs += wave.timings[i]
        }
    }

    private fun impact(amplitude: Int) {
        val intensity = (amplitude / 255.0).coerceIn(0.1, 1.0)
        val generator = when {
            amplitude >= 190 -> heavy
            amplitude >= 100 -> medium
            else -> light
        }
        generator.impactOccurredWithIntensity(intensity)
    }

    private fun applySuspension() {
        val suspended = !(lifecycleActive && windowFocused)
        val was = policy.suspended
        policy.suspended = suspended
        if (!suspended && was) policy.reset()
    }
}
