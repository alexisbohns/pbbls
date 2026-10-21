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
        /// Ordered by name, as the server sends them (#1121). `[]` on a row
        /// from before #1121, so an older payload still decodes.
        let souls: [PathSoul]

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }

        // swiftlint:disable:next nesting
        fileprivate enum CodingKeys: String, CodingKey {
            case id, intensity, positiveness, visibility, emotion, glyph, souls
        }
    }

    let id: UUID
    let name: String?
    let happenedAt: Date
    let createdAt: Date
    let firstSnapPath: String?
    let pebble: PebbleSummary?

    var isCompleted: Bool { pebble != nil }

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

// In an extension, so the struct keeps its memberwise initialiser (the Spine's
// fixture week builds pebbles with it).
extension MomentSummary.PebbleSummary {
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        intensity = try container.decode(Int.self, forKey: .intensity)
        positiveness = try container.decode(Int.self, forKey: .positiveness)
        visibility = try container.decode(Visibility.self, forKey: .visibility)
        emotion = try container.decodeIfPresent(EmotionRef.self, forKey: .emotion)
        glyph = try container.decodeIfPresent(PebbleGlyph.self, forKey: .glyph)
        souls = try container.decodeIfPresent([PathSoul].self, forKey: .souls) ?? []
    }
}
