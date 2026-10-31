package app.pbbls.android.features.home.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import kotlinx.coroutines.launch

/**
 * One first-five task, explained (#1383 D11; iOS `ChecklistTaskSheet`): a stock `ModalBottomSheet`
 * with its picture, two sentences and the one way to do it. The call to action hides the sheet first,
 * then calls [onGo], so the flow it opens never races the sheet (iOS's `pendingTask`). A swipe, a scrim
 * tap or back is [onDismiss] alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistTaskSheet(
    task: ChecklistTask,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ground = MaterialTheme.colorScheme.surfaceContainerLow
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ground) {
        ChecklistTaskContent(
            task = task,
            knockout = ground,
            onGo = {
                // The sheet's own motion, not a write: the composition's scope is the right one.
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                    onGo()
                }
            },
            modifier = Modifier.padding(bottom = Spacing.lg),
        )
    }
}

/**
 * The sheet's content without the sheet (iOS `ChecklistTaskContent`), so the previews pin it: the
 * task's title, its picture over [knockout] (the ground it sits on), the body, and the filled call to
 * action.
 */
@Composable
fun ChecklistTaskContent(
    task: ChecklistTask,
    knockout: Color,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(task.titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).semantics { heading() },
        )
        ChecklistIllustration(task = task, knockout = knockout)
        Text(
            text = stringResource(task.bodyRes),
            style = PebblesTheme.spine.type.summary,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(task.ctaRes),
            onClick = onGo,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}
