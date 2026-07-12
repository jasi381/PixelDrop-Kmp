package com.jasmeet.pixeldrop

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jasmeet.pixeldrop.ui.PixelDropApp
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    // Recents/Overview (gesture peek) keeps the Activity RESUMED for the live
    // thumbnail and never fires onPause — window focus is what actually drops,
    // so audio pause/resume is driven off this instead of the lifecycle alone.
    private var windowFocused by mutableStateOf(true)
    private lateinit var appUpdateManager: AppUpdateManager
    private var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var updateDownloaded by mutableStateOf(false)
    private var downloadedPromptDismissed by mutableStateOf(false)
    private val updateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            updateDownloaded = true
            downloadedPromptDismissed = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        hideNavigationBar()
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(updateListener)
        checkForOptionalUpdate()
        setContent {
            PixelDropApp(windowFocused = windowFocused)
            val update = availableUpdate
            if (update != null) {
                AlertDialog(
                    onDismissRequest = { availableUpdate = null },
                    title = { Text(getString(R.string.update_available_title)) },
                    text = { Text(getString(R.string.update_available_message)) },
                    confirmButton = {
                        TextButton(onClick = { startFlexibleUpdate(update) }) {
                            Text(getString(R.string.update_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { availableUpdate = null }) {
                            Text(getString(R.string.update_later_action))
                        }
                    },
                )
            } else if (updateDownloaded && !downloadedPromptDismissed) {
                AlertDialog(
                    onDismissRequest = { downloadedPromptDismissed = true },
                    title = { Text(getString(R.string.update_ready_title)) },
                    text = { Text(getString(R.string.update_ready_message)) },
                    confirmButton = {
                        TextButton(onClick = { appUpdateManager.completeUpdate() }) {
                            Text(getString(R.string.update_restart_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadedPromptDismissed = true }) {
                            Text(getString(R.string.update_later_action))
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) updateDownloaded = true
            }
        }
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) appUpdateManager.unregisterListener(updateListener)
        super.onDestroy()
    }

    /** Flexible updates stay optional and silently no-op for sideloaded builds. */
    private fun checkForOptionalUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                availableUpdate = info
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                updateDownloaded = true
            }
        }
    }

    private fun startFlexibleUpdate(info: AppUpdateInfo) {
        availableUpdate = null
        appUpdateManager.startUpdateFlow(
            info,
            this,
            AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
        windowFocused = hasFocus
    }

    /** Immersive: only the bottom nav bar hides (swipe reveals it briefly);
     *  status bar stays put so the console never feels like it's hiding chrome. */
    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
