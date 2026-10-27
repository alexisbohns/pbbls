package app.pbbls.android.features.path.wrap.month

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroMilestoneShape
import app.pbbls.android.features.path.wrap.intro.IntroMotion
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroStage
import app.pbbls.android.features.path.wrap.intro.IntroTempo
import app.pbbls.android.features.path.wrap.intro.IntroTick
import app.pbbls.android.features.path.wrap.intro.milestones
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS `MonthCardsTests.milestonesIntro` and `.milestonesFill` (#1241 D10, D13, D16), and the gate on a stone's
 * strike (iOS `MilestoneStone`: it ripples only as its stamp lands while its card plays).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MilestonesSectionTest {
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups
    private val card = IntroSequence.Section(MonthSection.MILESTONES)
    private val base = 1_000_000_000_000L

    /** The first stone's impact, at tempo 1. */
    private val impact = IntroScore.ROWS_AT + IntroMotion.STAMP_IMPACT * IntroScore.STAMP_SPAN

    /** [seconds] into the card, which starts its lead after it is shown on an active page. */
    private fun after(seconds: Double) = base + IntroClock.LEAD_NANOS + (seconds * 1e9).toLong()

    /** The milestones card on top of a clock at tempo 1, waiting for the page to be active. */
    private fun stage(ledger: IntroLedger = IntroLedger()): IntroStage {
        val clock = IntroClock(wrap.month, ledger, null, IntroTempo(cover = 1.0, sections = 1.0), now = { base })
        clock.register(card) { MilestonesSection.introBeats(wrap, lookups) }
        clock.show(card)
        return IntroStage(clock, card)
    }

    /** The first stone's strikes, collected while [drive] runs the card; the snapshot's changes are sent after it, as a frame would. */
    private fun TestScope.strikes(
        stage: IntroStage,
        drive: (step: () -> Unit) -> Unit,
    ): Int {
        val struck = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { stage.strikes(0).toList(struck) }
        val step = {
            Snapshot.sendApplyNotifications()
            runCurrent()
        }
        drive(step)
        step()
        return struck.size
    }

    @Test
    fun `a stone strikes once, as its stamp meets the paper while its card plays`() =
        runTest {
            val stage = stage()
            val strikes =
                strikes(stage) { step ->
                    assertEquals(IntroClock.Phase.Waiting, stage.clock.phase(card))
                    stage.clock.isActive = true
                    step()
                    stage.clock.frame(after(impact - 0.01))
                    step()
                    stage.clock.frame(after(impact + 0.01))
                    step()
                    assertTrue(stage.clock.isPlaying(card))
                    stage.clock.frame(after(impact + 0.3))
                    step()
                    // The card lands at its end, every stone struck: none strikes again.
                    stage.clock.frame(after(60.0))
                    step()
                    assertEquals(IntroClock.Phase.Settled, stage.clock.phase(card))
                }
            assertEquals(1, strikes)
        }

    @Test
    fun `a card composed settled never strikes`() =
        runTest {
            val ledger = IntroLedger().apply { record(wrap.month, card) }
            val stage = stage(ledger)
            val strikes =
                strikes(stage) { step ->
                    stage.clock.isActive = true
                    step()
                    stage.clock.frame(after(impact + 0.01))
                    step()
                    assertEquals(IntroClock.Phase.Settled, stage.clock.phase(card))
                }
            assertEquals(0, strikes)
        }

    @Test
    fun `a row that comes once its stone has struck strikes no more, though the card still plays`() =
        runTest {
            val stage = stage()
            stage.clock.isActive = true
            stage.clock.frame(after(impact + 0.01))
            Snapshot.sendApplyNotifications()
            // The row is composed again here (a refresh shifts it), its stone already struck.
            val strikes = strikes(stage) { _ -> assertTrue(stage.clock.isPlaying(card)) }
            assertEquals(0, strikes)
        }

    @Test
    fun `a skip before the impact lands every stone and strikes none`() =
        runTest {
            val stage = stage()
            val strikes =
                strikes(stage) { step ->
                    stage.clock.isActive = true
                    step()
                    stage.clock.frame(after(impact - 0.1))
                    step()
                    stage.clock.skip()
                    step()
                    assertEquals(IntroClock.Phase.Settled, stage.clock.phase(card))
                    assertEquals(1f, stage.reveal(IntroKey.Impact(0)))
                    stage.clock.frame(after(impact + 0.01))
                    step()
                }
            assertEquals(0, strikes)
        }

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
