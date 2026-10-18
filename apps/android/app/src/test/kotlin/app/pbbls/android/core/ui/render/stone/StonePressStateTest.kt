package app.pbbls.android.core.ui.render.stone

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The press choreography on a test frame clock: it plays, and it comes back to rest. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class StonePressStateTest {
    @Test
    fun `a press sets a ripple token and every element returns to rest`() =
        runTest {
            val state = StonePressState()

            withContext(TestMonotonicFrameClock(this)) { state.press(reduceMotion = false) }

            assertNotNull(state.rippleToken)
            assertEquals(1f, state.dipScale, 0f)
            assertEquals(1f, state.ringsScale, 0f)
            assertEquals(0f, state.sweep.value, 0f)
            assertEquals(0f, state.sheen.value, 0f)
            assertEquals(0f, state.sparkScale, 0f)
            assertEquals(0f, state.sparkAlpha, 0f)
            assertFalse(state.isPressing)
            assertFalse(state.touchedStill)
        }

    @Test
    fun `mid-run the stone is dipping`() =
        runTest {
            val state = StonePressState()
            val clock = TestMonotonicFrameClock(this)

            val job = launch { withContext(clock) { state.press(reduceMotion = false) } }
            advanceTimeBy(100)
            runCurrent()

            assertTrue(state.isPressing)
            assertTrue("dip ${state.dipScale}", state.dipScale < 1f)
            job.cancel()
        }

    @Test
    fun `under reduced motion the touch holds still, then clears, with no ripple`() =
        runTest {
            val state = StonePressState()

            launch { state.press(reduceMotion = true) }
            runCurrent()

            assertTrue(state.touchedStill)
            assertTrue(state.sheen.value > 0f)
            assertNull(state.rippleToken)

            advanceUntilIdle()

            assertFalse(state.touchedStill)
            assertEquals(0f, state.sheen.value, 0f)
            assertNull(state.rippleToken)
            assertFalse(state.isPressing)
        }
}
