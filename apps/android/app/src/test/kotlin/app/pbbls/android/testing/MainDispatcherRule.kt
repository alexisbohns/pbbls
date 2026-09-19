package app.pbbls.android.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a [TestDispatcher] for the duration of a test
 * (#849).
 *
 * Every ViewModel test needs this and none of them can express it themselves:
 * `viewModelScope` is hard-wired to `Dispatchers.Main.immediate`, which on the
 * JVM has no Looper and throws on first use. `runTest` alone does not help —
 * it controls the scope the test body runs in, not the one the subject launches
 * into.
 *
 * [StandardTestDispatcher] rather than `UnconfinedTestDispatcher`, deliberately:
 * unconfined runs a `launch` eagerly to its first suspension, which hides
 * exactly the ordering this migration is about — with it, a test cannot observe
 * `Loading` before `Content` because the load has already finished by the time
 * the constructor returns. Standard queues the work, so the test says
 * `advanceUntilIdle()` when it means it.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
