package com.jasmeet.pixeldrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jasmeet.pixeldrop.ui.platform.PlatformWebView
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

private const val PRIVACY_PAGE = "https://jasi381.github.io/pixel-Drop-Privacy-policy/"
private const val PRIVACY_HOST = "jasi381.github.io"

/** In-app viewer for PixelDrop's publicly hosted privacy policy. The web
 *  engine itself is platform-provided via [PlatformWebView]. */
@Composable
fun PrivacyPolicyScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0C0718))
            .semantics { contentDescription = "Privacy policy" },
    ) {
        PlatformWebView(
            url = PRIVACY_PAGE,
            allowedHost = PRIVACY_HOST,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp)
                .zIndex(2f)
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xF2171522))
                .border(2.dp, PixelDropColors.SynthSun, RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClose,
                )
                .semantics { contentDescription = "Close privacy policy" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "X",
                color = PixelDropColors.SynthSun,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
