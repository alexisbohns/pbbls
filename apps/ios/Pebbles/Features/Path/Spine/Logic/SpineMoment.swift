import Foundation

/// What the Spine's logic reads of a moment (#1130 D10). Ids are strings, so
/// the web's vectors (`"tue-late"`, `"s0"`) copy verbatim, and lowercase,
/// because web and Android hash and tie-break on lowercase ids.
protocol SpineMoment {
    var spineId: String { get }
    var happenedAt: Date { get }
    /// A bare moment (#1050) is not a pebble: it names no one.
    var isPebble: Bool { get }
    var soulIds: [String] { get }
}

extension MomentSummary: SpineMoment {
    /// `UUID.uuidString` is uppercase; web and Android use lowercase.
    var spineId: String { id.uuidString.lowercased() }
    var isPebble: Bool { pebble != nil }
    var soulIds: [String] { pebble?.souls.map { $0.id.uuidString.lowercased() } ?? [] }
}
