package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.ui.achievementDescription
import app.pbbls.android.core.ui.achievementFamilyIcon
import app.pbbls.android.core.ui.achievementGroupName
import app.pbbls.android.core.ui.achievementTitle
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Achievements grid (M48, D8) — ports web `/achievements` and iOS
 * `AchievementsView`: badges grouped by family in catalog `sort_order`, locked
 * ones greyed alongside the unlocked. Opening the screen fires
 * `check_achievements()` first — that call *is* the retroactive grant — then
 * reads catalog + unlocks, so history appears unlocked on first visit with no
 * celebration chain (the capsule is for the mutation path only).
 *
 * The load lives in [AchievementsViewModel] (#849), so rotating the device no
 * longer throws the grid away and re-runs all three calls. The composable owns
 * no data state at all now; it reads one [AchievementsUiState] and renders it.
 */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AchievementsScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * Stateless host: the chrome plus the [AchievementsUiState] switch.
 *
 * Split out from the `hiltViewModel()` overload above so the screen's own top
 * bar and empty/error/loading branches are renderable from a preview — until
 * now only the inner grid was, and a regression in this layer had nothing
 * watching it (`apps/android/CLAUDE.md`, "Screens that read services cannot be
 * previewed").
 */
@Composable
fun AchievementsScreen(
    uiState: AchievementsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.achievements_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a case added to AchievementsUiState must be
        // rendered here or the build fails, which is the whole point of the
        // sealed interface over the booleans this replaced.
        when (uiState) {
            AchievementsUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }

            is AchievementsUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(uiState.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.profile_retry),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }

            is AchievementsUiState.Content -> AchievementsContent(state = uiState)
        }
    }
}

/**
 * Stateless grid layer, preview/screenshot-friendly.
 *
 * Takes the [AchievementsUiState.Content] rather than its two fields so the
 * family grouping it renders is the one the state already computed — recomputing
 * it here is what this composable used to do on every recomposition.
 */
@Composable
fun AchievementsContent(
    state: AchievementsUiState.Content,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups
    val unlockedAt = state.unlockedAt
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        contentPadding = PaddingValues(PebblesTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        modifier = modifier.fillMaxSize(),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.family}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = achievementGroupName(group.family),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(group.records, key = { it.id }) { record ->
                AchievementBadgeCell(record = record, unlockedAt = unlockedAt[record.id])
            }
        }
    }
}

/**
 * One badge tile. Locked state reads through more than colour: muted styling
 * plus a lock mark and an explicit caption, mirroring web and iOS.
 */
@Composable
private fun AchievementBadgeCell(
    record: AchievementRecord,
    unlockedAt: OffsetDateTime?,
) {
    val colors = MaterialTheme.colorScheme
    val isUnlocked = unlockedAt != null

    // Locked cards fade as a whole, border included.
    OutlinedCard(modifier = Modifier.fillMaxWidth().alpha(if (isUnlocked) 1f else 0.72f)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PebblesTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(achievementFamilyIcon(record.family)),
                    contentDescription = null,
                    // Locked: a faint family mark. The lock and the caption below
                    // carry the state legibly, so the mark itself needn't clear 3:1.
                    tint = if (isUnlocked) colors.primary else colors.outlineVariant,
                    modifier = Modifier.size(PebblesIconToken.LARGE.size),
                )
                Spacer(Modifier.weight(1f))
                if (!isUnlocked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
                    )
                }
            }
            Text(
                text = achievementTitle(record),
                style = MaterialTheme.typography.titleMedium,
                color = if (isUnlocked) colors.onSurface else colors.onSurfaceVariant,
            )
            achievementDescription(record)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (unlockedAt != null) {
                Text(
                    text =
                        stringResource(
                            R.string.achievement_unlocked_on,
                            unlockedAt.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.achievement_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** One family section: records already in catalog `sort_order`. */
internal data class AchievementFamilyGroup(
    val family: String,
    val records: List<AchievementRecord>,
)

/**
 * Families in first-appearance order (the catalog is sorted by `sort_order`,
 * which the migration blocks per family). Inactive badges only show once
 * earned — retiring one hides it from the ladder without revoking anyone's
 * unlock. Pure so the filter is JVM-testable.
 */
internal fun visibleFamilyGroups(
    catalog: List<AchievementRecord>,
    unlockedIds: Set<String>,
): List<AchievementFamilyGroup> {
    val visible = catalog.filter { it.isActive || it.id in unlockedIds }
    val order = mutableListOf<String>()
    val byFamily = mutableMapOf<String, MutableList<AchievementRecord>>()
    for (record in visible) {
        if (record.family !in byFamily) order.add(record.family)
        byFamily.getOrPut(record.family) { mutableListOf() }.add(record)
    }
    return order.map { AchievementFamilyGroup(it, byFamily.getValue(it)) }
}
