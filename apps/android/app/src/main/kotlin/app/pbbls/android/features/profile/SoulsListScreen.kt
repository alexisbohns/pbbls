package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.ProfileEmptyState
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.souls.SoulChooserBody
import app.pbbls.android.core.ui.souls.SoulChooserMode
import app.pbbls.android.core.ui.souls.SoulChooserSearchButton
import app.pbbls.android.core.ui.souls.SoulChooserSearchField
import app.pbbls.android.core.ui.souls.SoulChooserSlot
import app.pbbls.android.core.ui.souls.SoulChooserState
import app.pbbls.android.core.ui.souls.rememberSoulChooserState

/**
 * The People tab (souls spec S10, mock 13): the souls chooser in browse mode — usual tiles, A–Z
 * rows with a count, a lens and a plus that open the search field — under a "People" bar with the
 * Circle button. Tap opens the soul's page (list-detail beside it on wide windows), long press
 * opens the delete menu then its confirm (the M39 D8 idiom; D7). This function wires
 * [SoulsListViewModel] — the fetch, the delete, the inline create and the reference-data refresh
 * that keeps the pebble-form picker in sync — and owns the delete dialogs; [SoulsListContent]
 * renders (#940).
 *
 * @param onOpenCircle Opens Connections, the People tab's way to your circle
 *   (#1383 D14), where iOS has it (`SoulsListView`'s leading toolbar item).
 *
 * @param onDeleteConfirmed Called with the deleted item's id once the user
 *   confirms, so a detail open beside this list can close (#940). It runs on
 *   confirm, not on success: if the delete fails, the error dialog says so and
 *   the item can be reopened.
 */
@Composable
fun SoulsListScreen(
    onOpenSoul: (SoulWithGlyph) -> Unit,
    onOpenCircle: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteConfirmed: (id: String) -> Unit = {},
    viewModel: SoulsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    // `Content` means loaded: until then the list is a placeholder and must not prune anything.
    val content = uiState as? SoulsListUiState.Content
    val chooser =
        rememberSoulChooserState(
            mode = SoulChooserMode.Browse,
            souls = content?.souls.orEmpty(),
            isLoaded = content != null,
        )

    // Returning from the detail must re-read the list: the ViewModel is
    // scoped to the back stack entry, which survives the round trip that
    // used to rebuild the screen and re-run its load.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    // Home's first five asked for it (#1383 D11), taken once by the ViewModel on resume: the
    // plus with no query opens the field, so that is what the ask does.
    ObserveUiEffects(viewModel.createRequests) { chooser.openSearch() }
    ObserveUiEffects(viewModel.createOutcomes) { outcome ->
        when (outcome) {
            is SoulCreateOutcome.Created -> {
                chooser.didCreate(outcome.soul)
                // The drawing is picked on the soul's page.
                onOpenSoul(outcome.soul)
            }
            SoulCreateOutcome.Failed -> chooser.createErrorRes = R.string.soul_save_error
        }
    }

    SoulsListContent(
        uiState = uiState,
        chooser = chooser,
        onOpenSoul = onOpenSoul,
        onCreate = viewModel::createSoul,
        onOpenCircle = onOpenCircle,
        onRetry = viewModel::retry,
        onDeleteSoul = viewModel::requestDelete,
        modifier = modifier,
        hapticsOn = viewModel.hapticsOn,
    )

    covers.pendingDeletion?.let { target ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.souls_delete_message),
            onConfirm = {
                // `target` was captured before confirmDelete clears it.
                viewModel.confirmDelete()
                onDeleteConfirmed(target.id)
            },
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (covers.didDeleteFail) DeleteErrorDialog(onDismiss = viewModel::dismissDeleteError)
}

/**
 * The People tab without its ViewModel (#940): the top bar (or the search field), the three states
 * and the chooser. [SoulsListScreen] wires it; screenshots drive it directly with their own
 * [chooser]. [hapticsOn] is Pebbles' haptics switch (#1242 D23), for the long press and the taps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoulsListContent(
    uiState: SoulsListUiState,
    chooser: SoulChooserState,
    onOpenSoul: (SoulWithGlyph) -> Unit,
    onCreate: (String) -> Unit,
    onOpenCircle: () -> Unit,
    onRetry: () -> Unit,
    onDeleteSoul: (SoulWithGlyph) -> Unit,
    hapticsOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val buzz = rememberTapHaptics { hapticsOn }
    // The soul and slot whose delete menu is open: a usual soul is a tile and a row, and only the
    // one long-pressed opens. The menu is drawn inside that item, so it anchors there.
    var menuFor by remember { mutableStateOf<Pair<String, SoulChooserSlot>?>(null) }

    // Back closes the open search field first, then leaves as usual.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = chooser.isSearching) { chooser.closeSearch() }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            if (chooser.isSearching) {
                SoulChooserSearchField(
                    state = chooser,
                    onClose = chooser::closeSearch,
                    modifier = Modifier.statusBarsPadding().padding(top = PebblesTheme.spacing.sm, bottom = PebblesTheme.spacing.sm),
                )
            } else {
                PebblesTopBar(
                    title = stringResource(R.string.tab_people),
                    trailing = {
                        SoulChooserSearchButton(state = chooser)
                        IconButton(onClick = onOpenCircle) {
                            PbblsIconImage(
                                icon = PbblsIcon.PEOPLE,
                                size = PbblsIconSize.TOOLBAR,
                                contentDescription = stringResource(R.string.people_circle_a11y),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        },
    ) {
        // Exhaustive with no `else`: a new SoulsListUiState case must be rendered.
        when (val state = uiState) {
            SoulsListUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }

            is SoulsListUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(state.messageRes),
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

            is SoulsListUiState.Content ->
                Box(Modifier.fillMaxSize()) {
                    // Nobody yet: the empty state sits behind the chooser, whose plus still adds one.
                    if (state.souls.isEmpty() && !chooser.isSearching) {
                        ProfileEmptyState(
                            title = stringResource(R.string.souls_empty_title),
                            message = stringResource(R.string.souls_empty_message),
                        )
                    }
                    SoulChooserBody(
                        state = chooser,
                        isLoaded = true,
                        loadFailed = false,
                        onRetry = onRetry,
                        onOpenSoul = onOpenSoul,
                        onCreate = onCreate,
                        onHaptic = buzz,
                        onLongClickSoul = { soul, slot -> menuFor = soul.id to slot },
                        hapticsOn = hapticsOn,
                        itemMenu = { soul, slot ->
                            DeleteMenu(
                                expanded = menuFor == (soul.id to slot),
                                onDismiss = { menuFor = null },
                                onDelete = {
                                    menuFor = null
                                    onDeleteSoul(soul)
                                },
                            )
                        },
                    )
                }
        }
    }
}

/** The long-press delete menu, anchored on the tile or row it is drawn in — the PebbleRow menu idiom. */
@Composable
private fun DeleteMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.pebble_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                PbblsIconImage(
                    icon = PbblsIcon.TRASH,
                    size = PbblsIconSize.ROW,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = onDelete,
        )
    }
}
