package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.gem
import app.pbbls.android.features.path.wrap.intro.gemRuns
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.week.familyNames
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The month's emotions as a gem (#1207, D10): the lead sentence over the server's lead, the stone
 * coloured by the server's facet allocation, each family named at its run with its share, and a
 * *more* to the fine emotions in the month sheet.
 *
 * In the month intro (#1242) the lead rises, the blank stone lands with its count running up to the
 * month's pebbles, then the families light in turn, each named with its first facet. On a card ([room],
 * the card's room under its title band) *more* stands at its foot (D13); on the page, [room] is null.
 */
@Composable
internal fun EmotionGem(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
    room: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    if (!MonthSections.isShown(MonthSection.GEM, wrap, lookups)) return
    val gem = requireNotNull(wrap.gem)
    val order = gem.lead?.order.orEmpty()
    val names = familyNames(order, lookups).map { it.lowercase() }
    val zero = familyNames(gem.lead?.zero.orEmpty(), lookups).map { it.lowercase() }
    val level = order.size > 1 && abs((gem.worths[order[0]] ?: 0.0) - (gem.worths[order[1]] ?: 0.0)) < 1e-9
    val labels = order.zip(names).map { (slug, name) -> GemLabel(slug, name, ((gem.shares[slug] ?: 0.0) * 100).roundToInt()) }
    // On a card the section takes the room, so its weighted spacer can stand *more* at the foot.
    Column(if (room != null) Modifier.fillMaxHeight() else Modifier) {
        if (!onCard) MonthSectionHead(MonthSections.head(MonthSection.GEM, wrap, lookups, copy))
        Text(
            copy.gemLead(names, zero, level),
            style = type.lead,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = SECTION_PAD_X).introReveal(reveal, IntroKey.Lead),
        )
        GemCanvas(
            facets = gem.facets,
            paletteOf = { slug -> lookups.family(slug)?.palette },
            labels = labels,
            count = wrap.pebbles,
            countLabel = copy.gemPebbles(wrap.pebbles),
            description = copy.gemDescription(labels.map { it.name to it.percent }),
            reveal = reveal,
        )
        if (room != null) Spacer(Modifier.weight(1f))
        MonthMore(copy.moreEmotions(wrap.fineEmotions.orEmpty().size), Modifier.introReveal(reveal, IntroKey.More)) {
            onMore(MonthSection.GEM)
        }
    }
}

/** The gem's pure parts (#1242; iOS `EmotionGem`'s statics). */
internal object EmotionGem {
    /** The gem's intro: the count, then the families in the lead's order, each with the facets the server gave it. */
    fun introBeats(wrap: MonthWrap): List<IntroBeat> {
        val gem = wrap.gem ?: return emptyList()
        return IntroScore.gem(IntroScore.gemRuns(gem.lead?.order.orEmpty(), gem.facets), wrap.pebbles)
    }
}
