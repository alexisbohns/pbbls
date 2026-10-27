package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The galley card's room (#1242 D21; iOS `MonthCardFill` and the card's fit): content that asks for the room
 * stands its foot at the room's, content taller than the room is scaled down to it from the top, and every
 * card the fixture deals lays out, which is the guard on "no SubcomposeLayout in a card" (the room asks its
 * content's intrinsics, and a SubcomposeLayout throws on that).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthDeckCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    private fun show(
        height: Dp = HOST_HEIGHT,
        head: MonthHead? = null,
        content: @Composable (room: Dp?) -> Unit,
    ) {
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    Box(Modifier.size(HOST_WIDTH, height)) {
                        MonthDeckCard(MONTH, MonthDeckStep(1, 3), head, content = content)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Where [coordinates]' top and bottom land in the root, through every layer: unclipped, unlike a node's bounds. */
    private fun verticalInRoot(coordinates: LayoutCoordinates): Pair<Dp, Dp> =
        with(compose.density) {
            val top = coordinates.localToRoot(Offset.Zero).y
            val bottom = coordinates.localToRoot(Offset(0f, coordinates.size.height.toFloat())).y
            top.toDp() to bottom.toDp()
        }

    @Test
    fun `content that asks for the room takes it whole, its foot at the room's foot`() {
        var room: Dp? = null
        show { r ->
            room = r
            Column(Modifier.fillMaxHeight()) {
                Text("top")
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(10.dp).testTag(FOOT))
            }
        }
        assertEquals(ROOM.value, room!!.value, 0.5f)
        assertEquals(
            ROOM_BOTTOM.value,
            compose
                .onNodeWithTag(FOOT)
                .getBoundsInRoot()
                .bottom.value,
            0.5f,
        )
    }

    @Test
    fun `content taller than the room is scaled down to it, anchored at the top`() {
        var content: LayoutCoordinates? = null
        show { Box(Modifier.fillMaxWidth().height(TALL).onGloballyPositioned { content = it }) }
        val (top, bottom) = verticalInRoot(content!!)
        assertEquals(ROOM_TOP.value, top.value, 0.5f)
        assertEquals(ROOM.value, (bottom - top).value, 0.5f)
    }

    @Test
    fun `an empty room is none, and leaves the content whole, at its own size`() {
        var content: LayoutCoordinates? = null
        var room: Dp? = TALL
        // The paper's inset takes the whole host: the room is 0 tall.
        show(height = ROOM_TOP + INSET_BOTTOM) { r ->
            room = r
            Box(Modifier.fillMaxWidth().height(TALL).onGloballyPositioned { content = it })
        }
        assertNull(room)
        val (top, bottom) = verticalInRoot(content!!)
        assertEquals(ROOM_TOP.value, top.value, 0.5f)
        assertEquals(TALL.value, (bottom - top).value, 0.5f)
    }

    @Test
    fun `every card the fixture deals lays out within its room, the cover and each section on a card`() {
        val cards = MonthDeck.cards(wrap, lookups)
        val dealt = cards.filter { it != IntroSequence.End }
        var shown by mutableStateOf(dealt.first())
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    Box(Modifier.size(HOST_WIDTH, HOST_HEIGHT)) {
                        val copy = rememberWrapCopy()
                        val card = shown
                        val leads = MonthDeck.leadsInsights(card, cards)
                        val head =
                            (card as? IntroSequence.Section)?.let {
                                if (leads) MonthSections.insightsHead(copy) else MonthSections.head(it.section, wrap, lookups, copy)
                            }
                        MonthDeckCard(MONTH, MonthDeckStep.of(card, cards), head) { room ->
                            Box(Modifier.testTag(CONTENT)) {
                                when (card) {
                                    IntroSequence.Cover -> MonthCover(wrap, lookups)
                                    is IntroSequence.Section ->
                                        MonthSectionContent(
                                            card.section,
                                            wrap,
                                            lookups,
                                            DayView.CIRCLE,
                                            SpineFixtureWeek.zone,
                                            aspect = null,
                                            onWeek = {},
                                            onOpen = {},
                                            onMore = {},
                                            leadsInsights = leads,
                                            onCard = true,
                                            room = room,
                                        )
                                    IntroSequence.End -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(cards.size - 1, dealt.size)
        dealt.forEach { card ->
            compose.runOnIdle { shown = card }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
            assertTrue("$card draws nothing", bounds.bottom > bounds.top)
            assertTrue("$card overruns its room: ${bounds.bottom}", bounds.bottom.value <= ROOM_BOTTOM.value + 0.5f)
        }
    }

    @Test
    fun `on a card the persons share the room, more at its foot`() {
        var room: Dp? = null
        var more = ""
        show(head = MonthHead("Persons", null)) { r ->
            room = r
            more = rememberWrapCopy().morePersons(MonthSections.namedPersons(wrap, lookups).size)
            MonthSectionContent(
                MonthSection.PERSONS,
                wrap,
                lookups,
                DayView.CIRCLE,
                SpineFixtureWeek.zone,
                aspect = null,
                onWeek = {},
                onOpen = {},
                onMore = {},
                onCard = true,
                room = r,
            )
        }
        val first = MonthSections.namedPersons(wrap, lookups).first()
        val name = lookups.soul(first.soulId)!!.name
        val row = compose.onNodeWithContentDescription("$name, ${first.count}", substring = true).getBoundsInRoot()
        val rows = minOf(5, MonthSections.namedPersons(wrap, lookups).size)
        assertEquals(PersonsSection.rowSize(room!!, rows).height.value, (row.bottom - row.top).value, 0.5f)
        assertEquals(
            ROOM_BOTTOM.value,
            compose
                .onNodeWithText(more)
                .getBoundsInRoot()
                .bottom.value,
            0.5f,
        )
    }

    @Test
    fun `on a card the milestones share the room, each row its share`() {
        var room: Dp? = null
        var copy: WrapCopy? = null
        show(head = MonthHead("Milestones", null)) { r ->
            room = r
            copy = rememberWrapCopy()
            MonthSectionContent(
                MonthSection.MILESTONES,
                wrap,
                lookups,
                DayView.CIRCLE,
                SpineFixtureWeek.zone,
                aspect = null,
                onWeek = {},
                onOpen = {},
                onMore = {},
                onCard = true,
                room = r,
            )
        }
        val moments = MonthSections.milestones(wrap, lookups)
        val first = moments.first()
        val date = copy!!.dateline(first.happenedAt.atZoneSameInstant(SpineFixtureWeek.zone).toLocalDate())
        val row = compose.onNodeWithContentDescription("$date, ${first.name}").getBoundsInRoot()
        val size = MilestonesSection.rowSize(room!!, moments.size)
        assertTrue(size.height > MilestonesSection.LIST_ROW)
        assertEquals(size.height.value, (row.bottom - row.top).value, 0.5f)
    }

    @Test
    fun `on a card the weeks stand as a column each, one button saying its week, a tap focusing it`() {
        var focused: LocalDate? = null
        var copy: WrapCopy? = null
        show(head = MonthHead("Weeks", null)) { r ->
            copy = rememberWrapCopy()
            MonthSectionContent(
                MonthSection.WEEKS,
                wrap,
                lookups,
                DayView.CIRCLE,
                SpineFixtureWeek.zone,
                aspect = null,
                onWeek = { focused = it },
                onOpen = {},
                onMore = {},
                onCard = true,
                room = r,
            )
        }
        val words = copy!!
        val weeks = WeeksSummary.weeks(wrap)
        val descriptions =
            weeks.map { week ->
                val empty = week.pebbles == 0
                val line = if (empty) words.emptyWeek() else words.line(week.pebbles, week.soulIds.size, live = false, hasMoments = true)
                "${words.range(wrap.month, week.from, week.to)}, $line"
            }
        val widths =
            descriptions.map { description ->
                val column = compose.onNodeWithContentDescription(description)
                column.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                val bounds = column.getBoundsInRoot()
                bounds.right - bounds.left
            }
        // Columns, not rows: the weeks share the card's width, each as wide as the next.
        assertTrue("the columns overrun the card: $widths", widths.fold(0.dp) { sum, w -> sum + w } <= HOST_WIDTH)
        assertTrue("the columns differ: $widths", widths.max() - widths.min() <= 1.dp)
        // A column is one node: its Monday's number and month speak through it, the weekdays' letters not at all
        // (the merged tree, as TalkBack reads it; the unmerged one keeps a cleared node's children).
        compose.onAllNodesWithText("28").assertCountEquals(0)
        compose.onAllNodesWithText(words.weekColumnMonth(weeks.first().weekStart)).assertCountEquals(0)
        compose.onAllNodesWithText("m").assertCountEquals(0)
        compose.onNodeWithContentDescription(descriptions[1]).performClick()
        assertEquals(weeks[1].weekStart, focused)
    }

    private companion object {
        const val MONTH = "October 2026"
        const val FOOT = "foot"
        const val CONTENT = "content"
        val HOST_WIDTH = 390.dp
        val HOST_HEIGHT = 600.dp
        val TALL = 2000.dp

        /** With no head, the room is the paper inside its [MonthDeckLayout.INSET]. */
        val ROOM_TOP = MonthDeckLayout.INSET.calculateTopPadding()
        val INSET_BOTTOM = MonthDeckLayout.INSET.calculateBottomPadding()
        val ROOM_BOTTOM = HOST_HEIGHT - INSET_BOTTOM
        val ROOM = ROOM_BOTTOM - ROOM_TOP
    }
}
