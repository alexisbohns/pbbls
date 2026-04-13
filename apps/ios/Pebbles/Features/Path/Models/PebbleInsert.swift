import Foundation

/// The Codable payload sent to `pebbles.insert(...)`.
/// Built from a validated `PebbleDraft` — non-optionals here mean
/// `PebbleDraft.isValid` was true at the call site.
///
/// `user_id` is REQUIRED: the `pebbles_insert` RLS policy enforces
/// `user_id = auth.uid()`. RLS *checks* user_id, it does not *set* it,
/// so the client must include it in the payload.
struct PebbleInsert: Encodable {
    let userId: UUID
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: String
    let emotionId: UUID

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId = "emotion_id"
    }
}

extension PebbleInsert {
    /// Build an insert payload from a validated draft.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft, userId: UUID) {
        precondition(draft.isValid, "PebbleInsert(from:) called with invalid draft")
        self.userId = userId
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
    }
}
