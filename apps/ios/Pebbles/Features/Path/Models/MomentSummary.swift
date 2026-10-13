import Foundation

/// One row of `path_moments()` (spec §4): a moment on the caller's Path.
/// `pebble` is nil for an uncompleted moment (D2), which draws as a blank
/// stone. Also the row shape of the soul and collection detail lists.
struct MomentSummary: Identifiable, Decodable, Hashable {

    struct PebbleSummary: Identifiable, Decodable, Hashable {
        let id: UUID
        let intensity: Int
        let positiveness: Int
        let visibility: Visibility
        let emotion: EmotionRef?
        let glyph: PebbleGlyph?

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }
    }

    let id: UUID
    let name: String?
    let happenedAt: Date
    let createdAt: Date
    let firstSnapPath: String?
    let pebble: PebbleSummary?

    var isCompleted: Bool { pebble != nil }

    /// True when `id` is this moment's id or its pebble's id. The old
    /// composers report pebble ids, which equal the moment id only for
    /// pre-split rows.
    func matches(_ id: UUID) -> Bool {
        self.id == id || pebble?.id == id
    }

    /// The first moment in `moments` that `matches(_:)` the given id.
    static func first(in moments: [MomentSummary], matching id: UUID) -> MomentSummary? {
        moments.first { $0.matches(id) }
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, pebble
        case happenedAt = "happened_at"
        case createdAt = "created_at"
        case firstSnapPath = "first_snap_path"
    }

    init(
        id: UUID,
        name: String?,
        happenedAt: Date,
        createdAt: Date,
        firstSnapPath: String?,
        pebble: PebbleSummary?
    ) {
        self.id = id
        self.name = name
        self.happenedAt = happenedAt
        self.createdAt = createdAt
        self.firstSnapPath = firstSnapPath
        self.pebble = pebble
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        happenedAt = try container.decodeFlexibleDate(forKey: .happenedAt)
        createdAt = try container.decodeFlexibleDate(forKey: .createdAt)
        firstSnapPath = try container.decodeIfPresent(String.self, forKey: .firstSnapPath)
        pebble = try container.decodeIfPresent(PebbleSummary.self, forKey: .pebble)
    }
}
