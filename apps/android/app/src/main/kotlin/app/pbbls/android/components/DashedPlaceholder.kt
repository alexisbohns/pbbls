package app.pbbls.android.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.theme.PebblesTheme

/**
 * 32dp bordered square standing in for an unpicked form-row leading slot
 * (emotion / valence / glyph) — extracted from `PebbleForm`'s private
 * placeholder so new form surfaces share one symbol. The name predates the
 * extraction: the border is solid (the dashed glyph chrome lives in
 * `GlyphView`'s CARVE/CREATE cases); kept for diff continuity with the M39
 * screenshots.
 */
@Composable
fun DashedPlaceholder(modifier: Modifier = Modifier) {
    val color = PebblesTheme.colors.system.secondary
    Box(modifier = modifier.size(32.dp).border(1.dp, color, RoundedCornerShape(6.dp)))
}
