import Foundation

/// The cards whose "seen" the server keeps (D10); the raw values are `mark_achievements_seen`'s.
enum AchievementCard: String, CaseIterable, Sendable {
    case explorer, emotions, domains, pebbles, year
}

/// The achievements page as the views draw it (#1306, spec §4): the payload's
/// values in Swift types, each unknown enum on its safe fallback (D17). Nothing
/// here is counted, compared, ordered or picked: `AchievementsMapping` is the
/// one mapping, and it is one-to-one. Android: `AchievementsPage.kt`.
struct AchievementsPage: Equatable {
    var since: CalendarDay?
    var daysKept: Int?
    var explorer: Explorer?
    var emotions: Emotions?
    var domains: Domains?
    var pebbles: Ladder?
    var glyphsCarved: Ladder?
    var glyphSales: Ladder?
    var years: [Year]
    /// When each card was last looked at, at whole seconds (D10).
    var seen: [AchievementCard: Date]
    /// The owner's Profile shelf as served (D18), newest first.
    var shelf: [ShelfItem]
    var shelfCount: Int?

    /// The completion a ceremony stack pulls, wherever its card carries it.
    func completion(_ stack: CeremonyStack) -> Completion? {
        switch stack {
        case .sixFirsts: explorer?.completion
        case .emotions: emotions?.completion
        case .domains: domains?.completion
        }
    }
}

extension AchievementsPage {
    /// A crossing pebble (D15).
    struct Pebble: Equatable {
        let momentId: UUID?
        let name: String?
        let happenedAt: Date?
        let emotionId: String?
        let valence: Valence
    }

    /// The Explorer wheel's rings, centre out (D6). The raw values are `rings_done`'s.
    enum Ring: String, CaseIterable {
        case firstFive = "first_five"
        case sixFirsts = "six_firsts"
        case twentyFive = "twenty_five"
        case domains, emotions
    }

    /// The six firsts (D5), in the server's order.
    enum StoneKind: String, CaseIterable {
        case small, medium, large, lowlight, neutral, highlight
    }

    /// A set whose completion can be pulled (D9); the raw values are `pull_ceremony`'s.
    enum CeremonyStack: String, CaseIterable, Sendable {
        case emotions, domains
        case sixFirsts = "six_firsts"
    }

    /// What a first-five task made. Nil on a row is a kind this build does not know.
    enum SubjectKind: String, CaseIterable {
        case moment, pebble, collection, soul, glyph
    }

    struct FirstFive: Equatable {
        /// Nil for a slug this build does not know: its seat draws as a ghost and its row is left out.
        let task: HomeChecklist.Task?
        let done: Bool
        /// The day its subject was made.
        let on: CalendarDay?
        let subjectName: String?
        /// Nil for no subject, or a kind this build does not know (the row stays).
        let subjectKind: SubjectKind?
        let subjectId: UUID?
        let isNew: Bool
    }

    struct SixFirst: Equatable {
        /// Nil for a kind this build does not know: its seat draws, its row is left out.
        let kind: StoneKind?
        let done: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    struct TwentyFive: Equatable {
        /// Capped at 25 by the server.
        let count: Int
        let done: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    /// A set made whole (D9). `isReady` is the server's: no clock is compared here.
    struct Completion: Equatable {
        let stack: CeremonyStack
        let completedAt: Date?
        let offeredFrom: Date?
        let isReady: Bool
        let pulledAt: Date?
        /// The stone that made the set whole.
        let pebble: Pebble?
    }

    struct Explorer: Equatable {
        let firstPebble: Pebble?
        let firstFive: [FirstFive]
        let sixFirsts: [SixFirst]
        let twentyFive: TwentyFive?
        let ringsDone: Set<Ring>
        let completion: Completion?
    }

    struct Seat: Equatable {
        let seat: Int
        /// Nil when the server sent none: the seat draws, its row cannot name it.
        let emotionId: String?
        /// The family's slug, nil when absent; one with no palette draws in ink.
        let family: String?
        let met: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    /// One family of the Emotions sheet, in the server's order: its met seats by
    /// the day met, then the ones not met yet (seat indices into `seats`).
    struct EmotionFamily: Equatable {
        /// The family's slug, nil when absent; one with no palette draws in ink.
        let family: String?
        let met: [Int]
        let notYet: [Int]
    }

    struct Emotions: Equatable {
        let count: Int
        let of: Int
        /// In seat order (wheel order, then slug), as the server seats them.
        let seats: [Seat]
        /// The sheet's order: families on the wheel.
        let families: [EmotionFamily]
        /// The card's "since": the first emotion's day.
        let since: CalendarDay?
        let completion: Completion?

        /// The seat at `index`, as `families` names it.
        func seat(_ index: Int) -> Seat? {
            seats.first { $0.seat == index }
        }
    }

    /// D11's tiers. `unknown` is a tier this build does not know, which says nothing.
    enum Coverage: String {
        case none, few, third, half, most, all, unknown
    }

    struct DomainEntry: Equatable {
        /// Nil when the server sent none: the entry draws, its row cannot name it.
        let domainId: String?
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    struct Domains: Equatable {
        let count: Int
        let of: Int
        let coverage: Coverage
        /// Newest first, as served: the register's order.
        let entered: [DomainEntry]
        let notYet: [String]
        let completion: Completion?

        /// The order they came (the wheel's ring and the band): the served list read backwards.
        var arrival: [DomainEntry] { entered.reversed() }
    }

    struct Level: Equatable {
        let threshold: Int
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    struct Next: Equatable {
        let threshold: Int
        let left: Int
        /// Toward `threshold` from the level before it, in [0, 1].
        let progress: Double
    }

    struct Ladder: Equatable {
        let count: Int
        let levels: [Level]
        let next: Next?
        /// The steps the figure draws, one stage each.
        let ladder: [Int]
        let thenEvery: Int?
        /// The card's "since": the day of its first level.
        let since: CalendarDay?

        /// Whether the server lists `threshold` among the levels reached.
        func isReached(_ threshold: Int) -> Bool {
            levels.contains { $0.threshold == threshold }
        }

        /// The ladder's steps the server lists as reached, in ladder order.
        var reachedSteps: [Int] { ladder.filter(isReached) }
    }

    enum MonthState: String {
        case upcoming, quiet, kept
    }

    /// Which of a month's counters the server says reached their target (D12).
    struct MonthDone: Equatable {
        let moments: Bool
        let pebbles: Bool
        let weeks: Bool
    }

    struct Month: Equatable {
        let month: Int
        let state: MonthState
        let family: String?
        let momentDays: Int
        let pebbleDays: Int
        let fullWeeks: Int
        let weeksFilled: Int
        /// 0 at the centre, 1 on the inner disc, 2 on the rim.
        let level: Int
        /// Toward the next level, in [0, 1]; 0 on the rim.
        let progress: Double
        /// The gauges' done style, as served.
        let done: MonthDone

        /// Drawn quietly: a month with no pebble (NOTES, "quieter disc"), by its
        /// served state alone. A kept month whose family is absent or unresolved
        /// still draws, in ink.
        var isMuted: Bool { state != .kept }
    }

    enum Needs: String {
        case moments, pebbles
    }

    struct NextStep: Equatable {
        let month: Int
        let needs: Needs
        let daysLeft: Int
        let reachable: Bool
    }

    struct Year: Equatable, Identifiable {
        var id: Int { year }
        let year: Int
        let isCurrent: Bool
        /// The days a level needs, as served; nil when the server sent none.
        let daysTarget: Int?
        let months: [Month]
        /// The table's months, newest first, as served.
        let listed: [Int]
        let nextStep: NextStep?

        func month(_ number: Int) -> Month? {
            months.first { $0.month == number }
        }
    }

    /// One badge on the owner's Profile shelf (D18). Titles and glyphs resolve
    /// from the cached catalog by slug.
    struct ShelfItem: Equatable {
        let slug: String
        let family: String
        let threshold: Int?
        let unlockedOn: CalendarDay?
    }
}
