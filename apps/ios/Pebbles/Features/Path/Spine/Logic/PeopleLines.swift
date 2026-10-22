import Foundation

/// Who a pebble was with, as one line (#1130 R7): 0 → no line · 1 → A ·
/// 2 → A & B · 3 → A, B & C · n ≥ 4 → A, B "& {n − 2 in words} others", up to
/// five marks of the others, then "+{n − 2 − 5}" when positive.
struct PeopleLine: Equatable {
    /// Soul ids set by name; empty means the pebble is solitary: no line at all.
    let named: [String]
    /// How many souls fold into "& N others" (0 when everyone is named).
    let others: Int
    /// `others` in words ("twelve"); "" when 0.
    let othersWords: String
    /// Soul ids of the others drawn as marks, at most five.
    let marks: [String]
    /// Others left after the marks, shown as "+N"; 0 means no "+".
    let overflow: Int
}

/// A port of `apps/web/lib/utils/spine/people-line.ts`.
enum PeopleLines {
    static let marks = 5
    /// The held card's limit: it names up to eight souls in full (its `namedInFull`).
    static let namedOnCard = 8

    /// - Parameters:
    ///   - soulIds: the pebble's souls.
    ///   - order: soul ids in persons-of-the-week order; ids missing from it keep
    ///     their relative order after the ranked ones. A repeated id ranks by its
    ///     first index here, where the web's `Map` keeps the last; persons-of-the-week
    ///     never repeats an id, so the two agree on every real input.
    ///   - namedInFull: up to this many souls are all named (the line's 3, the card's 8).
    ///     At least 3: past it the line names two and folds the rest, and under 3
    ///     would fold a single soul into "one others".
    static func peopleLine(_ soulIds: [String], order: [String], locale: Locale, namedInFull: Int = 3) -> PeopleLine {
        precondition(namedInFull >= 3, "namedInFull is at least 3 (the line's 3, the card's 8), got \(namedInFull)")
        var rank: [String: Int] = [:]
        for (index, id) in order.enumerated() where rank[id] == nil { rank[id] = index }
        var seen = Set<String>()
        let ranked = soulIds.filter { seen.insert($0).inserted }
            .enumerated()
            .sorted { lhs, rhs in
                let left = rank[lhs.element] ?? .max
                let right = rank[rhs.element] ?? .max
                return left != right ? left < right : lhs.offset < rhs.offset
            }
            .map(\.element)
        guard ranked.count > namedInFull else {
            return PeopleLine(named: ranked, others: 0, othersWords: "", marks: [], overflow: 0)
        }
        let rest = Array(ranked.dropFirst(2))
        let marked = Array(rest.prefix(marks))
        // "others" is "autres" in French, the same for either gender above one.
        return PeopleLine(named: Array(ranked.prefix(2)), others: rest.count,
                          othersWords: NumberWords.cardinal(rest.count, locale: locale),
                          marks: marked, overflow: rest.count - marked.count)
    }
}
