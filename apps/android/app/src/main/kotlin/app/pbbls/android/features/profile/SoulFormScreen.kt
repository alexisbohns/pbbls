package app.pbbls.android.features.profile

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.DashedPlaceholder
import app.pbbls.android.features.glyph.models.SystemGlyph
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.LocalSoulsService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "soul-form"

/**
 * Create/edit form for a soul — merges iOS `CreateSoulSheet` + `EditSoulSheet`
 * (which differ only in initial state and the write call) into one full-screen
 * surface (D5): name field + glyph row → [GlyphPickerSheet] (the M39 D12
 * parked glyph slot, landing here per D8). [original] `null` means create —
 * the glyph defaults to [SystemGlyph.DEFAULT] and its strokes are fetched for
 * the thumbnail. Writes are direct RLS-scoped single-table calls (D6);
 * `souls_glyph_usable` enforces glyph ownership server-side.
 *
 * Deviation from iOS: the picker already returns the full [Glyph], so the
 * post-pick thumbnail refetch iOS carries ("tracked separately" in its
 * comments) is dropped rather than ported.
 */
@Composable
fun SoulFormScreen(
    original: SoulWithGlyph?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soulsService = LocalSoulsService.current
    val system = PebblesTheme.colors.system
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var glyphId by remember { mutableStateOf(original?.glyphId ?: SystemGlyph.DEFAULT) }
    var currentGlyph by remember { mutableStateOf(original?.glyph) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    var isPresentingPicker by remember { mutableStateOf(false) }

    val canSave =
        soulFormCanSave(
            originalName = original?.name,
            originalGlyphId = original?.glyphId,
            name = name,
            glyphId = glyphId,
        )

    BackHandler(enabled = !isSaving) { onDismiss() }

    // Create starts on the system default glyph; fetch its strokes so the row
    // shows a real thumbnail — the `CreateSoulSheet.loadDefaultGlyph` analog.
    // Re-checked after the fetch: a picker selection made while it was in
    // flight must not be clobbered by the late default.
    LaunchedEffect(Unit) {
        if (currentGlyph == null) {
            try {
                val fetched = soulsService.loadGlyph(SystemGlyph.DEFAULT)
                if (currentGlyph == null && glyphId == SystemGlyph.DEFAULT) {
                    currentGlyph = fetched
                }
            } catch (e: Exception) {
                Log.e(TAG, "default glyph fetch failed", e)
                // The dashed placeholder still works as a tap target.
            }
        }
    }

    fun save() {
        if (!canSave || isSaving) return
        scope.launch {
            isSaving = true
            showSaveError = false
            val trimmed = name.trim()
            try {
                if (original == null) {
                    soulsService.create(name = trimmed, glyphId = glyphId)
                } else {
                    soulsService.update(soulId = original.id, name = trimmed, glyphId = glyphId)
                }
                onSaved()
            } catch (e: Exception) {
                Log.e(TAG, "soul save failed", e)
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
                        if (original == null) R.string.create_soul_title else R.string.soul_edit_title,
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
                header = stringResource(R.string.create_glyph_header),
                rows =
                    listOf(
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { isPresentingPicker = true },
                            ) {
                                val glyph = currentGlyph
                                if (glyph != null) {
                                    GlyphView(
                                        case = GlyphViewCase.DEFAULT,
                                        strokes = glyph.strokes,
                                        viewBox = glyph.viewBox,
                                        side = 32.dp,
                                    )
                                } else {
                                    DashedPlaceholder()
                                }
                                PebblesText(
                                    text = stringResource(R.string.soul_form_glyph_choose),
                                    style = PebblesTypography.body,
                                    color = system.foreground,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = system.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    ),
            )

            if (showSaveError) {
                PebblesText(
                    text =
                        stringResource(
                            if (original == null) R.string.soul_save_error else R.string.settings_save_error,
                        ),
                    style = PebblesTypography.subhead,
                    color = PebblesDestructive,
                )
            }
        }
    }

    if (isPresentingPicker) {
        GlyphPickerSheet(
            currentGlyphId = glyphId,
            onDismiss = { isPresentingPicker = false },
            onSelected = { glyph ->
                glyphId = glyph.id
                currentGlyph = glyph
                isPresentingPicker = false
            },
        )
    }
}

/**
 * Pure save gate — merges `SoulDraft.isValid` with `EditSoulSheet.canSave`:
 * the trimmed name must be non-empty, and in edit mode something must have
 * changed (name or glyph). Create mode ([originalName] null) needs validity only.
 */
internal fun soulFormCanSave(
    originalName: String?,
    originalGlyphId: String?,
    name: String,
    glyphId: String,
): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    if (originalName == null) return true
    return trimmed != originalName || glyphId != originalGlyphId
}
