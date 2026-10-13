package app.pbbls.android.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.renderHeightDp
import app.pbbls.android.core.ui.render.PebbleThumbnail
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.features.path.create.VisibilityChip

/**
 * Edit pebble (R5): the pebble half of a moment — emotion, domain, valence,
 * souls, collection and privacy — under its stone, saved with
 * `complete_pebble` and nothing else. The moment's own fields and its photo
 * are Edit moment's, so there is no photo picker and no upload to clean up.
 *
 * A pushed entry (#852). Keeps a `NavigationBackHandler`, always enabled
 * (D9): `dismiss()` already no-ops while saving, and a *disabled* handler
 * would let back fall through and pop the screen mid-save.
 */
@Composable
fun EditPebbleScreen(
    momentId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditPebbleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current

    LaunchedEffect(momentId) { viewModel.start(momentId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            EditPebbleEffect.Saved -> onSaved()
            EditPebbleEffect.Dismissed -> onDismiss()
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { viewModel.dismiss() }

    val emotionId = (uiState as? EditPebbleUiState.Content)?.draft?.emotionId
    EditPebbleContent(
        uiState = uiState,
        palette = emotionId?.let(palettes::palette),
        selectedEmotion = emotionId?.let { palettes.byEmotionId[it] },
        onDraftChange = viewModel::onDraftChange,
        onCancel = viewModel::dismiss,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        onAchievementCheck = viewModel::onAchievementCheck,
        modifier = modifier,
    )
}

/**
 * Edit pebble without its ViewModel: the bar, the stone and the three states.
 * [EditPebbleScreen] wires it; screenshots drive it. [palette] and
 * [selectedEmotion] are the draft emotion's, resolved by the caller.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditPebbleContent(
    uiState: EditPebbleUiState,
    palette: EmotionPalette?,
    selectedEmotion: EmotionWithPalette?,
    onDraftChange: (PebbleDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
) {
    val referenceData = LocalReferenceDataService.current
    val colors = MaterialTheme.colorScheme
    val content = uiState as? EditPebbleUiState.Content

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .readableWidth(),
    ) {
        EditTopBar(
            title = stringResource(R.string.edit_pebble_title),
            isSaving = content?.isSaving == true,
            saveEnabled = content?.canSave == true,
            onCancel = onCancel,
            onSave = onSave,
        )
        // Exhaustive with no `else`: a new EditPebbleUiState case must render.
        when (val state = uiState) {
            EditPebbleUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }
            EditPebbleUiState.Error ->
                EditLoadError(onRetry = onRetry)
            is EditPebbleUiState.Content ->
                Column(Modifier.fillMaxSize()) {
                    PebbleForm(
                        draft = state.draft,
                        onDraftChange = onDraftChange,
                        domains = referenceData.domains,
                        souls = referenceData.souls,
                        collections = referenceData.collections,
                        selectedEmotion = selectedEmotion,
                        saveError = state.saveErrorRes?.let { stringResource(it) },
                        header = {
                            val valence = state.draft.valence ?: Valence.NEUTRAL_MEDIUM
                            PebbleThumbnail(
                                valence = valence,
                                glyph = state.glyph,
                                palette = palette,
                                modifier = Modifier.fillMaxWidth().height(valence.sizeGroup.renderHeightDp.dp),
                            )
                        },
                        showMomentFields = false,
                        showPebbleFields = true,
                        modifier = Modifier.weight(1f),
                        onAchievementCheck = onAchievementCheck,
                    )
                    // Grade chip (M51) — mirrors iOS EditPebbleSheet's bottomBar
                    // ToolbarItemGroup.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VisibilityChip(
                            value = state.draft.visibility,
                            onChange = { onDraftChange(state.draft.copy(visibility = it)) },
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
        }
    }
}
