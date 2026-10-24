import Foundation

/// `path_month_wrap(p_month, p_tz)` (#1226), as the server sends it. The server
/// omits an absent section's keys, so each section is nil here. Mapped to
/// `MonthWrap` by `model`.
struct MonthWrapPayload: Decodable {
    let month: CalendarDay
    let daysInMonth: Int
    /// 0 = Monday.
    let firstWeekday: Int
    let count: Int
    let peopleCount: Int
    let sections: [String]
    let cover: CoverPayload?
    let weeks: [MonthWeekPayload]?
    let milestones: [String]?
    let persons: [PersonPayload]?
    let previousMonth: PreviousMonthPayload?
    let gem: GemPayload?
    let fineEmotions: [FineEmotionPayload]?
    let domains: [DomainPayload]?
    let tone: TonePayload?
    let hours: MonthHoursPayload?

    private enum CodingKeys: String, CodingKey {
        case month, count, sections, cover, weeks, milestones, persons, gem, domains, tone, hours
        case daysInMonth = "days_in_month"
        case firstWeekday = "first_weekday"
        case peopleCount = "people_count"
        case previousMonth = "previous_month"
        case fineEmotions = "fine_emotions"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        month = try c.decode(CalendarDay.self, forKey: .month)
        daysInMonth = try c.decode(.daysInMonth, or: 0)
        firstWeekday = try c.decode(.firstWeekday, or: 0)
        count = try c.decode(.count, or: 0)
        peopleCount = try c.decode(.peopleCount, or: 0)
        sections = try c.decode(.sections, or: [])
        cover = try c.decodeIfPresent(CoverPayload.self, forKey: .cover)
        weeks = try c.decodeIfPresent([MonthWeekPayload].self, forKey: .weeks)
        milestones = try c.decodeIfPresent([String].self, forKey: .milestones)
        persons = try c.decodeIfPresent([PersonPayload].self, forKey: .persons)
        previousMonth = try c.decodeIfPresent(PreviousMonthPayload.self, forKey: .previousMonth)
        gem = try c.decodeIfPresent(GemPayload.self, forKey: .gem)
        fineEmotions = try c.decodeIfPresent([FineEmotionPayload].self, forKey: .fineEmotions)
        domains = try c.decodeIfPresent([DomainPayload].self, forKey: .domains)
        tone = try c.decodeIfPresent(TonePayload.self, forKey: .tone)
        hours = try c.decodeIfPresent(MonthHoursPayload.self, forKey: .hours)
    }
}

struct CoverPayload: Decodable {
    let days: [CoverDayPayload]
    let soulsInOrder: [String]
    let inks: [String]
    let papers: [String]

    private enum CodingKeys: String, CodingKey {
        case days, inks, papers
        case soulsInOrder = "souls_in_order"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        days = try c.decode(.days, or: [])
        soulsInOrder = try c.decode(.soulsInOrder, or: [])
        inks = try c.decode(.inks, or: [])
        papers = try c.decode(.papers, or: [])
    }
}

struct CoverDayPayload: Decodable {
    let day: Int
    let pebbles: [CoverPebblePayload]

    private enum CodingKeys: String, CodingKey { case day, pebbles }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        day = try c.decode(.day, or: 0)
        pebbles = try c.decode(.pebbles, or: [])
    }
}

struct CoverPebblePayload: Decodable {
    let momentId: String
    let emotionId: String?
    let intensity: Int
    let polarity: Int

    private enum CodingKeys: String, CodingKey {
        case intensity, polarity
        case momentId = "moment_id"
        case emotionId = "emotion_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        momentId = try c.decode(.momentId, or: "")
        emotionId = try c.decodeIfPresent(String.self, forKey: .emotionId)
        intensity = try c.decode(.intensity, or: 0)
        polarity = try c.decode(.polarity, or: 0)
    }
}

struct MonthWeekPayload: Decodable {
    let weekStart: CalendarDay
    let from: Int
    let to: Int
    let count: Int
    let soulIds: [String]
    let days: [[Int]]

    private enum CodingKeys: String, CodingKey {
        case from, to, count, days
        case weekStart = "week_start"
        case soulIds = "soul_ids"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        weekStart = try c.decode(CalendarDay.self, forKey: .weekStart)
        from = try c.decode(.from, or: 0)
        to = try c.decode(.to, or: 0)
        count = try c.decode(.count, or: 0)
        soulIds = try c.decode(.soulIds, or: [])
        days = try c.decode(.days, or: [])
    }
}

struct PreviousMonthPayload: Decodable {
    let count: Int
    let soulIds: [String]

    private enum CodingKeys: String, CodingKey {
        case count
        case soulIds = "soul_ids"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        count = try c.decode(.count, or: 0)
        soulIds = try c.decode(.soulIds, or: [])
    }
}

/// `facets` maps all 28 data facets to a family slug, or null for an uncoloured facet.
struct GemPayload: Decodable {
    let worths: [String: Double]
    let shares: [String: Double]
    let main: String?
    let rotation: Int
    let facets: [String: String?]
    let lead: LeadPayload?

    private enum CodingKeys: String, CodingKey { case worths, shares, main, rotation, facets, lead }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        worths = try c.decode(.worths, or: [:])
        shares = try c.decode(.shares, or: [:])
        main = try c.decodeIfPresent(String.self, forKey: .main)
        rotation = try c.decode(.rotation, or: 0)
        facets = try c.decode(.facets, or: [:])
        lead = try c.decodeIfPresent(LeadPayload.self, forKey: .lead)
    }
}

struct FineEmotionPayload: Decodable {
    let family: String
    let emotionId: String
    let count: Int
    let days: [Int]

    private enum CodingKeys: String, CodingKey {
        case family, count, days
        case emotionId = "emotion_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        family = try c.decode(.family, or: "")
        emotionId = try c.decode(.emotionId, or: "")
        count = try c.decode(.count, or: 0)
        days = try c.decode(.days, or: [])
    }
}

/// `days[i]` is `[up, even, down]`: the day's summed intensities of bright, even and heavy pebbles.
struct TonePayload: Decodable {
    let days: [[Int]]
    let verdict: String
    let first: Int?
    let last: Int?

    private enum CodingKeys: String, CodingKey { case days, verdict, first, last }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        days = try c.decode(.days, or: [])
        verdict = try c.decode(.verdict, or: "")
        first = try c.decodeIfPresent(Int.self, forKey: .first)
        last = try c.decodeIfPresent(Int.self, forKey: .last)
    }
}

struct MonthHoursPayload: Decodable {
    let items: [HourPayload]
    let busiest: BusiestPayload?

    private enum CodingKeys: String, CodingKey { case items, busiest }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = try c.decode(.items, or: [])
        busiest = try c.decodeIfPresent(BusiestPayload.self, forKey: .busiest)
    }
}

struct BusiestPayload: Decodable {
    let start: Int
    let count: Int

    private enum CodingKeys: String, CodingKey { case start, count }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        start = try c.decode(.start, or: 0)
        count = try c.decode(.count, or: 0)
    }
}
