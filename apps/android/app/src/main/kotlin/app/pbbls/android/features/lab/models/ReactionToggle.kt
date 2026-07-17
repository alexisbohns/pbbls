package app.pbbls.android.features.lab.models

/**
 * Pure optimistic-reaction transitions (M44 design D4) — screens flip state
 * with [toggle] BEFORE awaiting the `log_reactions` write and undo with
 * [revert] when it throws. Reconciliation is purely revert-on-error; there is
 * no server refetch (iOS parity). Counts clamp at zero inside
 * [Log.withAdjustedCount], so a revert can legitimately land one above the
 * pre-toggle count on an already-zero row — ported as-is, pinned by tests.
 */
object ReactionToggle {
    data class State(
        val reactedIds: Set<String>,
        val logs: List<Log>,
    )

    /**
     * The membership check callers capture BEFORE toggling — it decides both
     * which write to await (unreact vs react) and the eventual [revert].
     */
    fun wasReacted(
        state: State,
        logId: String,
    ): Boolean = logId in state.reactedIds

    /**
     * Optimistic flip: membership toggles and the matching log's count moves
     * ±1. A [logId] absent from [State.logs] still flips membership but
     * leaves the list untouched (iOS adjusts only the list it owns).
     */
    fun toggle(
        state: State,
        logId: String,
    ): State {
        val wasReacted = wasReacted(state, logId)
        return State(
            reactedIds = if (wasReacted) state.reactedIds - logId else state.reactedIds + logId,
            logs = state.logs.map { if (it.id == logId) it.withAdjustedCount(if (wasReacted) -1 else +1) else it },
        )
    }

    /** Exact inverse of [toggle] given the pre-toggle [wasReacted] capture. */
    fun revert(
        state: State,
        logId: String,
        wasReacted: Boolean,
    ): State =
        State(
            reactedIds = if (wasReacted) state.reactedIds + logId else state.reactedIds - logId,
            logs = state.logs.map { if (it.id == logId) it.withAdjustedCount(if (wasReacted) +1 else -1) else it },
        )
}
