package app.pbbls.android.features.path.spine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineLayout.COL_X
import app.pbbls.android.features.path.spine.SpineLayout.LABEL_TOP
import app.pbbls.android.features.path.spine.SpineLayout.LABEL_WIDTH
import app.pbbls.android.features.path.spine.SpineLayout.SEAM_HEIGHT
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X
import app.pbbls.android.features.path.spine.logic.SpineDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val HAIRLINE = 1.dp
private val TOOTH_WIDTH = 1.dp
private val TEETH_OFFSET = 2.dp
private val TEETH_PERIOD = 7.dp
private val TEETH_HEIGHT = 3.5.dp
private val RIFFLE_OFFSET = 4.dp
private val RIFFLE_PERIOD = 19.dp
private val RIFFLE_HEIGHT = 6.5.dp

/** The label hangs 23 dp left of the line, so its 46 dp sit centred on it. */
private val LABEL_LEFT = SPINE_X - LABEL_WIDTH / 2

/**
 * A day's start: the hairline seam with its riffle teeth and the date set in the margin;
 * *still* under it when the day has no moment.
 */
@Composable
fun DaySeam(
    day: SpineDay,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(day.date, locale) { shortWeekday(day, locale) }
    val fullDate =
        remember(day.date, locale) {
            day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        }
    val labelShape = MaterialTheme.shapes.medium.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)

    Column(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SEAM_HEIGHT)
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = fullDate
                    }.drawBehind {
                        val hairline = HAIRLINE.toPx()
                        drawRect(colors.seam, size = Size(size.width, hairline))
                        teeth(colors.teeth, TEETH_OFFSET.toPx(), TEETH_PERIOD.toPx(), hairline, TEETH_HEIGHT.toPx())
                        teeth(colors.teeth, RIFFLE_OFFSET.toPx(), RIFFLE_PERIOD.toPx(), hairline, RIFFLE_HEIGHT.toPx())
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .offset(x = LABEL_LEFT, y = LABEL_TOP)
                        .width(LABEL_WIDTH)
                        .background(colors.ground, labelShape)
                        .padding(bottom = 5.dp)
                        .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = type.dayNumeral,
                    color = colors.ink,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp),
                )
                // The start padding balances the small caps' tracking, as the web's `pl-[.2em]` does.
                Text(
                    text = weekday,
                    style = type.dayWeekday,
                    color = colors.ink2,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                )
            }
        }
        if (day.still) {
            Text(
                text = stringResource(R.string.path_spine_still),
                style = type.still,
                color = colors.ink3,
                modifier = Modifier.padding(start = COL_X),
            )
        }
    }
}

/** One tooth every [period] from [offset], the width of a hairline, [height] tall from [top]. */
private fun DrawScope.teeth(
    color: Color,
    offset: Float,
    period: Float,
    top: Float,
    height: Float,
) {
    val width = TOOTH_WIDTH.toPx()
    var x = offset
    while (x < size.width) {
        drawRect(color, Offset(x, top), Size(width, height))
        x += period
    }
}

/** "mon", "lun": the locale's short weekday, lowercase, without the abbreviation point. */
private fun shortWeekday(
    day: SpineDay,
    locale: Locale,
): String =
    day.date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, locale)
        .lowercase(locale)
        .trimEnd('.')
