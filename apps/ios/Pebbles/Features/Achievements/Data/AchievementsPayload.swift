import Foundation

/// `get_achievements(p_tz)` (#1306; spec §3.3) as the server sends it. An
/// absent section omits its key, and so does an absent optional field (D17: an
/// unnamed moment has no `name`), so each is optional here; the one explicit
/// null served is `shelf[].threshold`. A section present but undecodable is
/// dropped and its key kept in `skipped` for the service's log, so one bad card
/// never blanks the page (spec §4). Inside a section a missing or null scalar
/// takes its default, a malformed day or id reads as absent, and a timestamp is
/// parsed from its string through `ISO8601Flexible`, never by a decoder's date
/// strategy. A null inside an array or map drops that element alone, and an
/// item missing a field keeps the item with that field nil (the shared
/// tolerance rules, spec §4). `AchievementsMapping` turns it into `AchievementsPage`.
/// Android: `AchievementsPayload.kt`.
struct AchievementsPayload: Decodable, Equatable {
    let since: CalendarDay?
    let daysKept: Int?
    let explorer: Explorer?
    let emotions: Emotions?
    let domains: Domains?
    let pebbles: Ladder?
    let glyphsCarved: Ladder?
    let glyphSales: Ladder?
    let years: [Year]?
    /// When each card was last looked at (D10), keyed by `mark_achievements_seen`'s
    /// card names. A time that does not parse is left out.
    let seen: [String: Date]
    /// The owner's Profile shelf (D18): up to six, newest first, private families removed.
    let shelf: [ShelfItem]?
    let shelfCount: Int?
    /// The keys present but undecodable.
    let skipped: [String]

    private enum CodingKeys: String, CodingKey {
        case since, explorer, emotions, domains, pebbles, years, seen, shelf
        case daysKept = "days_kept"
        case glyphsCarved = "glyphs_carved"
        case glyphSales = "glyph_sales"
        case shelfCount = "shelf_count"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        var skipped: [String] = []
        func section<T: Decodable>(_ key: CodingKeys) -> T? {
            guard c.contains(key) else { return nil }
            do {
                return try c.decodeIfPresent(T.self, forKey: key)
            } catch {
                skipped.append(key.stringValue)
                return nil
            }
        }
        since = (section(.since) as String?).flatMap(CalendarDay.init(iso:))
        daysKept = section(.daysKept)
        explorer = section(.explorer)
        emotions = section(.emotions)
        domains = section(.domains)
        pebbles = section(.pebbles)
        glyphsCarved = section(.glyphsCarved)
        glyphSales = section(.glyphSales)
        years = (section(.years) as [Year?]?)?.compactMap { $0 }
        let seenTimes: [String: String?] = section(.seen) ?? [:]
        seen = seenTimes.compactMapValues { $0.flatMap(ISO8601Flexible.parse) }
        shelf = (section(.shelf) as [ShelfItem?]?)?.compactMap { $0 }
        shelfCount = section(.shelfCount)
        self.skipped = skipped
    }
}

fileprivate extension KeyedDecodingContainer {
    /// An array whose null elements are dropped one by one, never failing the
    /// section; empty when absent or null (the shared tolerance rules, spec §4).
    func list<T: Decodable>(_ key: Key) throws -> [T] {
        (try decodeIfPresent([T?].self, forKey: key) ?? []).compactMap { $0 }
    }

    /// A `YYYY-MM-DD` day, or nil when absent, null or malformed.
    func day(_ key: Key) throws -> CalendarDay? {
        try decodeIfPresent(String.self, forKey: key).flatMap(CalendarDay.init(iso:))
    }

    /// A timestamp from its string form at any precision, or nil.
    func instant(_ key: Key) throws -> Date? {
        try decodeIfPresent(String.self, forKey: key).flatMap(ISO8601Flexible.parse)
    }

    /// A UUID from its string form, or nil when absent or malformed.
    func uuid(_ key: Key) throws -> UUID? {
        try decodeIfPresent(String.self, forKey: key).flatMap(UUID.init(uuidString:))
    }
}

extension AchievementsPayload {
    /// A crossing pebble's display snapshot (D15). A deleted moment's row still draws.
    struct Pebble: Decodable, Equatable {
        let momentId: UUID?
        let name: String?
        let happenedAt: Date?
        let emotionId: String?
        let intensity: Int
        let positiveness: Int

        private enum CodingKeys: String, CodingKey {
            case name, intensity, positiveness
            case momentId = "moment_id"
            case happenedAt = "happened_at"
            case emotionId = "emotion_id"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            momentId = try c.uuid(.momentId)
            name = try c.decodeIfPresent(String.self, forKey: .name)
            happenedAt = try c.instant(.happenedAt)
            emotionId = try c.decodeIfPresent(String.self, forKey: .emotionId)
            intensity = try c.decode(.intensity, or: 2)
            positiveness = try c.decode(.positiveness, or: 0)
        }
    }

    /// A set made whole (D9). `ready` is the server's, `pulled_at` absent until
    /// pulled, `pebble` the stone that made the set whole.
    struct Completion: Decodable, Equatable {
        let completedAt: Date?
        let offeredFrom: Date?
        let ready: Bool
        let pulledAt: Date?
        let pebble: Pebble?

        private enum CodingKeys: String, CodingKey {
            case ready, pebble
            case completedAt = "completed_at"
            case offeredFrom = "offered_from"
            case pulledAt = "pulled_at"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            completedAt = try c.instant(.completedAt)
            offeredFrom = try c.instant(.offeredFrom)
            ready = try c.decode(.ready, or: false)
            pulledAt = try c.instant(.pulledAt)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
        }
    }

    struct Explorer: Decodable, Equatable {
        let firstPebble: Pebble?
        let firstFive: [FirstFive]
        let sixFirsts: [SixFirst]
        let twentyFive: TwentyFive?
        let ringsDone: [String]
        let completion: Completion?

        private enum CodingKeys: String, CodingKey {
            case completion
            case firstPebble = "first_pebble"
            case firstFive = "first_five"
            case sixFirsts = "six_firsts"
            case twentyFive = "twenty_five"
            case ringsDone = "rings_done"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            firstPebble = try c.decodeIfPresent(Pebble.self, forKey: .firstPebble)
            firstFive = try c.list(.firstFive)
            sixFirsts = try c.list(.sixFirsts)
            twentyFive = try c.decodeIfPresent(TwentyFive.self, forKey: .twentyFive)
            ringsDone = try c.list(.ringsDone)
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    /// One of the first five; `on` is the day its subject was made.
    struct FirstFive: Decodable, Equatable {
        let slug: String
        let done: Bool
        let on: CalendarDay?
        let subject: Subject?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case slug, done, on, subject
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            slug = try c.decode(.slug, or: "")
            done = try c.decode(.done, or: false)
            on = try c.day(.on)
            subject = try c.decodeIfPresent(Subject.self, forKey: .subject)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    /// What a first-five task made: a moment, a pebble (its moment's id), a collection, a soul or a glyph.
    struct Subject: Decodable, Equatable {
        let kind: String
        let id: UUID?
        let name: String?

        private enum CodingKeys: String, CodingKey { case kind, id, name }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            kind = try c.decode(.kind, or: "")
            id = try c.uuid(.id)
            name = try c.decodeIfPresent(String.self, forKey: .name)
        }
    }

    struct SixFirst: Decodable, Equatable {
        let kind: String
        let done: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case kind, done, on, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            kind = try c.decode(.kind, or: "")
            done = try c.decode(.done, or: false)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct TwentyFive: Decodable, Equatable {
        let count: Int
        let done: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case count, done, on, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            done = try c.decode(.done, or: false)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct Emotions: Decodable, Equatable {
        let count: Int
        let of: Int
        let seats: [Seat]
        let families: [Family]
        let since: CalendarDay?
        let completion: Completion?

        private enum CodingKeys: String, CodingKey { case count, of, seats, families, since, completion }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            of = try c.decode(.of, or: 0)
            seats = try c.list(.seats)
            families = try c.list(.families)
            since = try c.day(.since)
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    struct Seat: Decodable, Equatable {
        let seat: Int
        let emotionId: String?
        let family: String?
        let met: Bool
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case seat, family, met, on, pebble
            case emotionId = "emotion_id"
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            seat = try c.decode(.seat, or: 0)
            emotionId = try c.decodeIfPresent(String.self, forKey: .emotionId)
            family = try c.decodeIfPresent(String.self, forKey: .family)
            met = try c.decode(.met, or: false)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    /// The Emotions sheet's order: one family on the wheel, its met seats by the
    /// day met, then the seats not met yet.
    struct Family: Decodable, Equatable {
        let family: String?
        let met: [Int]
        let notYet: [Int]

        private enum CodingKeys: String, CodingKey {
            case family, met
            case notYet = "not_yet"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            family = try c.decodeIfPresent(String.self, forKey: .family)
            met = try c.list(.met)
            notYet = try c.list(.notYet)
        }
    }

    struct Domains: Decodable, Equatable {
        let count: Int
        let of: Int
        let coverage: String
        let entered: [Entered]
        let notYet: [String]
        let completion: Completion?

        private enum CodingKeys: String, CodingKey {
            case count, of, coverage, entered, completion
            case notYet = "not_yet"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            of = try c.decode(.of, or: 0)
            coverage = try c.decode(.coverage, or: "")
            entered = try c.list(.entered)
            notYet = try c.list(.notYet)
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    struct Entered: Decodable, Equatable {
        let domainId: String?
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case on, pebble
            case domainId = "domain_id"
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            domainId = try c.decodeIfPresent(String.self, forKey: .domainId)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    /// One counting stack (D3): pebbles, glyphs carved or glyph sales. `since`
    /// is the day of its first level, absent before it.
    struct Ladder: Decodable, Equatable {
        let count: Int
        let levels: [Level]
        let next: Next?
        let ladder: [Int]
        let thenEvery: Int?
        let since: CalendarDay?

        private enum CodingKeys: String, CodingKey {
            case count, levels, next, ladder, since
            case thenEvery = "then_every"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            levels = try c.list(.levels)
            next = try c.decodeIfPresent(Next.self, forKey: .next)
            ladder = try c.list(.ladder)
            thenEvery = try c.decodeIfPresent(Int.self, forKey: .thenEvery)
            since = try c.day(.since)
        }
    }

    struct Level: Decodable, Equatable {
        let threshold: Int
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case threshold, on, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            threshold = try c.decode(.threshold, or: 0)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct Next: Decodable, Equatable {
        let threshold: Int
        let left: Int
        let progress: Double

        init(threshold: Int, left: Int, progress: Double) {
            self.threshold = threshold
            self.left = left
            self.progress = progress
        }

        private enum CodingKeys: String, CodingKey { case threshold, left, progress }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            threshold = try c.decode(.threshold, or: 0)
            left = try c.decode(.left, or: 0)
            progress = try c.decode(.progress, or: 0)
        }
    }

    /// One year disc (D12). `days_target` is the days a level needs, served so
    /// no client writes 14.
    struct Year: Decodable, Equatable {
        let year: Int
        let current: Bool
        let daysTarget: Int?
        let months: [Month]
        let listed: [Int]
        let nextStep: NextStep?

        private enum CodingKeys: String, CodingKey {
            case year, current, months, listed
            case daysTarget = "days_target"
            case nextStep = "next_step"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            year = try c.decode(.year, or: 0)
            current = try c.decode(.current, or: false)
            daysTarget = try c.decodeIfPresent(Int.self, forKey: .daysTarget)
            months = try c.list(.months)
            listed = try c.list(.listed)
            nextStep = try c.decodeIfPresent(NextStep.self, forKey: .nextStep)
        }
    }

    struct Month: Decodable, Equatable {
        let month: Int
        let state: String
        let family: String?
        let momentDays: Int
        let pebbleDays: Int
        let fullWeeks: Int
        let weeksFilled: Int
        let level: Int
        let progress: Double
        let done: Done

        private enum CodingKeys: String, CodingKey {
            case month, state, family, level, progress, done
            case momentDays = "moment_days"
            case pebbleDays = "pebble_days"
            case fullWeeks = "full_weeks"
            case weeksFilled = "weeks_filled"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            month = try c.decode(.month, or: 0)
            state = try c.decode(.state, or: "")
            family = try c.decodeIfPresent(String.self, forKey: .family)
            momentDays = try c.decode(.momentDays, or: 0)
            pebbleDays = try c.decode(.pebbleDays, or: 0)
            fullWeeks = try c.decode(.fullWeeks, or: 0)
            weeksFilled = try c.decode(.weeksFilled, or: 0)
            level = try c.decode(.level, or: 0)
            progress = try c.decode(.progress, or: 0)
            done = try c.decode(.done, or: Done(moments: false, pebbles: false, weeks: false))
        }
    }

    /// Which of a month's counters reached their target: the gauges' done style,
    /// the server's call (D12), so no client compares a count with `days_target`.
    struct Done: Decodable, Equatable {
        let moments: Bool
        let pebbles: Bool
        let weeks: Bool

        init(moments: Bool, pebbles: Bool, weeks: Bool) {
            self.moments = moments
            self.pebbles = pebbles
            self.weeks = weeks
        }

        private enum CodingKeys: String, CodingKey { case moments, pebbles, weeks }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            moments = try c.decode(.moments, or: false)
            pebbles = try c.decode(.pebbles, or: false)
            weeks = try c.decode(.weeks, or: false)
        }
    }

    struct NextStep: Decodable, Equatable {
        let month: Int
        let needs: String
        let daysLeft: Int
        let reachable: Bool

        init(month: Int, needs: String, daysLeft: Int, reachable: Bool) {
            self.month = month
            self.needs = needs
            self.daysLeft = daysLeft
            self.reachable = reachable
        }

        private enum CodingKeys: String, CodingKey {
            case month, needs, reachable
            case daysLeft = "days_left"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            month = try c.decode(.month, or: 0)
            needs = try c.decode(.needs, or: "")
            daysLeft = try c.decode(.daysLeft, or: 0)
            reachable = try c.decode(.reachable, or: true)
        }
    }

    /// One badge on the owner's Profile shelf (D18), the row `get_public_profile`'s
    /// shelf serves: `threshold` is an explicit null for the families that count nothing.
    struct ShelfItem: Decodable, Equatable {
        let slug: String
        let family: String
        let threshold: Int?
        let unlockedOn: CalendarDay?

        init(slug: String, family: String, threshold: Int?, unlockedOn: CalendarDay?) {
            self.slug = slug
            self.family = family
            self.threshold = threshold
            self.unlockedOn = unlockedOn
        }

        private enum CodingKeys: String, CodingKey {
            case slug, family, threshold
            case unlockedOn = "unlocked_on"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            slug = try c.decode(.slug, or: "")
            family = try c.decode(.family, or: "")
            threshold = try c.decodeIfPresent(Int.self, forKey: .threshold)
            unlockedOn = try c.day(.unlockedOn)
        }
    }
}
