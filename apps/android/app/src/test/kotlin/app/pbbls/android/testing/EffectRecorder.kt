package app.pbbls.android.testing

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/**
 * Records a ViewModel's one-shot effects for the length of a test (#849).
 *
 * **Collect on the test scope, not `backgroundScope`.** `advanceUntilIdle()`
 * does not drain `backgroundScope`'s tasks, so a collector launched there can
 * sit un-resumed while the emission is already buffered in the channel, and the
 * recorded list comes back empty — a test that then asserts "no effect" passes
 * for the wrong reason. Measured on kotlinx-coroutines 1.11.0: the same probe
 * returns `[x]` from `launch { … }` and `[]` from `backgroundScope.launch { … }`.
 *
 * The returned list is live — read it after `advanceUntilIdle()`. Cancel the
 * [Recording.job] at the end of the test, or `runTest` will not finish, because
 * an effects flow never completes.
 *
 * ```
 * val effects = recordEffects(viewModel.effects)
 * viewModel.save()
 * advanceUntilIdle()
 * assertEquals(listOf(Saved(…)), effects.values)
 * effects.stop()
 * ```
 */
class Recording<T>(
    val values: MutableList<T>,
    val job: Job,
) {
    fun stop() = job.cancel()
}

fun <T> TestScope.recordEffects(flow: Flow<T>): Recording<T> {
    val values = mutableListOf<T>()
    return Recording(values, launch { flow.toList(values) })
}
