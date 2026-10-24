package app.pbbls.android.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val DASH = 4.dp
private val DASH_GAP = 3.dp

/**
 * A wrap tile (#1207): a fixed 128 × 112 card with a rule-coloured border, dashed for a tile that
 * invites rather than reports (nobody named yet). It is a button when [onClick] is set; the caller
 * gives the tile its semantics.
 */
@Composable
fun TileCard(
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val shape = MaterialTheme.shapes.medium
    val stroke = SpineLayoutTokens.RULE
    val edge =
        if (dashed) {
            Modifier.drawWithContent {
                drawContent()
                val px = stroke.toPx()
                val outline = shape.createOutline(Size(size.width - px, size.height - px), layoutDirection, this)
                translate(px / 2, px / 2) {
                    drawOutline(
                        outline,
                        colors.rule,
                        style = Stroke(px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH_GAP.toPx()))),
                    )
                }
            }
        } else {
            Modifier.border(stroke, colors.rule, shape)
        }
    Column(
        modifier =
            modifier
                .size(SpineLayoutTokens.TILE_WIDTH, SpineLayoutTokens.TILE_HEIGHT)
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .then(edge)
                .padding(SpineLayoutTokens.TILE_PAD),
        content = content,
    )
}

/** A tile's small-caps label. */
@Composable
fun TileHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = PebblesTheme.spine.type.sectionLabel,
        color = PebblesTheme.spine.colors.ink2,
        maxLines = 1,
        modifier = modifier,
    )
}
