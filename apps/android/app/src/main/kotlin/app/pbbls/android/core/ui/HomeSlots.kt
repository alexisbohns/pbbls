package app.pbbls.android.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * filled by the entry provider for the same reason as [HomeMonthSlot]. [onWeek] receives the Monday
 * of the week tapped, which closes the sheet and focuses that week on the Path.
 */
typealias HomeWrapSlot = @Composable (wrap: MonthWrap, onWeek: (LocalDate) -> Unit, modifier: Modifier) -> Unit
