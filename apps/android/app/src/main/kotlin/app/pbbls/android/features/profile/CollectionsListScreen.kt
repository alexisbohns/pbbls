package app.pbbls.android.features.profile

import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.profile.components.CollectionModeBadge
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.LocalCollectionsService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "collections-list"

/**
 * The collections list — ports iOS `CollectionsListView.swift` as a NavHost
 * push (D1): bordered rows (name + mode badge + count), "+" top-bar create,
 * pull-to-refresh, tap → the detail route, long-press → delete menu + confirm
 * (D7 unifies on the M39 D8 idiom over iOS's swipe action). The screen fetches
 * its own rows (D10) and refreshes the reference-data collections cache after
 * every mutation so the pebble-form picker stays in sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsListScreen(
    onBack: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectionsService = LocalCollectionsService.current
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var items by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    var isPresentingCreate by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<Collection?>(null) }
    var deleteError by remember { mutableStateOf(false) }

    suspend fun loadItems() {
        loadFailed = false
        try {
            items = collectionsService.list()
        } catch (e: Exception) {
            Log.e(TAG, "collections fetch failed", e)
            loadFailed = true
        }
    }

    LaunchedEffect(loadKey) {
        isLoading = true
        loadItems()
        isLoading = false
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
                    IconButton(onClick = { isPresentingCreate = true }) {
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
                        text = stringResource(R.string.collections_load_error),
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

            items.isEmpty() ->
                ProfileEmptyState(
                    title = stringResource(R.string.collections_empty_title),
                    message = stringResource(R.string.collections_empty_message),
                )

            else ->
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            loadItems()
                            isRefreshing = false
                        }
                    },
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
                                items.map { collection ->
                                    {
                                        CollectionRow(
                                            collection = collection,
                                            onTap = { onOpenCollection(collection) },
                                            onDelete = { pendingDeletion = collection },
                                        )
                                    }
                                },
                        )
                    }
                }
        }
    }

    if (isPresentingCreate) {
        CollectionFormScreen(
            original = null,
            onDismiss = { isPresentingCreate = false },
            onSaved = {
                isPresentingCreate = false
                loadKey++
                scope.launch { refs.refreshCollections() }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    val target = pendingDeletion
    if (target != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.collections_delete_message),
            onConfirm = {
                pendingDeletion = null
                scope.launch {
                    try {
                        collectionsService.delete(target.id)
                        loadKey++
                        refs.refreshCollections()
                    } catch (e: Exception) {
                        Log.e(TAG, "delete collection failed", e)
                        deleteError = true
                    }
                }
            },
            onDismiss = { pendingDeletion = null },
        )
    }
    if (deleteError) DeleteErrorDialog(onDismiss = { deleteError = false })
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
