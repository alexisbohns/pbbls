package app.pbbls.android.testing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.services.PathStatsServicing

/**
 * In-memory [PathStatsServicing] (#849).
 *
 * Its fields are Compose state, like the real service's — not an
 * implementation detail a fake may simplify away. `PathViewModel` observes this
 * seam through `snapshotFlow`, so a fake backing the same properties with plain
 * `var`s would make the ViewModel look correct in a test while never emitting
 * in the app.
 */
class FakePathStatsService(
    karma: Int? = null,
    ripple: RippleSummary? = null,
) : PathStatsServicing {
    override var karma: Int? by mutableStateOf(karma)

    override var pebbles: Int? by mutableStateOf(null)

    override var ripple: RippleSummary? by mutableStateOf(ripple)

    override var daysPracticed: Int? by mutableStateOf(null)

    override var assiduity: List<Boolean>? by mutableStateOf(null)

    override var hasLoaded: Boolean by mutableStateOf(false)

    var loadCount = 0
        private set

    var refreshCount = 0
        private set

    override suspend fun load() {
        loadCount += 1
        hasLoaded = true
    }

    override suspend fun refresh() {
        refreshCount += 1
    }

    override fun applyKarmaBalance(balance: Int) {
        karma = balance
    }
}
