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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.components.PebbleRow
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography

/**
 * Pushed detail for one soul — ports iOS `SoulDetailView.swift`: compact
 * header (56dp glyph + name + live pebble count), the pebbles tagged with the
 * soul on the shared [PebbleRow] (tap → [EditPebbleScreen] cover, long-press →
 * `delete_pebble` with confirm), and an Edit top-bar action opening
 * [SoulFormScreen] as a cover (D9 surface swap). The NavHost passes only the
 * soul id, so [SoulDetailViewModel] fetches the soul itself (iOS receives the
 * row from the list; deviation noted in the plan).
 */
@Composable
fun SoulDetailScreen(
    soulId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoulDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system

    // Guarded on the id, so a rotation re-runs this without re-fetching.
    LaunchedEffect(soulId) { viewModel.start(soulId) }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = (uiState as? SoulDetailUiState.Content)?.soul?.name.orEmpty(),
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
                    if (uiState is SoulDetailUiState.Content) {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.pebble_detail_edit),
                            onClick = viewModel::openEdit,
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
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is SoulDetailUiState.Error ->
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
        (uiState as? SoulDetailUiState.Content)?.let { content ->
            SoulFormScreen(
                original = content.soul,
                onDismiss = viewModel::closeEdit,
                onSaved = viewModel::onSoulSaved,
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

/** Compact identity header — 56dp glyph + hand-face name + live pebble count. */
@Composable
private fun SoulHeader(
    soul: SoulWithGlyph,
    pebbleCount: Int,
) {
    val system = PebblesTheme.colors.system
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
            PebblesText(
                text = soul.name,
                style = PebblesTypography.bodyLeadHand,
                color = system.foreground,
            )
            // iOS uses the system caption face; the token set's closest match
            // is captionEmphasized (also the shared PebbleRow date treatment).
            PebblesText(
                text = pluralStringResource(R.plurals.pebbles_count, pebbleCount, pebbleCount),
                style = PebblesTypography.captionEmphasized,
                color = system.secondary,
            )
        }
    }
}
