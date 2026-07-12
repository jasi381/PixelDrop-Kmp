package com.jasmeet.pixeldrop.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

/** WKWebView-backed actual. Host restriction is not enforced here yet —
 *  the page is a static, first-party privacy policy. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(
    url: String,
    allowedHost: String,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            WKWebView().apply {
                setOpaque(false)
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        modifier = modifier,
    )
}
