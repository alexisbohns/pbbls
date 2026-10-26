package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.ZoneId

private val GLYPH = 14.dp
private const val GLYPHS = 4

/** A list row: tall enough for the stones' rings to clear their neighbours' (iOS `listRow`). */
private val LIST_ROW = 56.dp

/** The list's stones, about the seals' size they replace (iOS `listStone`). */
private const val LIST_STONE = 0.62f

/** The polaroid's box height in the list (iOS `listSnap`). */
private val LIST_SNAP = 40.dp

/** The stone column past the largest stone: room for its rings (iOS `ringRoom`). */
private const val RING_ROOM = 1.3f

/** The stone column's width: past the largest stone, room for its rings. */
private val COLUMN = StoneGeometry.STONE_WIDTH_DP.getValue(ValenceSizeGroup.LARGE) * LIST_STONE * RING_ROOM

/** The milestones' rings, quieter than the Spine's (iOS `MilestoneStone.rings`). */
private const val MILESTONE_RINGS = 0.45f

/**
 * The month's milestones (#1207, D8): the stones the server picked (large first, bright to heavy,
 * then medium), each its lit stone, its date over its name, and its photo as a polaroid or its
 * souls; a tap opens it.
 */
@Composable
internal fun MilestonesSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    zone: ZoneId,
    onOpen: (PathMoment) -> Unit,
    aspect: SnapAspectCache? = null,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.MILESTONES, wrap, lookups)) return
    val moments = MonthSections.milestones(wrap, lookups)
    val untitled = stringResource(R.string.path_spine_untitled)
    Column {
        if (!onCard) MonthSectionHead(MonthSections.head(MonthSection.MILESTONES, wrap, lookups, copy))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            moments.forEach { moment ->
                key(moment.id) {
                    val name = moment.name?.takeIf { it.isNotBlank() } ?: untitled
                    val nameType = EntryTypes.contentsName(moment.valence.intensity, moment.valence.polarity)
                    val date = copy.dateline(moment.happenedAt.atZoneSameInstant(zone).toLocalDate())
                    val souls =
                        moment.pebble
                            ?.souls
                            .orEmpty()
                            .mapNotNull { lookups.soul(it.id) }
                    val stoneWidth = StoneGeometry.STONE_WIDTH_DP.getValue(moment.valence.sizeGroup) * LIST_STONE
                    val seed = remember(moment.pebble?.id, moment.id) { UuidHash.fnv1a(moment.pebble?.id ?: moment.id) }
                    val press = rememberStonePressState()
                    MarkRow(
                        title = name,
                        titleStyle = type.contentsName(nameType.fontWeight, nameType.italic),
                        subtitle = date,
                        subtitleFirst = true,
                        minHeight = LIST_ROW,
                        markWidth = COLUMN.dp,
                        description = "$date, $name",
                        leading = {
                            Box(Modifier.width(COLUMN.dp), contentAlignment = Alignment.Center) {
                                LitStone(
                                    valence = moment.valence,
                                    palette = lookups.paletteFor(moment),
                                    glyph = moment.pebble?.glyph,
                                    seed = seed,
                                    press = press,
                                    width = stoneWidth.dp,
                                    rings = MILESTONE_RINGS,
                                )
                            }
                        },
                        trailing = {
                            val prefix = moment.firstSnapPath
                            if (prefix != null) {
                                MilestoneSnap(prefix, tint = colors.ink3, height = LIST_SNAP, aspect = aspect)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    souls.take(GLYPHS).forEach { SoulMark(it.glyph, colors.ink2, Modifier.size(GLYPH)) }
                                }
                            }
                        },
                        onClick = { onOpen(moment) },
                    )
                }
            }
        }
    }
}
