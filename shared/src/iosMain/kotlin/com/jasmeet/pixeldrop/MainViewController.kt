package com.jasmeet.pixeldrop

import androidx.compose.ui.window.ComposeUIViewController
import com.jasmeet.pixeldrop.ui.PixelDropApp
import platform.UIKit.UIViewController

/** iOS entry point: the whole console is common code from here down. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    PixelDropApp()
}
