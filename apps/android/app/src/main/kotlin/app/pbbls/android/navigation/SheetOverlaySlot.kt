package app.pbbls.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the app's celebrations draw (#940): the karma capsule and the
 * achievement moment belong above everything, the way iOS lifts them into
 * `KarmaPassthroughWindow` above its alerts.
 *
 * A docked sheet is a window of its own, above the activity's, so anything the
 * root draws is hidden under it. While a sheet is open the sheet draws
 * [content] inside its window, and the root does not; otherwise the root does.
 * Never both, which [isHostedBySheet] is what decides.
 *
 * Counted rather than flagged so the hand-over stays right if one sheet's
 * scene leaves composition just after the next one's enters.
 */
@Stable
class SheetOverlaySlot(
    val content: @Composable () -> Unit = {},
) {
    private var openSheets by mutableIntStateOf(0)

    /** True while at least one sheet is drawing [content] in its own window. */
    val isHostedBySheet: Boolean get() = openSheets > 0

    fun onSheetShown() {
        openSheets++
    }

    fun onSheetHidden() {
        openSheets--
    }
}

/**
 * The root's [SheetOverlaySlot]. The default draws nothing, so a sheet
 * rendered outside `RootScreen` (previews, tests) needs no setup.
 */
val LocalSheetOverlaySlot = staticCompositionLocalOf { SheetOverlaySlot() }
