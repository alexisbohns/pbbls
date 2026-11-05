package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage

/**
 * The stones in the top bar (#1510 §1): passed stones lit, the current one lit and larger, the ones
 * ahead outlined. One accessible node reading "Step n of m".
 */
@Composable
fun ProgressStones(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.onboarding_progress, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val lit = index <= current
            PbblsIconImage(
                icon = if (lit) PbblsIcon.PEBBLE_ROUND_LIT_1 else PbblsIcon.PEBBLE_ROUND,
                contentDescription = null,
                size = if (index == current) 18.dp else 12.dp,
                tint = if (lit) colors.primary else colors.outline,
            )
        }
    }
}
