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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.lab.components.LogTimeline
import app.pbbls.android.features.lab.components.LogTimelineMode
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.ReactionToggle
import app.pbbls.android.features.lab.services.LocalLogsService
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
) {
    val logsService = LocalLogsService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var logs by remember { mutableStateOf<List<Log>>(emptyList()) }
    var reactedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadKey) {
        isLoading = true
        loadFailed = false
        try {
            coroutineScope {
                val feed =
                    async {
                        when (mode) {
                            LogListMode.CHANGELOG -> logsService.changelog()
                            LogListMode.BACKLOG -> logsService.backlog()
                        }
                    }
                val reactions = async { logsService.myReactions() }
                logs = feed.await()
                reactedIds = reactions.await()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "list fetch failed", e)
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    fun toggleReaction(log: Log) {
        val before = ReactionToggle.State(reactedIds = reactedIds, logs = logs)
        val wasReacted = ReactionToggle.wasReacted(before, log.id)
        val next = ReactionToggle.toggle(before, log.id)
        reactedIds = next.reactedIds
        logs = next.logs
        scope.launch {
            try {
                if (wasReacted) logsService.unreact(log.id) else logsService.react(log.id)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "reaction toggle failed", e)
                val reverted =
                    ReactionToggle.revert(
                        ReactionToggle.State(reactedIds = reactedIds, logs = logs),
                        log.id,
                        wasReacted,
                    )
                reactedIds = reverted.reactedIds
                logs = reverted.logs
            }
        }
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(mode.titleRes),
                leading = {
                    IconButton(onClick = onBack) {
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
        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            loadFailed ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(R.string.lab_list_load_error),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = { loadKey++ }) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            logs.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    PebblesText(
                        text = stringResource(R.string.lab_list_empty),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                }

            else ->
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
                        logs = logs,
                        reactedIds = reactedIds,
                        onToggleReaction = { if (mode == LogListMode.BACKLOG) toggleReaction(it) },
                    )
                }
        }
    }
}
