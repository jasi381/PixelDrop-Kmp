package com.jasmeet.pixeldrop.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberRetroAudio(): RetroAudio {
    val context = LocalContext.current
    // Released by the caller (PixelDropApp) in its DisposableEffect.
    return remember { RetroSoundPlayer(context.applicationContext) }
}
