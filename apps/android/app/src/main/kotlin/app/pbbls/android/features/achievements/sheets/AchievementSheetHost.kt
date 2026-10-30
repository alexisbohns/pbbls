package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet

private val SHEET_SIDE = 20.dp
private val SHEET_BOTTOM = 34.dp

/** One sheet's content for its identity; null-safe on the payload (a section that left hides). */
@Composable
fun AchievementSheetContent(
    sheet: AchievementSheet,
    page: AchievementsPage,
    lookups: AchievementLookups,
    onPush: (AchievementSheet) -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sheet) {
        AchievementSheet.Explorer ->
            page.explorer?.let { ExplorerSheet(it, page.emotions, page.domains, lookups, onPush, onOpenMoment, onPull, modifier) }
        AchievementSheet.Emotions -> page.emotions?.let { EmotionsSheet(it, lookups, onOpenMoment, onPull, modifier) }
        AchievementSheet.Domains -> page.domains?.let { DomainsSheet(it, lookups, onOpenMoment, onPull, modifier) }
        AchievementSheet.Pebbles -> page.pebbles?.let { PebblesSheet(it, page.glyphsCarved, page.glyphSales, onOpenMoment, modifier) }
        is AchievementSheet.Year ->
            page.years.firstOrNull { it.year == sheet.year }?.let { year ->
                YearSheet(year, page.years.map { it.year }.filter { it != year.year }, lookups, onPush, modifier)
            }
    }
}

/**
 * The stacked sheets (#1307): one `ModalBottomSheet` per entry of [sheets], the last on top.
 * [onDismiss] pops the top one, which is the only one the user can reach. The stack itself
 * belongs to the screen's ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementSheetHost(
    sheets: List<AchievementSheet>,
    page: AchievementsPage,
    lookups: AchievementLookups,
    onPush: (AchievementSheet) -> Unit,
    onDismiss: () -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    sheets.forEachIndexed { depth, sheet ->
        key(depth, sheet) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.stock,
            ) {
                AchievementSheetContent(
                    sheet = sheet,
                    page = page,
                    lookups = lookups,
                    onPush = onPush,
                    onOpenMoment = onOpenMoment,
                    onPull = onPull,
                    modifier =
                        Modifier
                            .verticalScroll(
                                rememberScrollState(),
                            ).padding(start = SHEET_SIDE, end = SHEET_SIDE, bottom = SHEET_BOTTOM),
                )
            }
        }
    }
}
