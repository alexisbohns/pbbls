import Foundation

/// The Encodable payload sent as the `payload` jsonb parameter of the
/// `update_pebble` Postgres RPC.
///
/// Shape matches what the server expects: snake_case keys, arrays for
/// domain/soul/collection links (even when the UI only allows one of each).
///
/// We always send every scalar field — the RPC uses `coalesce(payload->>..., existing)`
/// to fall back to the current value on absent keys, so sending everything is
/// both correct and simpler than tracking dirty fields on the client.
struct PebbleUpdatePayload: Encodable {
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: String
    let emotionId: UUID
    let domainIds: [UUID]
    let soulIds: [UUID]
    let collectionIds: [UUID]
    let glyphId: UUID?

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
        case glyphId = "glyph_id"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        // Explicit nil encoding so absent descriptions clear the field server-side.
        try container.encode(description, forKey: .description)
        try container.encode(happenedAt, forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
        try container.encode(glyphId, forKey: .glyphId)
    }
}

extension PebbleUpdatePayload {
    /// Build a payload from a validated draft.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft) {
        precondition(draft.isValid, "PebbleUpdatePayload(from:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulId.map { [$0] } ?? []
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
        self.glyphId = draft.glyphId
    }
}
