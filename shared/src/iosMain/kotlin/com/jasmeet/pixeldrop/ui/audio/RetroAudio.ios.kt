package com.jasmeet.pixeldrop.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * AVAudioPlayer-backed console audio. Assets are WAV/M4A copies of the
 * Android res/raw OGGs (iOS has no Vorbis decoder), bundled by the Xcode
 * project — see iosApp/iosApp/Resources.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosRetroAudio : RetroAudio {

    private var released = false

    private fun load(name: String, volume: Float, loop: Boolean = false): AVAudioPlayer? {
        val url: NSURL = NSBundle.mainBundle.URLForResource(name, "wav")
            ?: NSBundle.mainBundle.URLForResource(name, "m4a")
            ?: return null
        return AVAudioPlayer(contentsOfURL = url, error = null).apply {
            setVolume(volume)
            numberOfLoops = if (loop) -1 else 0
            prepareToPlay()
        }
    }

    private val boot = load("pixel_drop_boot", CHIME_VOLUME)
    private val shutdown = load("pixel_drop_shutdown", CHIME_VOLUME)
    private val move = load("pixel_sfx_move", 0.25f)
    private val rotate = load("pixel_sfx_rotate", 0.3f)
    private val clear = load("pixel_sfx_clear", 0.5f)
    private val menuSelect = load("pixel_sfx_menu_select", 0.35f)
    private val gameOver = load("pixel_game_over", 0.8f)
    private val music = load("pixel_gameplay_loop", MUSIC_VOLUME, loop = true)

    private val oneShots = listOfNotNull(boot, shutdown, move, rotate, clear, menuSelect, gameOver)
    /** One-shots paused by [pauseSfx], to resume exactly those on return. */
    private var interrupted = emptyList<AVAudioPlayer>()

    // Note: no explicit AVAudioSession configuration — AVAudioPlayer
    // activates the default (solo-ambient) session on first play, which
    // already respects the silent switch like a pocket handheld should.

    private fun playOneShot(player: AVAudioPlayer?) {
        if (released || player == null) return
        player.setCurrentTime(0.0)
        player.play()
    }

    override fun playBoot() = playOneShot(boot)
    override fun playShutdown() = playOneShot(shutdown)
    override fun playMove() = playOneShot(move)
    override fun playRotate() = playOneShot(rotate)
    override fun playLineClear() = playOneShot(clear)
    override fun playMenuSelect() = playOneShot(menuSelect)
    override fun playGameOver() = playOneShot(gameOver)

    override fun stopAll() {
        if (released) return
        oneShots.forEach { if (it.playing) it.pause() }
    }

    override fun pauseSfx() {
        if (released) return
        interrupted = oneShots.filter { it.playing }
        interrupted.forEach { it.pause() }
    }

    override fun resumeSfx() {
        if (released) return
        interrupted.forEach { it.play() }
        interrupted = emptyList()
    }

    override fun startGameplayMusic() {
        if (released) return
        music?.takeIf { !it.playing }?.play()
    }

    override fun pauseGameplayMusic() {
        if (released) return
        music?.takeIf { it.playing }?.pause()
    }

    override fun stopGameplayMusic() {
        if (released) return
        music?.let {
            if (it.playing) it.pause()
            it.setCurrentTime(0.0)
        }
    }

    override fun release() {
        released = true
        oneShots.forEach { it.stop() }
        music?.stop()
    }

    private companion object {
        const val CHIME_VOLUME = 0.6f
        const val MUSIC_VOLUME = 0.3f
    }
}

@Composable
actual fun rememberRetroAudio(): RetroAudio = remember { IosRetroAudio() }
