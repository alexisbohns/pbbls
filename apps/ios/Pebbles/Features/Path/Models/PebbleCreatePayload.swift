import Foundation

/// The Encodable payload sent as the `payload` jsonb parameter of the
/// `create_pebble` Postgres RPC.
///
/// Shape mirrors `PebbleUpdatePayload`: snake_case keys, arrays for
/// domain/soul/collection links (even when the UI only allows one of each).
struct PebbleCreatePayload: Encodable {
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
    let snaps: [SnapPayload]?

    struct SnapPayload: Encodable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder   = "sort_order"
        }
    }

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId     = "emotion_id"
        case domainIds     = "domain_ids"
        case soulIds       = "soul_ids"
        case collectionIds = "collection_ids"
        case glyphId       = "glyph_id"
        case snaps
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encode(Self.iso8601.string(from: happenedAt), forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
        try container.encode(glyphId, forKey: .glyphId)
        if let snaps {
            try container.encode(snaps, forKey: .snaps)
        }
    }
}

extension PebbleCreatePayload {
    /// Build a payload from a validated draft.
    /// `userId` is the current authenticated user's id; it is needed only to
    /// derive the snap's `storage_path` (the RPC re-derives ownership from
    /// `auth.uid()` server-side, so this value is not security-sensitive).
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft, userId: UUID) {
        precondition(draft.isValid, "PebbleCreatePayload(from:userId:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
        self.glyphId = draft.glyphId
        self.snaps = {
            switch draft.formSnap {
            case .none:
                return nil
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            case .existing:
                // `.existing` only appears in edit flows. Reaching this branch
                // from create is a programming error — fail loudly in debug.
                assertionFailure("PebbleCreatePayload: unexpected .existing FormSnap during create")
                return nil
            }
        }()
    }
}
