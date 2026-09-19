package app.pbbls.android.ui

import kotlin.coroutines.cancellation.CancellationException

/**
 * [kotlin.runCatching], minus the bug (#849).
 *
 * `runCatching` and a bare `catch (e: Exception)` both swallow
 * [CancellationException], and structured concurrency is built on that exception
 * being allowed to travel. A coroutine whose cancellation is caught keeps
 * running inside a scope that believes it has stopped: `viewModelScope` will not
 * complete, a `withTimeout` never fires, and the failure shows up far from the
 * catch that caused it. Before this helper the app had 64 `catch (e: Exception)`
 * blocks in `features/` and **zero** references to `CancellationException`.
 *
 * Rethrowing first is one line, which is exactly why it goes missing. Putting it
 * in a function makes it structural rather than a discipline — the same reasoning
 * that routes every record-flow tap through `RecordFlowModel` so the haptic
 * cannot be forgotten.
 *
 * ```
 * val state = runCatchingCancellable { pathService.loadPathPebbles() }
 *     .fold(
 *         onSuccess = { PathUiState.Content(it) },
 *         onFailure = { PathUiState.Error(R.string.path_load_error) },
 *     )
 * ```
 *
 * This is the load/read helper. It is **not** for a write that must finish once
 * the request has left the device — that section belongs in
 * `withContext(NonCancellable)`, which is a different problem: this helper lets
 * cancellation through, `NonCancellable` prevents it arriving.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
