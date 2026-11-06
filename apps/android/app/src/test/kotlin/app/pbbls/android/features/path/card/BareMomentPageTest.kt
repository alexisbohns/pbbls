package app.pbbls.android.features.path.card

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.MomentDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/** The bare moment's page (pebble card spec §Bare moment): its one call to action, and its heading. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BareMomentPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val f4 =
        MomentDetail(
            id = "f4",
            name = "Sunday market",
            description = "Elio wanted the yellow tomatoes.",
            happenedAt = OffsetDateTime.parse("2026-10-11T09:40:00+02:00"),
            createdAt = OffsetDateTime.parse("2026-10-11T09:40:00+02:00"),
            snaps = emptyList(),
            pebble = null,
        )

    @Test
    fun `make it a pebble calls onComplete`() {
        var completes = 0
        compose.setContent {
            PebblesTheme {
                BareMomentPage(detail = f4, photoUrl = null, ratio = null, onComplete = { completes++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.bare_place_body)).assertExists()
        compose.onNodeWithText(context.getString(R.string.complete_cta)).performClick()
        assertEquals(1, completes)
    }

    private val wordless = f4.copy(description = null)

    @Test
    fun `a draft reads You started this and Continue, and Continue still calls onComplete`() {
        var completes = 0
        compose.setContent {
            PebblesTheme {
                BareMomentPage(detail = f4, photoUrl = null, ratio = null, onComplete = { completes++ }, hasDraft = true)
            }
        }
        compose.onNodeWithText(context.getString(R.string.bare_place_started)).assertExists()
        compose.onNodeWithText(context.getString(R.string.bare_place_kicker)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.complete_cta)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.record_action_continue)).performClick()
        assertEquals(1, completes)
    }

    @Test
    fun `without a draft the page keeps its kicker and Make it a pebble`() {
        compose.setContent {
            PebblesTheme { BareMomentPage(detail = f4, photoUrl = null, ratio = null, onComplete = {}) }
        }
        compose.onNodeWithText(context.getString(R.string.bare_place_kicker)).assertExists()
        compose.onNodeWithText(context.getString(R.string.bare_place_started)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.record_action_continue)).assertDoesNotExist()
    }

    @Test
    fun `Add a few words shows while the moment has none, and opens the edit`() {
        var adds = 0
        compose.setContent {
            PebblesTheme {
                BareMomentPage(detail = wordless, photoUrl = null, ratio = null, onComplete = {}, onAddWords = { adds++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.richer_add_words)).performClick()
        assertEquals(1, adds)
    }

    @Test
    fun `Add a few words is absent once the moment has words, and when the host offers none`() {
        compose.setContent {
            PebblesTheme {
                BareMomentPage(detail = f4, photoUrl = null, ratio = null, onComplete = {}, onAddWords = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.richer_add_words)).assertDoesNotExist()
    }

    @Test
    fun `no onAddWords, no row`() {
        compose.setContent {
            PebblesTheme { BareMomentPage(detail = wordless, photoUrl = null, ratio = null, onComplete = {}) }
        }
        compose.onNodeWithText(context.getString(R.string.richer_add_words)).assertDoesNotExist()
    }

    @Test
    fun `the title is a heading`() {
        compose.setContent {
            PebblesTheme {
                BareMomentPage(detail = f4, photoUrl = null, ratio = null, onComplete = {})
            }
        }
        compose
            .onNodeWithText("Sunday market", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the kicker is the full day and the time, in the moment's zone`() {
        val (day, time) = bareKicker(f4.happenedAt, Locale.UK, ZoneId.of("Europe/Paris"))
        assertEquals("Sunday 11 October", day)
        assertEquals("09:40", time)

        val (jour, heure) = bareKicker(f4.happenedAt, Locale.FRANCE, ZoneId.of("Europe/Paris"))
        assertEquals("dimanche 11 octobre", jour)
        assertEquals("09:40", heure)

        // The same instant, read in New York, is three in the morning.
        assertEquals("03:40", bareKicker(f4.happenedAt, Locale.UK, ZoneId.of("America/New_York")).second)
    }
}
