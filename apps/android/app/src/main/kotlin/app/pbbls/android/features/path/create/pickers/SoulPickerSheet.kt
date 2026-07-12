package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesTextInput
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

/**
 * The souls picker (D5/D12) — ports iOS `SoulPickerSheet` + `CreateSoulSheet`.
 * A multi-select `ModalBottomSheet` over the reference-data souls, plus a
 * leading "create" tile that opens the third-level [CreateSoulDialog] (a plain
 * dialog, well-behaved over the sheet). Inline creation inserts a name-only
 * soul and auto-selects it (D11 — `createSoul` updates the souls cache so the
 * new tile renders immediately). Pure [SoulPickerBody] powers screenshot
 * previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulPickerSheet(
    currentSelection: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(currentSelection.toSet()) }
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SheetToolbar(
                title = stringResource(R.string.create_souls_title),
                onCancel = onDismiss,
                onDone = { onConfirm(selection.toList()) },
            )
            SoulPickerBody(
                souls = refs.souls,
                selection = selection,
                onToggle = { id -> selection = if (id in selection) selection - id else selection + id },
                onCreateTap = { showCreate = true },
            )
        }
    }
    if (showCreate) {
        CreateSoulDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                scope.launch {
                    val created = refs.createSoul(name)
                    if (created != null) {
                        selection = selection + created.id
                        showCreate = false
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoulPickerBody(
    souls: List<SoulWithGlyph>,
    selection: Set<String>,
    onToggle: (String) -> Unit,
    onCreateTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (souls.isEmpty()) {
            PebblesText(
                text = stringResource(R.string.create_souls_empty),
                style = PebblesTypography.subhead,
                color = system.secondary,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CreateSoulTile(onClick = onCreateTap)
            souls.forEach { soul ->
                val tint =
                    when {
                        selection.isEmpty() -> system.secondary
                        soul.id in selection -> accent.primary
                        else -> system.muted
                    }
                SoulTile(soul = soul, tint = tint, onClick = { onToggle(soul.id) })
            }
        }
    }
}

@Composable
private fun SoulTile(
    soul: SoulWithGlyph,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphImage(
            strokes = soul.glyph.strokes,
            viewBox = soul.glyph.viewBox,
            strokeColor = tint,
            modifier = Modifier.size(64.dp),
        )
        PebblesText(soul.name, PebblesTypography.bodyLeadHand, color = tint, maxLines = 1)
        PebblesText(soul.pebblesCount.toString(), PebblesTypography.meta, color = tint)
    }
}

@Composable
private fun CreateSoulTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            modifier
                .width(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).border(1.dp, system.secondary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PebblesText("+", PebblesTypography.title, color = system.secondary)
        }
        PebblesText(
            text = stringResource(R.string.create_soul_add),
            style = PebblesTypography.meta,
            color = system.secondary,
            maxLines = 1,
        )
    }
}

/**
 * The name-only soul creation dialog (D12) — the third UI level (a plain
 * `AlertDialog` over the sheet, which owns its own IME window). Save is disabled
 * until the name is non-blank. `internal` so the screenshot file can render it
 * directly. Ports `CreateSoulSheet` minus the glyph row (souls carry a
 * system-glyph default).
 */
@Composable
internal fun CreateSoulDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.create_soul_title),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        text = {
            PebblesTextInput(
                placeholder = stringResource(R.string.create_soul_name_placeholder),
                value = name,
                onValueChange = { name = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                PebblesText(
                    text = stringResource(R.string.action_save),
                    style = PebblesTypography.buttonLabel,
                    color = if (name.isNotBlank()) accent.primary else system.muted,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                PebblesText(
                    text = stringResource(R.string.action_cancel),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
    )
}
