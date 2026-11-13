package app.pbbls.android.testing

import app.pbbls.android.core.data.ReportReceipt
import app.pbbls.android.core.data.ReportServicing
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import java.time.OffsetDateTime

/** In-memory [ReportServicing] (#839) for the Hilt UI harness: every report is filed and answered as a new open one. */
class FakeReportService : ReportServicing {
    override suspend fun send(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Boolean,
    ): ReportReceipt = ReportReceipt("report-1", "open", OffsetDateTime.parse("2026-11-12T20:54:43Z"))
}
