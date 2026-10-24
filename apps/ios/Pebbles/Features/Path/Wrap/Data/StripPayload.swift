import Foundation

/// `path_strip(p_tz)` (#1226), as the server sends it. Mapped to `Strip` by `model`.
struct StripPayload: Decodable {
    let items: [StripItemPayload]

    private enum CodingKeys: String, CodingKey { case items }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = try c.decode(.items, or: [])
    }
}

/// A week (`kind = "week"`) or a month (`kind = "month"`); the other kind's keys are absent.
/// A day is read only for its own kind, so an unknown kind is dropped whatever it carries.
struct StripItemPayload: Decodable {
    let kind: String
    let weekStart: CalendarDay?
    let isoWeek: Int
    let isoYear: Int
    let count: Int
    let focusable: Bool
    let live: Bool
    /// Seven `[moments, strongest intensity]` pairs, Monday first.
    let days: [[Int]]
    let month: CalendarDay?

    private enum CodingKeys: String, CodingKey {
        case kind, count, focusable, live, days, month
        case weekStart = "week_start"
        case isoWeek = "iso_week"
        case isoYear = "iso_year"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decode(.kind, or: "")
        isoWeek = try c.decode(.isoWeek, or: 0)
        isoYear = try c.decode(.isoYear, or: 0)
        count = try c.decode(.count, or: 0)
        focusable = try c.decode(.focusable, or: false)
        live = try c.decode(.live, or: false)
        days = try c.decode(.days, or: [])
        weekStart = kind == "week" ? try c.decodeIfPresent(CalendarDay.self, forKey: .weekStart) : nil
        month = kind == "month" ? try c.decodeIfPresent(CalendarDay.self, forKey: .month) : nil
    }
}
