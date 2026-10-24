import Foundation

// The stats the week and the month share (#1226). Ids stay the server's
// lowercase strings, the form the Spine's lookups already key by.

/// A soul in a week or a month: its pebbles, its days and, in a month, whether
/// it is new since the previous one.
struct PersonStat: Hashable, Sendable {
    let soulId: String
    let count: Int
    let days: DaySet
    let isNewSincePrevious: Bool
}

/// An emotion family (category slug) in a week: its pebbles, its worth
/// (intensity × charge), its share and its days.
struct FamilyStat: Hashable, Sendable {
    let slug: String
    let count: Int
    let worth: Double
    let share: Double
    let days: DaySet
}

/// The families in the order a lead sentence names them, and the families with
/// nothing. Slugs only; `WrapCopy` words it.
struct Lead: Hashable, Sendable {
    let order: [String]
    let zero: [String]
}

/// A domain in a week or a month: its pebbles and its days.
struct DomainStat: Hashable, Sendable {
    let domainId: String
    let count: Int
    let days: DaySet
}

/// A pebble on the hours: when it happened and when it was collected, in local
/// decimal hours (0 until 24).
struct HourMark: Hashable, Sendable {
    let momentId: String
    let happened: Double
    let collected: Double
}

extension PersonPayload {
    func model(length: Int) -> PersonStat {
        PersonStat(soulId: soulId, count: count, days: DaySet(length: length, days: days),
                   isNewSincePrevious: newSincePrevious)
    }
}

extension FamilyPayload {
    func model(length: Int) -> FamilyStat {
        FamilyStat(slug: slug, count: count, worth: worth, share: share, days: DaySet(length: length, days: days))
    }
}

extension LeadPayload {
    var model: Lead { Lead(order: order, zero: zero) }
}

extension DomainPayload {
    func model(length: Int) -> DomainStat {
        DomainStat(domainId: domainId, count: count, days: DaySet(length: length, days: days))
    }
}

extension HourPayload {
    var model: HourMark { HourMark(momentId: momentId, happened: happened, collected: collected) }
}

extension ValencePolarity {
    /// The payloads' polarity: below zero heavy, zero even, above zero bright.
    init(wire: Int) {
        self = wire < 0 ? .lowlight : wire == 0 ? .neutral : .highlight
    }
}
