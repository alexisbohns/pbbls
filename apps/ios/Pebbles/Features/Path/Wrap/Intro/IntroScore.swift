import Foundation

/// The beats of each sequence of a month's intro (#1241 §Choreography). Pure,
/// over what the views already show after their own `lookups` filtering, so a
/// beat never names a row the page doesn't draw. The times are starting
/// values, tuned on the device (this plan's "As built"). The sections' builders
/// live in the extensions beside this file.
enum IntroScore {
    static let nameAt: TimeInterval = 0.12
    static let yearAt: TimeInterval = 0.24
    static let garlandAt: TimeInterval = 0.4
    static let garlandSpan: TimeInterval = 1.5
    static let stackGap: TimeInterval = 0.035
    static let leafAfter: TimeInterval = 0.12
    static let soulsAfter: TimeInterval = 0.2
    static let soulGap: TimeInterval = 0.06
    static let lineGap: TimeInterval = 0.14

    /// When a sequence has landed: its latest beat's end.
    static func duration(_ beats: [IntroBeat]) -> TimeInterval {
        beats.map { $0.at + $0.duration }.max() ?? 0
    }

    /// The cover: the month's name; the garland's days from the 1st (an empty
    /// day's socket, or its seals, a stacked one a step after the first) with
    /// their numerals, under the course drawing round; the leaf; then the
    /// souls' glyphs drawn and the `lines` shown (⊆ summary, names, colophon).
    static func cover(days: [CoverDay], daysInMonth: Int, numbered: [Int], souls: Int,
                      lines: [IntroKey]) -> [IntroBeat] {
        var beats = [
            IntroBeat(key: .kicker, at: 0, duration: IntroMotion.rise),
            IntroBeat(key: .monthName, at: nameAt, duration: IntroMotion.rise, tick: .light),
            IntroBeat(key: .year, at: yearAt, duration: IntroMotion.rise),
            IntroBeat(key: .course, at: garlandAt, duration: garlandSpan)
        ]
        let seals = Dictionary(days.map { ($0.day, $0.pebbles) }, uniquingKeysWith: { first, _ in first })
        let numerals = Set(numbered)
        let slots = IntroCurve.times(count: daysInMonth, duration: garlandSpan).map { garlandAt + $0 }
        let leafAt = garlandAt + garlandSpan + leafAfter
        for (day, at) in slots.enumerated() {
            let pebbles = seals[day] ?? []
            let next = day + 1 < slots.count ? slots[day + 1] : leafAt
            let stacked = stackedSeals(pebbles, day: day, at: at, before: next)
            // A day whose stacked seals can't all tick taps once, at its strongest seal.
            let silenced = stacked.contains { $0.tick == nil }
            let firmness = silenced ? pebbles.map(\.intensity).max() : pebbles.first?.intensity
            beats.append(IntroBeat(key: .slot(day), at: at, duration: IntroMotion.pop,
                                   tick: firmness.map { .seal($0) } ?? .faint))
            beats += stacked
            if numerals.contains(day) {
                beats.append(IntroBeat(key: .numeral(day), at: at, duration: IntroMotion.rise))
            }
        }
        var at = leafAt
        beats.append(IntroBeat(key: .leaf, at: at, duration: IntroMotion.pop, tick: .knot))
        at += soulsAfter
        for soul in 0..<souls {
            beats.append(IntroBeat(key: .soul(soul), at: at, duration: IntroMotion.pop, tick: .light))
            at += soulGap
        }
        for line in lines {
            beats.append(IntroBeat(key: line, at: at, duration: IntroMotion.rise, tick: .light))
            at += lineGap
        }
        return beats
    }

    /// A day's stacked seals, a step apart after its slot (#1241 I1). Each
    /// ticks only clear of the tick before it and of the next tick in the
    /// score (the next day's slot, or the leaf after the last day), by
    /// `IntroHaptics.floor`: otherwise the floor would drop it or that slot.
    private static func stackedSeals(_ pebbles: [CoverPebble], day: Int, at slot: TimeInterval,
                                     before next: TimeInterval) -> [IntroBeat] {
        var lastTick = slot
        return pebbles.enumerated().dropFirst().map { index, pebble in
            let at = slot + stackGap * Double(index)
            let fits = at - lastTick >= IntroHaptics.floor && next - at >= IntroHaptics.floor
            if fits { lastTick = at }
            return IntroBeat(key: .seal(day, index), at: at, duration: IntroMotion.pop,
                             tick: fits ? .seal(pebble.intensity) : nil)
        }
    }
}
