package app.pbbls.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import app.pbbls.android.features.report.ReportContent
import app.pbbls.android.features.report.ReportPhase
import com.android.tools.screenshot.PreviewTest

/**
 * Render-to-view previews for the report screen (#839). They drive the
 * stateless [ReportContent], never the ViewModel-backed screen. References are
 * re-baselined by CI (the `rebaseline-screenshots` label), never locally.
 */
@Composable
private fun ReportPreviewFrame(
    target: ReportTarget,
    phase: ReportPhase = ReportPhase.Editing,
    reason: ReportReason? = null,
    detail: String = "",
    removeAndBlock: Boolean = true,
) {
    PebblesTheme {
        ReportContent(
            target = target,
            phase = phase,
            reason = reason,
            detail = detail,
            removeAndBlock = removeAndBlock,
            onReason = {},
            onDetail = {},
            onRemoveAndBlock = {},
            onSend = {},
            onCancel = {},
            onDone = {},
        )
    }
}

@PreviewTest
@Preview(name = "Report · connection", showBackground = true, heightDp = 1200)
@Composable
private fun ReportConnectionPreview() {
    ReportPreviewFrame(
        target = ReportTarget.Connection("c1", "Ana"),
        reason = ReportReason.HARASSMENT,
    )
}

@PreviewTest
@Preview(name = "Report · connection fr", showBackground = true, locale = "fr", heightDp = 1200)
@Composable
private fun ReportConnectionFrenchPreview() {
    ReportPreviewFrame(
        target = ReportTarget.Connection("c1", "Ana"),
        reason = ReportReason.HARASSMENT,
    )
}

@PreviewTest
@Preview(name = "Report · invite failed", showBackground = true, heightDp = 1000)
@Composable
private fun ReportInviteFailedPreview() {
    ReportPreviewFrame(
        target = ReportTarget.Invite("tok", null),
        phase = ReportPhase.Failed(R.string.report_error_generic),
        reason = ReportReason.SPAM,
        detail = "They keep sending me links.",
    )
}

@PreviewTest
@Preview(name = "Report · glyph", showBackground = true, heightDp = 1000)
@Composable
private fun ReportGlyphPreview() {
    ReportPreviewFrame(target = ReportTarget.Glyph("g1"))
}

@PreviewTest
@Preview(name = "Report · sent", showBackground = true, heightDp = 600)
@Composable
private fun ReportSentPreview() {
    ReportPreviewFrame(
        target = ReportTarget.Connection("c1", "Ana"),
        phase = ReportPhase.Sent(didBlock = true),
        reason = ReportReason.HARASSMENT,
    )
}
