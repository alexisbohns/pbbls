package app.pbbls.android.features.path.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineTypography
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val SCORE = 1.dp
private val SCORE_DASH = 3.dp
private val LEAF_GAP = 14.dp

/** The leaf's padding inside the printed border, in card units (the synthesis's `.leaf-in`). */
private const val LEAF_TOP_UNITS = 4.4f
private const val LEAF_SIDE_UNITS = 3.6f
private const val LEAF_FOOT_UNITS = 6.2f

/**
 * The recto's leaf (pebble card spec §Recto): under a dashed score, where the card
 * was folded on the Path, the full dateline and the words. With no words, the leaf
 * holds the dateline alone.
 *
 * @param datelineDescription what TalkBack reads for the dateline; the recto adds the
 *   emotion, so it is read straight after the date (spec §TalkBack reading order).
 * @param u the card unit; the score runs the leaf's full width, rule to rule.
 */
@Composable
internal fun CardLeaf(
    dateline: String,
    description: String?,
    u: Dp,
    modifier: Modifier = Modifier,
    datelineDescription: String = dateline,
) {
    val colors = PebblesTheme.spine.colors
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(SCORE)) {
            val dash = SCORE_DASH.toPx()
            drawLine(
                color = colors.cardRule,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = SCORE.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = u * LEAF_TOP_UNITS, start = u * LEAF_SIDE_UNITS, end = u * LEAF_SIDE_UNITS, bottom = u * LEAF_FOOT_UNITS),
        ) {
            Text(
                text = dateline,
                style = SpineTypography.cardLeafDateline,
                color = colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = datelineDescription },
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = SpineTypography.cardLeafBody,
                    color = colors.ink,
                    // After the title, the date and the souls (the recto's traversal group).
                    modifier = Modifier.padding(top = LEAF_GAP).semantics { traversalIndex = RECTO_LATE },
                )
            }
        }
    }
}

/** "Thursday 8 October 2026 · 21:00": the locale's full date and short time, in [zone]. */
internal fun cardDateline(
    at: OffsetDateTime,
    locale: Locale,
    zone: ZoneId,
): String {
    val zoned = at.atZoneSameInstant(zone)
    val date = zoned.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val time = zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return "$date · $time"
}

/** The recto's late stops: the souls and the words come after the dateline (spec §TalkBack reading order). */
internal const val RECTO_LATE = 1f
