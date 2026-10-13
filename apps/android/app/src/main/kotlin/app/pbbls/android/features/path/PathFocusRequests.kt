package app.pbbls.android.features.path

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot "show this moment's week" request from a flow that just wrote a
 * moment (Capture's save, Complete's completion) to the Path, which consumes it
 * on its next load (R14, iOS P12). By moment id, not date: Path finds the week
 * in what it just loaded, so the request can never point at a week the
 * timeline does not have.
 */
@Singleton
class PathFocusRequests
    @Inject
    constructor() {
        @Volatile private var pendingMomentId: String? = null

        fun request(momentId: String) {
            pendingMomentId = momentId.lowercase()
        }

        fun consume(): String? = pendingMomentId.also { pendingMomentId = null }
    }
