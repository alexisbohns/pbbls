package app.pbbls.android.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.wrap.MonthWrap
import java.time.LocalDate

/**
 * The month's grid at the top of Home, as a slot (#1383, spec D8).
 *
 * The grid is the Path's own week columns (`features/path`), and Home (`features/home`) may not
 * import another feature, so Home takes it as a parameter and the entry provider, which sees every
 * feature, fills it with `HomeMonthGrid`. `GlyphPickerSlot` is the precedent. [onWeek] receives the
 * Monday of the column tapped.
 */
typealias HomeMonthSlot = @Composable (wrap: MonthWrap, onWeek: (LocalDate) -> Unit, modifier: Modifier) -> Unit

/**
 * A revealed month wrap in Home's wrap sheet, as a slot (#1383, spec D12): the Path's `MonthPage`,
 * filled by the entry provider for the same reason as [HomeMonthSlot]. The sheet hands it what the
 * Path's page reads from the Path's ViewModel: the day-dot [dayView] and its switch [onDayView]
 * (shared with the Path through `DisplayPreferences`), and Pebbles' haptics switch [hapticsOn], read
 * at each vibration of the intro. [onWeek] receives the Monday of the week tapped, which closes the
 * sheet and focuses that week on the Path.
 */
typealias HomeWrapSlot = @Composable (
    wrap: MonthWrap,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    hapticsOn: () -> Boolean,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * A folded month's summary line in the wrap sheet (#1383, spec D12; iOS `WrapCopy.line`): "Twenty-three
 * pebbles, five people." The words are the Path's (`WrapCopy`), so the entry provider fills this too.
 */
typealias HomeWrapLineSlot = @Composable (pebbles: Int, people: Int) -> String
