package app.pbbls.android.features.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.core.ui.HomeMonthSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The Home tab (#1383 D1): the first of five, where a signed-in launch lands. Reads again on every
 * resume ([HomeViewModel.onResumed]); [reselected] fires when the user taps Home while on it, which
 * scrolls the page back to the greeting (iOS `scrollToTop`).
 *
 * [monthSlot] is the Path's month grid (D8), filled by the entry provider; a tap on one of its weeks
 * leaves the Path that focus and calls [onOpenPath] (D10).
 */
@Composable
fun HomeScreen(
    reselected: Flow<Unit>,
    monthSlot: HomeMonthSlot,
    onOpenPath: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val listState = rememberLazyListState()
    LaunchedEffect(reselected, listState) {
        reselected.collect { listState.animateScrollToItem(0) }
    }
    val now by rememberMinuteClock()
    HomeContent(
        state = state,
        now = now,
        monthSlot = monthSlot,
        onWeek = { weekStart ->
            viewModel.onWeek(weekStart)
            onOpenPath()
        },
        modifier = modifier,
        listState = listState,
    )
}

/**
 * The device's local time, read again at each minute's turn, so the greeting's band flips on the hour
 * while Home is on screen (iOS reads its clock every 60 s).
 */
@Composable
private fun rememberMinuteClock(): State<LocalDateTime> =
    produceState(LocalDateTime.now()) {
        while (true) {
            val now = LocalDateTime.now()
            value = now
            delay(ChronoUnit.MILLIS.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).coerceAtLeast(1))
        }
    }
