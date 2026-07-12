package com.jasmeet.pixeldrop.ui.platform

import androidx.compose.runtime.Composable

/**
 * Tiny key-value persistence seam for console customization (theme choice
 * and friends). SharedPreferences on Android, NSUserDefaults on iOS.
 */
interface ConsolePrefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

@Composable
expect fun rememberConsolePrefs(): ConsolePrefs
