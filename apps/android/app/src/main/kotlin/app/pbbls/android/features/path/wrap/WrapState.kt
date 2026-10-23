package app.pbbls.android.features.path.wrap

/**
 * A page's wrap as the Path holds it (#1207, spec §7.1): shown at once when there is one, revalidated
 * behind it, never replaced by a spinner.
 */
sealed interface WrapState<out T> {
    /** Never fetched in this session: the only state that may show less than the wrap. */
    data object NeverLoaded : WrapState<Nothing>

    /** [stale] after a write, until the refetch lands; the wrap stays on screen meanwhile. */
    data class Ready<T>(
        val value: T,
        val stale: Boolean = false,
    ) : WrapState<T>

    /** The fetch failed; [lastGood] is still shown when there is one, and the next focus retries. */
    data class Failed<T>(
        val lastGood: T?,
    ) : WrapState<T>
}

/** What a page renders: the wrap if one ever loaded, whatever its freshness. */
val <T> WrapState<T>.shown: T?
    get() =
        when (this) {
            WrapState.NeverLoaded -> null
            is WrapState.Ready -> value
            is WrapState.Failed -> lastGood
        }
