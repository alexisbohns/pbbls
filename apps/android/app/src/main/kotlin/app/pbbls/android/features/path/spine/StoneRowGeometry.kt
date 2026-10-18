package app.pbbls.android.features.path.spine

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X

/** The stone's hit area: the stone plus this all round, never under 48 dp (spec §Accessibility). */
internal val STONE_HIT_INSET = 10.dp
internal val STONE_MIN_TARGET = 48.dp

/** The pebble's room below its stone: the web's `min-h-[calc(var(--anchor)+var(--sth)/2+14px)]`. */
internal val STONE_CLEARANCE = 14.dp

/**
 * Where a pebble row puts its stone and the stone's hit box, in the row's own
 * coordinates (origin at the row's top-left, x = 0 on the page's left edge).
 *
 * The stone's centre is the anchor on the line, whatever that puts above the
 * row's top: on an entry the anchor is 0.6 × the name's size (11.4 / 14.4 / 18.6
 * dp) and every stone (27.2 / 44 / 64.5 dp tall) overhangs the top, by up to 13.6
 * dp; on a card (anchor 31) only the large stone does, by 1.2 dp. That is the
 * web's geometry and it is kept: Compose hit-tests a child placed beyond its
 * parent's edge as long as no ancestor clips (`NodeCoordinator.withinLayerBounds`
 * prunes only through a clipping layer), so the overhang stays pressable. The
 * row and the day column must therefore never take a `clip` or a
 * `graphicsLayer(clip = true)`; a `LazyColumn` clips its viewport, not its items.
 */
internal data class StoneRowGeometry(
    val stoneTop: Dp,
    val stoneLeft: Dp,
    val hitTop: Dp,
    val hitLeft: Dp,
    val hitWidth: Dp,
    val hitHeight: Dp,
    /** The row is at least this tall, so the next pebble clears the stone. */
    val minHeight: Dp,
)

internal fun stoneRowGeometry(
    anchor: Dp,
    stoneWidth: Dp,
    stoneHeight: Dp,
): StoneRowGeometry {
    val hitWidth = max(stoneWidth + STONE_HIT_INSET * 2, STONE_MIN_TARGET)
    val hitHeight = max(stoneHeight + STONE_HIT_INSET * 2, STONE_MIN_TARGET)
    return StoneRowGeometry(
        stoneTop = anchor - stoneHeight / 2,
        stoneLeft = SPINE_X - stoneWidth / 2,
        hitTop = anchor - hitHeight / 2,
        hitLeft = SPINE_X - hitWidth / 2,
        hitWidth = hitWidth,
        hitHeight = hitHeight,
        minHeight = anchor + stoneHeight / 2 + STONE_CLEARANCE,
    )
}
