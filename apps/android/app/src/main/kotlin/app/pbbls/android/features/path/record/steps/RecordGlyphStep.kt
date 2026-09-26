package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphPickerSlot
import app.pbbls.android.core.ui.GlyphPickerState

/**
 * Step 8 — the glyph, skippable (M58 D2) — ports iOS `RecordGlyphStep`.
 *
 * Renders the glyph picker's body inline, which brings its tabs, its inline buy
 * and its carve entry point with it. Both open as content swaps *within* the
 * step rather than as stacked surfaces, so there is nothing left to dismiss
 * when the step advances — and the content resets itself on select besides,
 * because the swap panel flips to its owned state rather than closing (the trap
 * that cost iOS a bug: its drawer relied on the sheet's dismissal, and a flow
 * does not dismiss).
 *
 * [state] is hoisted so the screen's back handler can unwind an open swap
 * before it steps backwards. [picker] is that body: it is glyph's, so the flow
 * receives it from the entry provider rather than importing it (#914).
 */
@Composable
fun RecordGlyphStep(
    selectedGlyphId: String?,
    onSelect: (Glyph) -> Unit,
    state: GlyphPickerState,
    picker: GlyphPickerSlot,
    modifier: Modifier = Modifier,
) {
    picker(selectedGlyphId, onSelect, state, modifier)
}
