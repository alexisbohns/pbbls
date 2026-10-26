package app.pbbls.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroStage
import app.pbbls.android.features.path.wrap.intro.LocalIntroStage
import app.pbbls.android.features.path.wrap.intro.gemRuns
import app.pbbls.android.features.path.wrap.month.EmotionGem
import app.pbbls.android.features.path.wrap.month.MonthCover
import app.pbbls.android.features.path.wrap.month.MonthDeck
import app.pbbls.android.features.path.wrap.month.MonthDeckCard
import app.pbbls.android.features.path.wrap.month.MonthDeckStep
import app.pbbls.android.features.path.wrap.month.MonthSectionContent
import app.pbbls.android.features.path.wrap.month.MonthSections
import app.pbbls.android.features.path.wrap.month.PersonsSection
import app.pbbls.android.features.path.wrap.month.coverBeats
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import com.android.tools.screenshot.PreviewTest

/** The month's intro, frozen mid-play (#1242; iOS `intro-garland-half`): the cover at 1150 ms, unscaled. */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun IntroGarlandHalf() {
    PebblesTheme {
        val wrap = SpineFixtureWeek.monthWrap
        val clock = IntroClock.frozen(wrap.month, IntroSequence.Cover, coverBeats(wrap, SpineFixtureWeek.lookups), elapsed = 1.15)
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.width(390.dp)) {
                CompositionLocalProvider(LocalIntroStage provides IntroStage(clock, IntroSequence.Cover)) {
                    MonthCover(wrap, SpineFixtureWeek.lookups)
                }
            }
        }
    }
}

/**
 * The persons' entrance, frozen mid-play (#1242; iOS `intro-persons-ring`): the first row's glyph and name in,
 * its ring half drawn from the 1st with the days it has reached; the rows after it still to come.
 */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun IntroPersonsRing() {
    PebblesTheme {
        val wrap = SpineFixtureWeek.monthWrap
        val lookups = SpineFixtureWeek.lookups
        val sequence = IntroSequence.Section(MonthSection.PERSONS)
        // Row 0's ring starts two part gaps after the rows; half its span in, the line is half way round.
        val elapsed = IntroScore.ROWS_AT + 2 * IntroScore.PART_GAP + IntroScore.RING_SPAN / 2
        val clock = IntroClock.frozen(wrap.month, sequence, PersonsSection.introBeats(wrap, lookups), elapsed)
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.width(390.dp)) {
                CompositionLocalProvider(LocalIntroStage provides IntroStage(clock, sequence)) {
                    PersonsSection(wrap, lookups, DayView.CIRCLE, onMore = {})
                }
            }
        }
    }
}

/**
 * The gem's entrance, frozen mid-play (#1242; iOS `intro-gem-second-family`): the stone landed and counted, the lead
 * family lit and named, the second half lit as its middle facet starts, and the families after it still blank.
 */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun IntroGemSecondFamily() {
    PebblesTheme {
        val wrap = SpineFixtureWeek.monthWrap
        val sequence = IntroSequence.Section(MonthSection.GEM)
        val beats = EmotionGem.introBeats(wrap)
        val gem = requireNotNull(wrap.gem)
        // The second family's middle facet, as it starts to light.
        val second = IntroScore.gemRuns(gem.lead?.order.orEmpty(), gem.facets)[1].facets
        val elapsed = beats.first { it.key == IntroKey.Facet(second[second.size / 2]) }.at
        val clock = IntroClock.frozen(wrap.month, sequence, beats, elapsed)
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.width(390.dp)) {
                CompositionLocalProvider(LocalIntroStage provides IntroStage(clock, sequence)) {
                    EmotionGem(wrap, SpineFixtureWeek.lookups, onMore = {})
                }
            }
        }
    }
}

/** A card of the month's deck on its page (#1242 D21), settled: no clock. */
@Composable
private fun DeckPage(content: @Composable () -> Unit) {
    PebblesTheme {
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.size(390.dp, 760.dp)) { content() }
        }
    }
}

/** The fixture's cards, as the deck deals them: the stepper counts them. */
private val deckCards get() = MonthDeck.cards(SpineFixtureWeek.monthWrap, SpineFixtureWeek.lookups)

/** The deck's cover card: the margin with the month upright, the stepper on its first segment, the cover in the room. */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
fun MonthDeckCover() {
    DeckPage {
        MonthDeckCard(
            month = "October 2026",
            step = MonthDeckStep.of(IntroSequence.Cover, deckCards),
            head = null,
        ) {
            MonthCover(SpineFixtureWeek.monthWrap, SpineFixtureWeek.lookups)
        }
    }
}

/** The deck's weeks card: its head in the title band, the weeks under it, the stepper on its second segment. */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
fun MonthDeckSection() {
    DeckPage {
        val wrap = SpineFixtureWeek.monthWrap
        val lookups = SpineFixtureWeek.lookups
        MonthDeckCard(
            month = "October 2026",
            step = MonthDeckStep.of(IntroSequence.Section(MonthSection.WEEKS), deckCards),
            head = MonthSections.head(MonthSection.WEEKS, wrap, lookups, rememberWrapCopy()),
        ) {
            MonthSectionContent(
                MonthSection.WEEKS,
                wrap,
                lookups,
                DayView.CIRCLE,
                SpineFixtureWeek.zone,
                aspect = null,
                onWeek = {},
                onOpen = {},
                onMore = {},
                onCard = true,
            )
        }
    }
}
