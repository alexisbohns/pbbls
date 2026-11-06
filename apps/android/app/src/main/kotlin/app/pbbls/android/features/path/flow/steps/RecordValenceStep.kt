package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.valence.ValenceFan
import app.pbbls.android.features.path.valence.ValenceHelpSheet

/**
 * Step 3 — how big and how bright — the Android analog of iOS
 * `RecordValenceStep`.
 *
 * Renders the fan of nine stones and its two-axis roll, the same content
 * `ValencePickerSheet` shows. Unlike the other tile steps this one does **not**
 * advance on pick: the fan is a comparison and the roll is continuous, so
 * `Continue` does the advancing. It arrives empty (D7): the fan shows its labels and a
 * ghost stone, and Continue stays off until a stone is chosen.
 *
 * The help sheet (D8) opens by itself over an account's first pebble — [onFirstPebbleHelp] is called
 * whenever [isFirstPebble] is true while this step is on screen, and the host decides whether the
 * seen flag lets it open — and lives behind "How to read this" after. [helpOpen] is the host's.
 */
@Composable
fun RecordValenceStep(
    selected: Valence?,
    onSelect: (Valence) -> Unit,
    isFirstPebble: Boolean,
    helpOpen: Boolean,
    onFirstPebbleHelp: () -> Unit,
    onOpenHelp: () -> Unit,
    onCloseHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The flag arrives from a read that may land after this step is on screen, so watch it rather than appear.
    LaunchedEffect(isFirstPebble) { if (isFirstPebble) onFirstPebbleHelp() }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ValenceFan(selected = selected, onSelect = onSelect)
        TextButton(onClick = onOpenHelp) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.valence_help_link),
                style = PebblesTheme.page.actionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
    if (helpOpen) ValenceHelpSheet(onDismiss = onCloseHelp)
}
