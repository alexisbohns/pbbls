package app.pbbls.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.auth.AuthMode
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Login/Sign-up segmented switcher — the `PebblesAuthSwitcher` analog. A pill
 * track in `system.muted` with the selected segment filled `system.secondary`
 * and a white label; unselected labels are `system.secondary`. Colors mirror the
 * globally-restyled iOS `UISegmentedControl` (`PebblesApp.init`).
 */
@Composable
fun PebblesAuthSwitcher(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val trackShape = RoundedCornerShape(50)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(trackShape)
                .background(system.muted)
                .padding(4.dp),
    ) {
        AuthMode.entries.forEach { entry ->
            val selected = entry == mode
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(trackShape)
                        .background(if (selected) system.secondary else Color.Transparent)
                        .clickable(role = Role.Tab) { onModeChange(entry) }
                        .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(entry.labelRes),
                    style = PebblesTypography.callout,
                    color = if (selected) Color.White else system.secondary,
                )
            }
        }
    }
}
