package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val FIGURE_GAP = 12.dp

/**
 * A section's head on Home and You (spec 2026-10-31 D3): the [title] in `sectionHead` (a
 * heading, sentence case), and an optional [figure] at the far end on the title's baseline, in
 * `sectionFigure` and `primary` ("2 of 5", "3"; build a count against a total with [figureOf]).
 * The title wraps; the figure keeps to one line.
 */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    figure: AnnotatedString? = null,
) {
    val type = PebblesTheme.page
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FIGURE_GAP),
    ) {
        Text(
            text = title,
            style = type.sectionHead,
            color = colors.onSurface,
            modifier =
                Modifier
                    .weight(1f)
                    .alignByBaseline()
                    .semantics { heading() },
        )
        if (figure != null) {
            Text(
                text = figure,
                style = type.sectionFigure,
                color = colors.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * "2 *of* 5": [done] and [total] in the figure's face, the [word] between them in the italic
 * `figureWord` and `onSurfaceVariant`. The caller passes the localised word, so French reads
 * "2 *sur* 5".
 */
@Composable
fun figureOf(
    done: Int,
    total: Int,
    word: String,
): AnnotatedString {
    val wordStyle =
        PebblesTheme.page.figureWord
            .toSpanStyle()
            .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    return buildAnnotatedString {
        append(done.toString())
        withStyle(wordStyle) { append(" $word ") }
        append(total.toString())
    }
}
