package app.pbbls.android.core.ui.souls

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import app.pbbls.android.R
import app.pbbls.android.core.data.ReferenceDataServicing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SoulCreate"

/**
 * The chooser's Add row and FAB, for a host that owns no ViewModel (the picker sheet, the inline
 * souls step): [state] gets the new soul selected on success ([SoulChooserState.didCreate], which
 * closes the search), and on a failed insert keeps the query and sets its line
 * ([SoulChooserState.createErrorRes], S4).
 *
 * One create at a time: a second tap while one runs does nothing, so a double tap cannot make two
 * souls of one name. The write and its achievement check run to the end even if the host leaves the
 * composition (tap Add, then Done): the server has the soul by then, and the cache and the check
 * must see it.
 */
@Composable
fun rememberSoulCreate(
    state: SoulChooserState,
    refs: ReferenceDataServicing,
    onAchievementCheck: () -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val check by rememberUpdatedState(onAchievementCheck)
    val creator = remember(state, refs, scope) { SoulCreator(state, refs, scope) { check() } }
    return creator::create
}

private class SoulCreator(
    private val state: SoulChooserState,
    private val refs: ReferenceDataServicing,
    private val scope: CoroutineScope,
    private val onAchievementCheck: () -> Unit,
) {
    // Main thread only: set by the tap, cleared by the coroutine back on the same dispatcher.
    private var inFlight = false

    fun create(name: String) {
        if (inFlight) return
        inFlight = true
        // ATOMIC: a host that leaves before the coroutine is dispatched must not skip the write.
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                val created =
                    withContext(NonCancellable) {
                        refs.createSoul(name)?.also { onAchievementCheck() }
                    }
                if (created != null) {
                    state.didCreate(created)
                } else {
                    Log.e(TAG, "createSoul returned null")
                    state.createErrorRes = R.string.soul_save_error
                }
            } finally {
                inFlight = false
            }
        }
    }
}
