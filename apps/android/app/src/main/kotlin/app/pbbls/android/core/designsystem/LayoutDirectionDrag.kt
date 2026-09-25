package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.LayoutDirection

/*
 * Pointer positions are physical (x grows to the right in every locale), but
 * RTL-aware layout — `Modifier.offset`, `Alignment.CenterStart` — is logical.
 * Drag code converts at the edge with these and then only ever reasons in
 * logical units, so the same arithmetic moves toward the end edge in both
 * directions.
 */

/** A horizontal pointer delta, in the direction the layout reads. */
fun LayoutDirection.logicalDelta(physicalDx: Float): Float = if (this == LayoutDirection.Rtl) -physicalDx else physicalDx

/** How far [x] (physical, within a box of [width]) sits from the box's start edge. */
fun LayoutDirection.distanceFromStart(
    x: Float,
    width: Float,
): Float = if (this == LayoutDirection.Rtl) width - x else x
