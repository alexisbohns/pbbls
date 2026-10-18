package app.pbbls.android.features.path.spine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.components.NewPebbleButton
import app.pbbls.android.features.path.spine.SpineLayout.EMPTY_BUTTON_GAP
import app.pbbls.android.features.path.spine.SpineLayout.EMPTY_BUTTON_INSET
import app.pbbls.android.features.path.spine.SpineLayout.EMPTY_PAD_Y
import app.pbbls.android.features.path.spine.SpineLayout.OPENING_SUMMARY_GAP
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X

/**
 * The fresh-week invitation, set on the paper like the rest of the Spine (#1122; the
 * web's `PathEmptyState`): only the current week can be empty, so its recto is this
 * instead of the feed. Keeps today's copy and its create button — the FAB is on the
 * Path too, but the empty page keeps its own door, as the week list's `EmptyWeek` did.
 */
@Composable
internal fun SpineEmptyWeek(
    onCreatePebble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = VERSO_PAD_X, vertical = EMPTY_PAD_Y),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.path_empty_week_title),
            style = type.title,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.path_empty_week_subtitle),
            style = type.summary,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OPENING_SUMMARY_GAP),
        )
        NewPebbleButton(
            onTap = onCreatePebble,
            modifier = Modifier.padding(top = EMPTY_BUTTON_GAP).fillMaxWidth().padding(horizontal = EMPTY_BUTTON_INSET),
        )
    }
}
