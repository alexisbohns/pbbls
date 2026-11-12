import Foundation
import Observation
import os

/// The report sheet's state. All logic lives here; `ReportSheet` only draws it.
@Observable
@MainActor
final class ReportSheetModel {
    enum Phase: Equatable {
        case editing
        case sending
        case sent
        case failed(String)
    }

    typealias Send = (ReportTarget, ReportReason, String?, Bool) async throws -> ReportReceipt

    /// `content_reports.detail`'s CHECK.
    static let detailLimit = 1_000

    let target: ReportTarget
    var reason: ReportReason?
    var detail = ""
    /// Only read for a connection; on by default (design decision 3).
    var removeAndBlock = true
    private(set) var phase: Phase = .editing
    /// True once a report with remove-and-block succeeded.
    private(set) var didBlock = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "report")

    init(target: ReportTarget) {
        self.target = target
    }

    var canSend: Bool {
        reason != nil && phase != .sending && phase != .sent
    }

    /// Caps `detail` at `detailLimit` code points, not grapheme clusters: the
    /// server CHECK is Postgres `length(detail) <= 1000`, which counts code
    /// points, so a family emoji (one character, five scalars) must count five.
    func clampDetail() {
        if detail.unicodeScalars.count > Self.detailLimit {
            detail = String(String.UnicodeScalarView(detail.unicodeScalars.prefix(Self.detailLimit)))
        }
    }

    func submit(using send: Send) async {
        guard canSend, let reason else { return }
        phase = .sending
        let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        let block = target.offersBlock && removeAndBlock
        do {
            _ = try await send(target, reason, trimmed.isEmpty ? nil : trimmed, block)
            didBlock = block
            phase = .sent
        } catch {
            logger.error("report failed: \(error.localizedDescription, privacy: .private)")
            phase = .failed(ReportError.from(error).message)
        }
    }
}
