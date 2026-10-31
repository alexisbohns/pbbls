package app.pbbls.android.features.home.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PageShapes
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesActionHeight
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.core.designsystem.figureOf
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.StoneOptions
import app.pbbls.android.features.home.ClaimStatus

// The box (the mock's `.box`): padding 24 / 16 / 10 / 18, 16 under the head.
private val BOX_TOP = 24.dp
private val BOX_END = 16.dp
private val BOX_BOTTOM = 10.dp
private val BOX_START = 18.dp
private val HEAD_GAP = 16.dp

// The stepper (the mock's `.steps`): 6 dp, 2 above and 8 below.
private val STEP_HEIGHT = 6.dp
private val STEP_TOP = 2.dp
private val STEP_BOTTOM = 8.dp
private const val STEP_SEED = "first-five"
private val STEP_OPTIONS = StoneOptions(corner = 3f, gutter = 5f, fillet = 2f, lean = 0.9f)

// A row (`.box .row`): 13 dp above and below, 12 between the mark, the text and the chevron.
private val ROW_PAD_Y = 13.dp
private val ROW_GAP = 12.dp
private val LINE_GAP = 2.dp
private val CHEVRON = 18.dp
private val MIN_TOUCH = 48.dp

private val CLAIM_TOP = 8.dp
private val SPINNER = 24.dp
private val ERROR_TOP = 8.dp
private val ERROR_INSET = 4.dp
private const val TOTAL = 5

/**
 * Your first five (#1383 D11; iOS `HomeChecklistView` in the c3 box): the head with "2 of 5", a thin
 * stone cut in five as the stepper, the five tasks, and once all five are done the claim of the blue
 * pebble. Under the box, iOS's line when a claim was not granted. The caller draws it only while the
 * checklist [HomeChecklist.isShown].
 */
@Composable
fun FirstFiveBox(
    checklist: HomeChecklist,
    claim: ClaimStatus,
    onTask: (ChecklistTask) -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer, PageShapes.box)
                .padding(start = BOX_START, top = BOX_TOP, end = BOX_END, bottom = BOX_BOTTOM),
        ) {
            val done = checklist.doneCount
            SectionHead(
                title = stringResource(R.string.home_first_five_title),
                figure = figureOf(done, TOTAL, stringResource(R.string.home_first_five_of)),
                modifier = Modifier.padding(bottom = HEAD_GAP),
            )
            // Decorative: the figure says how far along the user is.
            SplitStone(
                n = TOTAL,
                seed = STEP_SEED,
                fills = List(TOTAL) { k -> if (k < done) colors.primary else colors.surfaceContainerHighest },
                options = STEP_OPTIONS,
                minHeight = STEP_HEIGHT,
                modifier =
                    Modifier
                        .padding(top = STEP_TOP, bottom = STEP_BOTTOM)
                        .fillMaxWidth()
                        .clearAndSetSemantics {},
            ) {}
            checklist.rows.forEach { row -> TaskRow(row, onClick = { onTask(row.task) }) }
            if (checklist.state == ChecklistState.CLAIMABLE) {
                ClaimButton(claiming = claim == ClaimStatus.CLAIMING, onClick = onClaim, modifier = Modifier.padding(top = CLAIM_TOP))
            }
        }
        if (claim == ClaimStatus.FAILED) {
            Text(
                text = stringResource(R.string.home_first_five_claim_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = ERROR_TOP, start = ERROR_INSET, end = ERROR_INSET),
            )
        }
    }
}

/**
 * One task: its mark, title and line, and a chevron while it is still to do. A done row is not
 * clickable and says "Done" as its state; the row is one node for TalkBack.
 */
@Composable
private fun TaskRow(
    row: ChecklistRow,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val doneLabel = stringResource(R.string.home_first_five_done)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH)
                .clickable(enabled = !row.isDone, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { if (row.isDone) stateDescription = doneLabel }
                .padding(vertical = ROW_PAD_Y),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskMark(done = row.isDone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            Text(
                text = stringResource(row.task.titleRes),
                style = if (row.isDone) type.rowTitleDone else type.rowTitle,
                color = if (row.isDone) colors.onSurfaceVariant else colors.onSurface,
            )
            Text(text = stringResource(row.task.lineRes), style = type.rowLine, color = colors.onSurfaceVariant)
        }
        if (!row.isDone) {
            // Mirrored in RTL by the drawable's autoMirrored.
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.outline,
                modifier = Modifier.size(CHEVRON),
            )
        }
    }
}

/** iOS's claim row: a filled button with the blue pebble's mark, a spinner (and disabled) while it sends. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClaimButton(
    claiming: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(PebblesActionHeight),
        modifier = modifier.fillMaxWidth().heightIn(min = PebblesActionHeight),
        enabled = !claiming,
        contentPadding = ButtonDefaults.contentPaddingFor(PebblesActionHeight),
    ) {
        if (claiming) {
            LoadingIndicator(color = LocalContentColor.current, modifier = Modifier.size(SPINNER))
        } else {
            PbblsIconImage(PbblsIcon.PEBBLE_ROUND_LIT_1, contentDescription = null, size = ButtonDefaults.IconSize)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.home_first_five_claim), style = ButtonDefaults.textStyleFor(PebblesActionHeight))
        }
    }
}
