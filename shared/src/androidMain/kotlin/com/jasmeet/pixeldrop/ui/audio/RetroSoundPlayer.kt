package com.jasmeet.pixeldrop.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.jasmeet.pixeldrop.shared.R

/**
 * All console audio in one lightweight facade:
 *  - SoundPool for the short one-shot boot / shutdown / gameplay / menu chimes.
 *  - A looping MediaPlayer for the gameplay background music.
 *
 * SoundPool loads asynchronously; a play request arriving before the sample
 * finishes loading is queued and fired from the load-complete callback, so
 * the cold-start boot sound is never dropped.
 */
class RetroSoundPlayer(context: Context) : RetroAudio {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = mutableSetOf<Int>()
    private var pendingSoundId: Int? = null
    private var released = false

    private val bootId: Int
    private val shutdownId: Int
    private val moveId: Int
    private val rotateId: Int
    private val clearId: Int
    private val menuSelectId: Int
    private val gameOverId: Int

    private val gameplayTrack = LoopingTrack(R.raw.pixel_gameplay_loop)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded += sampleId
                if (pendingSoundId == sampleId) {
                    pendingSoundId = null
                    play(sampleId)
                }
            }
        }
        bootId = soundPool.load(appContext, R.raw.pixel_drop_boot, 1)
        shutdownId = soundPool.load(appContext, R.raw.pixel_drop_shutdown, 1)
        moveId = soundPool.load(appContext, R.raw.pixel_sfx_move, 1)
        rotateId = soundPool.load(appContext, R.raw.pixel_sfx_rotate, 1)
        clearId = soundPool.load(appContext, R.raw.pixel_sfx_clear, 1)
        menuSelectId = soundPool.load(appContext, R.raw.pixel_sfx_menu_select, 1)
        gameOverId = soundPool.load(appContext, R.raw.pixel_game_over, 1)
    }

    override fun playBoot() = playWhenReady(bootId)

    override fun playShutdown() = playWhenReady(shutdownId)

    // Gameplay SFX: fire-and-forget, silently skipped if not yet loaded.
    override fun playMove() = playSfx(moveId, 0.25f)

    override fun playRotate() = playSfx(rotateId, 0.3f)

    override fun playLineClear() = playSfx(clearId, 0.5f)

    override fun playMenuSelect() = playSfx(menuSelectId, 0.35f)

    /** Shared one-shot for every game's game-over transition; louder than
     *  the other chimes so it reads over the (stopping) gameplay music. */
    override fun playGameOver() = playSfx(gameOverId, 0.8f)

    /** Pauses any active one-shot streams (e.g. when boot is skipped). */
    override fun stopAll() {
        if (released) return
        pendingSoundId = null
        soundPool.autoPause()
    }

    /** Pauses in-flight one-shot SFX (boot/shutdown/move/etc.) without
     *  discarding them, so backgrounding mid-chime doesn't bleed audio. */
    override fun pauseSfx() {
        if (released) return
        soundPool.autoPause()
    }

    /** Resumes SFX streams paused by [pauseSfx] when the app returns. */
    override fun resumeSfx() {
        if (released) return
        soundPool.autoResume()
    }

    /** Starts the gameplay loop, resuming from a paused position if any. */
    override fun startGameplayMusic() = gameplayTrack.start()

    override fun pauseGameplayMusic() = gameplayTrack.pause()

    /** Stops the music and rewinds it, so the next game starts fresh. */
    override fun stopGameplayMusic() = gameplayTrack.stop()

    override fun release() {
        released = true
        pendingSoundId = null
        soundPool.release()
        gameplayTrack.release()
    }

    private fun playWhenReady(soundId: Int) {
        if (released) return
        if (soundId in loaded) play(soundId) else pendingSoundId = soundId
    }

    private fun play(soundId: Int) {
        if (released) return
        // Subtle volume: nostalgic, not loud.
        soundPool.play(soundId, CHIME_VOLUME, CHIME_VOLUME, 1, 0, 1f)
    }

    private fun playSfx(soundId: Int, volume: Float) {
        if (released || soundId !in loaded) return
        soundPool.play(soundId, volume, volume, 0, 0, 1f)
    }

    /** A single looping background track: lazily created, pause/resume in
     *  place, and self-healing if a codec quirk emits onCompletion despite
     *  [MediaPlayer.isLooping]. */
    private inner class LoopingTrack(private val resId: Int) {
        private var player: MediaPlayer? = null

        /** True between [start] and [pause]/[stop]; lets the completion
         *  listener restart the track if looping ever hiccups. */
        private var wanted = false

        fun start() {
            if (released) return
            wanted = true
            val p = player ?: MediaPlayer.create(appContext, resId)
                ?.apply {
                    isLooping = true
                    setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                    setOnCompletionListener { mp ->
                        if (wanted && !released) {
                            mp.seekTo(0)
                            mp.start()
                        }
                    }
                }
                ?.also { player = it }
            if (p != null && !p.isPlaying) p.start()
        }

        fun pause() {
            if (released) return
            wanted = false
            player?.takeIf { it.isPlaying }?.pause()
        }

        fun stop() {
            if (released) return
            wanted = false
            player?.let {
                if (it.isPlaying) it.pause()
                it.seekTo(0)
            }
        }

        fun release() {
            wanted = false
            player?.release()
            player = null
        }
    }

    private companion object {
        const val CHIME_VOLUME = 0.6f
        const val MUSIC_VOLUME = 0.3f
    }
}
