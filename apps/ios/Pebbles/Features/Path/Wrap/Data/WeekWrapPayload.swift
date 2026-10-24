import Foundation

/// `path_week_wrap(p_week_start, p_tz)` (#1226), as the server sends it. The
/// server omits an absent tile's key, so each tile's data is nil here. Mapped to
/// `WeekWrap` by `model`.
struct WeekWrapPayload: Decodable {
    let weekStart: CalendarDay
    let isoWeek: Int
    let isoYear: Int
    let live: Bool
    let count: Int
    let peopleCount: Int
    let tiles: [String]
    let persons: [PersonPayload]?
    let inviteMomentIds: [String]?
    let families: [FamilyPayload]?
    let lead: LeadPayload?
    let domains: [DomainPayload]?
    let weight: WeightPayload?
    let hours: [HourPayload]?
    let kept: KeptPayload?

    private enum CodingKeys: String, CodingKey {
        case live, count, tiles, persons, families, lead, domains, weight, hours, kept
        case weekStart = "week_start"
        case isoWeek = "iso_week"
        case isoYear = "iso_year"
        case peopleCount = "people_count"
        case inviteMomentIds = "invite_moment_ids"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        weekStart = try c.decode(CalendarDay.self, forKey: .weekStart)
        isoWeek = try c.decode(.isoWeek, or: 0)
        isoYear = try c.decode(.isoYear, or: 0)
        live = try c.decode(.live, or: false)
        count = try c.decode(.count, or: 0)
        peopleCount = try c.decode(.peopleCount, or: 0)
        tiles = try c.decode(.tiles, or: [])
        persons = try c.decodeIfPresent([PersonPayload].self, forKey: .persons)
        inviteMomentIds = try c.decodeIfPresent([String].self, forKey: .inviteMomentIds)
        families = try c.decodeIfPresent([FamilyPayload].self, forKey: .families)
        lead = try c.decodeIfPresent(LeadPayload.self, forKey: .lead)
        domains = try c.decodeIfPresent([DomainPayload].self, forKey: .domains)
        weight = try c.decodeIfPresent(WeightPayload.self, forKey: .weight)
        hours = try c.decodeIfPresent([HourPayload].self, forKey: .hours)
        kept = try c.decodeIfPresent(KeptPayload.self, forKey: .kept)
    }
}

/// Pebble counts by intensity 1..3, per valence.
struct WeightPayload: Decodable {
    let high: [Int]
    let neutral: [Int]
    let low: [Int]

    private enum CodingKeys: String, CodingKey { case high, neutral, low }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        high = try c.decode(.high, or: [])
        neutral = try c.decode(.neutral, or: [])
        low = try c.decode(.low, or: [])
    }
}

struct KeptPayload: Decodable {
    let photoMomentIds: [String]
    let cards: [CardCountPayload]

    private enum CodingKeys: String, CodingKey {
        case cards
        case photoMomentIds = "photo_moment_ids"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        photoMomentIds = try c.decode(.photoMomentIds, or: [])
        cards = try c.decode(.cards, or: [])
    }
}

struct CardCountPayload: Decodable {
    let momentId: String
    let count: Int

    private enum CodingKeys: String, CodingKey {
        case count
        case momentId = "moment_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        momentId = try c.decode(.momentId, or: "")
        count = try c.decode(.count, or: 0)
    }
}
