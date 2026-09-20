package app.pbbls.android.features.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.features.lab.components.LogTimeline
import app.pbbls.android.features.lab.components.LogTimelineMode
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography

private const val TAG = "lab-list"

/** The two see-all flavors — unlimited changelog or backlog (iOS `LogListView.Mode`). */
enum class LogListMode(
    val titleRes: Int,
    val timelineMode: LogTimelineMode,
) {
    CHANGELOG(R.string.lab_section_changelog, LogTimelineMode.CHANGELOG),
    BACKLOG(R.string.lab_section_backlog, LogTimelineMode.BACKLOG),
}

/**
 * The see-all list — ports iOS `LogListView`: the unlimited feed and
 * `myReactions` load together and, unlike the Lab screen, ANY failure shows
 * the error state (design D3). Reactions toggle only in backlog mode. A
 * content swap inside the Lab route (design D9).
 */
@Composable
fun LogListScreen(
    mode: LogListMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    // Guarded on the mode, so a rotation re-runs this without re-fetching.
    LaunchedEffect(mode) { viewModel.start(mode) }

    fun back() {
        viewModel.finish()
        onBack()
    }

    // This screen's own back handler (which called `back()` above, releasing
    // the mode guard before Lab's own handler cleared its cover flag) is gone
    // as of #852 Task 17, ahead of Task 19 promoting this to a `LabLogList`
    // entry. Until then, system/gesture back falls through to Lab's, which
    // does not call `viewModel.finish()` — so `startedMode` stays set and a
    // same-mode reopen can show a stale list until Task 19 lands. The toolbar
    // arrow still calls `back()` and is unaffected.
    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(mode.titleRes),
                leading = {
                    IconButton(onClick = { back() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new LogListUiState case must be rendered.
        when (val state = uiState) {
            LogListUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is LogListUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(state.messageRes),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = viewModel::retry) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            is LogListUiState.Content ->
                if (state.logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        PebblesText(
                            text = stringResource(R.string.lab_list_empty),
                            style = PebblesTypography.body,
                            color = system.secondary,
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = PebblesTheme.spacing.lg)
                                .padding(bottom = PebblesTheme.spacing.xxl),
                    ) {
                        LogTimeline(
                            mode = mode.timelineMode,
                            logs = state.logs,
                            reactedIds = state.reactedIds,
                            onToggleReaction = {
                                if (mode == LogListMode.BACKLOG) viewModel.toggleReaction(it)
                            },
                        )
                    }
                }
        }
    }
}
