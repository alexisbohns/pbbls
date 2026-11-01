package app.pbbls.android.features.home.wraps

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PageShapes
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.home.HomeWrapMonth
import app.pbbls.android.core.ui.HomeWrapLineSlot
import app.pbbls.android.core.ui.HomeWrapSlot
import app.pbbls.android.features.home.HomeSection
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

private const val TAG = "home-wrap"

/** iOS's three dashed placeholders under Unfold, where the month's cards will be. */
private const val PLACEHOLDERS = 3
private val PLACEHOLDER_HEIGHT = 72.dp

/**
 * One month's wrap (#1383 D12; iOS `HomeWrapSheet`), the `HomeWrap(month)` entry: a docked sheet on phones.
 * [month] is the key's `YearMonth` text ("2026-10"); one this build cannot read closes the sheet. [wrapSlot]
 * draws the revealed month (the Path's page) and [wrapLine] the folded month's summary, both filled by the
 * entry provider. [onClose] pops the sheet (Done, or the month gone from Home); a week tapped in the page leaves
 * the Path its focus, then [onOpenPath] closes the sheet and switches to the Path (D10).
 */
@Composable
fun HomeWrapScreen(
    month: String,
    wrapSlot: HomeWrapSlot,
    wrapLine: HomeWrapLineSlot,
    onClose: () -> Unit,
    onOpenPath: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeWrapViewModel = hiltViewModel(),
) {
    val parsed = remember(month) { parseMonth(month) }
    LaunchedEffect(parsed) { if (parsed == null) onClose() else viewModel.start(parsed) }
    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            HomeWrapEffect.Close -> onClose()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (parsed == null) return
    HomeWrapContent(
        month = parsed,
        state = state,
        wrapSlot = wrapSlot,
        wrapLine = wrapLine,
        dayView = viewModel.dayView,
        onDayView = viewModel::onDayView,
        hapticsOn = { viewModel.hapticsOn },
        onUnfold = viewModel::unfold,
        onRetry = viewModel::retry,
        onWeek = { weekStart ->
            viewModel.onWeek(weekStart)
            onOpenPath()
        },
        onDone = onClose,
        modifier = modifier,
    )
}

private fun parseMonth(text: String): YearMonth? =
    try {
        YearMonth.parse(text)
    } catch (e: DateTimeParseException) {
        Log.e(TAG, "unreadable wrap month '$text'", e)
        null
    }

/**
 * The sheet's body, stateless over [HomeWrapUiState]: what the screenshot tests render. A head with the month's
 * name and Done, then the state: a spinner, the error with Try again, the folded month, or the revealed page.
 */
@Composable
internal fun HomeWrapContent(
    month: YearMonth,
    state: HomeWrapUiState,
    wrapSlot: HomeWrapSlot,
    wrapLine: HomeWrapLineSlot,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    hapticsOn: () -> Boolean,
    onUnfold: () -> Unit,
    onRetry: () -> Unit,
    onWeek: (LocalDate) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val name = remember(month, locale) { monthName(month, locale) }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = PebblesTheme.spacing.lg, end = PebblesTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
        val body = Modifier.fillMaxWidth().weight(1f)
        when (state) {
            HomeWrapUiState.Loading -> Spinner(body)
            is HomeWrapUiState.Error -> Retry(stringResource(state.message), onRetry, body)
            is HomeWrapUiState.Folded -> FoldedMonth(state, wrapLine, onUnfold, body)
            is HomeWrapUiState.Revealed ->
                when (val deck = state.deck) {
                    HomeSection.Loading -> Spinner(body)
                    HomeSection.Failed -> Retry(stringResource(R.string.home_wraps_error), onRetry, body)
                    is HomeSection.Ready -> wrapSlot(deck.value, dayView, onDayView, hapticsOn, onWeek, body)
                }
        }
    }
}

/** iOS `HomeWrapFoldedView`: the month's line, Unfold, and three dashed placeholders. */
@Composable
private fun FoldedMonth(
    state: HomeWrapUiState.Folded,
    wrapLine: HomeWrapLineSlot,
    onUnfold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PebblesTheme.spacing
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = wrapLine(state.month.count, peopleOf(state.month)),
            style = PebblesTheme.spine.type.summary,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.home_wrap_unfold),
            onClick = onUnfold,
            isLoading = state.unfold == Unfold.RUNNING,
        )
        if (state.unfold == Unfold.FAILED) {
            Text(
                text = stringResource(R.string.home_wraps_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column(
            modifier = Modifier.clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val outline = MaterialTheme.colorScheme.outlineVariant
            repeat(PLACEHOLDERS) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PLACEHOLDER_HEIGHT)
                        .dashedOutline(outline, PageShapes.card),
                )
            }
        }
    }
}

private fun peopleOf(month: HomeWrapMonth): Int = month.cover?.soulsInOrder?.size ?: 0

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
private fun Retry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PebblesTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.home_wraps_retry)) }
    }
}
