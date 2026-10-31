package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.AchievementsCopy
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.MonthCounter
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.monthHues

private val DISC = 330.dp
private val COUNTER_COLUMN = 30.dp
private val CELL = 58.dp
private val GAUGE = 52.dp
private val GAUGE_HEIGHT = 4.dp
private val GAUGE_BAR = 2.5.dp
private const val MUTED_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.55f
private const val EM_DASH = "—"

/**
 * One table cell (the board's `cell`): a short number over a hairline gauge. [done] is the
 * server's `done` flag, the only thing that sets the number in full-weight [doneColor]; [fill] is
 * the gauge's length, the count over its served target, null when the server sends none.
 */
@Composable
private fun YearCell(
    text: AnnotatedString,
    fill: Float?,
    done: Boolean,
    tone: Color,
    doneColor: Color,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = text,
            style = if (done) type.cellNumberDone else type.cellNumber,
            color = if (done) doneColor else colors.ink2,
            textAlign = TextAlign.Center,
        )
        Canvas(Modifier.size(GAUGE, GAUGE_HEIGHT)) {
            drawLine(colors.ink2, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(), alpha = TRACK_ALPHA)
            if (fill != null && fill > 0f) {
                val h = GAUGE_BAR.toPx()
                drawRoundRect(
                    tone,
                    topLeft = Offset(0f, (size.height - h) / 2),
                    size = Size(size.width * fill, h),
                    cornerRadius =
                        CornerRadius(
                            h / 2,
                        ),
                )
            }
        }
    }
}

private fun fraction(
    n: Int,
    of: Int?,
): Float? = of?.takeIf { it > 0 }?.let { (n.toFloat() / it).coerceIn(0f, 1f) }

/** The weeks cell's text: the weeks filled, their full weeks muted after it, or a dash with none. */
@Composable
private fun weeksText(
    filled: Int,
    full: Int,
    copy: AchievementsCopy,
): AnnotatedString {
    val muted = PebblesTheme.spine.colors.ink2
    if (full <= 0) return AnnotatedString(EM_DASH)
    return buildAnnotatedString {
        append(copy.number(filled))
        withStyle(SpanStyle(color = muted)) { append("/" + copy.number(full)) }
    }
}

/**
 * The year sheet (the board's `sheetYear`): the docked disc large, then one row per month the
 * server lists, newest first, then its next step, then the earlier years as rows that [onPush].
 * Every count is the served one, never set against a target; the done style is the served `done`.
 */
@Composable
fun YearSheet(
    year: AchievementYear,
    otherYears: List<Int>,
    lookups: AchievementLookups,
    onPush: (AchievementSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hues = monthHues(year.months, lookups)
    val rows = year.months.zip(hues).associateBy { (m, _) -> m.month }
    Column(modifier.fillMaxWidth()) {
        SheetBand(year.year.toString(), copy.yearHint(year.current))
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            YearDisc(year, lookups, copy.discDescription(year), size = DISC)
        }
        SheetSection(copy.string(R.string.achievements_section_months), copy.string(R.string.achievements_newest_first))
        Row(
            Modifier
                .fillMaxWidth()
                .rule(colors.rule, above = false)
                .padding(top = 8.dp, bottom = 4.dp)
                .clearAndSetSemantics {},
        ) {
            Box(Modifier.width(COUNTER_COLUMN))
            Box(Modifier.weight(1f))
            val head = type.tableHead
            listOf(R.string.achievements_column_moments, R.string.achievements_column_pebbles, R.string.achievements_column_weeks).forEach {
                // One line in its column at any font scale: the head shrinks to fit rather than wrap mid-word.
                Text(
                    copy.string(it),
                    style = head,
                    color = colors.ink2,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = head.fontSize / 2, maxFontSize = head.fontSize),
                    modifier = Modifier.width(CELL),
                )
            }
        }
        year.listed.mapNotNull(rows::get).forEach { (month, hue) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SpineLayoutTokens.TOUCH_MIN)
                        .rule(colors.rule, above = false)
                        .clearAndSetSemantics { contentDescription = copy.monthDescription(month) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(COUNTER_COLUMN)) { MonthCounter(month, lookups) }
                // Muted on its state alone, as the disc mutes it: any month not kept. A kept month whose
                // family this device does not know is drawn in ink, never dimmed.
                val dim = if (month.state != MonthState.KEPT) Modifier.alpha(MUTED_ALPHA) else Modifier
                Text(copy.monthHeading(month.month), style = type.soulName, color = colors.ink, modifier = Modifier.weight(1f).then(dim))
                Row(dim) {
                    YearCell(
                        AnnotatedString(copy.number(month.momentDays)),
                        fraction(month.momentDays, year.daysTarget),
                        month.done.moments,
                        colors.ink,
                        colors.ink,
                    )
                    YearCell(
                        AnnotatedString(copy.number(month.pebbleDays)),
                        fraction(month.pebbleDays, year.daysTarget),
                        month.done.pebbles,
                        hue ?: colors.ink,
                        hue ?: colors.ink,
                    )
                    YearCell(
                        weeksText(month.weeksFilled, month.fullWeeks, copy),
                        fraction(month.weeksFilled, month.fullWeeks),
                        month.done.weeks,
                        colors.ink,
                        colors.ink,
                    )
                }
            }
        }
        year.nextStep
            ?.let(
                copy::nextStep,
            )?.let { Text(it, style = type.lead, color = colors.ink, modifier = Modifier.padding(top = 14.dp)) }

        SheetSection(copy.string(R.string.achievements_section_earlier_years), hint = null)
        if (otherYears.isEmpty()) {
            Text(copy.firstYear(year.year), style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 10.dp))
        } else {
            otherYears.forEach { other ->
                val title = other.toString()
                RegisterRow(
                    mark = {},
                    title = title,
                    subtitle = null,
                    date = null,
                    isNew = false,
                    description = title,
                    onClick = { onPush(AchievementSheet.Year(other)) },
                    clickLabel = copy.string(R.string.achievements_open_sheet),
                    titleStyle = type.milestone,
                    trailing = { Chevron() },
                )
            }
        }
    }
}
