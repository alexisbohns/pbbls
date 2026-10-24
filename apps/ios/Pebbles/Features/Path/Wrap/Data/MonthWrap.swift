import Foundation

/// One month's page (#1226), decided by the server: `sections` are the
/// thresholds' verdict in render order, and a section's data is non-nil exactly
/// when the section is present (`cover` whenever the month holds a pebble).
/// Days are 0-based indices into the month.
struct MonthWrap: Hashable, Sendable {
    let month: CalendarMonth
    let daysInMonth: Int
    /// The weekday of the 1st, 0 = Monday.
    let firstWeekday: Int
    let pebbles: Int
    let people: Int
    let sections: [MonthSection]
    let cover: MonthCover?
    let weeks: [MonthWeek]?
    let milestones: [String]?
    let persons: [PersonStat]?
    let previousMonth: PreviousMonth?
    let gem: Gem?
    let fineEmotions: [FineEmotionStat]?
    let domains: [DomainStat]?
    let tone: Tone?
    let hours: MonthHours?
}

enum MonthSection: String, CaseIterable, Hashable, Sendable {
    case weeks, milestones, persons, gem, domains, tone, hours
}

/// The garland's days (only those holding a pebble), the souls in order of
/// appearance, and the colophon's inks and papers.
struct MonthCover: Hashable, Sendable {
    let days: [CoverDay]
    let soulsInOrder: [String]
    let inks: [String]
    let papers: [String]
}

struct CoverDay: Hashable, Sendable {
    let day: Int
    let pebbles: [CoverPebble]
}

struct CoverPebble: Hashable, Sendable {
    let momentId: String
    let emotionId: String?
    let intensity: Int
    let polarity: ValencePolarity
}

/// An ISO week of the month, its in-month range `from...to` (day indices), and its cairn.
struct MonthWeek: Hashable, Sendable {
    let weekStart: CalendarDay
    let from: Int
    let to: Int
    let pebbles: Int
    let soulIds: [String]
    let days: [DayWeight]
}

struct PreviousMonth: Hashable, Sendable {
    let pebbles: Int
    let soulIds: [String]
}

/// The emotions gem: `facets` maps the 28 data facets to a family slug (nil when
/// uncoloured), already rotated.
struct Gem: Hashable, Sendable {
    let worths: [String: Double]
    let shares: [String: Double]
    let main: String?
    let rotation: Int
    let facets: [String: String?]
    let lead: Lead?

    /// The family that owns `facet`, or nil when the facet is uncoloured or unknown.
    func family(ofFacet facet: String) -> String? {
        facets[facet] ?? nil
    }
}

struct FineEmotionStat: Hashable, Sendable {
    let family: String
    let emotionId: String
    let count: Int
    let days: DaySet
}

struct Tone: Hashable, Sendable {
    let days: [ToneDay]
    let verdict: ToneVerdict
    let first: ValencePolarity?
    let last: ValencePolarity?
}

/// A day's summed intensities of bright (`up`), even and heavy (`down`) pebbles.
struct ToneDay: Hashable, Sendable {
    let up: Int
    let even: Int
    let down: Int
}

enum ToneVerdict: String, Hashable, Sendable {
    case brightMoreThanTwoToOne = "bright_more_than_two_to_one"
    case brightTwoToOne = "bright_two_to_one"
    case balance
    case heavyOutweighed = "heavy_outweighed"
    case nothingHeavy = "nothing_heavy"
    case nothingBright = "nothing_bright"
    case allEven = "all_even"
    /// A verdict this client does not know yet; the sentence falls back to neutral wording.
    case unknown

    init(wire: String) {
        self = ToneVerdict(rawValue: wire) ?? .unknown
    }
}

struct MonthHours: Hashable, Sendable {
    let items: [HourMark]
    let busiest: BusiestWindow?
}

/// The four-hour window starting at `start` o'clock that holds the most collections.
struct BusiestWindow: Hashable, Sendable {
    let start: Int
    let count: Int
}

extension MonthWrapPayload {
    /// Unknown sections are dropped and an unknown verdict maps to `.unknown`.
    var model: MonthWrap {
        let length = daysInMonth
        return MonthWrap(
            month: CalendarMonth(month),
            daysInMonth: daysInMonth,
            firstWeekday: min(max(firstWeekday, 0), 6),
            pebbles: count,
            people: peopleCount,
            sections: sections.compactMap(MonthSection.init(rawValue:)),
            cover: cover.map { cover in
                MonthCover(
                    days: cover.days.map { day in
                        CoverDay(day: day.day, pebbles: day.pebbles.map {
                            CoverPebble(momentId: $0.momentId, emotionId: $0.emotionId, intensity: $0.intensity,
                                        polarity: ValencePolarity(wire: $0.polarity))
                        })
                    },
                    soulsInOrder: cover.soulsInOrder,
                    inks: cover.inks,
                    papers: cover.papers
                )
            },
            weeks: weeks?.map {
                MonthWeek(weekStart: $0.weekStart, from: $0.from, to: $0.to, pebbles: $0.count, soulIds: $0.soulIds,
                          days: $0.days.map(DayWeight.init(pair:)))
            },
            milestones: milestones,
            persons: persons?.map { $0.model(length: length) },
            previousMonth: previousMonth.map { PreviousMonth(pebbles: $0.count, soulIds: $0.soulIds) },
            gem: gem.map {
                Gem(worths: $0.worths, shares: $0.shares, main: $0.main, rotation: $0.rotation, facets: $0.facets,
                    lead: $0.lead?.model)
            },
            fineEmotions: fineEmotions?.map {
                FineEmotionStat(family: $0.family, emotionId: $0.emotionId, count: $0.count,
                                days: DaySet(length: length, days: $0.days))
            },
            domains: domains?.map { $0.model(length: length) },
            tone: tone.map { tone in
                Tone(
                    days: tone.days.map { day in
                        ToneDay(up: day.first ?? 0, even: day.count > 1 ? day[1] : 0, down: day.count > 2 ? day[2] : 0)
                    },
                    verdict: ToneVerdict(wire: tone.verdict),
                    first: tone.first.map(ValencePolarity.init(wire:)),
                    last: tone.last.map(ValencePolarity.init(wire:))
                )
            },
            hours: hours.map { hours in
                MonthHours(items: hours.items.map(\.model),
                           busiest: hours.busiest.map { BusiestWindow(start: $0.start, count: $0.count) })
            }
        )
    }
}
