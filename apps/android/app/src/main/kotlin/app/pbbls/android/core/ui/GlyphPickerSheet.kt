package app.pbbls.android.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem

/**
 * The glyph picker's body, as a slot (#914).
 *
 * The picker is glyph's: it hosts the carve studio and the store's buy panel
 * inline. Its hosts are path's (the pebble form, the record flow's glyph step)
 * and profile's (Settings, the soul form), and a feature may not import another
 * feature. So the hosts take the body as a parameter, and the entry provider,
 * which sees every feature, fills it. Everything a host needs to hold the
 * picker without seeing it lives in this file.
 */
typealias GlyphPickerSlot = @Composable (
    currentGlyphId: String?,
    onSelected: (Glyph) -> Unit,
    state: GlyphPickerState,
    modifier: Modifier,
) -> Unit

/**
 * The glyph picker's content-swap state, hoisted out of the picker so both
 * callers can unwind it.
 *
 * The sheet unwinds on a dismiss gesture (back returns to the grid before it
 * closes the sheet); the record flow's glyph step unwinds on the system back
 * before stepping backwards. Without the hoist, the flow would have no way to
 * ask "is a swap panel open?" — and a nested panel that outlives its step is
 * exactly the trap the iOS port hit, where `GlyphDetailDrawer` survived onto the
 * next step because only the sheet's dismissal had ever taken it down.
 */
class GlyphPickerState {
    var isCarving: Boolean by mutableStateOf(false)
        internal set

    var buying: GlyphGridItem? by mutableStateOf(null)
        internal set

    /**
     * Close the topmost content swap. Returns true when one was open (so the
     * caller keeps its own surface), false when the grid is already showing.
     */
    fun unwind(): Boolean =
        when {
            isCarving -> {
                isCarving = false
                true
            }
            buying != null -> {
                buying = null
                true
            }
            else -> false
        }

    /** Back to the grid — after a select, or after a purchase completes. */
    internal fun reset() {
        isCarving = false
        buying = null
    }
}

@Composable
fun rememberGlyphPickerState(): GlyphPickerState = remember { GlyphPickerState() }

/**
 * The tabbed glyph picker in a sheet — the #549 harmonization (M43 D10): the
 * same Mine / Owned / Commu tabs as the store inside the caller's single
 * `ModalBottomSheet` level, with inline buy and a carve row. The API is the
 * M39 drop-in (`currentGlyphId` + `onSelected`) plus the [picker] body.
 *
 * D5 adaptation (named): the swap panel and the carve studio open as CONTENT
 * SWAPS inside this sheet rather than stacked sheets/covers, so back (or a
 * dismiss gesture) returns to the grid first.
 *
 * The record flow's glyph step renders the same [picker] inline, under the
 * flow's own chrome (M58 D5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphPickerSheet(
    currentGlyphId: String?,
    onDismiss: () -> Unit,
    onSelected: (Glyph) -> Unit,
    picker: GlyphPickerSlot,
) {
    val state = rememberGlyphPickerState()
    ModalBottomSheet(
        // Content swaps unwind before the sheet itself dismisses (D5).
        onDismissRequest = { if (!state.unwind()) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        picker(currentGlyphId, onSelected, state, Modifier)
    }
}
