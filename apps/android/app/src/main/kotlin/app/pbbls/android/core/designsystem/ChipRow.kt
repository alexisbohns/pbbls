package app.pbbls.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.stone.StoneShape

private val CHIP_WIDTH = 48.dp
private val CHIP_HEIGHT = 46.dp
private val CHIP_CORNER = 16.dp
private val CHEVRON = 18.dp
private val ROW_GAP = 14.dp
private val ROW_PADDING = 10.dp
private val LINE_GAP = 2.dp
private val MIN_TOUCH = 48.dp

/**
 * A row with its icon on a pebble chip (spec 2026-10-31, the mock's `.about .row`): a 48 × 46 dp
 * `surfaceContainer` stone cut from [seed] holding [icon] in `primary`, the [title] over its
 * [line], and a chevron in `outline`. The whole row is one button for TalkBack, at least 48 dp.
 */
@Composable
fun ChipRow(
    icon: PbblsIcon,
    title: String,
    line: String,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val chip = remember(seed) { StoneShape(seed, CHIP_CORNER) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = ROW_PADDING),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CHIP_WIDTH, CHIP_HEIGHT)
                    .background(colors.surfaceContainer, chip),
            contentAlignment = Alignment.Center,
        ) {
            PbblsIconImage(icon, PbblsIconSize.TILE, contentDescription = null, tint = colors.primary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            Text(text = title, style = type.rowTitle, color = colors.onSurface)
            Text(text = line, style = type.rowLine, color = colors.onSurfaceVariant)
        }
        // Mirrored in RTL by the drawable's autoMirrored.
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.outline,
            modifier = Modifier.size(CHEVRON),
        )
    }
}
