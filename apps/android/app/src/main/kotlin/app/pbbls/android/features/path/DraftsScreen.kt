package app.pbbls.android.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Unpublished quick captures, most recently saved first (M47) — ports iOS
 * `DraftsListSheet`.
 *
 * Its own surface rather than a section inside the Path timeline, whose week
 * grouping, ripple, bounce and stats all assume real pebbles (design D4).
 * Tapping a row resumes it in the record flow (#852 supersedes `CreatePebbleScreen`
 * stacking underneath: resuming now pops this entry and pushes `RecordFlow`).
 *
 * A pushed-and-popped entry now (#852), not a cover — self-applies
 * `safeDrawingPadding()`. System back is `NavDisplay`'s own; nothing here needs
 * to refuse it, since [DraftsViewModel]'s delete is already uncancellable.
 */
@Composable
fun DraftsScreen(
    onResume: (PebbleDraftRecord) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    DraftsContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onResume = onResume,
        onDelete = viewModel::delete,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * Stateless drafts list — what screenshot previews drive. Takes its data as
 * parameters rather than reading services, the same previewability rule the
 * funnel and Path screens follow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DraftsContent(
    uiState: DraftsUiState,
    onRetry: () -> Unit,
    onResume: (PebbleDraftRecord) -> Unit,
    onDelete: (PebbleDraftRecord) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.drafts_title),
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_done),
                    onClick = onDismiss,
                    color = colors.primary,
                )
            },
        )

        // Exhaustive with no `else`: a new DraftsUiState case must render.
        when (uiState) {
            DraftsUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

            DraftsUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.drafts_load_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) {
                            Text(
                                text = stringResource(R.string.action_retry),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.primary,
                            )
                        }
                    }
                }

            is DraftsUiState.Content ->
                if (uiState.drafts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.drafts_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.drafts, key = { it.id }) { record ->
                            DraftRow(
                                record = record,
                                onClick = { onResume(record) },
                                onDelete = { onDelete(record) },
                            )
                        }
                    }
                }
        }
    }
}

/**
 * A draft has no `render_svg` — nothing is carved until it publishes — so the row
 * leads with a dashed placeholder rather than a pebble visual.
 */
@Composable
private fun DraftRow(
    record: PebbleDraftRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = record.payload.name

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(colors.surfaceContainerHighest, MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: stringResource(R.string.drafts_untitled),
                style = MaterialTheme.typography.bodyLarge,
                color = if (name != null) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.drafts_saved_ago, relativeAgo(record.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.action_delete),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Coarse "saved N ago" label. Pure and JVM-testable — for a draft's last-saved
 * stamp the exact minute matters far less than "recent" vs "a while back".
 *
 * Returns a bare quantity ("3h", "2d"); the surrounding sentence is localized by
 * `drafts_saved_ago`, so no per-unit plural catalog entries are needed.
 */
fun relativeAgo(
    then: OffsetDateTime,
    now: OffsetDateTime = OffsetDateTime.now(),
): String {
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}
