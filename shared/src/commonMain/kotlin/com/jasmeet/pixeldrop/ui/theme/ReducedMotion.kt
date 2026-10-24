package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.runtime.Composable

/** True when the user turned system animations off; decorative motion should hold still. */
@Composable
expect fun rememberReducedMotion(): Boolean
