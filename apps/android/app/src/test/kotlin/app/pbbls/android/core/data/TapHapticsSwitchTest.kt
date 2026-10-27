package app.pbbls.android.core.data

import android.app.Application
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The taps ask Pebbles' haptics switch (#1242 D23; iOS `TapHaptics.play`): off, a tap never reaches
 * `View.performHapticFeedback`; the switch is read at each tap, so a change in Settings applies at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TapHapticsSwitchTest {
    /** A view that records each feedback constant it is asked to play. */
    private class RecordingView(
        context: Context,
    ) : View(context) {
        val played = mutableListOf<Int>()

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            played += feedbackConstant
            return true
        }
    }

    private val view = RecordingView(ApplicationProvider.getApplicationContext())

    @Test
    fun `with the switch off no tap reaches the view`() {
        val play = TapHaptics.gated(view) { false }
        TapHaptic.entries.forEach(play)
        assertTrue(view.played.isEmpty())
    }

    @Test
    fun `with the switch on every tap plays its own texture`() {
        val play = TapHaptics.gated(view) { true }
        TapHaptic.entries.forEach(play)
        assertEquals(TapHaptic.entries.map(TapHaptics::constantFor), view.played)
    }

    @Test
    fun `the switch is asked at each tap`() {
        var on = true
        val play = TapHaptics.gated(view) { on }
        play(TapHaptic.SELECTION)
        on = false
        play(TapHaptic.SUCCESS)
        on = true
        play(TapHaptic.ADVANCE)
        assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.CONTEXT_CLICK), view.played)
    }
}
