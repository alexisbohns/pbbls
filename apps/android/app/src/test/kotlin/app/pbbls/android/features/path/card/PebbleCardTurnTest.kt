package app.pbbls.android.features.path.card

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.EmotionRef
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.core.model.Visibility
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The held card's turn (pebble card spec §Behaviour): the button, the TalkBack action, a
 * drag and a changed requested face each bring the other face in front, and the face not
 * in front says nothing. The verso's lockup heading ("Your pebble. …") is the verso-only
 * node; both faces stay composed, so it must be absent from the semantics on the recto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PebbleCardTurnTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val detail =
        MomentDetail(
            id = "m1",
            name = "Couldn’t sleep",
            happenedAt = OffsetDateTime.of(2026, 10, 7, 3, 10, 0, 0, ZoneOffset.UTC),
            createdAt = OffsetDateTime.of(2026, 10, 7, 3, 10, 0, 0, ZoneOffset.UTC),
            pebble =
                MomentPebble(
                    id = "p1",
                    intensity = 1,
                    positiveness = -1,
                    visibility = Visibility.SECRET,
                    emotion = EmotionRef(id = "e1", slug = "scared", name = "Fear"),
                ),
        )

    private val versoHeading =
        hasContentDescription(context.getString(R.string.card_verso_heading), substring = true) and isHeading()

    @Before
    fun fullMotion() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun setCard(requested: () -> CardFace = { CardFace.RECTO }) {
        compose.setContent {
            PebblesTheme {
                PebbleCard(
                    detail = detail,
                    palette = null,
                    ratio = null,
                    photoUrl = null,
                    thumbUrl = null,
                    soulsById = emptyMap(),
                    domains = emptyList(),
                    requestedFace = requested(),
                    zone = ZoneOffset.UTC,
                )
            }
        }
    }

    @Test
    fun `the turn button shows the verso, and then offers the moment`() {
        setCard()
        compose.onNode(versoHeading).assertDoesNotExist()

        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).performClick()
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_moment)).assertExists()
    }

    @Test
    fun `the card's TalkBack action turns it`() {
        setCard()
        val label = context.getString(R.string.card_turn_action_to_pebble)
        val withAction =
            SemanticsMatcher("has the custom action \"$label\"") { node ->
                node.config
                    .getOrElseNullable(SemanticsActions.CustomActions) { null }
                    .orEmpty()
                    .any { it.label == label }
            }
        val action =
            compose
                .onNode(withAction)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == label }

        compose.runOnUiThread { assertTrue(action.action()) }
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `a drag toward the start edge past the half turns the card`() {
        setCard()

        compose.onNode(cardRoot()).performTouchInput {
            swipe(Offset(width * 0.9f, height * 0.3f), Offset(width * 0.1f, height * 0.3f), durationMillis = 600)
        }
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `a short drag reverts`() {
        setCard()

        compose.onNode(cardRoot()).performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.3f), Offset(width * 0.45f, height * 0.3f), durationMillis = 600)
        }
        compose.waitForIdle()

        compose.onNode(versoHeading).assertDoesNotExist()
    }

    @Test
    fun `a changed requested face turns the card to it`() {
        var requested by mutableStateOf(CardFace.RECTO)
        setCard { requested }
        compose.onNode(versoHeading).assertDoesNotExist()

        requested = CardFace.VERSO
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `under reduced motion the button crossfades to the verso`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setCard()

        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).performClick()
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    /** The card's root: the node that carries the turn's TalkBack action. */
    private fun cardRoot() = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
}
