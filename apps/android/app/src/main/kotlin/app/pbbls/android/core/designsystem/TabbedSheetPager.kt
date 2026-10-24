package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TAB_EDGE = 24.dp

/**
 * One half sheet over several lists (#1207, D4): the tabs repeat the lists' names and the pager
 * moves by whole pages. A stock `ModalBottomSheet` (partial expansion first), a stock
 * `SecondaryScrollableTabRow` and a `HorizontalPager`, on the Spine's paper. The pager fills the
 * sheet whatever its pages hold, so the sheet opens at half the screen, drags to the top, and never
 * resizes as the reader swipes between a short list and a long one. Each [page] scrolls itself. The open state and the page belong to the caller, never to a
 * ViewModel: [onPageChange] reports the page so the caller can keep it across a rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSheetPager(
    tabs: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    page: @Composable (Int) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) { tabs.size }
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect(onPageChange) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.stock,
        contentColor = colors.ink,
        modifier = modifier,
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = colors.ink,
            edgePadding = TAB_EDGE,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pagerState.currentPage), color = colors.ink)
            },
            divider = { HorizontalDivider(color = colors.rule) },
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == pagerState.currentPage
                Tab(
                    selected = selected,
                    onClick = {
                        scope.launch { if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(text = label, style = type.sectionLabel, color = if (selected) colors.ink else colors.ink3) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { index -> page(index) }
    }
}
