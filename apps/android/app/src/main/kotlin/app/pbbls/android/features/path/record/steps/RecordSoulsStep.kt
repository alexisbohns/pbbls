package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pbbls.android.features.path.create.pickers.CreateSoulDialog
import app.pbbls.android.features.path.create.pickers.SoulPickerBody
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.services.LocalReferenceDataService
import kotlinx.coroutines.launch

/**
 * Step 6 — who was there — ports iOS `RecordSoulsStep`. Multi-select, so a tap
 * never advances; the step's Skip / Done button does (M58 D3). Making souls
 * single-select to keep every step uniform would be a real capability loss, not
 * a simplification.
 *
 * Reads `ReferenceDataService.souls` — already cached and already refreshed
 * after every Profile mutation — and `createSoul` updates that cache in place,
 * so an inline-created soul renders immediately.
 */
@Composable
fun RecordSoulsStep(
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refs = LocalReferenceDataService.current
    val achievements = LocalAchievementsService.current
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }

    SoulPickerBody(
        souls = refs.souls,
        selection = selectedIds.toSet(),
        onToggle = onToggle,
        onCreateTap = { showCreate = true },
        modifier = modifier,
    )

    if (showCreate) {
        CreateSoulDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                scope.launch {
                    val created = refs.createSoul(name)
                    if (created != null) {
                        achievements.fireCheck()
                        // Select it immediately — the user created it *for* this
                        // pebble, so making them tap it again is friction.
                        onToggle(created.id)
                        showCreate = false
                    }
                }
            },
        )
    }
}
