import Foundation

/// The quick moment's two fields (spec D13): a name and a time, prefilled to
/// now. Pure; the sheet owns one and `payload` is what `save_moment` gets.
@MainActor
struct QuickMomentDraft: Equatable {
    private(set) var name = ""
    var happenedAt: Date

    init(now: Date) { happenedAt = now }

    /// Trimmed, then capped at Capture's limit, so the two flows agree on what
    /// a savable name is.
    mutating func setName(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        name = String(trimmed.prefix(CaptureFlowModel.nameLimit))
    }

    var canSave: Bool { !name.isEmpty }

    /// Whole seconds: the cross-surface timestamp rule.
    var payload: SaveMomentPayload {
        var p = SaveMomentPayload()
        p.name = name
        p.happenedAt = Date(timeIntervalSince1970: happenedAt.timeIntervalSince1970.rounded(.down))
        return p
    }
}
