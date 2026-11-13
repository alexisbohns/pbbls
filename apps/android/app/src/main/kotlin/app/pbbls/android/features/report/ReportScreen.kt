package app.pbbls.android.features.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.ListGroupHeader
import app.pbbls.android.core.designsystem.ListGroupItemScope
import app.pbbls.android.core.designsystem.ListGroupSwitchItem
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget

/**
 * Report a person or a glyph (#839, ports iOS `ReportSheet`): one reason,
 * optional details and, for a connection, remove-and-block in the same step.
 * [onFinished] fires on Done after a sent report, with whether a block
 * happened; [onCancel] on Cancel or back before sending.
 *
 * `target` comes from the nav key and only ever reaches the ViewModel through
 * [ReportViewModel.start]: the ViewModel's own copy is a plain field, never
 * read in composition.
 */
@Composable
fun ReportScreen(
    target: ReportTarget,
    onCancel: () -> Unit,
    onFinished: (blocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    LaunchedEffect(target) { viewModel.start(target) }
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    // Always enabled: a disabled handler hands the gesture to NavDisplay's own,
    // which would pop the entry mid-send. So back is swallowed while sending.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) {
        when (val p = phase) {
            ReportPhase.Sending -> Unit
            is ReportPhase.Sent -> onFinished(p.didBlock)
            else -> onCancel()
        }
    }

    ReportContent(
        target = target,
        phase = phase,
        reason = viewModel.reason,
        detail = viewModel.detail,
        removeAndBlock = viewModel.removeAndBlock,
        onReason = { viewModel.reason = it },
        onDetail = viewModel::onDetailChange,
        onRemoveAndBlock = { viewModel.removeAndBlock = it },
        onSend = viewModel::submit,
        onCancel = onCancel,
        onDone = { (phase as? ReportPhase.Sent)?.let { onFinished(it.didBlock) } },
        modifier = modifier,
    )
}

/** Stateless report surface: what the screenshot previews drive. */
@Composable
fun ReportContent(
    target: ReportTarget,
    phase: ReportPhase,
    reason: ReportReason?,
    detail: String,
    removeAndBlock: Boolean,
    onReason: (ReportReason) -> Unit,
    onDetail: (String) -> Unit,
    onRemoveAndBlock: (Boolean) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val sent = phase is ReportPhase.Sent
    val sending = phase is ReportPhase.Sending
    // From the parameters, never from the ViewModel: its `canSend` reads a flow
    // value, which composition does not observe.
    val canSend = reason != null && !sending && !sent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .readableWidth(),
    ) {
        PebblesTopBar(
            title = reportTitle(target),
            leading = {
                if (!sent) {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onCancel,
                        enabled = !sending,
                    )
                }
            },
            trailing = {
                when {
                    sent ->
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_done),
                            onClick = onDone,
                            color = colors.primary,
                        )

                    sending -> SendingIndicator()

                    else ->
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.report_send),
                            onClick = onSend,
                            enabled = canSend,
                            color = colors.primary,
                        )
                }
            },
        )

        if (phase is ReportPhase.Sent) {
            SentMessage()
        } else {
            ReportForm(
                target = target,
                phase = phase,
                reason = reason,
                detail = detail,
                removeAndBlock = removeAndBlock,
                onReason = onReason,
                onDetail = onDetail,
                onRemoveAndBlock = onRemoveAndBlock,
            )
        }
    }
}

/** The send button's place while the request runs; a 48 dp slot so the bar does not shift. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendingIndicator() {
    val description = stringResource(R.string.report_sending)
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(
            modifier =
                Modifier
                    .size(24.dp)
                    .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ReportForm(
    target: ReportTarget,
    phase: ReportPhase,
    reason: ReportReason?,
    detail: String,
    removeAndBlock: Boolean,
    onReason: (ReportReason) -> Unit,
    onDetail: (String) -> Unit,
    onRemoveAndBlock: (Boolean) -> Unit,
) {
    val spacing = PebblesTheme.spacing
    val enabled = phase !is ReportPhase.Sending

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        ListGroup(
            header = stringResource(R.string.report_reasons_header),
            modifier = Modifier.selectableGroup(),
            items =
                ReportReason.entries.map { option ->
                    {
                        ReasonItem(
                            reason = option,
                            selected = option == reason,
                            enabled = enabled,
                            onClick = { onReason(option) },
                        )
                    }
                },
        )

        Column {
            ListGroupHeader(text = stringResource(R.string.report_details_header))
            OutlinedTextField(
                value = detail,
                onValueChange = onDetail,
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (target.offersBlock) {
            ListGroup(
                items =
                    listOf(
                        {
                            ListGroupSwitchItem(
                                headline = blockLabel(target),
                                checked = removeAndBlock,
                                onCheckedChange = onRemoveAndBlock,
                                supporting = stringResource(R.string.report_block_footer),
                                enabled = enabled,
                            )
                        },
                    ),
            )
        }

        if (phase is ReportPhase.Failed) {
            Text(
                text = stringResource(phase.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                // Orange in this theme: there is no red anywhere.
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(horizontal = spacing.lg)
                        // Appears after a send, away from focus: announce it.
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One reason: a stock segmented row, selected like a radio button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListGroupItemScope.ReasonItem(
    reason: ReportReason,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        colors = colors,
        enabled = enabled,
        // The row is the one control TalkBack reads ("radio button, selected");
        // the RadioButton takes no click, so it is not a second focus target.
        modifier = Modifier.semantics { role = Role.RadioButton },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    ) {
        Text(stringResource(reason.label))
    }
}

/** The filed state: a check and one line, announced politely. */
@Composable
private fun SentMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PebblesTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        ) {
            PbblsGlyphImage(
                glyph = PbblsGlyph.CHECK_CIRCLE_DOT,
                size = PbblsGlyphSize.HERO,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.report_sent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun reportTitle(target: ReportTarget): String =
    when (target) {
        is ReportTarget.Glyph -> stringResource(R.string.report_title_glyph)
        is ReportTarget.Connection, is ReportTarget.Invite ->
            target.personName?.let { stringResource(R.string.report_title_named, it) }
                ?: stringResource(R.string.report_title_person)
    }

@Composable
private fun blockLabel(target: ReportTarget): String =
    target.personName?.let { stringResource(R.string.report_block_named, it) }
        ?: stringResource(R.string.report_block_person)
