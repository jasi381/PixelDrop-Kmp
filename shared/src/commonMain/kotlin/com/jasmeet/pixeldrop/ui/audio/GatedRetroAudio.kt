package com.jasmeet.pixeldrop.ui.audio

/** Wraps a platform [RetroAudio] with the SOUND setting: while off, nothing plays. */
class GatedRetroAudio(private val delegate: RetroAudio, enabled: Boolean) : RetroAudio by delegate {
    var enabled: Boolean = enabled
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                delegate.stopAll()
                delegate.pauseGameplayMusic()
            }
        }

    override fun playBoot() { if (enabled) delegate.playBoot() }
    override fun playShutdown() { if (enabled) delegate.playShutdown() }
    override fun playMove() { if (enabled) delegate.playMove() }
    override fun playRotate() { if (enabled) delegate.playRotate() }
    override fun playLineClear() { if (enabled) delegate.playLineClear() }
    override fun playMenuSelect() { if (enabled) delegate.playMenuSelect() }
    override fun playGameOver() { if (enabled) delegate.playGameOver() }
    override fun startGameplayMusic() { if (enabled) delegate.startGameplayMusic() }
}
