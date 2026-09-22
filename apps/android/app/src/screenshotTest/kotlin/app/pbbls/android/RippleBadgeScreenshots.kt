package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesText
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTypography
import app.pbbls.android.core.ui.RippleBadge
import com.android.tools.screenshot.PreviewTest

/**
 * Ripple badge previews (#566): every level 0–6 in active and inactive states
 * (compare against the iOS `RipplePreviewGrid` side-by-side — the B acceptance
 * gate) — light and dark.
 *
 * The bar that used to render alongside these is gone: `PathBottomBar` collided
 * with the four-tab navigation bar and its replacement, `PathTopStats`, cost
 * more vertical room on Path than it earned (#852). Karma and Ripples have no
 * home in the UI right now and will come back in a top bar of their own; the
 * data is still loaded and still in `PathUiState.Content`.
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
