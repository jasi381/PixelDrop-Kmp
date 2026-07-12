package com.jasmeet.pixeldrop

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Verifies the hold-POWER power-off, the powered-off state and the tap-POWER
 * reboot.
 *
 * The app animates continuously (boot clock, blink transitions), so the test
 * pauses the compose clock and advances virtual time manually. Coroutine
 * delays inside LaunchedEffect (the power hold timer) also follow this
 * virtual clock in compose tests.
 */
class PowerComboTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun pauseClock() {
        rule.mainClock.autoAdvance = false
    }

    private fun assertMode(mode: String) {
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(hasContentDescription("mode:$mode")).assertExists()
    }

    private fun finishBoot() {
        rule.mainClock.advanceTimeBy(6600)
        assertMode("Menu")
    }

    private fun centerOf(tag: String): Offset {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.positionInRoot +
            Offset(node.size.width / 2f, node.size.height / 2f)
    }

    private fun tap(tag: String) {
        val c = centerOf(tag)
        rule.onRoot().performTouchInput {
            down(0, c)
            up(0)
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun holdPower(holdMillis: Long) {
        val power = centerOf("pill-POWER")
        rule.onRoot().performTouchInput { down(0, power) }
        rule.mainClock.advanceTimeByFrame() // recompose -> hold timer arms
        rule.mainClock.advanceTimeBy(holdMillis) // virtual hold duration
        rule.onRoot().performTouchInput { up(0) }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun powerHold_powersOff_thenPowerTapReboots() {
        finishBoot()

        holdPower(holdMillis = 1500) // past the 1000ms threshold
        assertMode("PoweringOff")

        rule.mainClock.advanceTimeBy(2800) // play the shutdown animation
        assertMode("PoweredOff")

        tap("pill-POWER")
        assertMode("Boot") // console reboots
    }

    @Test
    fun powerTap_doesNotPowerOff() {
        finishBoot()

        holdPower(holdMillis = 250) // well under the threshold
        rule.mainClock.advanceTimeBy(1500) // would-be trigger window passes
        assertMode("Menu") // still powered on; tap only powers ON when off
    }

    /** Also saves key shutdown animation frames for visual review. */
    @Test
    fun shutdownAnimation_rendersKeyFrames() {
        finishBoot()
        holdPower(holdMillis = 1500)
        assertMode("PoweringOff")

        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null)

        fun snap(name: String) {
            rule.mainClock.advanceTimeByFrame()
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(File(dir, name)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
            }
        }

        rule.mainClock.advanceTimeBy(500); snap("off_0500_fade.png")
        rule.mainClock.advanceTimeBy(500); snap("off_1000_collapse.png")
        rule.mainClock.advanceTimeBy(500); snap("off_1500_shrink.png")
        rule.mainClock.advanceTimeBy(450); snap("off_1950_dot.png")
        rule.mainClock.advanceTimeBy(850); snap("off_2800_final.png")
        assertMode("PoweredOff")
    }

    @Test
    fun powerHold_worksFromGameplay() {
        finishBoot()
        tap("pill-START") // start game
        assertMode("Game")

        holdPower(holdMillis = 1500)
        assertMode("PoweringOff")
        rule.mainClock.advanceTimeBy(2800)
        assertMode("PoweredOff")
    }
}
