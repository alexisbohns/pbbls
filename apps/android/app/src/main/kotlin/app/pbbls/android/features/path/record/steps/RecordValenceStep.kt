package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.features.path.create.pickers.ValencePickerBody
import app.pbbls.android.features.path.models.Valence

/**
 * Step 3 — how big and how bright — the Android analog of iOS
 * `RecordValenceStep`.
 *
 * Renders the same nine-tile grid `ValencePickerSheet` shows, and commits on
 * tap (M58 D3). iOS moved this step to a fan of stones plus a continuous roll
 * in #728, which is why its step carries a `Continue` button; the grid here is
 * discrete, so a tap is unambiguously the answer and the step advances on it.
 * Porting the fan is a separate piece of work — it is a 800-LOC art + layout
 * subsystem with no Android assets yet.
 */
@Composable
fun RecordValenceStep(
    selected: Valence?,
    onSelect: (Valence) -> Unit,
    modifier: Modifier = Modifier,
) {
    ValencePickerBody(current = selected, onSelected = onSelect, modifier = modifier)
}
