package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.components.PebbleRow
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.profile.components.CollectionModeBadge
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pushed detail for one collection — ports iOS `CollectionDetailView.swift`:
 * a subheader row (mode badge + live pebble count), the collection's pebbles
 * grouped by calendar month with locale-formatted headers (D14), tap →
 * [EditPebbleScreen] cover, long-press → `delete_pebble` with confirm, and an
 * Edit top-bar action opening [CollectionFormScreen] as a cover (D9 surface
 * swap). The NavHost passes only the collection id, so
 * [CollectionDetailViewModel] fetches the collection itself (same named
 * deviation as the soul detail), and it owns the month grouping — only the
 * locale-dependent header formatting stays here, because only the view knows
 * the active locale.
 */
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system

    // Guarded on the id, so a rotation re-runs this without re-fetching.
    LaunchedEffect(collectionId) { viewModel.start(collectionId) }

    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = (uiState as? CollectionDetailUiState.Content)?.collection?.name.orEmpty(),
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
                    if (uiState is CollectionDetailUiState.Content) {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.pebble_detail_edit),
                            onClick = viewModel::openEdit,
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new CollectionDetailUiState case must be rendered.
        when (val state = uiState) {
            CollectionDetailUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is CollectionDetailUiState.Error ->
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

            is CollectionDetailUiState.Content ->
                if (state.pebbles.isEmpty()) {
                    ProfileEmptyState(
                        title = stringResource(R.string.soul_detail_empty_title),
                        message = stringResource(R.string.collection_detail_empty_message),
                    )
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
                    ) {
                        PebblesListSection(
                            rows =
                                listOf(
                                    {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            CollectionModeBadge(mode = state.collection.mode)
                                            Spacer(Modifier.weight(1f))
                                            PebblesText(
                                                text = pebbleCountLabel(state.pebbles.size),
                                                style = PebblesTypography.captionEmphasized,
                                                color = system.secondary,
                                            )
                                        }
                                    },
                                ),
                        )
                        state.groups.forEach { (month, monthPebbles) ->
                            PebblesListSection(
                                header = month.format(monthFormatter),
                                rows =
                                    monthPebbles.map { pebble ->
                                        {
                                            PebbleRow(
                                                pebble = pebble,
                                                palette = pebble.emotion?.let { palettes.palette(it.id) },
                                                onTap = { viewModel.openPebble(pebble.id) },
                                                onDelete = { viewModel.requestDelete(pebble) },
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }
        }
    }

    if (covers.isPresentingEdit) {
        (uiState as? CollectionDetailUiState.Content)?.let { content ->
            CollectionFormScreen(
                collectionId = content.collection.id,
                onDismiss = viewModel::closeEdit,
                onSaved = viewModel::onCollectionSaved,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    covers.editingPebbleId?.let { pebbleId ->
        EditPebbleScreen(
            pebbleId = pebbleId,
            onDismiss = viewModel::closePebble,
            onSaved = viewModel::onPebbleSaved,
            modifier = Modifier.fillMaxSize(),
        )
    }

    covers.pendingDeletion?.let { target ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.pebble_delete_confirm_message),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (covers.didDeleteFail) DeleteErrorDialog(onDismiss = viewModel::dismissDeleteError)
}
