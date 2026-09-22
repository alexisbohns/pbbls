package app.pbbls.android.features.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.model.Collection
import app.pbbls.android.features.profile.components.CollectionModeBadge
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography

/**
 * The collections list — ports iOS `CollectionsListView.swift` as a NavHost
 * push (D1): bordered rows (name + mode badge + count), "+" top-bar create,
 * pull-to-refresh, tap → the detail route, long-press → delete menu + confirm
 * (D7 unifies on the M39 D8 idiom over iOS's swipe action).
 * [CollectionsListViewModel] owns the fetch (D10), the delete and the
 * reference-data refresh that keeps the pebble-form picker in sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsListScreen(
    onBack: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreateCollection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    // Returning from the detail must re-read the list: the ViewModel is
    // scoped to the back stack entry, which survives the round trip that
    // used to rebuild the screen and re-run its load.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.profile_collections_header),
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
                trailing = {
                    IconButton(onClick = onCreateCollection) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.collections_add_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new CollectionsListUiState case must be rendered.
        when (val state = uiState) {
            CollectionsListUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is CollectionsListUiState.Error ->
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

            is CollectionsListUiState.Content ->
                if (state.collections.isEmpty()) {
                    ProfileEmptyState(
                        title = stringResource(R.string.collections_empty_title),
                        message = stringResource(R.string.collections_empty_message),
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 32.dp),
                        ) {
                            PebblesListSection(
                                rows =
                                    state.collections.map { collection ->
                                        {
                                            CollectionRow(
                                                collection = collection,
                                                onTap = { onOpenCollection(collection) },
                                                onDelete = { viewModel.requestDelete(collection) },
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }
        }
    }

    covers.pendingDeletion?.let { target ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.collections_delete_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (covers.didDeleteFail) DeleteErrorDialog(onDismiss = viewModel::dismissDeleteError)
}

/**
 * Two-line row (name / badge · count) with the long-press delete menu — the
 * `CollectionRow` + swipe-action port, on the PebbleRow menu idiom (D7).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionRow(
    collection: Collection,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onTap, onLongClick = { menuExpanded = true })
                    .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PebblesText(
                text = collection.name,
                style = PebblesTypography.body,
                color = system.foreground,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CollectionModeBadge(mode = collection.mode)
                if (collection.mode != null) {
                    PebblesText(
                        text = "·",
                        style = PebblesTypography.captionEmphasized,
                        color = system.secondary,
                    )
                }
                PebblesText(
                    text = pebbleCountLabel(collection.pebbleCount),
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.pebble_delete),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = PebblesDestructive,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

/** iOS `pebbleCountLabel`: zero gets its own copy instead of "0 pebbles". */
@Composable
internal fun pebbleCountLabel(count: Int): String =
    if (count == 0) {
        stringResource(R.string.collection_count_zero)
    } else {
        pluralStringResource(R.plurals.pebbles_count, count, count)
    }
