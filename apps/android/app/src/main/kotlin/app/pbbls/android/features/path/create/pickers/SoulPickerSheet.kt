package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.SoulItem
import app.pbbls.android.core.ui.SoulItemCase
import kotlinx.coroutines.launch

/**
 * The souls picker (D5/D12) — ports iOS `SoulPickerSheet` + `CreateSoulSheet`.
 * A multi-select `ModalBottomSheet` over the reference-data souls on the
 * shared [SoulItem] cell (the M41 #459-contract lift that replaced this file's
 * private tiles), plus a leading create tile that opens the third-level
 * [CreateSoulDialog] (a plain dialog, well-behaved over the sheet). Inline
 * creation inserts a name-only soul and auto-selects it (D11 — `createSoul`
 * updates the souls cache so the new tile renders immediately). Pure
 * [SoulPickerBody] powers screenshot previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulPickerSheet(
    currentSelection: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onAchievementCheck: () -> Unit = {},
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
                        onAchievementCheck()
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
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (souls.isEmpty()) {
            Text(
                text = stringResource(R.string.create_souls_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoulItem(
                case = SoulItemCase.CREATE,
                soul = null,
                count = null,
                onTap = onCreateTap,
            )
            souls.forEach { soul ->
                // No selection anywhere → everything reads as available
                // (DEFAULT); once a selection exists, unselected souls mute.
                val case =
                    when {
                        soul.id in selection -> SoulItemCase.SELECTED
                        selection.isEmpty() -> SoulItemCase.DEFAULT
                        else -> SoulItemCase.UNSELECTED
                    }
                SoulItem(
                    case = case,
                    soul = soul,
                    count = soul.pebblesCount,
                    onTap = { onToggle(soul.id) },
                )
            }
        }
    }
}

/**
 * The name-only soul creation dialog (D12) — the third UI level (a plain
 * `AlertDialog` over the sheet, which owns its own IME window). Save is disabled
 * until the name is non-blank. `internal` so the screenshot file can render it
 * directly. Ports `CreateSoulSheet` minus the glyph row (souls carry a
 * system-glyph default).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CreateSoulDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.create_soul_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_soul_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (name.isNotBlank()) colors.primary else colors.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}
