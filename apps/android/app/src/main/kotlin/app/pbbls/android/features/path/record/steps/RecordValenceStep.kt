package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.valence.ValenceFan

/**
 * Step 3 — how big and how bright — the Android analog of iOS
 * `RecordValenceStep`.
 *
 * Renders the fan of nine stones and its two-axis roll, the same content
 * `ValencePickerSheet` shows. Unlike the other tile steps this one does **not**
 * advance on pick: the fan is a comparison and the roll is continuous, so
 * `Continue` does the advancing. It arrives parked on neutral-medium
 * ([onSeed]) so the roll has something to roll.
 */
@Composable
fun RecordValenceStep(
    selected: Valence?,
    onSelect: (Valence) -> Unit,
    onSeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onSeed() }
    ValenceFan(selected = selected, onSelect = onSelect, modifier = modifier)
}
