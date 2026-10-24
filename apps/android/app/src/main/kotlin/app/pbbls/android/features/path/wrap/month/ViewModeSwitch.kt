package app.pbbls.android.features.path.wrap.month

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.DayView
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

/** The month sheet's day-dot view (#1207, D9): a stock segmented control, calendar · circle. */
@Composable
fun ViewModeSwitch(
    view: DayView,
    onView: (DayView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberWrapCopy()
    val options = listOf(DayView.CALENDAR to copy.viewCalendar(), DayView.CIRCLE to copy.viewCircle())
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = view == option,
                onClick = { onView(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
            )
        }
    }
}
