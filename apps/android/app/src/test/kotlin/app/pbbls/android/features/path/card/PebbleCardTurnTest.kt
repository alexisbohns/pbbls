package app.pbbls.android.features.path.card

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The held card's turn (pebble card spec §Behaviour): the footer's button (TalkBack's turn
 * too), a drag and a changed requested face each bring the other face in front, the
 * face a turn lands on survives a restore, and the face not in front says nothing. The
 * verso's lockup heading ("Your pebble. …") is the verso-only node; both faces stay
 * composed, so it must be absent from the semantics on the recto.
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

    private fun setCard(
        request: () -> Int = { 0 },
        requested: () -> CardFace = { CardFace.RECTO },
    ) {
        compose.setContent { Card(requested(), request()) }
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
    fun `for TalkBack the turn is the footer's button, named for where it leads`() {
        setCard()
        val toPebble = context.getString(R.string.card_turn_action_to_pebble)
        val toMoment = context.getString(R.string.card_turn_action_to_moment)

        compose
            .onNodeWithContentDescription(toPebble)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.waitForIdle()
        compose.onNode(versoHeading).assertIsDisplayed()

        compose.onNodeWithContentDescription(toMoment).assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNode(versoHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(toPebble).assertExists()
    }

    @Test
    fun `a drag toward the start edge past the half turns the card`() {
        setCard()

        compose.onNode(hasTestTag(PEBBLE_CARD_TAG)).performTouchInput {
            swipe(Offset(width * 0.9f, height * 0.3f), Offset(width * 0.1f, height * 0.3f), durationMillis = 600)
        }
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `a short drag reverts`() {
        setCard()

        compose.onNode(hasTestTag(PEBBLE_CARD_TAG)).performTouchInput {
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
    fun `the face asked for again turns back a card turned by hand`() {
        var request by mutableIntStateOf(0)
        setCard(request = { request })
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).performClick()
        compose.waitForIdle()
        compose.onNode(versoHeading).assertIsDisplayed()

        // The card pressed again on the Path: the recto again, as a new request.
        request = 1
        compose.waitForIdle()

        compose.onNode(versoHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).assertExists()
    }

    @Test
    fun `a new request for the face already showing turns nothing`() {
        var request by mutableIntStateOf(0)
        setCard(request = { request })
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread {
            request = 1
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(TURN_MS / 2L)

        // No turn ran: the recto was never left, not even part way.
        compose.onNode(versoHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `under reduced motion the button crossfades to the verso`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setCard()

        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).performClick()
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `a face requested during a turn is turned to once the turn lands`() {
        var requested by mutableStateOf(CardFace.RECTO)
        setCard { requested }
        compose.mainClock.autoAdvance = false

        request(CardFace.VERSO) { requested = it }
        // Past the half of that turn: the verso is in front, and the turn still runs.
        compose.mainClock.advanceTimeBy(TURN_MS * 3L / 4)
        compose.onNode(versoHeading).assertExists()
        request(CardFace.RECTO) { requested = it }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNode(versoHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).assertExists()
    }

    @Test
    fun `a face requested while a finger holds the card is turned to once it lets go`() {
        var requested by mutableStateOf(CardFace.VERSO)
        setCard { requested }
        // Turned back to the recto by hand, so the verso stays the answered request.
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_moment)).performClick()
        compose.waitForIdle()
        compose.onNode(versoHeading).assertDoesNotExist()

        // A finger holds the card past the half: the verso is in front, and the drag is not released.
        compose.onNode(hasTestTag(PEBBLE_CARD_TAG)).performTouchInput {
            down(Offset(width * 0.9f, height * 0.3f))
            moveBy(Offset(-width * 0.7f, 0f))
        }
        compose.waitForIdle()
        compose.onNode(versoHeading).assertExists()

        requested = CardFace.RECTO
        compose.mainClock.advanceTimeBy(TURN_MS / 4L)
        // Waiting for the finger: nothing has turned toward the recto yet.
        compose.onNode(versoHeading).assertExists()

        compose.onNode(hasTestTag(PEBBLE_CARD_TAG)).performTouchInput { up() }
        compose.waitForIdle()

        compose.onNode(versoHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).assertExists()
    }

    @Test
    fun `a card turned by hand stays turned across a restore`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Card(CardFace.RECTO) }
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_pebble)).performClick()
        compose.waitForIdle()

        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNode(versoHeading).assertIsDisplayed()
    }

    @Test
    fun `mid-turn the card saves the face the turn lands on, and takes no second turn`() {
        lateinit var turn: CardTurnState
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            turn = remember { CardTurnState(CardFace.RECTO, scope) }
        }
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { assertTrue(turn.turn(reduceMotion = false)) }
        compose.mainClock.advanceTimeBy(TURN_MS / 3L)

        compose.runOnUiThread {
            // Short of the half the recto is still up and in front, but the card is bound for the verso.
            assertEquals(CardFace.RECTO, turn.face)
            assertEquals(CardFace.RECTO, turn.front)
            assertEquals(CardFace.VERSO, with(CardTurnState.saver(scope)) { SaverScope { true }.save(turn) })
            assertFalse(turn.turn(reduceMotion = false))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnUiThread { assertEquals(CardFace.VERSO, turn.face) }
    }

    @Test
    fun `an answered request is not answered again after a restore`() {
        var requested by mutableStateOf(CardFace.RECTO)
        val restore = StateRestorationTester(compose)
        restore.setContent { Card(requested) }
        requested = CardFace.VERSO
        compose.waitForIdle()
        compose.onNode(versoHeading).assertIsDisplayed()
        // Turned back by hand: the verso was asked for, but the reader chose the recto since.
        compose.onNodeWithContentDescription(context.getString(R.string.card_turn_action_to_moment)).performClick()
        compose.waitForIdle()

        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNode(versoHeading).assertDoesNotExist()
    }

    /**
     * Sets the requested face with the clock held: a write from the test thread reaches the
     * composition only once its snapshot is applied, which `waitForIdle` would otherwise do.
     */
    private fun request(
        face: CardFace,
        set: (CardFace) -> Unit,
    ) {
        compose.runOnUiThread {
            set(face)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Composable
    private fun Card(
        requested: CardFace,
        request: Int = 0,
    ) {
        PebblesTheme {
            PebbleCard(
                detail = detail,
                palette = null,
                ratio = null,
                photoUrl = null,
                thumbUrl = null,
                soulsById = emptyMap(),
                domains = emptyList(),
                requestedFace = requested,
                faceRequest = request,
                zone = ZoneOffset.UTC,
            )
        }
    }
}
