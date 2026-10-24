package com.jasmeet.pixeldrop.ui.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberRetroHaptics(initiallyEnabled: Boolean): RetroHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context) { AndroidRetroHaptics(context, initiallyEnabled) }
    DisposableEffect(haptics, view) {
        haptics.attachView(view)
        onDispose { haptics.attachView(null) }
    }
    DisposableEffect(haptics) { onDispose { haptics.release() } }
    return haptics
}

/**
 * Turns a [HapticPlan] into hardware calls: touch-style events go through
 * [View.performHapticFeedback] (the system gates them on the touch-feedback
 * preference), custom patterns use [VibrationEffect] (API 26+, predefined on
 * 29+), API 24-25 falls back to plain vibrate calls. All guarded: a missing
 * vibrator or revoked permission is a silent no-op, never a crash.
 */
private class AndroidRetroHaptics(context: Context, initiallyEnabled: Boolean) : RetroHaptics {
    private val appContext = context.applicationContext
    private val policy = HapticPolicy().also { it.enabled = initiallyEnabled }
    private val vibrator: Vibrator? = resolveVibrator(appContext)
    private val hasVibrator: Boolean = try { vibrator?.hasVibrator() == true } catch (_: Exception) { false }

    private var view: View? = null
    private var released = false
    private var lifecycleActive = true
    private var windowFocused = true
    private var systemTouchFeedbackOn = readSystemTouchFeedback()

    fun attachView(target: View?) {
        view = target
    }

    override fun setEnabled(enabled: Boolean) {
        policy.enabled = enabled
        if (!enabled) cancel()
    }

    override fun setLifecycleActive(active: Boolean) {
        lifecycleActive = active
        applySuspension()
        if (active) systemTouchFeedbackOn = readSystemTouchFeedback()
    }

    override fun setWindowFocused(focused: Boolean) {
        windowFocused = focused
        applySuspension()
    }

    override fun release() {
        if (released) return
        released = true
        cancel()
        view = null
    }

    override fun play(event: HapticEvent?) {
        if (event == null || released || !hasVibrator && view == null) return
        try {
            val plan = policy.plan(event, SystemClock.uptimeMillis()) ?: return
            execute(plan)
        } catch (_: Exception) {
            // Vibrator service died or permission revoked: haptics are optional.
        }
    }

    private fun execute(plan: HapticPlan) {
        when (plan) {
            is HapticPlan.ViewFeedback -> {
                val v = view
                val handled = systemTouchFeedbackOn && v != null && v.isHapticFeedbackEnabled &&
                    v.performHapticFeedback(viewConstant(plan.kind))
                // If the system declined (feedback off) stay silent; only fall back with no View at all.
                if (!handled && v == null) vibrate(plan.fallback)
            }
            is HapticPlan.Predefined -> {
                if (!hasVibrator) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(predefinedConstant(plan.kind)))
                } else {
                    vibrate(plan.fallback)
                }
            }
            is HapticPlan.Waveform -> vibrate(plan.wave)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(wave: Wave) {
        val v = vibrator ?: return
        if (!hasVibrator) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(wave.timings.toLongArray(), wave.amplitudes.toIntArray(), NO_REPEAT)
            } else {
                // No amplitude control: keep the rhythm, drop the strengths.
                VibrationEffect.createWaveform(wave.toLegacyPattern(), NO_REPEAT)
            }
            v.vibrate(effect)
        } else if (wave.timings.size == 2) {
            v.vibrate(wave.timings[1]) // API 24-25: single pulse
        } else {
            v.vibrate(wave.toLegacyPattern(), NO_REPEAT)
        }
    }

    private fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun applySuspension() {
        val suspended = !(lifecycleActive && windowFocused)
        val was = policy.suspended
        policy.suspended = suspended
        if (suspended && !was) cancel()
        if (!suspended && was) policy.reset()
    }

    @Suppress("DEPRECATION") // still the documented switch for touch feedback on all supported APIs
    private fun readSystemTouchFeedback(): Boolean = try {
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (_: Exception) {
        true
    }

    private fun viewConstant(kind: ViewKind): Int = when (kind) {
        ViewKind.VirtualKey -> HapticFeedbackConstants.VIRTUAL_KEY
        ViewKind.ClockTick -> HapticFeedbackConstants.CLOCK_TICK
    }

    private fun predefinedConstant(kind: PredefinedKind): Int = when (kind) {
        PredefinedKind.Tick -> VibrationEffect.EFFECT_TICK
        PredefinedKind.Click -> VibrationEffect.EFFECT_CLICK
        PredefinedKind.HeavyClick -> VibrationEffect.EFFECT_HEAVY_CLICK
    }

    private companion object {
        const val NO_REPEAT = -1

        fun resolveVibrator(context: Context): Vibrator? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}
