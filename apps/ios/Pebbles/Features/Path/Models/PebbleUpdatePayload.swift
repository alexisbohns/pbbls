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
///
/// `snaps` is always sent (possibly empty) so `update_pebble`'s replace block
/// fires every save: an unchanged snap round-trips with the same `id` and
/// `storage_path`; a removed-then-not-replaced photo sends `[]` so any stale
/// row is wiped server-side as defense in depth (the eager `delete_pebble_media`
/// path should already have removed it).
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
    let snaps: [SnapPayload]

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
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
        case glyphId = "glyph_id"
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
        // Explicit nil encoding so absent descriptions clear the field server-side.
        try container.encode(description, forKey: .description)
        // Encode Date as an ISO8601 string so Postgres' timestamptz cast accepts
        // it. The Supabase SDK's .functions.invoke() path uses an encoder whose
        // default date strategy emits Double seconds — which Postgres rejects.
        try container.encode(Self.iso8601.string(from: happenedAt), forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
        try container.encode(glyphId, forKey: .glyphId)
        try container.encode(snaps, forKey: .snaps)
    }
}

extension PebbleUpdatePayload {
    /// Build a payload from a validated draft.
    /// `userId` is needed to derive the storage_path of a `.pending` snap;
    /// `.existing` snaps already carry the path from the DB.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft, userId: UUID) {
        precondition(draft.isValid, "PebbleUpdatePayload(from:userId:) called with invalid draft")
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
                return []
            case .existing(let id, let storagePath):
                return [SnapPayload(id: id, storagePath: storagePath, sortOrder: 0)]
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            }
        }()
    }
}
