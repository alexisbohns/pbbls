import Foundation

/// Edit moment's form state (R5). The photo lives on the coordinator.
struct EditMomentDraft: Equatable {
    var happenedAt: Date
    var name: String
    var description: String

    init(happenedAt: Date, name: String, description: String) {
        self.happenedAt = happenedAt
        self.name = name
        self.description = description
    }

    init(from detail: MomentDetail) {
        self.init(happenedAt: detail.happenedAt, name: detail.name ?? "", description: detail.description ?? "")
    }

    /// R6: a moment needs a name or a photo.
    func canSave(hasSnap: Bool) -> Bool {
        hasSnap || MomentName.display(name) != nil
    }

    /// Clamped like Capture's name step (A1).
    mutating func setName(_ raw: String) {
        name = String(raw.prefix(CaptureFlowModel.nameLimit))
    }
}

extension SaveMomentPayload {
    /// An edit (A2): every moment field sent, blanks as "" (null cannot
    /// clear), and the full snap set (`[]` removes the photo).
    init(edit draft: EditMomentDraft, momentId: UUID, formSnap: FormSnap?, userId: UUID) {
        self.init()
        id = momentId
        name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        description = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
        happenedAt = draft.happenedAt
        snaps = Self.snaps(for: formSnap, userId: userId)
    }
}
