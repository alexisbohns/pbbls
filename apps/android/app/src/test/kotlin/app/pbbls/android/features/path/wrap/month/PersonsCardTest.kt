package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The persons on a card (#1242 D13): each row's share of the room, in whole pixels. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `on a card the persons' rows take their share in whole pixels, never overrunning the room left by more`() {
        // Five rows share 457.5 dp less more's 56: 80.3 dp each, 160.6 px at xhdpi. Rounded, they would take 805 px of 803.
        val room = 457.5.dp
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    Box(Modifier.size(HOST_WIDTH, HOST_HEIGHT)) {
                        PersonsSection(wrap, lookups, DayView.CIRCLE, onMore = {}, onCard = true, room = room)
                    }
                }
            }
        }
        compose.waitForIdle()
        val heights = personRowHeights()
        assertEquals(5, heights.size)
        heights.forEach { assertEquals(80f, it.value, 0f) }
        val share = room - PersonsSection.MORE_RESERVE
        assertTrue("the rows overrun their share of the room: $heights of $share", heights.fold(0.dp) { sum, h -> sum + h } <= share)
    }

    /** Each shown person's row height as laid out, found by the "name, count" it says. */
    private fun personRowHeights(): List<Dp> =
        MonthSections.namedPersons(wrap, lookups).take(5).map { person ->
            val name = lookups.soul(person.soulId)!!.name
            val bounds = compose.onNodeWithContentDescription("$name, ${person.count}", substring = true).getBoundsInRoot()
            bounds.bottom - bounds.top
        }

    private companion object {
        val HOST_WIDTH = 390.dp
        val HOST_HEIGHT = 600.dp
    }
}
