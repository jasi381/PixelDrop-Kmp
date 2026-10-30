package com.jasmeet.pixeldrop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.jasmeet.pixeldrop.ui.PixelDropApp
import com.jasmeet.pixeldrop.ui.components.FoldPosture
import platform.UIKit.UIViewController

private val hingePosture = mutableStateOf(FoldPosture.Flat)

/** iOS entry point: the whole console is common code from here down. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    PixelDropApp(posture = hingePosture.value)
}

/** Fed by UIHingeInteraction (iOS 27.1+) in ContentView.swift; a landscape window means a vertical crease. */
fun setHinge(partiallyOpen: Boolean, landscape: Boolean) {
    hingePosture.value = when {
        !partiallyOpen -> FoldPosture.Flat
        landscape -> FoldPosture.Book
        else -> FoldPosture.Tabletop
    }
}
