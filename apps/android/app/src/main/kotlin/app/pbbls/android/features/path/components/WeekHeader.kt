package app.pbbls.android.features.path.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.WeekHeaderFormatting
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.LocalDate

/**
 * The "MAY 4 · MAY 10" pill above the path body — the `WeekHeaderView`
 * analog: focused week's date range (year suffix only when the week-based
 * year differs from today's), chevrons stepping through [entries], disabled
 * and dimmed at the edges.
 */
@Composable
fun WeekHeader(
    entries: List<WeekRollEntry>,
    focusedWeekStart: LocalDate,
    today: LocalDate,
    onFocusChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val locale = LocalConfiguration.current.locales[0]
    val label =
        WeekHeaderFormatting
            .formatRange(focusedWeekStart, today, locale)
            .uppercase(locale)
    val previous = WeekRollBuilder.previous(focusedWeekStart, entries)
    val next = WeekRollBuilder.next(focusedWeekStart, entries)

    Row(
        modifier =
            modifier
                .height(40.dp)
                .border(1.dp, system.muted, RoundedCornerShape(17.dp))
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronButton(
            pointsLeft = true,
            targetWeekStart = previous?.weekStart,
            a11yLabel = stringResource(R.string.path_week_previous),
            onFocusChange = onFocusChange,
        )
        Spacer(modifier = Modifier.weight(1f))
        PebblesText(
            text = label,
            style = PebblesTypography.buttonLabel,
            color = system.secondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        ChevronButton(
            pointsLeft = false,
            targetWeekStart = next?.weekStart,
            a11yLabel = stringResource(R.string.path_week_next),
            onFocusChange = onFocusChange,
        )
    }
}

@Composable
private fun ChevronButton(
    pointsLeft: Boolean,
    targetWeekStart: LocalDate?,
    a11yLabel: String,
    onFocusChange: (LocalDate) -> Unit,
) {
    val accent = PebblesTheme.colors.accent
    ChevronGlyph(
        pointsLeft = pointsLeft,
        tint = accent.primary,
        modifier =
            Modifier
                .size(24.dp)
                .alpha(if (targetWeekStart == null) 0.3f else 1f)
                .clickable(enabled = targetWeekStart != null) {
                    targetWeekStart?.let(onFocusChange)
                }.semantics { contentDescription = a11yLabel },
    )
}

/**
 * Hand-drawn chevron (the `chevron.compact.left/right` analog) — drawn like
 * `CheckGlyph` rather than pulling an icon dependency.
 */
@Composable
private fun ChevronGlyph(
    pointsLeft: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val nearX = if (pointsLeft) w * 0.58f else w * 0.42f
        val farX = if (pointsLeft) w * 0.42f else w * 0.58f
        val path =
            Path().apply {
                moveTo(nearX, h * 0.28f)
                lineTo(farX, h * 0.5f)
                lineTo(nearX, h * 0.72f)
            }
        drawPath(
            path = path,
            color = tint,
            style =
                Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}
