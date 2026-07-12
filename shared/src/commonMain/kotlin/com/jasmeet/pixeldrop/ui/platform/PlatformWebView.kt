package com.jasmeet.pixeldrop.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Embedded web page: android.webkit.WebView on Android, WKWebView on iOS.
 * [allowedHost] restricts in-page navigation to the given https host where
 * the platform supports interception (Android enforces it; the iOS actual
 * currently just loads the page).
 */
@Composable
expect fun PlatformWebView(
    url: String,
    allowedHost: String,
    modifier: Modifier = Modifier,
)
