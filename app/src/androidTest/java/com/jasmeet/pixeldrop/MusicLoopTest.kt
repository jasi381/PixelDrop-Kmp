package com.jasmeet.pixeldrop

import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the gameplay track keeps playing across its end when looping —
 * guards against the MediaPlayer end-of-stream stop that silently killed
 * the music mid-round with the previous MP3 encoding.
 */
class MusicLoopTest {

    @Test
    fun gameplayMusic_loopsSeamlesslyPastTrackEnd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = MediaPlayer.create(context, R.raw.pixel_gameplay_loop)
        try {
            player.isLooping = true
            player.seekTo(player.duration - 2_000) // 2s before the end
            player.start()
            Thread.sleep(5_000) // play across the loop boundary

            assertTrue("player stopped at end of track", player.isPlaying)
            val position = player.currentPosition
            assertTrue(
                "position $position did not wrap to the start",
                position in 0 until 10_000,
            )
        } finally {
            player.release()
        }
    }
}
