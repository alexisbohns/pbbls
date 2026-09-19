package app.pbbls.android.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two properties `ObserveUiEffects` relies on: nothing is dropped, and
 * order is preserved.
 *
 * The lifecycle half (collect only at or above STARTED) is not testable here —
 * it needs a real `LifecycleOwner`, so it waits for Robolectric (#857). What is
 * testable is the part that makes the lifecycle half safe: an effect emitted
 * while nobody is collecting is buffered rather than lost.
 */
class UiEffectsTest {
    @Test
    fun `an effect emitted before anyone collects is buffered, not dropped`() =
        runTest {
            val effects = UiEffects<String>(backgroundScope)

            effects.emit("navigate")
            advanceUntilIdle()

            assertEquals("navigate", effects.flow.first())
        }

    @Test
    fun `effects arrive in the order they were emitted`() =
        runTest {
            val effects = UiEffects<Int>(backgroundScope)

            repeat(5) { effects.emit(it) }
            advanceUntilIdle()

            val received = mutableListOf<Int>()
            effects.flow.take(5).collect { received.add(it) }

            assertEquals(listOf(0, 1, 2, 3, 4), received)
        }

    /**
     * `receiveAsFlow` is single-consumer by design: one collector gets each
     * element. That is the property that keeps a one-shot effect one-shot, so a
     * test pins it rather than leaving it to the reader's memory of Channel
     * semantics.
     */
    @Test
    fun `an element is delivered once, not replayed to the next collector`() =
        runTest {
            val effects = UiEffects<String>(backgroundScope)

            effects.emit("only-once")
            advanceUntilIdle()

            assertEquals("only-once", effects.flow.first())

            effects.emit("second")
            advanceUntilIdle()
            assertEquals("second", effects.flow.first())
        }
}
