package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.R
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StoneRipple
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroMilestoneShape
import app.pbbls.android.features.path.wrap.intro.IntroMotion
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroStage
import app.pbbls.android.features.path.wrap.intro.LocalIntroStage
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.intro.milestones
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.floor

/** The stone column past the largest stone: room for its rings (iOS `ringRoom`). */
private const val RING_ROOM = 1.3f

/** The milestones' halo, rings and ripple, quieter than the Spine's (iOS `MilestoneStone.rings`). */
private const val MILESTONE_RINGS = 0.45f

/**
 * The month's milestones (#1207, D8): the stones the server picked (large first, bright to heavy,
 * then medium), each its lit stone, its date over its name, and its photo as a polaroid or its
 * souls; a tap opens it.
 *
 * In the month intro (#1242; iOS D10) each stone stamps in and strikes the paper, then its date rises, its name,
 * and its photo's card or its souls pop one by one. On a card ([room], the card's room under its title band) the
 * rows share the room, the stones, the polaroids and the souls growing with them (D13); on the page, [room] is
 * null and the rows keep their size.
 */
@Composable
internal fun MilestonesSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    zone: ZoneId,
    onOpen: (PathMoment) -> Unit,
    aspect: SnapAspectCache? = null,
    onCard: Boolean = false,
    room: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    if (!MonthSections.isShown(MonthSection.MILESTONES, wrap, lookups)) return
    val moments = MonthSections.milestones(wrap, lookups)
    val untitled = stringResource(R.string.path_spine_untitled)
    val size = MilestonesSection.rowSize(room, moments.size)
    // On a card, each row's share in whole pixels, floored: rounded, the rows could overrun the room by up to
    // half a pixel each and tip the card into scaling its content down.
    val density = LocalDensity.current
    val rowHeight = if (room == null) size.height else with(density) { floor(size.height.toPx()).toInt().toDp() }
    // The stone column: past the largest stone, room for its rings.
    val column = (StoneGeometry.STONE_WIDTH_DP.getValue(ValenceSizeGroup.LARGE) * size.stone * RING_ROOM).dp
    Column {
        if (!onCard) MonthSectionHead(MonthSections.head(MonthSection.MILESTONES, wrap, lookups, copy))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            moments.forEachIndexed { index, moment ->
                key(moment.id) {
                    // Settled (the page, or no clock), every part takes the core defaults: the row draws as ever.
                    val row = index.takeIf { reveal !== IntroReveal.settled }
                    val dateKey = remember(row) { row?.let(IntroKey::Date) }
                    val name = moment.name?.takeIf { it.isNotBlank() } ?: untitled
                    val nameType = EntryTypes.contentsName(moment.valence.intensity, moment.valence.polarity)
                    val date = copy.dateline(moment.happenedAt.atZoneSameInstant(zone).toLocalDate())
                    MarkRow(
                        title = name,
                        titleStyle = type.contentsName(nameType.fontWeight, nameType.italic),
                        subtitle = date,
                        subtitleFirst = true,
                        minHeight = rowHeight,
                        markWidth = column,
                        description = "$date, $name",
                        leading = { MilestoneStone(moment, lookups.paletteFor(moment), row, size.stone, column, reveal) },
                        trailing = {
                            val prefix = moment.firstSnapPath
                            if (prefix != null) {
                                MilestoneSnap(
                                    prefix,
                                    tint = colors.ink3,
                                    height = size.snap,
                                    aspect = aspect,
                                    modifier = Modifier.introReveal(reveal, row?.let { IntroKey.Mark(it, 0) }, IntroLanding.POP),
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    MilestonesSection.souls(moment, lookups).forEachIndexed { k, soul ->
                                        val pop = Modifier.introReveal(reveal, row?.let { IntroKey.Mark(it, k) }, IntroLanding.POP)
                                        SoulMark(soul.glyph, colors.ink2, pop.size(size.soul))
                                    }
                                }
                            }
                        },
                        onClick = { onOpen(moment) },
                        // The date over the name (D12), landing apart: the date, then the name.
                        titleModifier = Modifier.introReveal(reveal, row?.let(IntroKey::Name)),
                        subtitleModifier = Modifier.introReveal(reveal, dateKey),
                        // The rule fades in with the date (iOS: the rule's `.fade` on the row's text key).
                        ruleAlpha = if (dateKey == null) ({ 1f }) else ({ RevealMotion.easeOut(reveal(dateKey).toDouble()).toFloat() }),
                    )
                }
            }
        }
    }
}

/**
 * A milestone's own stone (#1242; iOS `MilestoneStone`, D16): the Spine's lit stone with its halo and rings
 * quieter, in a column [column] wide. In the intro ([row] set) it stamps in on its `Stamp`, and as the stamp
 * meets the paper on its `Impact` it is pressed once, rippling, at the stone's own pace whatever the intro's
 * tempo. Only while its card plays: a card dealt settled, revisited or skipped lands every stone at once, and
 * none ripples. Under reduced motion the press is the still one, with no ripple, as on the Spine.
 */
@Composable
private fun MilestoneStone(
    moment: PathMoment,
    palette: EmotionPalette?,
    row: Int?,
    scale: Float,
    column: Dp,
    reveal: IntroReveal,
) {
    val valence = moment.valence
    val seed = remember(moment.pebble?.id, moment.id) { UuidHash.fnv1a(moment.pebble?.id ?: moment.id) }
    val press = rememberStonePressState()
    val width = (StoneGeometry.STONE_WIDTH_DP.getValue(valence.sizeGroup) * scale).dp
    val stage = LocalIntroStage.current
    // The press runs in the stone's own scope, not the strike's: the strike restarts with a new stage (a
    // Replay's clock) or a row shifted by a refresh, and a press cut short would leave the rings swollen.
    val pressScope = rememberCoroutineScope()
    if (row != null && stage != null) {
        val reduceMotion by rememberUpdatedState(rememberReduceMotion())
        LaunchedEffect(stage, row) {
            stage.strikes(row).collect { pressScope.launch { press.press(reduceMotion) } }
        }
    }
    Box(Modifier.width(column), contentAlignment = Alignment.Center) {
        // The ripple under the stone, overflowing freely, as quiet as its rings.
        press.rippleToken?.let { token ->
            StoneRipple(
                valence = valence,
                blank = palette == null,
                token = token,
                onDone = press::rippleDone,
                modifier = QUIETER,
                width = width,
            )
        }
        LitStone(
            valence = valence,
            palette = palette,
            glyph = moment.pebble?.glyph,
            seed = seed,
            press = press,
            modifier = Modifier.stamp(reveal, row),
            width = width,
            rings = MILESTONE_RINGS,
        )
    }
}

/**
 * Each strike of [row]'s stone on the paper while its card plays (iOS `MilestoneStone`'s `onChange`): its
 * `Impact` reaching 1, never the value it held when collected, so a stone already struck when the row came
 * strikes no more; and only while the card plays, so a skip, or a throw away, which lands every stone at
 * once, strikes none. `snapshotFlow` emits only a changed value.
 */
internal fun IntroStage.strikes(row: Int): Flow<Unit> {
    val impact = IntroKey.Impact(row)
    return snapshotFlow { reveal(impact) >= 1f }
        .drop(1)
        .filter { it }
        .filter { clock.isPlaying(sequence) }
        .map { }
}

/**
 * The ripple at the rings' strength (LitStone's KDoc), its alpha applied to each draw: an offscreen layer
 * would clip the waves to the stone's box.
 */
private val QUIETER = Modifier.graphicsLayer(alpha = MILESTONE_RINGS, compositingStrategy = CompositingStrategy.ModulateAlpha)

/**
 * The stamp on [row]'s `Stamp` (iOS `MilestoneStone`): down from past its size to the paper, a slight squash,
 * back; there by its impact. Only below 1; with no [row], no layer.
 */
private fun Modifier.stamp(
    reveal: IntroReveal,
    row: Int?,
): Modifier {
    if (row == null) return this
    val key = IntroKey.Stamp(row)
    return graphicsLayer {
        val p = reveal(key).toDouble()
        if (p < 1) {
            val scale = IntroMotion.stampScale(p).toFloat()
            scaleX = scale
            scaleY = scale
            alpha = IntroMotion.stampOpacity(p).toFloat()
        }
        // The halo, the rings and the shadow draw past the stone's box; an offscreen layer (alpha < 1) would
        // clip them to it. ModulateAlpha applies the alpha per draw, as the rings' own layer does.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** The milestones' pure parts (#1242; iOS `MilestonesSection`'s statics). */
internal object MilestonesSection {
    /** A list row: tall enough for the stones' rings to clear their neighbours' (iOS `listRow`). */
    val LIST_ROW = 56.dp

    /** A milestone's row on a card: its share of the room, within the list row and this (D13). */
    val ROW_CAP = 104.dp

    /** The list's stones, about the seals' size they replace; on a card they grow to the Spine's own. */
    const val LIST_STONE = 0.62f

    /** The polaroid's box height in the list, and its cap on a card. */
    val LIST_SNAP = 40.dp
    val SNAP_CAP = 76.dp

    /** A soul's glyph side in the list (iOS `SoulMarks.side`), and its cap on a card (iOS `WeeksSummary.soulCap`). */
    val SOUL = 14.dp
    val SOUL_CAP = 20.dp

    /** A milestone's souls, at most. */
    const val SOUL_LIMIT = 4

    /** A milestone's row: its height, its stone's scale, its polaroid's box height and its souls' glyph side. */
    data class RowSize(
        val height: Dp,
        val stone: Float,
        val snap: Dp,
        val soul: Dp,
    )

    /** A milestone's souls the row draws: those the device knows, in the server's order, at most [SOUL_LIMIT]. */
    fun souls(
        moment: PathMoment,
        lookups: WrapLookups,
    ): List<SoulWithGlyph> =
        moment.pebble
            ?.souls
            .orEmpty()
            .mapNotNull { lookups.soul(it.id) }
            .take(SOUL_LIMIT)

    /**
     * The milestones' intro (iOS D10): the head, then each stone stamped, ticking at its pebble's intensity, its
     * date, its name, and its photo's card or its souls one by one.
     */
    fun introBeats(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroBeat> =
        IntroScore.milestones(
            MonthSections.milestones(wrap, lookups).map { moment ->
                IntroMilestoneShape(
                    intensity = moment.pebble?.intensity ?: 1,
                    souls = if (moment.firstSnapPath == null) souls(moment, lookups).size else 1,
                )
            },
        )

    /**
     * A milestone's row (D13, D16): the list's, or on a card ([room]) its share of the room, the stone, the
     * polaroid and the souls growing with it, each within its bounds.
     */
    fun rowSize(
        room: Dp?,
        rows: Int,
    ): RowSize {
        if (room == null) return RowSize(LIST_ROW, LIST_STONE, LIST_SNAP, SOUL)
        val height = (room / maxOf(rows, 1)).coerceIn(LIST_ROW, ROW_CAP)
        val stone = (height.value / 100f).coerceIn(LIST_STONE, 1f)
        return RowSize(height, stone, (height * 0.7f).coerceIn(LIST_SNAP, SNAP_CAP), min(SOUL * stone / LIST_STONE, SOUL_CAP))
    }
}
