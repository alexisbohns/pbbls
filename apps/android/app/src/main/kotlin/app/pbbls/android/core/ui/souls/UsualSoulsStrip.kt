package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph

private val TILE_MIN_HEIGHT = 88.dp
private val PLUS_SIDE = 44.dp
private val DASH_STROKE = 1.5.dp
private val DASH = 6.dp

/**
 * The people the user most often tags, as the Life screen's strip (record-flow D10, souls spec S5): up to
 * [SoulRanking.STRIP_CAP] tiles that toggle in place, and a More tile that opens the chooser sheet. With
 * nobody at all, one "Add people" tile. Mirrors iOS `UsualSoulsStrip`, including [shown].
 *
 * Until the souls have loaded the row keeps its height and draws no tile: an empty cache is not "nobody"
 * (`200 []` under RLS), and a tile claiming so would flash and move the button.
 */
@Composable
fun UsualSoulsStrip(
    souls: List<SoulWithGlyph>,
    selection: List<String>,
    isLoaded: Boolean,
    onToggle: (String) -> Unit,
    onMore: () -> Unit,
    hapticsOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val space = PebblesTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.xs)) {
        Text(
            text = stringResource(R.string.souls_who_was_there).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            souls.isEmpty() && !isLoaded -> Row(Modifier.fillMaxWidth().height(TILE_MIN_HEIGHT)) {}
            souls.isEmpty() ->
                Row(Modifier.fillMaxWidth()) {
                    MoreTile(label = stringResource(R.string.souls_add_people), onClick = onMore, modifier = Modifier.weight(1f))
                }
            else ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    shown(souls, selection).forEach { soul ->
                        SoulTile(
                            soul = soul,
                            isSelected = soul.id in selection,
                            showsSelection = true,
                            onClick = { onToggle(soul.id) },
                            hapticsOn = hapticsOn,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    MoreTile(label = stringResource(R.string.souls_more), onClick = onMore, modifier = Modifier.weight(1f).fillMaxHeight())
                }
        }
    }
}

/**
 * The usual four, with anyone chosen for this pebble but missing from them (a person created inline in
 * the chooser) taking the last slot, so a fresh pick is visible on return. Several such people share that
 * one slot: only the latest shows, the others stay in the answers.
 */
internal fun shown(
    souls: List<SoulWithGlyph>,
    selection: List<String>,
): List<SoulWithGlyph> {
    val tiles = SoulRanking.usual(souls).take(SoulRanking.STRIP_CAP).toMutableList()
    for (id in selection) {
        if (tiles.any { it.id == id }) continue
        val soul = souls.firstOrNull { it.id == id } ?: continue
        if (tiles.size == SoulRanking.STRIP_CAP) tiles.removeAt(tiles.lastIndex)
        tiles.add(soul)
    }
    return tiles
}

@Composable
private fun MoreTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val dashed = colors.outline
    Column(
        modifier =
            modifier
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val width = DASH_STROKE.toPx()
                    drawOutline(
                        outline = outline,
                        color = dashed,
                        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))),
                    )
                }.heightIn(min = TILE_MIN_HEIGHT)
                .padding(horizontal = PebblesTheme.spacing.xs, vertical = PebblesTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs, Alignment.CenterVertically),
    ) {
        PbblsIconImage(icon = PbblsIcon.PLUS_CIRCLE, contentDescription = null, size = PLUS_SIDE, tint = colors.onSurfaceVariant)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
