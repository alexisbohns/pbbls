package app.pbbls.android.features.path.card

import android.icu.text.ListFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.SpineTypography

private const val BAND_HAIRLINE_ALPHA = 0.35f
private const val BAND_FOLD_ALPHA = 0.5f
private val BAND_HAIRLINE_INSET = 3.dp
private val BAND_FOLD = 3.dp
private val BAND_MARK = 14.dp
private val BAND_KNOT = 5.dp
private val BAND_GAP = 10.dp
private val BAND_LINE_GAP = 4.dp

/**
 * The pebble's collections as a paper band wrapped across the card's foot (pebble card
 * spec §Verso 5): full bleed to the shell's edges, the names in italic, a small
 * diamond between, each led by the collection mark. Nothing when [names] is empty.
 *
 * Two hairlines in [edge] run inside its long edges, and a narrow darker fold at each
 * end shows the band turning under. TalkBack reads it once, as a list.
 */
@Composable
internal fun CollectionBand(
    names: List<String>,
    paper: Color,
    ink: Color,
    edge: Color,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val listed = remember(names, locale) { ListFormatter.getInstance(locale).format(names) }
    val description = stringResource(R.string.card_collections_a11y) + ": " + listed

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = CardLayout.BAND_MIN_HEIGHT)
            .background(paper)
            .drawBehind {
                val hairline = 1.dp.toPx()
                val inset = BAND_HAIRLINE_INSET.toPx()
                val rule = edge.copy(alpha = edge.alpha * BAND_HAIRLINE_ALPHA)
                drawRect(rule, Offset(0f, inset), Size(size.width, hairline))
                drawRect(rule, Offset(0f, size.height - inset - hairline), Size(size.width, hairline))
                val fold = BAND_FOLD.toPx()
                val shade = edge.copy(alpha = edge.alpha * BAND_FOLD_ALPHA)
                drawRect(shade, Offset.Zero, Size(fold, size.height))
                drawRect(shade, Offset(size.width - fold, 0f), Size(fold, size.height))
            }.clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BandFlow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            names.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pebble_collection),
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(BAND_MARK),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = name,
                        style = SpineTypography.cardBandName,
                        color = ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // The knots between names: small diamonds in the band's rule colour.
            repeat(names.size - 1) {
                Box(Modifier.size(BAND_KNOT).rotate(45f).background(edge))
            }
        }
    }
}

/**
 * A centred flow of the band's names (the first half of the children) with a knot
 * (the second half) between two names on the same line only: a wrapped line never
 * starts or ends on a diamond, which would read as a stray mark.
 */
@Composable
private fun BandFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val count = (measurables.size + 1) / 2
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val items = measurables.take(count).map { it.measure(loose) }
        val knots = measurables.drop(count).map { it.measure(loose) }
        val gap = BAND_GAP.roundToPx()
        val lineGap = BAND_LINE_GAP.roundToPx()
        val knotRun = if (knots.isEmpty()) 0 else gap * 2 + knots.first().width

        // Greedy lines of item indices.
        val lines = mutableListOf(mutableListOf<Int>())
        var lineWidth = 0
        items.forEachIndexed { i, item ->
            val line = lines.last()
            if (line.isNotEmpty() && lineWidth + knotRun + item.width > constraints.maxWidth) {
                lines += mutableListOf(i)
                lineWidth = item.width
            } else {
                lineWidth += (if (line.isEmpty()) 0 else knotRun) + item.width
                line += i
            }
        }
        val widths = lines.map { line -> line.sumOf { items[it].width } + knotRun * (line.size - 1) }
        val heights = lines.map { line -> line.maxOf { items[it].height } }
        val width = (widths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            (heights.sum() + lineGap * (lines.size - 1)).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            var y = 0
            lines.forEachIndexed { l, line ->
                var x = (width - widths[l]) / 2
                line.forEachIndexed { k, i ->
                    if (k > 0) {
                        val knot = knots[i - 1]
                        knot.placeRelative(x + gap, y + (heights[l] - knot.height) / 2)
                        x += knotRun
                    }
                    items[i].placeRelative(x, y + (heights[l] - items[i].height) / 2)
                    x += items[i].width
                }
                y += heights[l] + lineGap
            }
        }
    }
}
