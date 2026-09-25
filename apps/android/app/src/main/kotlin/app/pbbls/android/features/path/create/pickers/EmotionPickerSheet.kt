package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/**
 * One emotion as an M3 `FilterChip`: the state is announced ("selected") and
 * drawn twice — the emotion's own palette fill (server data, never a scheme
 * role) and a check mark — so selection no longer rests on colour alone (#854).
 */
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
        modifier = modifier.heightIn(min = 48.dp),
        leadingIcon = { Text(text = emotion.emoji, style = MaterialTheme.typography.titleLarge) },
        trailingIcon =
            if (selected) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        shape = MaterialTheme.shapes.medium,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = palette.surface,
                labelColor = colors.onSurface,
                selectedContainerColor = palette.primary,
                selectedLabelColor = palette.light,
                selectedTrailingIconColor = palette.light,
            ),
        border = null,
    )
}

/**
 * Cancel · centered title · Done for the sheet pickers — a stock
 * `CenterAlignedTopAppBar` with no window insets (the sheet owns those) on the
 * sheet's own container colour (#854). `internal` so the sibling
 * [SoulPickerSheet] and [ValencePickerSheet] reuse it without a second copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetToolbar(
    title: String,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        title = { Text(text = title, modifier = Modifier.semantics { heading() }) },
        modifier = modifier,
        navigationIcon = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
        actions = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}
