package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.CalendarLabels
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT_GAP
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.DayMode
import java.time.LocalDate

/** A month row's ring (#1207, the board's `ring(…, 40)`). */
internal val ROW_RING = 40.dp

/** The month's days as the reader chose to see them (D9): a ring from the 1st, or a calendar. */
internal fun MonthWrap.dayMode(view: DayView): DayMode =
    when (view) {
        DayView.CIRCLE -> DayMode.Circle
        DayView.CALENDAR -> DayMode.Calendar(leading = firstWeekday.value - 1)
    }

internal fun sizeOfIntensity(intensity: Int): ValenceSizeGroup =
    when {
        intensity <= 1 -> ValenceSizeGroup.SMALL
        intensity == 2 -> ValenceSizeGroup.MEDIUM
        else -> ValenceSizeGroup.LARGE
    }

/** Above a calendar: the weekday letters over its columns. Decorative; each row says its days. */
@Composable
internal fun CalendarHead(modifier: Modifier = Modifier) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    // Any Monday: the letters only need the week's order.
    val letters = remember(locale) { CalendarLabels.weekdayLetters(LocalDate.of(2026, 10, 5), locale) }
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DOT_GAP)) {
        letters.forEach {
            Text(
                it,
                style = type.dayLetter,
                color = colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(DOT),
            )
        }
    }
}

internal val ValencePolarity.isHeavy: Boolean get() = this == ValencePolarity.LOWLIGHT
