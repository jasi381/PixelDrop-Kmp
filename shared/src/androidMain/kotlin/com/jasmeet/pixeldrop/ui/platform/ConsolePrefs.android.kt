package com.jasmeet.pixeldrop.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "console_customization"

@Composable
actual fun rememberConsolePrefs(): ConsolePrefs {
    val context = LocalContext.current
    return remember(context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        object : ConsolePrefs {
            override fun getString(key: String): String? = prefs.getString(key, null)
            override fun putString(key: String, value: String) {
                prefs.edit().putString(key, value).apply()
            }
        }
    }
}
