package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.familyNames
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The month's emotions as a gem (#1207, D10): the lead sentence over the server's lead, the stone
 * coloured by the server's facet allocation, each family named at its run with its share, and a
 * *more* to the fine emotions in the month sheet.
 */
@Composable
internal fun EmotionGem(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.GEM, wrap, lookups)) return
    val gem = requireNotNull(wrap.gem)
    val order = gem.lead?.order.orEmpty()
    val names = familyNames(order, lookups).map { it.lowercase() }
    val zero = familyNames(gem.lead?.zero.orEmpty(), lookups).map { it.lowercase() }
    val level = order.size > 1 && abs((gem.worths[order[0]] ?: 0.0) - (gem.worths[order[1]] ?: 0.0)) < 1e-9
    val labels = order.zip(names).map { (slug, name) -> GemLabel(slug, name, ((gem.shares[slug] ?: 0.0) * 100).roundToInt()) }
    Column {
        if (!onCard) MonthSectionHead(copy.tile(WeekTile.EMOTIONS), copy.gemHint())
        Text(
            copy.gemLead(names, zero, level),
            style = type.lead,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = SECTION_PAD_X),
        )
        GemCanvas(
            facets = gem.facets,
            paletteOf = { slug -> lookups.family(slug)?.palette },
            labels = labels,
            count = wrap.pebbles.toString(),
            countLabel = copy.gemPebbles(wrap.pebbles),
            description = copy.gemDescription(labels.map { it.name to it.percent }),
        )
        MonthMore(copy.moreEmotions(wrap.fineEmotions.orEmpty().size)) { onMore(MonthSection.GEM) }
    }
}
