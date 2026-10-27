package app.pbbls.android.features.path.wrap.month

import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroMilestoneShape
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroTick
import app.pbbls.android.features.path.wrap.intro.milestones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.milestonesIntro` and `.milestonesFill` (#1241 D10, D13, D16). */
class MilestonesSectionTest {
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `the milestones' intro stamps each stone at its pebble's intensity, then its date, name and souls`() {
        val beats = MilestonesSection.introBeats(wrap, lookups)
        val moments = MonthSections.milestones(wrap, lookups)
        assertTrue(moments.isNotEmpty())
        moments.forEachIndexed { row, moment ->
            val impact = beats.first { it.key == IntroKey.Impact(row) }
            assertEquals(IntroTick.seal(moment.pebble?.intensity ?: 1), impact.tick)
            val date = beats.first { it.key == IntroKey.Date(row) }
            val name = beats.first { it.key == IntroKey.Name(row) }
            assertTrue(date.at < name.at)
        }
    }

    @Test
    fun `a milestone plays its photo's card as one mark, else the souls the row draws, four at most`() {
        val shapes =
            MonthSections.milestones(wrap, lookups).map { moment ->
                val souls =
                    moment.pebble
                        ?.souls
                        .orEmpty()
                        .count { lookups.soul(it.id) != null }
                IntroMilestoneShape(moment.pebble?.intensity ?: 1, if (moment.firstSnapPath == null) minOf(souls, 4) else 1)
            }
        assertEquals(IntroScore.milestones(shapes), MilestonesSection.introBeats(wrap, lookups))
    }

    @Test
    fun `the deck's milestones card plays the milestones' stamps`() {
        val card = IntroSequence.Section(MonthSection.MILESTONES)
        assertEquals(MilestonesSection.introBeats(wrap, lookups), MonthDeck.beats(card, wrap, lookups, leadsInsights = false))
    }

    @Test
    fun `on a card the milestones share its room and their stones grow to the Spine's own, the list keeps its size`() {
        val list = MilestonesSection.rowSize(room = null, rows = 5)
        assertEquals(56f, list.height.value, 0f)
        assertEquals(MilestonesSection.LIST_ROW.value, list.height.value, 0f)
        assertEquals(MilestonesSection.LIST_STONE, list.stone, 0f)
        assertEquals(0.62f, list.stone, 0f)
        assertEquals(MilestonesSection.LIST_SNAP.value, list.snap.value, 0f)
        assertEquals(40f, list.snap.value, 0f)
        assertEquals(14f, list.soul.value, 0f)

        val card = MilestonesSection.rowSize(room = 400.dp, rows = 5)
        assertEquals(80f, card.height.value, 1e-4f)
        // stone = clamp(height / 100, 0.62, 1); snap = clamp(height × 0.7, 40, 76); soul = min(14 × stone / 0.62, 20).
        assertEquals(0.8f, card.stone, 1e-5f)
        assertEquals(56f, card.snap.value, 1e-4f)
        assertEquals(14f * 0.8f / 0.62f, card.soul.value, 1e-4f)
        assertTrue(card.stone > list.stone && card.snap > list.snap && card.soul > list.soul)

        val roomy = MilestonesSection.rowSize(room = 900.dp, rows = 2)
        assertEquals(MilestonesSection.ROW_CAP.value, roomy.height.value, 0f)
        assertEquals(104f, roomy.height.value, 0f)
        assertEquals(1f, roomy.stone, 0f)
        assertEquals(104f * 0.7f, roomy.snap.value, 1e-4f)
        assertTrue(roomy.snap <= MilestonesSection.SNAP_CAP)
        assertEquals(MilestonesSection.SOUL_CAP.value, roomy.soul.value, 0f)
        assertTrue(roomy.soul.value <= 20f)

        assertEquals(MilestonesSection.LIST_ROW.value, MilestonesSection.rowSize(room = 100.dp, rows = 5).height.value, 0f)
        // No rows divides the room by one, never by zero.
        assertEquals(MilestonesSection.ROW_CAP.value, MilestonesSection.rowSize(room = 300.dp, rows = 0).height.value, 0f)
    }
}
