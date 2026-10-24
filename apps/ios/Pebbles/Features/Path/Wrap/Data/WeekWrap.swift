import Foundation

/// One week's wrap (#1226), decided by the server: `tiles` are the thresholds'
/// verdict in render order, and a tile's data is non-nil exactly when the tile
/// is present.
struct WeekWrap: Hashable, Sendable {
    let weekStart: CalendarDay
    let isoWeek: Int
    let isoYear: Int
    let isLive: Bool
    let pebbles: Int
    let people: Int
    let tiles: [WeekTile]
    let persons: [PersonStat]?
    /// The week's first moments, for the "was someone there for…" invitation; only when nobody is named.
    let inviteMomentIds: [String]?
    let families: [FamilyStat]?
    let lead: Lead?
    let domains: [DomainStat]?
    let weight: WeightGrid?
    let hours: [HourMark]?
    let kept: Kept?
}

enum WeekTile: String, CaseIterable, Hashable, Identifiable, Sendable {
    case contents, persons, emotions, domains, weight, hours, kept

    var id: String { rawValue }
}

/// Pebble counts by intensity 1..3 (index 0..2), for bright, even and heavy pebbles.
struct WeightGrid: Hashable, Sendable {
    let bright: [Int]
    let even: [Int]
    let heavy: [Int]
}

struct Kept: Hashable, Sendable {
    let photoMomentIds: [String]
    let cards: [CardCount]
}

struct CardCount: Hashable, Sendable {
    let momentId: String
    let count: Int
}

extension WeekWrapPayload {
    private static let daysInWeek = 7

    /// Unknown tiles are dropped, so a server that adds a tile does not break this client.
    var model: WeekWrap {
        let length = Self.daysInWeek
        return WeekWrap(
            weekStart: weekStart,
            isoWeek: isoWeek,
            isoYear: isoYear,
            isLive: live,
            pebbles: count,
            people: peopleCount,
            tiles: tiles.compactMap(WeekTile.init(rawValue:)),
            persons: persons?.map { $0.model(length: length) },
            inviteMomentIds: inviteMomentIds,
            families: families?.map { $0.model(length: length) },
            lead: lead?.model,
            domains: domains?.map { $0.model(length: length) },
            weight: weight.map { WeightGrid(bright: $0.high, even: $0.neutral, heavy: $0.low) },
            hours: hours?.map(\.model),
            kept: kept.map { kept in
                Kept(photoMomentIds: kept.photoMomentIds,
                             cards: kept.cards.map { CardCount(momentId: $0.momentId, count: $0.count) })
            }
        )
    }
}
