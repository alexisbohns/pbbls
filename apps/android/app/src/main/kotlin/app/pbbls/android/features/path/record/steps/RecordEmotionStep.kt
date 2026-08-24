package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.pbbls.android.features.path.create.EmotionPickerGrouping
import app.pbbls.android.features.path.create.pickers.EmotionPickerBody
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.services.LocalEmotionPaletteService

/**
 * Step 4 — the emotion — ports iOS `RecordEmotionStep`. Categories arrive
 * ordered by the valence chosen on step 3, which is the reason valence comes
 * first (M58 D2).
 *
 * Unlike `EmotionPickerSheet` there is no staging and no tap-again-to-clear: a
 * step that advances on tap cannot be cancelled, so the tap is the commit.
 */
@Composable
fun RecordEmotionStep(
    selectedId: String?,
    valence: Valence?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palettes = LocalEmotionPaletteService.current
    val groups =
        remember(palettes.byEmotionId, valence) {
            EmotionPickerGrouping.groups(palettes.byEmotionId.values, valence)
        }
    EmotionPickerBody(
        groups = groups,
        stagedId = selectedId,
        onToggle = onSelect,
        modifier = modifier,
    )
}
