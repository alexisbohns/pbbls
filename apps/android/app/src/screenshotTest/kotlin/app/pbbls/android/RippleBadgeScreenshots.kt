package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.components.PathBottomBar
import app.pbbls.android.features.shared.ripples.RippleBadge
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import com.android.tools.screenshot.PreviewTest

/**
 * Ripple badge + bottom-bar previews (#566): every level 0–6 in active and
 * inactive states (compare against the iOS `RipplePreviewGrid` side-by-side —
 * the B acceptance gate), plus the PathBottomBar with real stats and the
 * loading (null) state — light and dark.
 */
@Composable
private fun RippleGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(true, false).forEach { active ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PebblesText(
                    text = if (active) "active" else "inactive",
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                    modifier = Modifier.width(60.dp),
                )
                (0..6).forEach { level ->
                    RippleBadge(level = level, activeToday = active)
                }
            }
        }
        PathBottomBar(
            karma = 42,
            ripple = RippleSummary(rippleLevel = 3, pebbles28d = 11, activeToday = true),
            onProfile = {},
            modifier = Modifier.fillMaxWidth(),
        )
        // Loading state: em-dash number, level-0 inactive badge.
        PathBottomBar(
            karma = null,
            ripple = null,
            onProfile = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun RippleBadgesLight() {
    PebblesTheme { RippleGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RippleBadgesDark() {
    PebblesTheme { RippleGallery() }
}
