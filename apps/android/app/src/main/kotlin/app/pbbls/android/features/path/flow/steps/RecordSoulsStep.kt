package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.souls.SoulChooserBody
import app.pbbls.android.core.ui.souls.SoulChooserMode
import app.pbbls.android.core.ui.souls.SoulChooserSearchField
import app.pbbls.android.core.ui.souls.SoulChooserTray
import app.pbbls.android.core.ui.souls.rememberSoulChooserState
import app.pbbls.android.core.ui.souls.rememberSoulCreate
import kotlinx.coroutines.launch

/**
 * Step 6 — who was there. Interim until the Life screen's tiles and More sheet (Part 7): the souls
 * chooser inline, its tray's button advancing the flow ([onAdvance] reads Skip with nobody chosen).
 * The chooser scrolls itself, so the step brings its own scroll and nothing nests.
 *
 * [selectedIds] is the flow's answer and [onToggle] its only writer (`CompleteFlowModel.toggleSoul`,
 * which buzzes). The chooser keeps an editing copy for search and pills; each change in it is
 * replayed into the model, and the chooser's own haptic is off so a tap buzzes once, from the model.
 */
@Composable
fun RecordSoulsStep(
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onAdvance: () -> Unit,
    hapticsOn: Boolean,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
) {
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val state =
        rememberSoulChooserState(
            mode = SoulChooserMode.Pick(allowsSkip = true),
            souls = refs.souls,
            isLoaded = refs.hasLoaded,
            selection = selectedIds,
        )

    // The cache refreshes only on soul mutations, so the counts would lag every
    // pebble recorded since launch (#1370).
    LaunchedEffect(Unit) { refs.refreshSouls() }

    // The model is the source of truth: replay what the chooser changed into it, one toggle each.
    LaunchedEffect(state.selection) {
        val chosen = state.selection.toSet()
        val answered = selectedIds.toSet()
        (chosen - answered).forEach(onToggle)
        (answered - chosen).forEach(onToggle)
    }

    val onCreate = rememberSoulCreate(state, refs, onAchievementCheck)

    Column(modifier = modifier.fillMaxSize()) {
        if (state.isSearching) {
            SoulChooserSearchField(
                state = state,
                onClose = state::closeSearch,
                modifier = Modifier.padding(bottom = PebblesTheme.spacing.sm),
            )
        }
        SoulChooserBody(
            state = state,
            isLoaded = refs.hasLoaded,
            loadFailed = false,
            onRetry = { scope.launch { refs.refreshSouls() } },
            onOpenSoul = {},
            onCreate = onCreate,
            onHaptic = {},
            hapticsOn = hapticsOn,
            modifier = Modifier.weight(1f),
        )
        SoulChooserTray(state = state, onDone = onAdvance, onHaptic = {})
    }
}
