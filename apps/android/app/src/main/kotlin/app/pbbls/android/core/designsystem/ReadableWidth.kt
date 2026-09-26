package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a content column gets (#855): 600 dp, the M3 Compact/Medium
 * breakpoint. Below it the cap never binds, so phones keep today's
 * edge-to-edge layout with no size-class branch; above it — tablets,
 * foldables, desktop windows — content becomes a centered column instead of
 * stretching every row, picker and pager page across the window.
 */
val ReadableContentMaxWidth: Dp = 600.dp

/**
 * Centers the content in a column at most [ReadableContentMaxWidth] wide.
 * Apply it to a screen's content container, never to its top bar or the
 * navigation chrome: those stay full-width by design.
 *
 * The first `fillMaxWidth` claims the whole width so the surrounding
 * background still spans the window, `wrapContentWidth` releases the minimum
 * so the cap can apply and centers what is left, and the trailing
 * `fillMaxWidth` makes the column exactly `min(window, cap)` wide, so a screen
 * of narrow, start-aligned rows does not shrink to its widest row and drift to
 * the middle. Height is untouched, so a `fillMaxSize` below still fills the
 * viewport vertically.
 */
fun Modifier.readableWidth(): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = ReadableContentMaxWidth)
        .fillMaxWidth()
