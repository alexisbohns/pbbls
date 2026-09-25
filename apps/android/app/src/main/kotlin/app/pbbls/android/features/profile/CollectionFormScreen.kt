package app.pbbls.android.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.features.profile.components.labelRes

/**
 * Create/edit form for a collection — merges iOS `CreateCollectionSheet` +
 * `EditCollectionSheet` (they differ only in initial state and the write call)
 * into one full-screen surface (D5): name field + mode picker (None / Stack /
 * Pack / Track). [collectionId] `null` means create; a non-null id is fetched
 * by the ViewModel itself (#852), so this screen never receives the
 * row from its caller. Selecting "None" on edit really clears the column — the
 * payload encodes mode as explicit JSON null (see `collectionUpdatePayload`).
 * Writes are direct RLS-scoped single-table calls (D6), driven by
 * [CollectionFormViewModel].
 *
 * Deviation from iOS: the segmented mode control renders as Pebbles-styled
 * capsule toggles rather than Material's segmented buttons — same reason the
 * app avoids Material color roles everywhere (M38 D6).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionFormScreen(
    collectionId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    // `start` is guarded, so a rotation cannot re-seed over the user's edits.
    LaunchedEffect(collectionId) { viewModel.start(collectionId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            CollectionFormEffect.Saved -> onSaved()
            CollectionFormEffect.Dismiss -> onDismiss()
        }
    }

    PebblesScreen(
        modifier = modifier.background(colors.surface),
        topBar = {
            PebblesTopBar(
                title = stringResource(uiState.titleRes),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = viewModel::onDismissRequested,
                    )
                },
                trailing = {
                    if (uiState.isSaving) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::save,
                            enabled = uiState.canSave,
                            color = if (uiState.canSave) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
                        )
                    }
                },
            )
        },
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@PebblesScreen
        }

        val loadErrorRes = uiState.loadErrorRes
        if (loadErrorRes != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(loadErrorRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
            }
            return@PebblesScreen
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.settings_name_label)) },
                placeholder = { Text(stringResource(R.string.create_soul_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            PebblesListSection(
                header = stringResource(R.string.collection_mode_header),
                rows =
                    listOf(
                        {
                            CollectionModePicker(
                                selected = uiState.mode,
                                onSelect = viewModel::onModeChange,
                            )
                        },
                    ),
            )

            if (uiState.didSaveFail) {
                Text(
                    text = stringResource(uiState.saveErrorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }
    }
}

/**
 * The segmented-control port: four equal-width capsule toggles (None + the
 * three modes). Selection is carried by `primary` (label and border) against
 * an `outline` boundary, mirroring how the rest of the design system marks
 * selected state. `internal` for screenshots.
 */
@Composable
internal fun CollectionModePicker(
    selected: CollectionMode?,
    onSelect: (CollectionMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<CollectionMode?> = listOf(null, CollectionMode.STACK, CollectionMode.PACK, CollectionMode.TRACK)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        options.forEach { option ->
            ModeOption(
                label = stringResource(option?.labelRes ?: R.string.collection_mode_none),
                isSelected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Text(
        text = label,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, if (isSelected) colors.primary else colors.outline, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/**
 * Pure save gate — the `EditCollectionSheet.canSave` port with create folded
 * in: the trimmed name must be non-empty, and in edit mode something must have
 * changed (trimmed name or mode — including clearing the mode to null).
 * Create mode ([originalName] null) needs a non-blank name only.
 */
internal fun collectionFormCanSave(
    originalName: String?,
    originalMode: CollectionMode?,
    name: String,
    mode: CollectionMode?,
): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    if (originalName == null) return true
    return trimmed != originalName || mode != originalMode
}
