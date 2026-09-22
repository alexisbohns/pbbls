package app.pbbls.android.core.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one behaviour that separates [runCatchingCancellable] from
 * `kotlin.runCatching`, pinned.
 *
 * Worth a test of its own rather than leaning on the ViewModel tests: this is
 * the helper eleven screens' error paths will be built on, and the bug it
 * prevents (a coroutine that swallows its own cancellation) is invisible at the
 * call site — the symptom shows up as a scope that never completes, somewhere
 * else entirely.
 */
class CoroutineErrorsTest {
    @Test
    fun `returns the value on success`() {
        assertEquals("ok", runCatchingCancellable { "ok" }.getOrNull())
    }

    @Test
    fun `captures an ordinary failure`() {
        val result = runCatchingCancellable { error("boom") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `captures a checked IO failure rather than throwing it`() {
        val result = runCatchingCancellable { throw IOException("offline") }
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException instead of capturing it`() {
        runCatchingCancellable { throw CancellationException("cancelled") }
    }

    /**
     * The regression in its real shape: a cancelled child must actually die.
     *
     * `kotlin.runCatching` catches `Throwable`, so the body below would carry on
     * past its own cancellation and `job.join()` would hang or complete with the
     * work done anyway.
     */
    @Test
    fun `a cancelled coroutine using the helper really stops`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var reachedTheEnd = false

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    runCatchingCancellable {
                        started.complete(Unit)
                        // Suspends forever; cancellation resumes it by throwing.
                        CompletableDeferred<Unit>().await()
                    }
                    reachedTheEnd = true
                }

            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertFalse("the body swallowed its own cancellation", reachedTheEnd)
        }
}
