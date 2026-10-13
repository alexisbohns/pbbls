import Foundation

/// The `payload` of `save_moment(payload jsonb)` (spec §3). Create when `id`
/// is nil, else update the caller's moment.
///
/// Every key is optional and **omitted** when nil: the server reads an
/// explicit JSON null as "absent" (keep), so null can never clear a field.
/// To clear `name` or `description`, send `""` (A2). `snaps` replaces the
/// whole set when present; `[]` removes the photo.
struct SaveMomentPayload: Encodable, Equatable {

    struct Snap: Encodable, Equatable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        private enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder = "sort_order"
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(id.uuidString.lowercased(), forKey: .id)
            try container.encode(storagePath, forKey: .storagePath)
            try container.encode(sortOrder, forKey: .sortOrder)
        }
    }

    var id: UUID?
    var name: String?
    var description: String?
    var happenedAt: Date?
    var snaps: [Snap]?

    private enum CodingKeys: String, CodingKey {
        case id, name, description, snaps
        case happenedAt = "happened_at"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(id.map { $0.uuidString.lowercased() }, forKey: .id)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(description, forKey: .description)
        // Whole seconds (standing timestamp rule); the server truncates too.
        try container.encodeIfPresent(happenedAt.map(ISO8601Flexible.string(from:)), forKey: .happenedAt)
        try container.encodeIfPresent(snaps, forKey: .snaps)
    }

    /// The snap set for the coordinator's current photo: empty without one.
    /// The bytes are already in Storage (upload happens at pick time).
    static func snaps(for formSnap: FormSnap?, userId: UUID) -> [Snap] {
        switch formSnap {
        case .none:
            return []
        case .existing(let id, let storagePath):
            return [Snap(id: id, storagePath: storagePath, sortOrder: 0)]
        case .pending(let snap):
            return [Snap(id: snap.id, storagePath: snap.storagePrefix(userId: userId), sortOrder: 0)]
        }
    }
}

extension SaveMomentPayload {
    /// A new moment from Capture (A2): no id, the name only when non-blank,
    /// no description, the photo only when there is one.
    init(capture draft: CaptureDraft, formSnap: FormSnap?, userId: UUID) {
        self.init()
        name = MomentName.display(draft.name)
        happenedAt = draft.happenedAt
        let snaps = Self.snaps(for: formSnap, userId: userId)
        self.snaps = snaps.isEmpty ? nil : snaps
    }
}
