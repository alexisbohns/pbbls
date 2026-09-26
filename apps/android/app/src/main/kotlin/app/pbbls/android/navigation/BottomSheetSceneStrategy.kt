package app.pbbls.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * A docked pane on compact windows (#940): the top entry, when it asks for it,
 * as a full-height `ModalBottomSheet` over whatever is below — the M3 "docked
 * pane" that becomes co-planar on large screens, where the list-detail
 * strategy claims the same entry first.
 *
 * Adapted from the Navigation 3 bottom-sheet recipe, in the shape of the
 * library's own `DialogSceneStrategy`. The sheet's own dismissal (scrim tap,
 * drag, system or predictive back, all handled by the sheet's window) animates
 * the sheet away and then calls `onBack`, so leaving it is always a pop.
 *
 * There is deliberately no `onRemove` animation. The only way the sheet leaves
 * without dismissing itself is an entry pushed over it (Edit), and a sheet
 * sliding down on top of that entry's own slide-up would be worse than the
 * sheet simply giving way to it.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[BottomSheetKey] != true) return null
        return BottomSheetScene(
            key = last.contentKey,
            entry = last,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            onBack = onBack,
        )
    }

    /** Marks an entry as a docked sheet on compact windows. */
    object BottomSheetKey : NavMetadataKey<Boolean>

    companion object {
        /** Metadata marking an entry as a docked sheet on compact windows. */
        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        // The sheet composes in its own window, which provides the activity's
        // lifecycle owner again rather than the one NavDisplay gives this
        // overlay. Captured out here and re-provided inside, as the library's
        // DialogScene does, so the entry's lifecycle (the detail's resume
        // refresh) follows the overlay and not the activity.
        val lifecycleOwner = rememberLifecycleOwner()
        // The celebrations draw in here while the sheet is up: the root's copy
        // would sit under this window, unseen and untappable.
        val overlaySlot = LocalSheetOverlaySlot.current
        DisposableEffect(overlaySlot) {
            overlaySlot.onSheetShown()
            onDispose { overlaySlot.onSheetHidden() }
        }
        // Full height only: the details it hosts are pages, not peeks.
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // The sheet keeps the status bar clear; the entry pads the
            // gesture bar itself, so its own page colour runs under it rather
            // than a band of sheet colour. It has to anyway: the same entry is
            // a pane on large screens, where no sheet pads anything.
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                Box(Modifier.fillMaxSize()) {
                    entry.Content()
                    overlaySlot.content()
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BottomSheetScene<*> &&
            key == other.key &&
            entry == other.entry &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries

    override fun hashCode(): Int = listOf(key, entry, previousEntries, overlaidEntries).hashCode()

    override fun toString(): String = "BottomSheetScene(key=$key, entry=$entry)"
}
