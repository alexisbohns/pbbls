package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.path.create.CategoryGroup
import app.pbbls.android.features.path.create.EmotionPickerGrouping

/**
 * The emotion picker (D5/D14) — ports iOS `EmotionPickerSheet`. Reads the
 * palette cache, groups emotions by category ordered for the current valence
 * (pure [EmotionPickerGrouping]), and stages a single selection with
 * tap-again-to-clear, committed on Done. Pure [EmotionPickerBody] renders the
 * grouped grid for screenshot previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionPickerSheet(
    currentEmotionId: String?,
    valence: Valence?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    val palettes = LocalEmotionPaletteService.current
    var staged by remember { mutableStateOf(currentEmotionId) }
    val groups =
        remember(palettes.byEmotionId, valence) {
            EmotionPickerGrouping.groups(palettes.byEmotionId.values, valence)
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SheetToolbar(
                title = stringResource(R.string.create_emotion_title),
                onCancel = onDismiss,
                onDone = { onSelected(staged) },
            )
            EmotionPickerBody(
                groups = groups,
                stagedId = staged,
                onToggle = { id -> staged = if (staged == id) null else id },
            )
        }
    }
}

@Composable
fun EmotionPickerBody(
    groups: List<CategoryGroup>,
    stagedId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // referenceName is @Composable — resolve every localized name up-front in a
    // for-loop so the sort/lookups below stay pure (a @Composable call inside a
    // non-inline sort lambda would not compile).
    val emotionNames = HashMap<String, String>()
    for (group in groups) {
        for (row in group.rows) {
            emotionNames[row.id] =
                ReferenceStrings.referenceName(ReferenceType.EMOTION, row.slug, row.name)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        groups.forEach { group ->
            val header =
                ReferenceStrings.referenceName(ReferenceType.EMOTION_CATEGORY, group.categorySlug, group.categoryName)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(group.palette.primary))
                    Text(header, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                }
                val sortedRows = group.rows.sortedBy { (emotionNames[it.id] ?: it.name).lowercase() }
                sortedRows.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowPair.forEach { emotion ->
                            EmotionChip(
                                emotion = emotion,
                                label = emotionNames[emotion.id] ?: emotion.name,
                                selected = emotion.id == stagedId,
                                onClick = { onToggle(emotion.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionChip(
    emotion: EmotionWithPalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = emotion.palette
    val background = if (selected) palette.primary else palette.surface
    val foreground = if (selected) palette.light else colors.onSurface
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emotion.emoji, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = foreground, maxLines = 1)
    }
}

/**
 * Cancel · centered title · Done toolbar for the sheet pickers (shared by the
 * emotion and soul sheets). `internal` so the sibling [SoulPickerSheet] reuses
 * it without a second copy.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SheetToolbar(
    title: String,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDone) {
            Text(
                text = stringResource(R.string.action_done),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}
