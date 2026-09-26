package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.ProfileEmptyState
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.core.ui.PebbleRow
import app.pbbls.android.features.path.EditPebbleScreen

/**
 * Pushed detail for one soul — ports iOS `SoulDetailView.swift`: compact
 * header (56dp glyph + name + live pebble count), the pebbles tagged with the
 * soul on the shared [PebbleRow] (tap → [EditPebbleScreen] cover, long-press →
 * `delete_pebble` with confirm), and an Edit top-bar action opening
 * [SoulFormScreen] as a cover (D9 surface swap). The NavHost passes only the
 * soul id, so [SoulDetailViewModel] fetches the soul itself (iOS receives the
 * row from the list; deviation noted in the plan).
 *
 * @param showBack False beside its list on a large screen (#940): the list is
 *   the way back, and system back still pops.
 */
@Composable
fun SoulDetailScreen(
    soulId: String,
    onBack: () -> Unit,
    onEditSoul: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    viewModel: SoulDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current

    // Guarded on the id, so a rotation re-runs this without re-fetching.
    LaunchedEffect(soulId) { viewModel.start(soulId) }

    // Returning from the edit form must re-read the soul: the ViewModel is
    // scoped to the back stack entry, which survives the round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    SoulDetailContent(
        uiState = uiState,
        onBack = onBack,
        onEditSoul = onEditSoul,
        showBack = showBack,
        onRetry = viewModel::retry,
        onOpenPebble = viewModel::openPebble,
        onDeletePebble = viewModel::requestDelete,
        paletteFor = { pebble -> pebble.emotion?.let { palettes.palette(it.id) } },
        modifier = modifier,
    )

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

/**
 * One soul without its ViewModel (#940): top bar, header and tagged pebbles.
 * [SoulDetailScreen] wires it and owns the edit cover and delete dialogs.
 *
 * @param showBack False beside its list on a large screen (#940): the list is
 *   the way back, and system back still pops.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoulDetailContent(
    uiState: SoulDetailUiState,
    onBack: () -> Unit,
    onEditSoul: () -> Unit,
    onRetry: () -> Unit,
    onOpenPebble: (String) -> Unit,
    onDeletePebble: (Pebble) -> Unit,
    paletteFor: (Pebble) -> EmotionPalette?,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = (uiState as? SoulDetailUiState.Content)?.soul?.name.orEmpty(),
                leading = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.profile_back_a11y),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                trailing = {
                    if (uiState is SoulDetailUiState.Content) {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.pebble_detail_edit),
                            onClick = onEditSoul,
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new SoulDetailUiState case must be rendered.
        when (val state = uiState) {
            SoulDetailUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }

            is SoulDetailUiState.Error ->
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

            is SoulDetailUiState.Content ->
                Column(Modifier.fillMaxSize()) {
                    SoulHeader(soul = state.soul, pebbleCount = state.pebbles.size)
                    if (state.pebbles.isEmpty()) {
                        ProfileEmptyState(
                            title = stringResource(R.string.soul_detail_empty_title),
                            message = stringResource(R.string.soul_detail_empty_message),
                        )
                    } else {
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
                                    state.pebbles.map { pebble ->
                                        {
                                            PebbleRow(
                                                pebble = pebble,
                                                palette = paletteFor(pebble),
                                                onTap = { onOpenPebble(pebble.id) },
                                                onDelete = { onDeletePebble(pebble) },
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }
        }
    }
}

/** Compact identity header — 56dp glyph + hand-face name + live pebble count. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoulHeader(
    soul: SoulWithGlyph,
    pebbleCount: Int,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphView(
            case = GlyphViewCase.DEFAULT,
            strokes = soul.glyph.strokes,
            viewBox = soul.glyph.viewBox,
            side = 56.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = soul.name,
                style = PebblesTheme.hand.bodyLeadHand,
                color = colors.onSurface,
            )
            // iOS uses the system caption face; the closest role is
            // labelMediumEmphasized (also the shared PebbleRow date treatment).
            Text(
                text = pluralStringResource(R.plurals.pebbles_count, pebbleCount, pebbleCount),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
