package com.jasmeet.pixeldrop.ui.audio

import androidx.compose.runtime.Composable

/**
 * Platform seam for all console audio. Method names mirror the original
 * Android RetroSoundPlayer one-to-one so UI call sites did not change when
 * the app went multiplatform.
 *
 * Android: SoundPool + looping MediaPlayer over res/raw OGGs.
 * iOS: AVAudioPlayer over WAV/M4A copies bundled with the Xcode app.
 */
interface RetroAudio {
    fun playBoot()
    fun playShutdown()
    fun playMove()
    fun playRotate()
    fun playLineClear()
    fun playMenuSelect()
    fun playGameOver()

    /** Pauses any active one-shot streams (e.g. when boot is skipped). */
    fun stopAll()

    /** Pauses in-flight one-shot SFX without discarding them. */
    fun pauseSfx()
    fun resumeSfx()

    /** Starts the gameplay loop, resuming from a paused position if any. */
    fun startGameplayMusic()
    fun pauseGameplayMusic()

    /** Stops the music and rewinds it, so the next game starts fresh. */
    fun stopGameplayMusic()
    fun release()
}

/** Platform-appropriate [RetroAudio], remembered for the composition. */
@Composable
expect fun rememberRetroAudio(): RetroAudio
