package app.pbbls.android.features.path.card

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the print plays (pebble card spec §Completion): into a place the reader sees whole.
 * A place under the screen's foot, where the stone's flight and press played behind the
 * navigation bar, is scrolled into view first and printed into; one taller than the view
 * crossfades. A page standing in for the bare one, on the test clock run free.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
class CardCompletionPlaceTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scroll: ScrollState

    private fun play(
        above: Dp,
        placeHeight: Dp,
    ): CardCompletionState {
        val completion = CardCompletionState()
        compose.setContent {
            PebblesTheme {
                CardCompletion(
                    completion = completion,
                    page = { placeModifier, ghostModifier ->
                        scroll = rememberScrollState()
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            Spacer(Modifier.height(above))
                            Box(placeModifier.fillMaxWidth().height(placeHeight)) {
                                Box(ghostModifier.size(GHOST))
                            }
                            Spacer(Modifier.height(BELOW))
                        }
                    },
                    card = { cardModifier -> Box(cardModifier.fillMaxWidth().height(CARD)) },
                    modifier = Modifier.fillMaxSize(),
                )
                LaunchedEffect(completion) { completion.play(reduceMotion = false) }
            }
        }
        compose.waitForIdle()
        return completion
    }

    @Test
    fun `a place below the fold is scrolled into view, whole, and printed into`() {
        val completion = play(above = 1_500.dp, placeHeight = 400.dp)

        assertFalse("printed, not crossfaded", completion.crossfade)
        assertTrue("the whole place shows", completion.placeShown())
        assertTrue("the page scrolled", scroll.value > 0)
    }

    @Test
    fun `a place cut by the screen's foot is scrolled up until it shows whole`() {
        val completion = play(above = 600.dp, placeHeight = 400.dp)

        assertFalse(completion.crossfade)
        assertTrue(completion.placeShown())
    }

    @Test
    fun `a place already on screen is printed into where it stands`() {
        val completion = play(above = 100.dp, placeHeight = 400.dp)

        assertFalse(completion.crossfade)
        assertEquals("the page did not move", 0, scroll.value)
    }

    @Test
    fun `a place taller than the view crossfades`() {
        val completion = play(above = 1_500.dp, placeHeight = 1_000.dp)

        assertTrue(completion.crossfade)
    }

    private companion object {
        val GHOST = 24.dp
        val BELOW = 400.dp
        val CARD = 500.dp
    }
}
