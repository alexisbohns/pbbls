import Foundation

/// `home_wraps(p_tz)`, as the server sends it (spec §1.3): the months that hold
/// a pebble, each with its count, its reveal state and its garland cover. A
/// month this build cannot read is dropped, never fatal: a malformed item fails
/// that one month, never Home. Mapped to `HomeWraps` by `model`.
struct HomeWrapsPayload: Decodable {
    let months: [HomeMonthPayload]

    private enum CodingKeys: String, CodingKey { case months }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let items: [Lossy<HomeMonthPayload>] = try c.decode(.months, or: [])
        months = items.compactMap(\.value)
    }
}

/// Decodes to nil instead of throwing, and always consumes its array slot.
private struct Lossy<T: Decodable>: Decodable {
    let value: T?

    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
}

struct HomeMonthPayload: Decodable {
    let month: CalendarDay
    let count: Int
    /// The raw timestamp, or nil while the month is folded.
    let revealedAt: String?
    let cover: CoverPayload

    private enum CodingKeys: String, CodingKey {
        case month, count, cover
        case revealedAt = "revealed_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        month = try c.decode(CalendarDay.self, forKey: .month)
        count = try c.decode(.count, or: 0)
        revealedAt = try c.decodeIfPresent(String.self, forKey: .revealedAt)
        cover = try c.decode(CoverPayload.self, forKey: .cover)
    }
}

/// `reveal_wrap(p_month, p_tz)`: the month and when it was revealed (D5).
struct HomeRevealPayload: Decodable {
    let month: CalendarDay
    let revealedAt: String?

    private enum CodingKeys: String, CodingKey {
        case month
        case revealedAt = "revealed_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        month = try c.decode(CalendarDay.self, forKey: .month)
        revealedAt = try c.decodeIfPresent(String.self, forKey: .revealedAt)
    }
}

extension HomeWrapsPayload {
    /// Newest month first, as Home lists them.
    var model: HomeWraps {
        HomeWraps(months: months.map(\.model).sorted { $0.month > $1.month })
    }
}

extension HomeMonthPayload {
    var model: HomeMonth {
        HomeMonth(
            month: CalendarMonth(month),
            count: count,
            // Tolerant: any ISO-8601 precision; an unparseable stamp reads as folded.
            revealedAt: revealedAt.flatMap(ISO8601Flexible.parse),
            cover: MonthCover(
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
        )
    }
}

extension HomeRevealPayload {
    var model: HomeReveal {
        HomeReveal(month: CalendarMonth(month), revealedAt: revealedAt.flatMap(ISO8601Flexible.parse))
    }
}
