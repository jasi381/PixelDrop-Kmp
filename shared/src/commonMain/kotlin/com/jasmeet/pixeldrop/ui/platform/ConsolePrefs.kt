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

fun ConsolePrefs.getBoolean(key: String, default: Boolean): Boolean =
    getString(key)?.let { it == "1" } ?: default

fun ConsolePrefs.putBoolean(key: String, value: Boolean) = putString(key, if (value) "1" else "0")
