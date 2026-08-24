package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.valence.ValenceFan
import app.pbbls.android.services.TapHaptic
import app.pbbls.android.services.rememberTapHaptics
import app.pbbls.android.theme.Spacing

/**
 * The valence picker (D5) — ports iOS `ValencePickerSheet`. A single
 * `ModalBottomSheet` over the fan of nine stones and its two-axis roll.
 *
 * It **stages** rather than committing on pick, which it used to do: the roll
 * changes the value at every detent, so a sheet that dismissed on change would
 * close on the first swipe. Done is what closes it now, matching the souls and
 * emotion sheets' toolbar.
 *
 * Buzzes on its own, unlike the record flow's valence step: that step's picks
 * go through `RecordFlowModel`, which buzzes for every interaction, and this
 * sheet has no model behind it. Either way exactly one selection tick per
 * detent — see `ValenceRoll`.
 *
 * The record flow's valence step renders the same fan inline instead (D5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValencePickerSheet(
    current: Valence?,
    onDismiss: () -> Unit,
    onSelected: (Valence) -> Unit,
) {
    val haptic = rememberTapHaptics()
    // The roll always needs a value under the finger, so an untouched sheet
    // shows `current` or parks on neutral-medium the way the record step does.
    var staged by remember { mutableStateOf(current ?: Valence.NEUTRAL_MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SheetToolbar(
                title = stringResource(R.string.create_valence_title),
                onCancel = onDismiss,
                onDone = { onSelected(staged) },
            )
            ValenceFan(
                selected = staged,
                onSelect = {
                    if (it != staged) haptic(TapHaptic.SELECTION)
                    staged = it
                },
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.lg),
            )
        }
    }
}
