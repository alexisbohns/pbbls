import Foundation

/// One soul of the week (#1130 R15): the days they appear on, Monday first.
struct PersonOfWeek: Equatable {
    let soulId: String
    let name: String
    let days: [Bool]
    let dayCount: Int
    let pebbleCount: Int
}

/// The persons of the week, a port of `apps/web/lib/utils/spine/persons-of-week.ts`.
/// Only a pebble names people, and a soul counts once per pebble. The order
/// (days desc, pebbles desc, name by `locale` collation, then id) is the one
/// rule every surface uses, here, in each people line and on the card.
enum PersonsOfWeek {
    /// Rows shown before the rest fold into "and N others".
    static let cap = 8

    /// - Parameter names: soul id → name. A soul missing here (deleted, still
    ///   on an old pebble) has no name to set and is skipped.
    static func personsOfWeek<M: SpineMoment>(
        _ moments: [M], names: [String: String], weekStart: Date, calendar: Calendar, locale: Locale
    ) -> [PersonOfWeek] {
        var days: [String: [Bool]] = [:]
        var pebbles: [String: Int] = [:]
        for moment in moments where moment.isPebble {
            let day = LocalDays.dayIndexInWeek(weekStart: weekStart, moment.happenedAt, calendar: calendar)
            guard (0...6).contains(day) else { continue }
            var seen = Set<String>()
            for soulId in moment.soulIds where seen.insert(soulId).inserted && names[soulId] != nil {
                days[soulId, default: Array(repeating: false, count: 7)][day] = true
                pebbles[soulId, default: 0] += 1
            }
        }
        let rows = days.map { soulId, marked in
            PersonOfWeek(soulId: soulId, name: names[soulId] ?? "", days: marked,
                         dayCount: marked.filter { $0 }.count, pebbleCount: pebbles[soulId] ?? 0)
        }
        return rows.sorted { lhs, rhs in
            if lhs.dayCount != rhs.dayCount { return lhs.dayCount > rhs.dayCount }
            if lhs.pebbleCount != rhs.pebbleCount { return lhs.pebbleCount > rhs.pebbleCount }
            let byName = lhs.name.compare(rhs.name, options: [], range: nil, locale: locale)
            if byName != .orderedSame { return byName == .orderedAscending }
            // Two souls can share a name; their ids keep the order stable anyway.
            return lhs.soulId < rhs.soulId
        }
    }

    /// The first `cap` rows, and the rest behind "and N others".
    static func split(_ rows: [PersonOfWeek], cap: Int = cap) -> (shown: [PersonOfWeek], folded: [PersonOfWeek]) {
        (Array(rows.prefix(cap)), Array(rows.dropFirst(cap)))
    }
}
