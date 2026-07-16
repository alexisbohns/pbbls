package app.pbbls.android.features.profile

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.profile.components.labelRes
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.services.LocalCollectionsService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "collection-form"

/**
 * Create/edit form for a collection — merges iOS `CreateCollectionSheet` +
 * `EditCollectionSheet` (they differ only in initial state and the write call)
 * into one full-screen surface (D5): name field + mode picker (None / Stack /
 * Pack / Track). [original] `null` means create. Selecting "None" on edit
 * really clears the column — the payload encodes mode as explicit JSON null
 * (see `collectionUpdatePayload`). Writes are direct RLS-scoped single-table
 * calls (D6).
 *
 * Deviation from iOS: the segmented mode control renders as Pebbles-styled
 * capsule toggles rather than Material's segmented buttons — same reason the
 * app avoids Material color roles everywhere (M38 D6).
 */
@Composable
fun CollectionFormScreen(
    original: Collection?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectionsService = LocalCollectionsService.current
    val system = PebblesTheme.colors.system
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var mode by remember { mutableStateOf(original?.mode) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }

    val canSave =
        collectionFormCanSave(
            originalName = original?.name,
            originalMode = original?.mode,
            name = name,
            mode = mode,
        )

    BackHandler(enabled = !isSaving) { onDismiss() }

    fun save() {
        if (!canSave || isSaving) return
        scope.launch {
            isSaving = true
            showSaveError = false
            val trimmed = name.trim()
            try {
                if (original == null) {
                    collectionsService.create(name = trimmed, mode = mode)
                } else {
                    collectionsService.update(collectionId = original.id, name = trimmed, mode = mode)
                }
                onSaved()
            } catch (e: Exception) {
                Log.e(TAG, "collection save failed", e)
                showSaveError = true
                isSaving = false
            }
        }
    }

    PebblesScreen(
        modifier = modifier.background(system.background),
        topBar = {
            PebblesTopBar(
                title =
                    stringResource(
                        if (original == null) R.string.profile_collection_new else R.string.collection_edit_title,
                    ),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { if (!isSaving) onDismiss() },
                    )
                },
                trailing = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = PebblesTheme.colors.accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = { save() },
                            enabled = canSave,
                            color = if (canSave) system.secondary else system.muted,
                        )
                    }
                },
            )
        },
    ) {
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
                                value = name,
                                onValueChange = { name = it },
                                singleLine = true,
                                textStyle = PebblesTypography.body.copy(color = system.foreground),
                                cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                                keyboardOptions =
                                    KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                decorationBox = { inner ->
                                    if (name.isEmpty()) {
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
                                selected = mode,
                                onSelect = { mode = it },
                            )
                        },
                    ),
            )

            if (showSaveError) {
                PebblesText(
                    text =
                        stringResource(
                            if (original == null) R.string.collection_save_error else R.string.settings_save_error,
                        ),
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
