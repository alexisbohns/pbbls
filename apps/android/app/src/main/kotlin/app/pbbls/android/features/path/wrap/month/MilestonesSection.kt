package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.ZoneId

private val GLYPH = 14.dp
private const val GLYPHS = 4

/**
 * The month's milestones (#1207, D8): the stones the server picked (large first, bright to heavy,
 * then medium), each a seal, its name weighted by intensity, its date and its souls; a tap opens it.
 */
@Composable
internal fun MilestonesSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    zone: ZoneId,
    onOpen: (PathMoment) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val moments = wrap.milestones.orEmpty().mapNotNull(lookups.moment)
    if (moments.isEmpty()) return
    val untitled = stringResource(R.string.path_spine_untitled)
    Column {
        MonthSectionHead(copy.milestonesHeader(moments.size), copy.milestonesHint())
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            moments.forEach { moment ->
                val name = moment.name?.takeIf { it.isNotBlank() } ?: untitled
                val nameType = EntryTypes.contentsName(moment.valence.intensity, moment.valence.polarity)
                val date = copy.dateline(moment.happenedAt.atZoneSameInstant(zone).toLocalDate())
                val souls =
                    moment.pebble
                        ?.souls
                        .orEmpty()
                        .mapNotNull { lookups.soul(it.id) }
                MarkRow(
                    title = name,
                    titleStyle = type.contentsName(nameType.fontWeight, nameType.italic),
                    subtitle = date,
                    description = "$name, $date",
                    leading = {
                        StoneSeal(
                            moment.valence,
                            lookups.paletteFor(moment),
                            moment.pebble?.glyph,
                            colors.ground,
                            size = SealSize.MILESTONE,
                        )
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            souls.take(GLYPHS).forEach { SoulMark(it.glyph, colors.ink2, Modifier.size(GLYPH)) }
                        }
                    },
                    onClick = { onOpen(moment) },
                )
            }
        }
    }
}
