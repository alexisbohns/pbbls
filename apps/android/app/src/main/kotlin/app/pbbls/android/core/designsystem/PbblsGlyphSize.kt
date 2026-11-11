package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The five sizes a pebbles icon draws at (Android icon migration spec D6; the
 * iOS `PbblsGlyphSize` values). A 24-unit drawing with a 1.75 stroke draws a
 * 1.1dp line at 16, the floor. Hand-written: the generator owns `PbblsGlyph.kt`
 * and `PbblsGlyphState.kt` only.
 *
 * - [INLINE] beside text: karma amounts, prices, the soul count, photo states.
 *   The one size that follows the font scale, through the platform's own sp
 *   curve, so the stone keeps pace with the line it sits in (this narrows the
 *   package spec's D9 for one size).
 * - [ROW] list-row, pill and menu-item leads.
 * - [TOOLBAR] an icon alone in an `IconButton` (its 48dp target does not grow).
 * - [TILE] `SurfaceTile`, form and card leads, achievement family marks.
 * - [HERO] detail placeholders, the Capture photo step, the achievement celebration.
 *
 * An M3 slot with its own icon token (`ButtonDefaults.IconSize`,
 * `FilterChipDefaults.IconSize`, `ToggleButtonDefaults.IconSize`, the 24dp
 * navigation and FAB icons) keeps that token. A fixed dp slot (the band mark,
 * the glyph cells, the shelf badge) passes its `Dp` to `PbblsGlyphImage`'s
 * `size: Dp` overload instead.
 */
enum class PbblsGlyphSize(
    val value: Float,
    val followsFontScale: Boolean,
) {
    INLINE(16f, true),
    ROW(20f, false),
    TOOLBAR(22f, false),
    TILE(28f, false),
    HERO(48f, false),
}

/** The side to draw at: [PbblsGlyphSize.value] read as sp when it follows the font scale, as dp otherwise. */
fun PbblsGlyphSize.toDp(density: Density): Dp =
    if (followsFontScale) {
        with(density) { value.sp.toDp() }
    } else {
        value.dp
    }
