package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * The whole generated icon set (`packages/icons`, #1311) at its default 24dp,
 * in manifest order and in the ambient `onSurface`, light and dark: the
 * maintainer's visual review surface for a redraw before any call site moves.
 */
@Composable
private fun IconSheet() {
    FlowRow(
        modifier =
            Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PbblsIcon.entries.forEach { icon ->
            PbblsIconImage(icon = icon, contentDescription = null)
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PbblsIconsSheetLight() {
    PebblesTheme { IconSheet() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PbblsIconsSheetDark() {
    PebblesTheme { IconSheet() }
}
