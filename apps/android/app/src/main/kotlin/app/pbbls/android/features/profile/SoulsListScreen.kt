package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.features.shared.SoulItem
import app.pbbls.android.features.shared.SoulItemCase
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography

/**
 * The souls grid — ports iOS `SoulsListView.swift` as a NavHost push (D1):
 * adaptive-96 grid of shared [SoulItem] cells, "+" top-bar create, tap → the
 * detail route, long-press → delete menu + confirm (D7 unifies on the M39 D8
 * idiom over iOS's context menu). [SoulsListViewModel] owns the fetch, the
 * delete and the reference-data refresh that keeps the pebble-form picker in
 * sync; this function is render and callbacks only.
 */
@Composable
fun SoulsListScreen(
    onBack: () -> Unit,
    onOpenSoul: (SoulWithGlyph) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoulsListViewModel = hiltViewModel(),
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
                title = stringResource(R.string.souls_title),
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
                    IconButton(onClick = viewModel::openCreate) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.souls_add_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new SoulsListUiState case must be rendered.
        when (val state = uiState) {
            SoulsListUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is SoulsListUiState.Error ->
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

            is SoulsListUiState.Content ->
                if (state.souls.isEmpty()) {
                    ProfileEmptyState(
                        title = stringResource(R.string.souls_empty_title),
                        message = stringResource(R.string.souls_empty_message),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(PebblesTheme.spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
                    ) {
                        items(state.souls, key = { it.id }) { soul ->
                            SoulCell(
                                soul = soul,
                                onTap = { onOpenSoul(soul) },
                                onDelete = { viewModel.requestDelete(soul) },
                            )
                        }
                    }
                }
        }
    }

    if (covers.isPresentingCreate) {
        SoulFormScreen(
            soulId = null,
            onDismiss = viewModel::closeCreate,
            onSaved = viewModel::onSoulSaved,
            modifier = Modifier.fillMaxSize(),
        )
    }

    covers.pendingDeletion?.let { target ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.souls_delete_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (covers.didDeleteFail) DeleteErrorDialog(onDismiss = viewModel::dismissDeleteError)
}

/** Grid cell wrapper anchoring the long-press delete menu — the PebbleRow menu idiom. */
@Composable
private fun SoulCell(
    soul: SoulWithGlyph,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        SoulItem(
            case = SoulItemCase.DEFAULT,
            soul = soul,
            count = soul.pebblesCount,
            onTap = onTap,
            onLongPress = { menuExpanded = true },
        )
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
