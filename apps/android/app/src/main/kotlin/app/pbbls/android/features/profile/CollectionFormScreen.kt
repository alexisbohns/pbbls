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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.features.profile.components.labelRes
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.ui.ObserveUiEffects

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
@Composable
fun CollectionFormScreen(
    collectionId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    // `start` is guarded, so a rotation cannot re-seed over the user's edits.
    LaunchedEffect(collectionId) { viewModel.start(collectionId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            CollectionFormEffect.Saved -> onSaved()
            CollectionFormEffect.Dismiss -> onDismiss()
        }
    }

    PebblesScreen(
        modifier = modifier.background(system.background),
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
                        CircularProgressIndicator(
                            color = PebblesTheme.colors.accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::save,
                            enabled = uiState.canSave,
                            color = if (uiState.canSave) system.secondary else system.muted,
                        )
                    }
                },
            )
        },
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
            }
            return@PebblesScreen
        }

        val loadErrorRes = uiState.loadErrorRes
        if (loadErrorRes != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                PebblesText(
                    text = stringResource(loadErrorRes),
                    style = PebblesTypography.body,
                    color = PebblesDestructive,
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
            PebblesListSection(
                header = stringResource(R.string.settings_name_label),
                rows =
                    listOf(
                        {
                            BasicTextField(
                                value = uiState.name,
                                onValueChange = viewModel::onNameChange,
                                singleLine = true,
                                textStyle = PebblesTypography.body.copy(color = system.foreground),
                                cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                                keyboardOptions =
                                    KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                decorationBox = { inner ->
                                    if (uiState.name.isEmpty()) {
                                        PebblesText(
                                            text = stringResource(R.string.create_soul_name_placeholder),
                                            style = PebblesTypography.body,
                                            color = system.muted,
                                        )
                                    }
                                    inner()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    ),
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
                PebblesText(
                    text = stringResource(uiState.saveErrorRes),
                    style = PebblesTypography.subhead,
                    color = PebblesDestructive,
                )
            }
        }
    }
}

/**
 * The segmented-control port: four equal-width capsule toggles (None + the
 * three modes). Selection is carried by accent color, mirroring how the rest
 * of the design system marks selected state. `internal` for screenshots.
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

@Composable
private fun ModeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val shape = RoundedCornerShape(50)
    PebblesText(
        text = label,
        style = PebblesTypography.captionEmphasized,
        color = if (isSelected) accent.primary else system.secondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, if (isSelected) accent.primary else system.muted, shape)
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
