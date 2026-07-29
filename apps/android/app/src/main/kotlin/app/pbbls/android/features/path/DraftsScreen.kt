package app.pbbls.android.features.path

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.services.LocalPebbleDraftsService
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

private const val TAG = "drafts"

/**
 * Unpublished quick captures, most recently saved first (M47) — ports iOS
 * `DraftsListSheet`.
 *
 * Its own surface rather than a section inside the Path timeline, whose week
 * grouping, ripple, bounce and stats all assume real pebbles (design D4).
 * Tapping a row resumes it in `CreatePebbleScreen`.
 *
 * Self-applies `safeDrawingPadding()`, so the caller composes it in the OUTER
 * (unpadded) Box alongside the other full-screen covers (D5).
 */
@Composable
fun DraftsScreen(
    onResume: (PebbleDraftRecord) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reloadKey: Int = 0,
) {
    val draftsService = LocalPebbleDraftsService.current
    val scope = rememberCoroutineScope()

    var drafts by remember { mutableStateOf<List<PebbleDraftRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    // Retry token, the EditPebbleScreen idiom.
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey, retryKey) {
        isLoading = true
        loadFailed = false
        try {
            drafts = draftsService.list()
            isLoading = false
        } catch (e: Exception) {
            Log.e(TAG, "drafts load failed", e)
            loadFailed = true
            isLoading = false
        }
    }

    BackHandler { onDismiss() }

    DraftsContent(
        drafts = drafts,
        isLoading = isLoading,
        loadFailed = loadFailed,
        onRetry = { retryKey++ },
        onResume = onResume,
        onDelete = { record ->
            // Optimistic: the row leaves immediately and a failure reloads the
            // truth back in.
            drafts = drafts.filterNot { it.id == record.id }
            scope.launch {
                try {
                    draftsService.delete(record.id)
                } catch (e: Exception) {
                    Log.e(TAG, "draft delete failed", e)
                    retryKey++
                }
            }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * Stateless drafts list — what screenshot previews drive. Takes its data as
 * parameters rather than reading services, the same previewability rule the
 * funnel and Path screens follow.
 */
@Composable
fun DraftsContent(
    drafts: List<PebbleDraftRecord>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onResume: (PebbleDraftRecord) -> Unit,
    onDelete: (PebbleDraftRecord) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.drafts_title),
            titleStyle = PebblesTypography.headlineEmphasized,
            titleColor = system.foreground,
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_done),
                    onClick = onDismiss,
                    color = accent.primary,
                )
            },
        )

        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent.primary)
                }

            loadFailed ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PebblesText(
                            text = stringResource(R.string.drafts_load_error),
                            style = PebblesTypography.body,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) {
                            PebblesText(
                                text = stringResource(R.string.action_retry),
                                style = PebblesTypography.body,
                                color = accent.primary,
                            )
                        }
                    }
                }

            drafts.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PebblesText(
                        text = stringResource(R.string.drafts_empty),
                        style = PebblesTypography.body,
                        color = system.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(drafts, key = { it.id }) { record ->
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
    val system = PebblesTheme.colors.system
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
                    .background(system.muted, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            PebblesText(
                text = name ?: stringResource(R.string.drafts_untitled),
                style = PebblesTypography.body,
                color = if (name != null) system.foreground else system.secondary,
                maxLines = 1,
            )
            PebblesText(
                text = stringResource(R.string.drafts_saved_ago, relativeAgo(record.updatedAt)),
                style = PebblesTypography.meta,
                color = system.secondary,
                maxLines = 1,
            )
        }
        TextButton(onClick = onDelete) {
            PebblesText(
                text = stringResource(R.string.action_delete),
                style = PebblesTypography.body,
                color = system.secondary,
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
